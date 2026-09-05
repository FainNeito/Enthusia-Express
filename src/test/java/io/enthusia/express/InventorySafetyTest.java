package io.enthusia.express;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.enthusia.express.gui.*;
import io.enthusia.express.util.ContainerScanner;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BundleMeta;
import org.junit.jupiter.api.Test;

class InventorySafetyTest {
  ItemStack item(Material type, int amount) {
    ItemStack item = mock(ItemStack.class);
    Material material = mock(Material.class);
    when(material.name()).thenReturn(type.name());
    when(item.getType()).thenReturn(material);
    when(item.getAmount()).thenReturn(amount);
    return item;
  }

  ItemStack bundle(ItemStack... contents) {
    ItemStack item = item(Material.BUNDLE, 1);
    BundleMeta meta = mock(BundleMeta.class);
    when(item.getItemMeta()).thenReturn(meta);
    when(meta.getItems()).thenReturn(List.of(contents));
    return item;
  }

  @Test
  void recursiveCountIncludesNestedContainerAndItsContents() {
    assertEquals(
        66,
        ContainerScanner.countPackedItems(
            bundle(item(Material.STONE, 64), bundle(item(Material.DIAMOND, 1))), 8));
    assertEquals(0, ContainerScanner.countPackedItems(bundle(), 8));
  }

  @Test
  void excessiveDepthRejectsInsteadOfUndercharging() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ContainerScanner.countPackedItems(bundle(bundle(item(Material.DIAMOND, 64))), 1));
  }

  @Test
  void bundleRecognitionUsesMetadataForColoredVariants() {
    ItemStack item = item(Material.PAPER, 1);
    when(item.getItemMeta()).thenReturn(mock(BundleMeta.class));
    assertTrue(ContainerScanner.isAllowedShippingContainer(item));
    assertFalse(ContainerScanner.isAllowedShippingContainer(null));
    assertFalse(ContainerScanner.isAllowedShippingContainer(item(Material.STONE, 1)));
  }

  @Test
  void shippingAllowsCursorPickupAndRejectsShiftNumberAndDoubleClicks() {
    ShippingService shipping = mock(ShippingService.class);
    MailboxService mailbox = mock(MailboxService.class);
    GuiListener listener = new GuiListener(shipping, mailbox);
    Player player = mock(Player.class);
    Inventory inventory = mock(Inventory.class);
    when(inventory.getSize()).thenReturn(27);
    InventoryView view = mock(InventoryView.class);
    when(view.getTopInventory()).thenReturn(inventory);
    when(shipping.owns(player, inventory)).thenReturn(true);
    for (ClickType click :
        List.of(
            ClickType.LEFT, ClickType.SHIFT_LEFT, ClickType.NUMBER_KEY, ClickType.DOUBLE_CLICK)) {
      InventoryClickEvent event = mock(InventoryClickEvent.class);
      when(event.getWhoClicked()).thenReturn(player);
      when(event.getView()).thenReturn(view);
      when(event.getRawSlot()).thenReturn(30);
      when(event.getClick()).thenReturn(click);
      listener.onClick(event);
      verify(event).setCancelled(click != ClickType.LEFT);
    }
  }

  @Test
  void dragCannotOverwriteControlsOrExtractMailboxIcons() {
    ShippingService shipping = mock(ShippingService.class);
    MailboxService mailbox = mock(MailboxService.class);
    GuiListener listener = new GuiListener(shipping, mailbox);
    Player player = mock(Player.class);
    Inventory top = mock(Inventory.class);
    when(top.getSize()).thenReturn(27);
    InventoryView view = mock(InventoryView.class);
    when(view.getTopInventory()).thenReturn(top);
    InventoryDragEvent event = mock(InventoryDragEvent.class);
    when(event.getWhoClicked()).thenReturn(player);
    when(event.getView()).thenReturn(view);
    when(event.getRawSlots()).thenReturn(Set.of(13, 15));
    when(shipping.owns(player, top)).thenReturn(true);
    listener.onDrag(event);
    verify(event).setCancelled(true);
    reset(event);
    when(event.getWhoClicked()).thenReturn(player);
    when(event.getView()).thenReturn(view);
    when(event.getRawSlots()).thenReturn(Set.of(10));
    when(shipping.owns(player, top)).thenReturn(false);
    when(mailbox.owns(player)).thenReturn(true);
    listener.onDrag(event);
    verify(event).setCancelled(true);
  }
}
