package io.enthusia.express;

import static org.junit.jupiter.api.Assertions.*;

import io.enthusia.express.domain.*;
import io.enthusia.express.infrastructure.db.MailRepository;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** REQ-050: event packages are idempotent, limit/block free, claimable and never expire. */
class EventDeliveryRepositoryTest {
  @TempDir Path directory;
  MailRepository repository;
  UUID recipient = UUID.randomUUID();
  UUID token = UUID.randomUUID();
  byte[] payload = {7, 7, 7};

  @BeforeEach
  void start() {
    repository = new MailRepository(null, directory.resolve("mail.db").toFile(), 5000);
    repository.initialize().join();
  }

  @AfterEach
  void stop() {
    repository.close();
  }

  EventDeliveryRecord deliver(UUID to, UUID key) {
    return repository.insertEventPackage(to, "Recipient", "Secret Santa", payload, 3, key).join();
  }

  /** A new token stores one unclaimed system package for the recipient. */
  @Test
  void deliveryCreatesClaimablePackage() {
    EventDeliveryRecord first = deliver(recipient, token);
    assertTrue(first.created());
    MailRecord record = repository.get(first.mailId()).join();
    assertEquals(MailType.PACKAGE, record.type());
    assertEquals(MailStatus.UNCLAIMED, record.status());
    assertNull(record.sender());
    assertEquals("Secret Santa", record.senderName());
    assertEquals(3, record.packedItemCount());
    assertArrayEquals(payload, record.payload());
    assertEquals(1, repository.listInbox(recipient, MailType.PACKAGE).join().size());
  }

  /** Retrying with the same token never stores a second package, even after restart. */
  @Test
  void tokenRetriesAreIdempotent() {
    long id = deliver(recipient, token).mailId();
    EventDeliveryRecord retry = deliver(recipient, token);
    assertFalse(retry.created());
    assertEquals(id, retry.mailId());
    repository.close();
    repository = new MailRepository(null, directory.resolve("mail.db").toFile(), 5000);
    repository.initialize().join();
    assertEquals(id, deliver(recipient, token).mailId());
    assertEquals(1, repository.listInbox(recipient, MailType.PACKAGE).join().size());
  }

  /** Concurrent retries of one token resolve to a single package. */
  @Test
  void concurrentTokenRetriesStoreOnce() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(6);
    try {
      List<Future<EventDeliveryRecord>> futures = new ArrayList<>();
      for (int i = 0; i < 6; i++) futures.add(pool.submit(() -> deliver(recipient, token)));
      Set<Long> ids = new HashSet<>();
      int created = 0;
      for (Future<EventDeliveryRecord> f : futures) {
        EventDeliveryRecord r = f.get(10, TimeUnit.SECONDS);
        ids.add(r.mailId());
        if (r.created()) created++;
      }
      assertEquals(1, ids.size());
      assertEquals(1, created);
    } finally {
      pool.shutdownNow();
    }
  }

  /** A token cannot be replayed to redirect a delivery to someone else. */
  @Test
  void tokenReuseForAnotherRecipientIsRejected() {
    deliver(recipient, token);
    assertThrows(CompletionException.class, () -> deliver(UUID.randomUUID(), token));
  }

  /** Blocks and outstanding limits do not apply: the system, not a player, is sending. */
  @Test
  void blocksDoNotStopEventDeliveries() {
    repository.setBlocked(recipient, UUID.randomUUID(), "Someone", true).join();
    deliver(recipient, token);
    deliver(recipient, UUID.randomUUID());
    assertEquals(2, repository.listInbox(recipient, MailType.PACKAGE).join().size());
  }

  /** Retention never returns, expires or purges an unclaimed event package. */
  @Test
  void eventPackagesNeverExpire() {
    long id = deliver(recipient, token).mailId();
    long future = System.currentTimeMillis() + 1_000_000_000L;
    repository.expire(future, future, future, future).join();
    MailRecord record = repository.get(id).join();
    assertEquals(MailStatus.UNCLAIMED, record.status());
    assertArrayEquals(payload, record.payload());
  }

  /** Event packages use the ordinary claim and acknowledgment path. */
  @Test
  void eventPackagesClaimNormally() {
    long id = deliver(recipient, token).mailId();
    assertTrue(repository.claim(id, recipient).join());
    assertFalse(repository.claim(id, recipient).join());
    assertTrue(repository.confirmDelivery(id, recipient).join());
    assertEquals(MailStatus.CLAIMED, repository.get(id).join().status());
  }

  /** Invalid deliveries are rejected before storage. */
  @Test
  void invalidDeliveriesAreRejected() {
    assertThrows(Exception.class, () -> repository.insertEventPackage(recipient, "Recipient", "Santa", new byte[0], 1, token).join());
    assertThrows(Exception.class, () -> repository.insertEventPackage(recipient, "Recipient", " ", payload, 1, token).join());
    assertTrue(repository.listInbox(recipient, MailType.PACKAGE).join().isEmpty());
  }
}
