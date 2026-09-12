package io.enthusia.express;

import io.enthusia.express.application.MailStore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.enthusia.express.infrastructure.db.MailRepository;
import io.enthusia.express.infrastructure.db.DeliveryAcknowledgments;
import io.enthusia.express.domain.MailType;
import io.enthusia.express.domain.MailStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeliveryAcknowledgmentsTest {
  @TempDir Path directory;
  /** Verifies that malformed receipt does not release an uncertain claim. */

  @Test void malformedReceiptDoesNotReleaseAnUncertainClaim() throws Exception {
    var store = mock(MailStore.class);
    Path receipts = directory.resolve("acks");
    Files.createDirectories(receipts);
    Files.writeString(receipts.resolve("1.ack"), "incomplete");
    try (var journal = new DeliveryAcknowledgments(receipts, store, Logger.getAnonymousLogger())) {
      journal.retry().join();
      verifyNoInteractions(store);
      assertEquals("incomplete", Files.readString(receipts.resolve("1.ack")));
    }
  }
  /** Verifies that failed acknowledgment survives restart and releases only delivered reservation. */

  @Test void failedAcknowledgmentSurvivesRestartAndReleasesOnlyDeliveredReservation() throws Exception {
    var repository = new MailRepository(null, directory.resolve("mail.db").toFile(), 5000);
    repository.initialize().join();
    var sender = UUID.randomUUID();
    var recipient = UUID.randomUUID();
    long id = repository.insertMailLimited(sender, "s", recipient, "r", MailType.PACKAGE,
        new byte[] {1}, 1, true).join().getAsLong();
    assertTrue(repository.claim(id, recipient).join());
    var broken = spy(repository);
    doReturn(CompletableFuture.failedFuture(new SQLException("temporarily busy")))
        .when(broken).confirmDelivery(id, recipient);
    Path receipts = directory.resolve("acks");
    try (var journal = new DeliveryAcknowledgments(receipts, broken, Logger.getAnonymousLogger())) {
      journal.record(id, recipient).join();
      assertTrue(Files.exists(receipts.resolve(id + ".ack")));
      assertTrue(repository.insertMailLimited(sender, "s", recipient, "r", MailType.PACKAGE,
          new byte[] {2}, 1, true).join().isEmpty());
    }
    try (var journal = new DeliveryAcknowledgments(receipts, repository, Logger.getAnonymousLogger())) {
      journal.retry().join();
      assertFalse(Files.exists(receipts.resolve(id + ".ack")));
      assertEquals(MailStatus.CLAIMED, repository.get(id).join().status());
      assertFalse(repository.claim(id, recipient).join());
      long next = repository.insertMailLimited(sender, "s", recipient, "r", MailType.PACKAGE,
          new byte[] {2}, 1, true).join().getAsLong();
      assertTrue(repository.claim(next, recipient).join());
      journal.retry().join();
      assertTrue(repository.insertMailLimited(sender, "s", recipient, "r", MailType.PACKAGE,
          new byte[] {3}, 1, true).join().isEmpty(), "Unknown claims must retain reservations");
    } finally { repository.close(); }
  }
  /** Verifies that already acknowledged returned package receipt is idempotent. */

  @Test void alreadyAcknowledgedReturnedPackageReceiptIsIdempotent() throws Exception {
    var repository = new MailRepository(null, directory.resolve("mail.db").toFile(), 5000);
    repository.initialize().join();
    var recipient = UUID.randomUUID();
    long id = repository.insertPackage(UUID.randomUUID(), "s", recipient, "r", new byte[] {1}, 1, true).join();
    assertTrue(repository.claim(id, recipient).join());
    assertTrue(repository.confirmDelivery(id, recipient).join());
    Path receipts = directory.resolve("acks");
    try (var journal = new DeliveryAcknowledgments(receipts, repository, Logger.getAnonymousLogger())) {
      journal.record(id, recipient).join();
      journal.retry().join();
      assertFalse(Files.exists(receipts.resolve(id + ".ack")));
      assertEquals(MailStatus.RETURN_CLAIMED, repository.get(id).join().status());
      assertFalse(repository.claim(id, recipient).join());
    } finally { repository.close(); }
  }
}
