package io.enthusia.express;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.enthusia.express.api.EventDelivery;
import io.enthusia.express.application.MailStore;
import io.enthusia.express.domain.EventDeliveryRecord;
import io.enthusia.express.domain.MailType;
import io.enthusia.express.domain.UncertainMailCommitException;
import io.enthusia.express.infrastructure.api.EventDeliveryService;
import io.enthusia.express.infrastructure.api.EventPackager;
import io.enthusia.express.infrastructure.db.MailRepository;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Event retries preserve the ownership of an earlier durable or still-running delivery. */
class EventDeliveryRecoveryTest {
  @TempDir Path directory;
  MailRepository repository;
  UUID recipient = UUID.randomUUID();
  UUID token = UUID.randomUUID();
  byte[] payload = {7, 8, 9};

  @BeforeEach
  void start() {
    repository = new MailRepository(null, directory.resolve("mail.db").toFile(), 5000);
    repository.initialize().join();
  }

  @AfterEach
  void stop() {
    repository.close();
  }

  List<ItemStack> items() {
    ItemStack item = mock(ItemStack.class);
    when(item.isEmpty()).thenReturn(false);
    return List.of(item);
  }

  EventPackager acceptingPackager() {
    return (items, sender) -> new EventPackager.Packed(payload, 1);
  }

  EventPackager rejectingPackager() {
    return (items, sender) -> {
      throw new IllegalArgumentException("Container nesting exceeds maximum depth");
    };
  }

  long storeOriginal() {
    return repository.insertEventPackage(recipient, "Recipient", "Santa", payload, 1, token).join().mailId();
  }

  /** A real committed transaction can lose its reply before a later packing policy rejects the retry. */
  @Test
  void committedTokenSurvivesUnknownReplyRestartAndChangedPackingPolicy() throws Exception {
    VarHandle connection = MethodHandles.privateLookupIn(MailRepository.class, MethodHandles.lookup())
        .findVarHandle(MailRepository.class, "connection", Connection.class);
    Connection real = (Connection) connection.get(repository);
    Connection wrapped = (Connection) Proxy.newProxyInstance(
        Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
          try {
            Object result = method.invoke(real, args);
            if (method.getName().equals("commit")) throw new SQLException("reply lost after commit");
            return result;
          } catch (InvocationTargetException failure) {
            throw failure.getCause();
          }
        });
    connection.set(repository, wrapped);
    CompletionException uncertain = assertThrows(CompletionException.class, this::storeOriginal);
    assertTrue(UncertainMailCommitException.causedBy(uncertain));
    long id = repository.listInbox(recipient, MailType.PACKAGE).join().getFirst().id();
    repository.close();
    start();

    EventDelivery retry = new EventDeliveryService(repository, rejectingPackager());
    assertEquals(id, retry.deliverPackage(recipient, "Recipient", "Santa", items(), token).join());
    assertEquals(1, repository.listInbox(recipient, MailType.PACKAGE).join().size());
    assertArrayEquals(payload, repository.get(id).join().payload());
  }

  /** Repository metadata validation must not hide an already committed token either. */
  @Test
  void storedTokenSurvivesChangedInvalidMetadata() {
    long id = storeOriginal();
    EventDelivery retry = new EventDeliveryService(repository, acceptingPackager());
    assertEquals(id, retry.deliverPackage(recipient, "Recipient", " ", items(), token).join());
    assertEquals(1, repository.listInbox(recipient, MailType.PACKAGE).join().size());
    assertEquals("Santa", repository.get(id).join().senderName());
    assertArrayEquals(payload, repository.get(id).join().payload());
  }

  /** Token reconciliation queues behind an earlier accepted write even while its commit is blocked. */
  @Test
  void rejectingRetryObservesEarlierQueuedCommit() throws Exception {
    VarHandle connection = MethodHandles.privateLookupIn(MailRepository.class, MethodHandles.lookup())
        .findVarHandle(MailRepository.class, "connection", Connection.class);
    Connection real = (Connection) connection.get(repository);
    CountDownLatch commitEntered = new CountDownLatch(1);
    CountDownLatch releaseCommit = new CountDownLatch(1);
    Connection wrapped = (Connection) Proxy.newProxyInstance(
        Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
          if (method.getName().equals("commit")) {
            commitEntered.countDown();
            if (!releaseCommit.await(5, TimeUnit.SECONDS)) throw new SQLException("test commit gate timed out");
          }
          try {
            return method.invoke(real, args);
          } catch (InvocationTargetException failure) {
            throw failure.getCause();
          }
        });
    connection.set(repository, wrapped);
    EventDelivery retry = new EventDeliveryService(repository, rejectingPackager());
    List<ItemStack> retryItems = items();
    CompletableFuture<EventDeliveryRecord> first = repository.insertEventPackage(
        recipient, "Recipient", "Santa", payload, 1, token);
    try {
      assertTrue(commitEntered.await(5, TimeUnit.SECONDS));
      CompletableFuture<Long> result = retry.deliverPackage(recipient, "Recipient", "Santa", retryItems, token);
      assertFalse(result.isDone());
      releaseCommit.countDown();
      assertEquals(first.join().mailId(), result.join());
      assertEquals(1, repository.listInbox(recipient, MailType.PACKAGE).join().size());
    } finally {
      releaseCommit.countDown();
    }
  }

  /** A successful lookup proving absence keeps invalid new cargo permanently rejected. */
  @Test
  void absentTokenRetainsPackingRejection() {
    EventDelivery api = new EventDeliveryService(repository, rejectingPackager());
    CompletableFuture<Long> result = assertDoesNotThrow(() ->
        api.deliverPackage(recipient, "Recipient", "Santa", items(), token));
    CompletionException failure = assertThrows(CompletionException.class, result::join);
    assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    assertTrue(repository.listInbox(recipient, MailType.PACKAGE).join().isEmpty());
  }

  /** Failure to query the token is uncertainty, not permission to release retained cargo. */
  @Test
  void closedLookupNeverDowngradesUncertaintyToPackingRejection() {
    repository.close();
    EventDelivery api = new EventDeliveryService(repository, rejectingPackager());
    CompletableFuture<Long> result = assertDoesNotThrow(() ->
        api.deliverPackage(recipient, "Recipient", "Santa", items(), token));
    CompletionException failure = assertThrows(CompletionException.class, result::join);
    assertInstanceOf(IllegalStateException.class, failure.getCause());
  }

  /** A recipient conflict proves a token exists and must never be reported as known-not-stored. */
  @Test
  void storedTokenConflictKeepsOriginalDeliveryHeld() {
    long id = storeOriginal();
    UUID other = UUID.randomUUID();
    EventDelivery api = new EventDeliveryService(repository, rejectingPackager());
    CompletionException failure = assertThrows(CompletionException.class, () ->
        api.deliverPackage(other, "Other", "Santa", items(), token).join());
    assertInstanceOf(IllegalStateException.class, failure.getCause());
    assertTrue(repository.listInbox(other, MailType.PACKAGE).join().isEmpty());
    assertEquals(recipient, repository.get(id).join().recipient());
  }

  /** Caller cancellation or manual completion cannot settle or evict the private operation owner. */
  @Test
  void overlappingCallersCannotCancelOrCompleteInternalDelivery() {
    MailStore store = mock(MailStore.class);
    EventPackager packager = mock(EventPackager.class);
    List<ItemStack> items = items();
    CompletableFuture<EventDeliveryRecord> pending = new CompletableFuture<>();
    when(packager.pack(items, "Santa")).thenReturn(new EventPackager.Packed(payload, 1));
    when(store.insertEventPackage(any(), anyString(), anyString(), any(), anyInt(), any())).thenReturn(pending);
    EventDelivery api = new EventDeliveryService(store, packager);
    try {
      CompletableFuture<Long> cancelled = api.deliverPackage(recipient, "Recipient", "Santa", items, token);
      assertTrue(cancelled.cancel(false));
      CompletableFuture<Long> forged = api.deliverPackage(recipient, "Recipient", "Santa", items, token);
      assertTrue(forged.complete(999L));
      CompletableFuture<Long> waiting = api.deliverPackage(recipient, "Recipient", "Santa", items, token);
      assertFalse(waiting.isDone());
      assertFalse(pending.isDone());
      pending.complete(new EventDeliveryRecord(42L, true));
      assertEquals(42L, waiting.join());
      assertTrue(cancelled.isCancelled());
      assertEquals(999L, forged.join());
      verify(packager, times(1)).pack(items, "Santa");
      verify(store, times(1)).insertEventPackage(any(), anyString(), anyString(), any(), anyInt(), any());
    } finally {
      pending.complete(new EventDeliveryRecord(42L, true));
    }
  }

  /** A conflicting overlapping recipient must not disturb the owner's pending storage operation. */
  @Test
  void overlappingRecipientConflictDoesNotDisturbOwner() {
    MailStore store = mock(MailStore.class);
    EventPackager packager = mock(EventPackager.class);
    List<ItemStack> items = items();
    CompletableFuture<EventDeliveryRecord> pending = new CompletableFuture<>();
    when(packager.pack(items, "Santa")).thenReturn(new EventPackager.Packed(payload, 1));
    when(store.insertEventPackage(any(), anyString(), anyString(), any(), anyInt(), any())).thenReturn(pending);
    EventDelivery api = new EventDeliveryService(store, packager);
    CompletableFuture<Long> owner = api.deliverPackage(recipient, "Recipient", "Santa", items, token);
    try {
      CompletableFuture<Long> conflict = api.deliverPackage(UUID.randomUUID(), "Other", "Santa", items, token);
      assertTrue(conflict.isCompletedExceptionally());
      CompletionException failure = assertThrows(CompletionException.class, conflict::join);
      assertInstanceOf(IllegalStateException.class, failure.getCause());
      assertFalse(owner.isDone());
      assertFalse(pending.isDone());
      verify(packager, times(1)).pack(items, "Santa");
    } finally {
      pending.complete(new EventDeliveryRecord(42L, true));
    }
    assertEquals(42L, owner.join());
  }

  /** Already-completed storage futures must not leave a permanent in-flight cache entry. */
  @Test
  void immediateCompletionReleasesInFlightEntry() {
    MailStore store = mock(MailStore.class);
    EventPackager packager = mock(EventPackager.class);
    List<ItemStack> items = items();
    when(packager.pack(items, "Santa")).thenReturn(new EventPackager.Packed(payload, 1));
    when(store.insertEventPackage(any(), anyString(), anyString(), any(), anyInt(), any()))
        .thenReturn(CompletableFuture.completedFuture(new EventDeliveryRecord(42L, true)))
        .thenReturn(CompletableFuture.completedFuture(new EventDeliveryRecord(42L, false)));
    EventDelivery api = new EventDeliveryService(store, packager);
    assertEquals(42L, api.deliverPackage(recipient, "Recipient", "Santa", items, token).join());
    assertEquals(42L, api.deliverPackage(recipient, "Recipient", "Santa", items, token).join());
    verify(packager, times(2)).pack(items, "Santa");
    verify(store, times(2)).insertEventPackage(any(), anyString(), anyString(), any(), anyInt(), any());
  }
}
