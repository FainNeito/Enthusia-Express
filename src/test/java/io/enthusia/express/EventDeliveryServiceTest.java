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
    verifyNoInteractions(store);
  }

  @Test
  void packagingFailuresBecomeFailedFutures() {
    List<ItemStack> items = List.of(stack());
    when(packager.pack(items, "Santa")).thenThrow(new IllegalArgumentException("Container nesting too deep"));
    CompletableFuture<Long> result = api.deliverPackage(recipient, "Bob", "Santa", items, token);
    assertTrue(result.isCompletedExceptionally());
    verifyNoInteractions(store);
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
    verifyNoInteractions(store);
  }
}
