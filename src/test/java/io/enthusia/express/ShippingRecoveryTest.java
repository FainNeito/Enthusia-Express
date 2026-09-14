package io.enthusia.express;

import static org.junit.jupiter.api.Assertions.*;

import io.enthusia.express.infrastructure.payment.ShippingRecovery;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShippingRecoveryTest {
  @TempDir Path directory;

  /** Only known failed, unattempted compensation can survive as an automatic retry. */
  @Test void onlyPendingRecordsReplay() {
    var journal = new ShippingRecovery(directory, Logger.getAnonymousLogger());
    var sender = UUID.randomUUID();
    var recipient = UUID.randomUUID();
    journal.prepare(sender, recipient, 2, "Raw Gold", new byte[] {1, 2});
    var uncertain = journal.prepare(sender, recipient, 2, "currency", new byte[] {3});
    journal.transition(uncertain, ShippingRecovery.Phase.APPLYING);
    var pending = journal.prepare(sender, recipient, 2, "currency", new byte[] {4, 5});
    journal.transition(pending, ShippingRecovery.Phase.PENDING);
    var complete = journal.prepare(sender, recipient, 2, "currency", new byte[] {6});
    journal.transition(complete, ShippingRecovery.Phase.COMPLETE);
    var recovered = new ShippingRecovery(directory, Logger.getAnonymousLogger()).pending();
    assertEquals(1, recovered.size());
    assertEquals(pending.getId(), recovered.getFirst().getId());
    assertArrayEquals(new byte[] {4, 5}, recovered.getFirst().getCargo());
    assertEquals("currency", recovered.getFirst().getRoute());
  }

  /** Corrupt or incomplete records remain evidence but never become asset mutations. */
  @Test void malformedRecordsAreNotReplayed() throws Exception {
    var path = directory.resolve(UUID.randomUUID() + ".properties");
    Files.writeString(path, "version=1\nphase=PENDING\n");
    var journal = new ShippingRecovery(directory, Logger.getAnonymousLogger());
    assertTrue(journal.pending().isEmpty());
    assertTrue(Files.exists(path));
  }

  /** The bounded retry budget must not starve accounts behind a contended batch. */
  @Test void retryBatchesRotate() {
    var journal = new ShippingRecovery(directory, Logger.getAnonymousLogger());
    ShippingRecovery.Record last = null;
    for (int index = 0; index < 17; index++) {
      last = journal.prepare(UUID.randomUUID(), UUID.randomUUID(), 1, "Raw Gold", new byte[] {1});
      journal.transition(last, ShippingRecovery.Phase.PENDING);
    }
    var first = journal.pending();
    assertEquals(16, first.size());
    assertFalse(first.contains(last));
    assertTrue(journal.pending().contains(last));
  }
}
