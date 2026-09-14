package io.enthusia.express;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.enthusia.express.domain.MailType;
import io.enthusia.express.domain.MailStatus;
import io.enthusia.express.infrastructure.db.MailRepository;
import io.enthusia.express.infrastructure.db.DeliveryAcknowledgments;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real SQLite recovery and resource-admission regressions for the production review. */
class ProductionSafetyTest {
  @TempDir Path directory;

  /** A forced temporary undelivered receipt must survive a crash before its atomic rename. */
  @Test void completeTemporaryReceiptRecoversAfterRestart() throws Exception {
    var repo = new MailRepository(null, directory.resolve("mail.db").toFile(), 50);
    repo.initialize().join();
    UUID recipient = UUID.randomUUID();
    long id = repo.insertPackage(null, "S", recipient, "R", new byte[]{1}, 1, false).join();
    var record = repo.get(id).join();
    assertTrue(repo.claim(record).join());
    Path receipts = directory.resolve("receipts");
    Path undelivered = Files.createDirectories(receipts.resolve("undelivered"));
    Path temporary = undelivered.resolve(id + "-0.tmp");
    Files.writeString(temporary, id + "\n" + recipient + "\nUNCLAIMED\n" + record.updatedAt() + "\n0\n");
    long damagedId = repo.insertPackage(null, "S", recipient, "R", new byte[]{2}, 1, false).join();
    assertTrue(repo.claim(damagedId, recipient).join());
    Path damaged = undelivered.resolve(damagedId + "-0.tmp");
    Files.writeString(damaged, damagedId + "\n" + recipient + "\nUNCLAIMED\n1\n0");
    try (var journal = new DeliveryAcknowledgments(receipts, repo, Logger.getAnonymousLogger())) {
      journal.retry().join();
      assertEquals(MailStatus.UNCLAIMED, repo.get(id).join().status());
      assertFalse(Files.exists(temporary));
      assertFalse(Files.exists(undelivered.resolve(id + "-0.restore")));
      assertEquals(MailStatus.CLAIMED, repo.get(damagedId).join().status());
      assertTrue(Files.exists(damaged), "Incomplete receipt remains for inspection");
    } finally { repo.close(); }
  }

  /** A complete delivered .tmp receipt is promoted and acknowledged; malformed evidence stays held. */
  @Test void completeDeliveredTemporaryReceiptRecoversAfterRestart() throws Exception {
    var repo = new MailRepository(null, directory.resolve("delivered.db").toFile(), 50);
    repo.initialize().join();
    UUID sender = UUID.randomUUID();
    UUID recipient = UUID.randomUUID();
    long id = repo.insertPackage(sender, "S", recipient, "R", new byte[]{1}, 1, false).join();
    assertTrue(repo.claim(id, recipient).join());
    long damagedId = repo.insertPackage(sender, "S", recipient, "R", new byte[]{2}, 1, false).join();
    assertTrue(repo.claim(damagedId, recipient).join());
    Path receipts = Files.createDirectories(directory.resolve("delivered-receipts"));
    Path temporary = receipts.resolve(id + ".tmp");
    Files.writeString(temporary, recipient.toString());
    Path damaged = receipts.resolve(damagedId + ".tmp");
    Files.writeString(damaged, "not-a-uuid");
    try (var journal = new DeliveryAcknowledgments(receipts, repo, Logger.getAnonymousLogger())) {
      journal.retry().join();
      assertFalse(Files.exists(temporary));
      assertFalse(Files.exists(receipts.resolve(id + ".ack")));
      var delivered = repo.get(id).join();
      assertEquals(MailStatus.CLAIMED, delivered.status());
      var sent = repo.listSent(sender, MailType.PACKAGE, 0).join().stream()
          .filter(entry -> entry.getMail().id() == id).findFirst().orElseThrow();
      assertFalse(sent.getDeliveryPending(), "Recovered receipt must release the sender reservation");
      assertFalse(repo.confirmDelivery(id, recipient).join(), "Recovered receipt already released the reservation");
      assertTrue(Files.exists(damaged), "Malformed temporary evidence remains for inspection");
      var damagedSent = repo.listSent(sender, MailType.PACKAGE, 0).join().stream()
          .filter(entry -> entry.getMail().id() == damagedId).findFirst().orElseThrow();
      assertTrue(damagedSent.getDeliveryPending(), "Malformed evidence must not release a reservation");
    } finally { repo.close(); }
  }

  /** A write lock leaves a durable non-delivery receipt which recovers after restart. */
  @Test void busyRestoreRecoversAfterRestartWithoutReplayingLaterClaim() throws Exception {
    var file = directory.resolve("mail.db").toFile();
    var receipts = directory.resolve("receipts");
    UUID sender = UUID.randomUUID();
    UUID recipient = UUID.randomUUID();
    var repo = new MailRepository(null, file, 50);
    repo.initialize().join();
    long id = repo.insertPackage(sender, "Sender", recipient, "Recipient", new byte[]{7}, 1, false).join();
    var original = repo.get(id).join();
    assertTrue(repo.claim(original).join());
    String savedReceipt;
    var receipt = receipts.resolve("undelivered/" + id + "-0.restore");
    try (var lock = DriverManager.getConnection("jdbc:sqlite:" + file); var sql = lock.createStatement()) {
      sql.execute("BEGIN IMMEDIATE");
      try (var journal = new DeliveryAcknowledgments(receipts, repo, Logger.getAnonymousLogger())) {
        assertFalse(journal.restore(original).join());
        assertTrue(Files.exists(receipt));
        savedReceipt = Files.readString(receipt);
      }
      sql.execute("ROLLBACK");
    }
    repo.close();
    var reopened = new MailRepository(null, file, 50);
    reopened.initialize().join();
    try (var journal = new DeliveryAcknowledgments(receipts, reopened, Logger.getAnonymousLogger())) {
      journal.retry().join();
      var restored = reopened.get(id).join();
      assertEquals(MailStatus.UNCLAIMED, restored.status());
      assertEquals(original.updatedAt(), restored.updatedAt());
      assertEquals(1, restored.claimGeneration());
      assertEquals(1, reopened.listInbox(recipient, MailType.PACKAGE).join().size());
      assertFalse(Files.exists(receipt));
      assertFalse(reopened.claim(original).join(), "Stale snapshots cannot reserve a new generation");
      assertTrue(reopened.claim(restored).join());
      // Simulate successful SQLite compensation followed by failed deletion of an old receipt.
      Files.writeString(receipt, savedReceipt);
      journal.retry().join();
      assertEquals(MailStatus.CLAIMED, reopened.get(id).join().status());
      assertTrue(reopened.listSent(sender, MailType.PACKAGE, 0).join().getFirst().getDeliveryPending());
      assertFalse(reopened.restoreClaim(original).join());
      assertTrue(reopened.confirmDelivery(id, recipient).join());
      assertFalse(reopened.restoreClaim(restored).join(), "Delivered items cannot be restored");
      assertArrayEquals(new byte[]{7}, reopened.get(id).join().payload());
    } finally { reopened.close(); }
  }

  /** Returned claims recover as returned mail and unknown reservations remain untouched. */
  @Test void returnedRecoveryKeepsOwnerAndUnknownClaimsHeld() throws Exception {
    var repo = new MailRepository(null, directory.resolve("mail.db").toFile(), 50);
    repo.initialize().join();
    UUID recipient = UUID.randomUUID();
    long returned = repo.insertPackage(UUID.randomUUID(), "S", recipient, "R", new byte[]{1}, 1, true).join();
    long unknown = repo.insertPackage(UUID.randomUUID(), "S", recipient, "R", new byte[]{2}, 1, false).join();
    var original = repo.get(returned).join();
    assertTrue(repo.claim(original).join());
    assertTrue(repo.claim(unknown, recipient).join());
    try (var journal = new DeliveryAcknowledgments(directory.resolve("receipts"), repo, Logger.getAnonymousLogger())) {
      assertTrue(journal.restore(original).join());
      journal.retry().join();
      assertEquals(MailStatus.RETURNED, repo.get(returned).join().status());
      assertEquals(MailStatus.CLAIMED, repo.get(unknown).join().status());
      assertEquals(1, repo.listInbox(recipient, MailType.PACKAGE).join().size());
    } finally { repo.close(); }
  }

  /** A disk error retains the intent in memory and retries once its directory becomes writable. */
  @Test void receiptWriteFailureRetriesWithoutChangingTheClaim() throws Exception {
    var repo = new MailRepository(null, directory.resolve("mail.db").toFile(), 50);
    repo.initialize().join();
    UUID recipient = UUID.randomUUID();
    long id = repo.insertPackage(null, "S", recipient, "R", new byte[]{1}, 1, false).join();
    var original = repo.get(id).join();
    assertTrue(repo.claim(original).join());
    Path receipts = directory.resolve("receipts");
    Files.createDirectories(receipts);
    Files.writeString(receipts.resolve("undelivered"), "blocks directory creation");
    try (var journal = new DeliveryAcknowledgments(receipts, repo, Logger.getAnonymousLogger())) {
      assertFalse(journal.restore(original).join());
      assertEquals(MailStatus.CLAIMED, repo.get(id).join().status());
      Files.delete(receipts.resolve("undelivered"));
      journal.retry().join();
      assertEquals(MailStatus.UNCLAIMED, repo.get(id).join().status());
    } finally { repo.close(); }
  }

  /** Pages do not materialize old oversized payloads, but claims retain the original bytes. */
  @Test void pageSummariesOmitPayloadsWithoutDestroyingExistingMail() {
    var repo = new MailRepository(null, directory.resolve("mail.db").toFile(), 50);
    repo.initialize().join();
    UUID sender = UUID.randomUUID();
    UUID recipient = UUID.randomUUID();
    byte[] oversized = new byte[8 * 1024 * 1024];
    try {
      long id = repo.insertPackage(sender, "S", recipient, "R", oversized, 12, false).join();
      assertEquals(0, repo.listInbox(recipient, MailType.PACKAGE).join().getFirst().payload().length);
      assertEquals(0, repo.listSent(sender, MailType.PACKAGE, 0).join().getFirst().getMail().payload().length);
      assertEquals(oversized.length, repo.get(id).join().payload().length);
    } finally { repo.close(); }
  }

  /** Accepted writes drain on close even when later work is rejected by queue admission. */
  @Test void boundedQueueRejectsExcessWorkWithoutDiscardingAcceptedWrites() throws Exception {
    var plugin = mock(org.bukkit.plugin.java.JavaPlugin.class);
    var config = new org.bukkit.configuration.file.YamlConfiguration();
    config.set("database.max-queued-operations", 8);
    when(plugin.getConfig()).thenReturn(config);
    when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
    var file = directory.resolve("mail.db").toFile();
    var repo = new MailRepository(plugin, file, 5000);
    repo.initialize().join();
    UUID recipient = UUID.randomUUID();
    var accepted = new java.util.ArrayList<CompletableFuture<Long>>();
    try (var lock = DriverManager.getConnection("jdbc:sqlite:" + file); var sql = lock.createStatement()) {
      sql.execute("BEGIN IMMEDIATE");
      for (int i = 0; i < 8; i++) accepted.add(repo.insertPackage(null, "S", recipient, "R", new byte[]{1}, 1, false));
      CompletableFuture<Long> overflow = null;
      for (int i = 0; i < 3; i++) {
        var next = repo.insertPackage(null, "S", recipient, "R", new byte[]{1}, 1, false);
        if (next.isCompletedExceptionally()) overflow = next; else accepted.add(next);
      }
      assertNotNull(overflow);
      var rejected = overflow;
      assertInstanceOf(java.util.concurrent.RejectedExecutionException.class,
          assertThrows(CompletionException.class, rejected::join).getCause());
      sql.execute("ROLLBACK");
    } finally { repo.close(); }
    for (var write : accepted) assertTrue(write.join() > 0);
    var reopened = new MailRepository(null, file, 50);
    reopened.initialize().join();
    try { assertEquals(accepted.size(), reopened.listInbox(recipient, MailType.PACKAGE).join().size()); }
    finally { reopened.close(); }
  }
}
