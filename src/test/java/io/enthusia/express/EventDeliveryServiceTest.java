package io.enthusia.express;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.enthusia.express.api.EventDelivery;
import io.enthusia.express.application.MailStore;
import io.enthusia.express.domain.EventDeliveryRecord;
import io.enthusia.express.infrastructure.api.EventDeliveryService;
import io.enthusia.express.infrastructure.api.EventPackager;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;

/** REQ-050: the public service validates items, packs them once and forwards the token. */
class EventDeliveryServiceTest {
  MailStore store = mock(MailStore.class);
  EventPackager packager = mock(EventPackager.class);
  EventDelivery api = new EventDeliveryService(store, packager);
  UUID recipient = UUID.randomUUID();
  UUID token = UUID.randomUUID();

  @BeforeEach
  void absentTokenByDefault() {
    when(store.findEventDelivery(any(), any())).thenReturn(CompletableFuture.completedFuture(OptionalLong.empty()));
  }

  ItemStack stack() {
    ItemStack item = mock(ItemStack.class);
    when(item.isEmpty()).thenReturn(false);
    return item;
  }

  @Test
  void packsItemsAndForwardsToken() {
    List<ItemStack> items = List.of(stack(), stack());
    when(packager.pack(items, "Secret Santa")).thenReturn(new EventPackager.Packed(new byte[] {1}, 2));
    when(store.insertEventPackage(eq(recipient), eq("Bob"), eq("Secret Santa"), any(), eq(2), eq(token)))
        .thenReturn(CompletableFuture.completedFuture(new EventDeliveryRecord(42L, true)));
    assertEquals(42L, api.deliverPackage(recipient, "Bob", "Secret Santa", items, token).join());
    verify(packager, times(1)).pack(items, "Secret Santa");
    verify(store, never()).findEventDelivery(any(), any());
  }

  @Test
  void rejectsEmptyOversizedAndAirItems() {
    assertTrue(api.deliverPackage(recipient, "Bob", "Santa", List.of(), token).isCompletedExceptionally());
    List<ItemStack> tooMany = new ArrayList<>();
    for (int i = 0; i < 28; i++) tooMany.add(stack());
    assertTrue(api.deliverPackage(recipient, "Bob", "Santa", tooMany, token).isCompletedExceptionally());
    ItemStack air = mock(ItemStack.class);
    when(air.isEmpty()).thenReturn(true);
    assertTrue(api.deliverPackage(recipient, "Bob", "Santa", List.of(air), token).isCompletedExceptionally());
    verify(store, times(3)).findEventDelivery(recipient, token);
    verifyNoMoreInteractions(store);
  }

  @Test
  void packagingFailuresBecomeFailedFutures() {
    List<ItemStack> items = List.of(stack());
    when(packager.pack(items, "Santa")).thenThrow(new IllegalArgumentException("Container nesting too deep"));
    CompletableFuture<Long> result = api.deliverPackage(recipient, "Bob", "Santa", items, token);
    assertTrue(result.isCompletedExceptionally());
    verify(store).findEventDelivery(recipient, token);
    verifyNoMoreInteractions(store);
  }

  /** Any packaging failure, not only validation, comes back as a failed future. */
  @Test
  void unexpectedPackagingErrorsBecomeFailedFutures() {
    List<ItemStack> items = List.of(stack());
    when(packager.pack(items, "Santa")).thenThrow(new ArithmeticException("overflow"));
    assertTrue(api.deliverPackage(recipient, "Bob", "Santa", items, token).isCompletedExceptionally());
    List<ItemStack> withNull = new ArrayList<>();
    withNull.add(null);
    assertTrue(api.deliverPackage(recipient, "Bob", "Santa", withNull, token).isCompletedExceptionally());
    verify(store, times(2)).findEventDelivery(recipient, token);
    verifyNoMoreInteractions(store);
  }

  /** A lookup that throws cannot convert uncertain ownership into a permanent cargo rejection. */
  @Test
  void synchronousLookupFailureIsHeld() {
    List<ItemStack> items = List.of(stack());
    when(packager.pack(items, "Santa")).thenThrow(new IllegalArgumentException("changed cargo policy"));
    when(store.findEventDelivery(recipient, token)).thenThrow(new IllegalArgumentException("lookup failed"));
    CompletableFuture<Long> result = assertDoesNotThrow(() ->
        api.deliverPackage(recipient, "Bob", "Santa", items, token));
    CompletionException failure = assertThrows(CompletionException.class, result::join);
    assertInstanceOf(IllegalStateException.class, failure.getCause());
  }

  /** Asynchronous lookup failures are also held even when their original type is a validation error. */
  @Test
  void asynchronousLookupFailureIsHeld() {
    List<ItemStack> items = List.of(stack());
    when(packager.pack(items, "Santa")).thenThrow(new IllegalArgumentException("changed cargo policy"));
    when(store.findEventDelivery(recipient, token))
        .thenReturn(CompletableFuture.failedFuture(new IllegalArgumentException("lookup failed")));
    CompletableFuture<Long> result = assertDoesNotThrow(() ->
        api.deliverPackage(recipient, "Bob", "Santa", items, token));
    CompletionException failure = assertThrows(CompletionException.class, result::join);
    assertInstanceOf(IllegalStateException.class, failure.getCause());
  }

  /** A store that throws before returning a future still gets durable-token reconciliation. */
  @Test
  void synchronousStoreFailureReconcilesSavedToken() {
    List<ItemStack> items = List.of(stack());
    when(packager.pack(items, "Santa")).thenReturn(new EventPackager.Packed(new byte[] {1}, 1));
    when(store.insertEventPackage(any(), anyString(), anyString(), any(), anyInt(), any()))
        .thenThrow(new IllegalArgumentException("metadata validation changed"));
    when(store.findEventDelivery(recipient, token)).thenReturn(CompletableFuture.completedFuture(OptionalLong.of(42L)));
    assertEquals(42L, api.deliverPackage(recipient, "Bob", "Santa", items, token).join());
    verify(store).findEventDelivery(recipient, token);
  }

  /** An immediately rejected attempt must release its entry so corrected new cargo can be tried. */
  @Test
  void immediateRejectionReleasesInFlightEntry() {
    List<ItemStack> items = List.of(stack());
    when(packager.pack(items, "Santa"))
        .thenThrow(new IllegalArgumentException("invalid cargo"))
        .thenReturn(new EventPackager.Packed(new byte[] {1}, 1));
    when(store.insertEventPackage(any(), anyString(), anyString(), any(), anyInt(), any()))
        .thenReturn(CompletableFuture.completedFuture(new EventDeliveryRecord(42L, true)));
    assertThrows(CompletionException.class, () -> api.deliverPackage(recipient, "Bob", "Santa", items, token).join());
    assertEquals(42L, api.deliverPackage(recipient, "Bob", "Santa", items, token).join());
    verify(packager, times(2)).pack(items, "Santa");
    verify(store, times(1)).findEventDelivery(recipient, token);
  }

  /** A later call cannot insert while the first rejection is still proving that its token is absent. */
  @Test
  void overlappingCallWaitsForPendingReconciliation() {
    List<ItemStack> items = List.of(stack());
    CompletableFuture<OptionalLong> lookup = new CompletableFuture<>();
    when(packager.pack(items, "Santa"))
        .thenThrow(new IllegalArgumentException("invalid first cargo"))
        .thenReturn(new EventPackager.Packed(new byte[] {1}, 1));
    when(store.findEventDelivery(recipient, token)).thenReturn(lookup);
    CompletableFuture<Long> first = api.deliverPackage(recipient, "Bob", "Santa", items, token);
    try {
      CompletableFuture<Long> second = api.deliverPackage(recipient, "Bob", "Santa", items, token);
      assertFalse(first.isDone());
      assertFalse(second.isDone());
      verify(packager, times(1)).pack(items, "Santa");
      verify(store, never()).insertEventPackage(any(), anyString(), anyString(), any(), anyInt(), any());
      lookup.complete(OptionalLong.empty());
      assertInstanceOf(IllegalArgumentException.class,
          assertThrows(CompletionException.class, first::join).getCause());
      assertInstanceOf(IllegalArgumentException.class,
          assertThrows(CompletionException.class, second::join).getCause());
    } finally {
      lookup.complete(OptionalLong.empty());
    }
  }

  /** Reentrant callbacks join the settled private owner before its identity-based removal. */
  @Test
  void reentrantCompletionJoinsSettledOwner() {
    List<ItemStack> items = List.of(stack());
    CompletableFuture<EventDeliveryRecord> pending = new CompletableFuture<>();
    when(packager.pack(items, "Santa")).thenReturn(new EventPackager.Packed(new byte[] {1}, 1));
    when(store.insertEventPackage(any(), anyString(), anyString(), any(), anyInt(), any())).thenReturn(pending);
    CompletableFuture<Long> first = api.deliverPackage(recipient, "Bob", "Santa", items, token);
    CompletableFuture<Long> reentrant = first.thenCompose(id ->
        api.deliverPackage(recipient, "Bob", "Santa", items, token));
    pending.complete(new EventDeliveryRecord(42L, true));
    assertEquals(42L, reentrant.join());
    verify(packager, times(1)).pack(items, "Santa");
    verify(store, times(1)).insertEventPackage(any(), anyString(), anyString(), any(), anyInt(), any());
  }
}
