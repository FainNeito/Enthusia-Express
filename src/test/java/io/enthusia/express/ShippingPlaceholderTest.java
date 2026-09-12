package io.enthusia.express;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.enthusia.express.application.MailStore;
import io.enthusia.express.infrastructure.gui.*;
import io.enthusia.express.infrastructure.hook.CombatLogXHook;
import io.enthusia.express.infrastructure.util.MainThread;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class ShippingPlaceholderTest {
  JavaPlugin plugin;
  MailStore store;
  Player player;
  OfflinePlayer target;
  Inventory top;
  PlayerInventory inventory;
  InventoryView view;
  ShippingService shipping;
  GuiListener listener;
  MockedStatic<Bukkit> bukkit;
  MockedConstruction<ItemStack> icons;
  Map<Integer, ItemStack> slots;
  List<Runnable> tasks;
  ItemStack cursor;

  @BeforeEach void setup() {
    plugin = mock(JavaPlugin.class, invocation ->
        invocation.getMethod().getName().equals("namespace") ? "enthusiaexpress" : RETURNS_DEFAULTS.answer(invocation));
    when(plugin.getName()).thenReturn("EnthusiaExpress");
    when(plugin.getConfig()).thenReturn(new YamlConfiguration());
    store = mock(MailStore.class);
    CombatLogXHook combat = mock(CombatLogXHook.class);
    player = mock(Player.class);
    when(player.isOnline()).thenReturn(true);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.hasPermission(anyString())).thenReturn(true);
    when(combat.mayUseMail(player)).thenReturn(true);
    target = mock(OfflinePlayer.class);
    when(target.getUniqueId()).thenReturn(UUID.randomUUID());
    when(target.getName()).thenReturn("Recipient");
    top = mock(Inventory.class);
    when(top.getSize()).thenReturn(27);
    slots = new HashMap<>();
    doAnswer(i -> { slots.put(i.getArgument(0), i.getArgument(1)); return null; }).when(top).setItem(anyInt(), nullable(ItemStack.class));
    when(top.getItem(anyInt())).thenAnswer(i -> slots.get(i.getArgument(0)));
    inventory = mock(PlayerInventory.class);
    when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
    when(player.getInventory()).thenReturn(inventory);
    view = mock(InventoryView.class);
    when(view.getTopInventory()).thenReturn(top);
    when(player.getOpenInventory()).thenReturn(view);
    when(player.getItemOnCursor()).thenAnswer(i -> cursor);
    doAnswer(i -> { cursor = i.getArgument(0); return null; }).when(player).setItemOnCursor(nullable(ItemStack.class));
    BukkitScheduler scheduler = mock(BukkitScheduler.class);
    tasks = new ArrayList<>();
    doAnswer(i -> { tasks.add(i.getArgument(1)); return null; }).when(scheduler).runTask(eq(plugin), any(Runnable.class));
    bukkit = mockStatic(Bukkit.class);
    bukkit.when(() -> Bukkit.createInventory(isNull(), eq(27), anyString())).thenReturn(top);
    bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
    bukkit.when(() -> Bukkit.getOfflinePlayer(target.getUniqueId())).thenReturn(target);
    icons = mockConstruction(ItemStack.class, (item, context) -> {
      when(item.getType()).thenReturn((Material) context.arguments().get(0));
      ItemMeta meta = mock(ItemMeta.class);
      when(item.getItemMeta()).thenReturn(meta);
      PersistentDataContainer data = mock(PersistentDataContainer.class);
      when(meta.getPersistentDataContainer()).thenReturn(data);
      Set<NamespacedKey> marked = new HashSet<>();
      doAnswer(i -> { marked.add(i.getArgument(0)); return null; }).when(data).set(any(NamespacedKey.class), eq(PersistentDataType.BYTE), any(Byte.class));
      when(data.has(any(NamespacedKey.class), eq(PersistentDataType.BYTE))).thenAnswer(i -> marked.contains(i.getArgument(0)));
    });
    shipping = new ShippingService(plugin, store, combat, mock(MainThread.class));
    listener = new GuiListener(shipping, mock(MailboxService.class));
    shipping.open(player, target);
  }

  @AfterEach void cleanup() { icons.close(); bukkit.close(); }

  InventoryClickEvent click(ClickType type) {
    InventoryClickEvent event = mock(InventoryClickEvent.class);
    when(event.getWhoClicked()).thenReturn(player);
    when(event.getView()).thenReturn(view);
    when(event.getRawSlot()).thenReturn(ShippingService.PACKAGE_SLOT);
    when(event.getClick()).thenReturn(type);
    listener.onClick(event);
    return event;
  }

  ItemStack packageItem() {
    ItemStack stack = mock(ItemStack.class);
    Material type = mock(Material.class);
    when(stack.getType()).thenReturn(type);
    when(stack.getAmount()).thenReturn(1);
    when(stack.clone()).thenReturn(stack);
    return stack;
  }
  /** Verifies that opening displays gray glass placeholder. */

  @Test void openingDisplaysGrayGlassPlaceholder() {
    assertNotNull(slots.get(13));
    assertEquals(Material.GRAY_STAINED_GLASS_PANE, slots.get(13).getType());
  }
  /** Verifies that empty close discards marker without giving any item. */

  @Test void emptyCloseDiscardsMarkerWithoutGivingAnyItem() {
    assertNotNull(slots.get(13));
    shipping.returnPackageOnClose(player, top);
    assertNull(slots.get(13));
    verify(inventory, never()).addItem(any(ItemStack.class));
  }
  /** Verifies that ordinary package still returns on close. */

  @Test void ordinaryPackageStillReturnsOnClose() {
    ItemStack item = packageItem();
    slots.put(13, item);
    shipping.returnPackageOnClose(player, top);
    verify(inventory).addItem(item);
    assertNull(slots.get(13));
  }
  /** Verifies that normal cursor deposit is cancelled then safely replaces marker. */

  @Test void normalCursorDepositIsCancelledThenSafelyReplacesMarker() {
    ItemStack marker = slots.get(13);
    assertNotNull(marker);
    cursor = packageItem();
    ItemStack packageStack = cursor;
    InventoryClickEvent event = click(ClickType.LEFT);
    verify(event).setCancelled(true);
    assertSame(marker, slots.get(13));
    assertFalse(tasks.isEmpty());
    tasks.forEach(Runnable::run);
    assertSame(packageStack, slots.get(13));
    assertTrue(cursor == null || cursor.getType().isAir());
    verify(inventory, never()).addItem(any(ItemStack.class));
  }
  /** Verifies that empty cursor and unsafe clicks never extract marker. */

  @Test void emptyCursorAndUnsafeClicksNeverExtractMarker() {
    ItemStack marker = slots.get(13);
    assertNotNull(marker);
    for (ClickType type : List.of(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.NUMBER_KEY, ClickType.DOUBLE_CLICK)) {
      verify(click(type)).setCancelled(true);
    }
    tasks.forEach(Runnable::run);
    assertSame(marker, slots.get(13));
    assertNull(cursor);
  }
  /** Verifies that dragging over marker is cancelled. */

  @Test void draggingOverMarkerIsCancelled() {
    InventoryDragEvent event = mock(InventoryDragEvent.class);
    when(event.getWhoClicked()).thenReturn(player);
    when(event.getView()).thenReturn(view);
    when(event.getRawSlots()).thenReturn(Set.of(13));
    listener.onDrag(event);
    verify(event).setCancelled(true);
  }
  /** Verifies that stale deposit after closing cannot move cursor. */

  @Test void staleDepositAfterClosingCannotMoveCursor() {
    assertNotNull(slots.get(13));
    cursor = packageItem();
    ItemStack held = cursor;
    click(ClickType.LEFT);
    shipping.returnPackageOnClose(player, top);
    tasks.forEach(Runnable::run);
    assertSame(held, cursor);
    assertNull(slots.get(13));
  }
  /** Verifies that marker cannot be submitted as package. */

  @Test void markerCannotBeSubmittedAsPackage() {
    assertNotNull(slots.get(13));
    shipping.confirm(player, top);
    verifyNoInteractions(store);
    verify(inventory, never()).addItem(any(ItemStack.class));
  }
}
