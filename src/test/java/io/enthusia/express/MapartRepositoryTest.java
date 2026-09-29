package io.enthusia.express;

import static org.junit.jupiter.api.Assertions.*;
import io.enthusia.express.domain.*;
import io.enthusia.express.infrastructure.db.MailRepository;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapartRepositoryTest {
  @TempDir Path directory;

  @Test void sharedIntakeIsIdempotentSeparateAndSurvivesRestart() {
    var file = directory.resolve("mail.db").toFile();
    var sender = UUID.randomUUID();
    var token = UUID.randomUUID();
    var repository = open();
    long id = repository.insertMapart(sender, "Artist", new byte[] {1}, 42, "Museum Map", token).join();
    assertEquals(id, repository.insertMapart(sender, "Artist", new byte[] {1}, 42, "Museum Map", token).join());
    assertEquals(MapartQueue.ID, repository.get(id).join().recipient());
    assertTrue(repository.listInbox(sender, MailType.PACKAGE).join().isEmpty());
    assertTrue(repository.listSent(sender, MailType.PACKAGE, 0).join().isEmpty());
    repository.close();
    repository = new MailRepository(null, file, 5000);
    try {
      repository.initialize().join();
      assertEquals(id, repository.listMapart(0, false).join().getFirst().getMail().id());
      assertEquals(42, repository.getMapart(id).join().getMapId());
      assertFalse(repository.markMapartProcessed(id, UUID.randomUUID()).join());
    } finally { repository.close(); }
  }

  @Test void exactlyOneManagerClaimsAndAnotherCanProcessAfterDelivery() {
    var repository = open();
    try {
      var first = UUID.randomUUID(); var second = UUID.randomUUID();
      long id = repository.insertMapart(UUID.randomUUID(), "Artist", new byte[] {1}, 2, "Map", UUID.randomUUID()).join();
      var record = repository.get(id).join();
      assertTrue(repository.claimMapart(record, first, "First").join());
      assertFalse(repository.claimMapart(record, second, "Second").join());
      assertEquals(first, repository.get(id).join().recipient());
      assertFalse(repository.markMapartProcessed(id, second).join());
      assertTrue(repository.confirmDelivery(id, first).join());
      assertFalse(repository.confirmDelivery(id, second).join());
      assertTrue(repository.markMapartProcessed(id, second).join());
      assertFalse(repository.markMapartProcessed(id, first).join());
      assertEquals(second, repository.getMapart(id).join().getProcessedBy());
      assertEquals(1, repository.listMapart(0, true).join().size());
    } finally { repository.close(); }
  }

  @Test void failedDeliveryRequeuesWithNewGenerationForAnotherManager() {
    var repository = open();
    try {
      var first = UUID.randomUUID(); var second = UUID.randomUUID();
      long id = repository.insertMapart(UUID.randomUUID(), "Artist", new byte[] {1}, null, "Map", UUID.randomUUID()).join();
      var original = repository.get(id).join();
      assertTrue(repository.claimMapart(original, first, "First").join());
      var bound = original.copy(original.id(), original.sender(), original.senderName(), first, "First",
          original.type(), original.status(), original.payload(), original.packedItemCount(),
          original.createdAt(), original.updatedAt(), original.unread(), original.returnDelivery(),
          original.claimGeneration());
      assertTrue(repository.restoreClaim(bound).join());
      var requeued = repository.get(id).join();
      assertEquals(MapartQueue.ID, requeued.recipient());
      assertEquals(MailStatus.UNCLAIMED, requeued.status());
      assertTrue(requeued.claimGeneration() > original.claimGeneration());
      assertFalse(repository.claimMapart(original, second, "Second").join());
      assertTrue(repository.claimMapart(requeued, second, "Second").join());
      assertFalse(repository.restoreClaim(bound).join());
      assertEquals(second, repository.get(id).join().recipient());
    } finally { repository.close(); }
  }

  @Test void mapartDoesNotExpireWithPersonalPackages() {
    var repository = open();
    try {
      long id = repository.insertMapart(UUID.randomUUID(), "A", new byte[] {1}, 7, "Landscape", UUID.randomUUID()).join();
      repository.expire(System.currentTimeMillis() + 1_000_000, Long.MAX_VALUE, 0, 0).join();
      assertEquals(MailStatus.UNCLAIMED, repository.get(id).join().status());
    } finally { repository.close(); }
  }

  @Test void personalMailBlockDoesNotHideDedicatedMuseumIntake() {
    var repository = open();
    try {
      var artist = UUID.randomUUID(); var manager = UUID.randomUUID();
      repository.setBlocked(manager, artist, "Artist", true).join();
      long id = repository.insertMapart(artist, "Artist", new byte[] {1}, 1, "Art", UUID.randomUUID()).join();
      assertEquals(id, repository.listMapart(0, false).join().getFirst().getMail().id());
      assertTrue(repository.listInbox(manager, MailType.PACKAGE).join().isEmpty());
    } finally { repository.close(); }
  }

  @Test void sharedInboxPaginatesWithoutLoadingPayloads() {
    var repository = open();
    try {
      for (int i = 0; i < 30; i++) repository.insertMapart(UUID.randomUUID(), "Artist",
          new byte[] {1, 2, 3}, i, "Same Name", UUID.randomUUID()).join();
      var first = repository.listMapart(0, false).join();
      var second = repository.listMapart(1, false).join();
      assertEquals(27, first.size()); assertEquals(3, second.size());
      assertEquals(0, first.getFirst().getMail().payload().length);
      assertEquals(3, repository.getMapart(first.getFirst().getMail().id()).join().getMail().payload().length);
    } finally { repository.close(); }
  }

  @Test void legacyManagerRowsMigrateOnlyWhenUnclaimed() throws Exception {
    var file = directory.resolve("mail.db").toFile();
    var repository = open();
    var oldManager = UUID.randomUUID();
    long waiting = repository.insertMapart(UUID.randomUUID(), "A", new byte[] {1}, 1, "Waiting", UUID.randomUUID()).join();
    long delivered = repository.insertMapart(UUID.randomUUID(), "B", new byte[] {2}, 2, "Delivered", UUID.randomUUID()).join();
    assertTrue(repository.claimMapart(repository.get(delivered).join(), oldManager, "Old").join());
    assertTrue(repository.confirmDelivery(delivered, oldManager).join());
    repository.close();
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
         var statement = connection.prepareStatement("UPDATE mail SET recipient_uuid=? WHERE id=?")) {
      statement.setString(1, oldManager.toString()); statement.setLong(2, waiting);
      statement.executeUpdate();
    }
    repository = new MailRepository(null, file, 5000);
    try {
      repository.initialize().join();
      assertEquals(MapartQueue.ID, repository.get(waiting).join().recipient());
      assertEquals(oldManager, repository.get(delivered).join().recipient());
    } finally { repository.close(); }
  }

  private MailRepository open() {
    var repository = new MailRepository(null, directory.resolve("mail.db").toFile(), 5000);
    repository.initialize().join(); return repository;
  }
}
