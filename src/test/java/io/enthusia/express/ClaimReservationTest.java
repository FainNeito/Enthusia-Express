package io.enthusia.express;

import static org.junit.jupiter.api.Assertions.*;
import io.enthusia.express.infrastructure.db.MailRepository;
import io.enthusia.express.domain.MailType;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClaimReservationTest {
  @TempDir Path directory;
  /** Verifies that undelivered claim keeps outstanding allowance reserved. */

  @Test void undeliveredClaimKeepsOutstandingAllowanceReserved() {
    var file = directory.resolve("mail.db").toFile();
    var a = new MailRepository(null, file, 5000);
    var b = new MailRepository(null, file, 5000);
    try {
      a.initialize().join();
      b.initialize().join();
      UUID sender = UUID.randomUUID();
      UUID recipient = UUID.randomUUID();
      long first = a.insertMailLimited(sender, "S", recipient, "R", MailType.PACKAGE, new byte[]{1}, 1, true).join().orElseThrow();
      var before = a.get(first).join();
      assertTrue(a.claim(first, recipient).join());
      assertTrue(b.insertMailLimited(sender, "S", recipient, "R", MailType.PACKAGE, new byte[]{2}, 1, true).join().isEmpty());
      assertTrue(a.restoreClaim(before).join());
      assertEquals(1, a.listInbox(recipient, MailType.PACKAGE).join().size());
      assertTrue(b.insertMailLimited(sender, "S", recipient, "R", MailType.PACKAGE, new byte[]{3}, 1, true).join().isEmpty());
    } finally {
      b.close();
      a.close();
    }
  }
  /** Verifies that delivery acknowledgment releases limit and prevents late compensation. */

  @Test void deliveryAcknowledgmentReleasesLimitAndPreventsLateCompensation() {
    var repo = new MailRepository(null, directory.resolve("ack.db").toFile(), 5000);
    try {
      repo.initialize().join();
      UUID sender = UUID.randomUUID();
      UUID recipient = UUID.randomUUID();
      long id = repo.insertMailLimited(sender, "S", recipient, "R", MailType.PACKAGE, new byte[]{1}, 1, true).join().orElseThrow();
      var original = repo.get(id).join();
      assertFalse(repo.confirmDelivery(id, recipient).join());
      assertTrue(repo.claim(id, recipient).join());
      assertFalse(repo.confirmDelivery(id, UUID.randomUUID()).join());
      assertTrue(repo.confirmDelivery(id, recipient).join());
      assertFalse(repo.confirmDelivery(id, recipient).join());
      assertFalse(repo.restoreClaim(original).join());
      assertTrue(repo.insertMailLimited(sender, "S", recipient, "R", MailType.PACKAGE, new byte[]{2}, 1, true).join().isPresent());
    } finally { repo.close(); }
  }
  /** Verifies that legacy schema migrates without changing mail payloads. */

  @Test void legacySchemaMigratesWithoutChangingMailPayloads() throws Exception {
    var file = directory.resolve("legacy.db").toFile();
    UUID sender = UUID.randomUUID();
      UUID recipient = UUID.randomUUID();
    var seed = new MailRepository(null, file, 5000);
    seed.initialize().join();
    long id = seed.insertPackage(sender, "S", recipient, "R", new byte[]{4, 8}, 2, false).join();
    seed.close();
    try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
         var statement = connection.createStatement()) {
      statement.execute("ALTER TABLE mail DROP COLUMN delivery_pending");
    }
    var migrated = new MailRepository(null, file, 5000);
    try {
      migrated.initialize().join();
      assertArrayEquals(new byte[]{4, 8}, migrated.get(id).join().payload());
      assertTrue(migrated.claim(id, recipient).join());
      assertTrue(migrated.confirmDelivery(id, recipient).join());
    } finally { migrated.close(); }
  }
  /** Verifies that competing initializers serialize schema migration. */

  @Test void competingInitializersSerializeSchemaMigration() {
    var file = directory.resolve("shared.db").toFile();
    var a = new MailRepository(null, file, 5000);
    var b = new MailRepository(null, file, 5000);
    try {
      java.util.concurrent.CompletableFuture.allOf(a.initialize(), b.initialize()).join();
      UUID sender = UUID.randomUUID();
      UUID recipient = UUID.randomUUID();
      long id = a.insertPackage(sender, "S", recipient, "R", new byte[]{1}, 1, false).join();
      assertNotNull(b.get(id).join());
    } finally { b.close(); a.close(); }
  }
}
