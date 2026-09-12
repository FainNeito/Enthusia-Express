package io.enthusia.express;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import io.enthusia.express.infrastructure.db.DeliveryAcknowledgments;

import static org.mockito.Mockito.*;

import io.enthusia.express.infrastructure.db.MailRepository;
import io.enthusia.express.infrastructure.gui.MailboxService;
import io.enthusia.express.infrastructure.hook.CombatLogXHook;
import io.enthusia.express.domain.*;
import io.enthusia.express.infrastructure.mail.*;
import io.enthusia.express.infrastructure.util.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockito.*;

class MailboxServiceTest {
  JavaPlugin plugin;
  MailRepository repository;
  CombatLogXHook combat;
  MainThread main;
  Player player;
  PlayerInventory inventory;
  Inventory top;
  InventoryView view;
  MailboxService service;
  SoundFeedback sounds;
  UUID id;
  MockedStatic<Bukkit> bukkit;
  MockedStatic<ItemCodec> codec;
  MockedConstruction<ItemStack> icons;
  List<Runnable> callbacks;

  /** Optional Nexo title glyphs can replace the plain mailbox title. */
  @Test
  void configuredNexoTitleIsUsedWhenAvailable() {
    plugin.getConfig().set("gui.nexo.enabled", true);
    plugin.getConfig().set("gui.nexo.titles.mailbox", "<glyph:mail_menu>");
    PluginManager manager = mock(PluginManager.class);
    Plugin nexo = mock(Plugin.class);
    when(nexo.isEnabled()).thenReturn(true);
    when(manager.getPlugin("Nexo")).thenReturn(nexo);
    bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
    when(repository.listInbox(any(), any(), anyInt())).thenReturn(CompletableFuture.completedFuture(List.of()));
    service.open(player, MailType.PACKAGE);
    bukkit.verify(() -> Bukkit.createInventory(isNull(), eq(54), eq("<glyph:mail_menu>")));
  }

  /** History cannot claim packages, even though the viewer owns the sender record. */
  @Test
  void sentPackagesAreReadOnlyAndNavigationKeepsSentMode() {
    MailRecord sent = sentRecord(MailType.PACKAGE);
    when(repository.listSent(id, MailType.PACKAGE, 0)).thenReturn(CompletableFuture.completedFuture(
        List.of(new SentMailRecord(sent, "Recipient", false))));
    when(repository.listSent(id, MailType.LETTER, 0)).thenReturn(CompletableFuture.completedFuture(List.of()));
    service.openSent(player, MailType.PACKAGE);
    drain();
    clearInvocations(repository, player);
    service.click(player, 9);
    verifyNoInteractions(repository);
    verify(inventory, never()).addItem(any(ItemStack.class));
    service.click(player, 4);
    verify(repository).listSent(id, MailType.LETTER, 0);
    verify(player, never()).openInventory(any(Inventory.class));
  }

  /** Reading a sent letter must not alter its recipient's unread state. */
  @Test
  void sentLettersOpenWithoutMarkingRecipientCopyRead() {
    MailRecord sent = sentRecord(MailType.LETTER);
    ItemStack book = mock(ItemStack.class);
    codec.when(() -> ItemCodec.decode(sent.payload())).thenReturn(book);
    when(repository.listSent(id, MailType.LETTER, 0)).thenReturn(CompletableFuture.completedFuture(
        List.of(new SentMailRecord(sent, "Recipient", false))));
    service.openSent(player, MailType.LETTER);
    drain();
    clearInvocations(repository);
    service.click(player, 9);
    verify(player).openBook(book);
    verifyNoInteractions(repository);
  }

  /** Sent permission is rechecked before showing a history page. */
  @Test
  void sentHistoryRequiresPermission() {
    when(player.hasPermission("enthusiaexpress.sent")).thenReturn(false);
    service.openSent(player, MailType.PACKAGE);
    verifyNoInteractions(repository);
  }

  private MailRecord sentRecord(MailType type) {
    return new MailRecord(1, id, "Sender", UUID.randomUUID(), "Recipient", type, MailStatus.UNCLAIMED,
        new byte[] {1}, 2, 1, 1, true, false);
  }

  /** The current category is explicitly marked instead of relying on a long title. */
  @Test
  void selectedCategoryHasAnExplicitLabel() {
    when(repository.listInbox(any(), any(), anyInt())).thenReturn(CompletableFuture.completedFuture(List.of()));
    service.open(player, MailType.PACKAGE);
    verify(icons.constructed().get(1).getItemMeta()).setDisplayName("§a▶ Packages");
  }



  /** Navigation must not close or reopen the active inventory. */
  @Test
  void navigationKeepsTheSameInventoryOpen() {
    when(repository.listInbox(any(), any(), anyInt())).thenReturn(CompletableFuture.completedFuture(List.of()));
    service.open(player, MailType.PACKAGE);
    clearInvocations(player);
    service.click(player, 4);
    verify(player, never()).closeInventory();
    verify(player, never()).openInventory(any(Inventory.class));
    verify(top).clear();
  }

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setup() {
    plugin = mock(JavaPlugin.class);
    repository = mock(MailRepository.class);
    combat = mock(CombatLogXHook.class);
    main = mock(MainThread.class);
    when(plugin.getConfig()).thenReturn(new YamlConfiguration());
    when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
    player = mock(Player.class);
    id = UUID.randomUUID();
    when(player.getUniqueId()).thenReturn(id);
    when(player.isOnline()).thenReturn(true);
    when(repository.confirmDelivery(anyLong(), any())).thenReturn(CompletableFuture.completedFuture(true));
    when(player.hasPermission(anyString())).thenReturn(true);
    when(combat.mayUseMail(player)).thenReturn(true);
    inventory = mock(PlayerInventory.class);
    when(player.getInventory()).thenReturn(inventory);
    when(inventory.firstEmpty()).thenReturn(0);
    top = mock(Inventory.class);
    view = mock(InventoryView.class);
    when(player.getOpenInventory()).thenReturn(view);
    when(view.getTopInventory()).thenReturn(top);
    bukkit = mockStatic(Bukkit.class);
    bukkit.when(() -> Bukkit.createInventory(isNull(), eq(54), anyString())).thenReturn(top);
    codec = mockStatic(ItemCodec.class);
    icons =
        mockConstruction(
            ItemStack.class,
            (item, context) -> when(item.getItemMeta()).thenReturn(mock(ItemMeta.class)));
    callbacks = new ArrayList<>();
    doAnswer(
            invocation -> {
              CompletableFuture<Object> future = invocation.getArgument(0);
              BiConsumer<Object, Throwable> consumer = invocation.getArgument(1);
              future.whenComplete(
                  (value, error) -> callbacks.add(() -> consumer.accept(value, error)));
              return null;
            })
        .when(main)
        .complete(any(), any());
    sounds = mock(SoundFeedback.class);
    service = new MailboxService(plugin, repository, combat, main, sounds);
  }

  void drain() {
    while (!callbacks.isEmpty()) callbacks.removeFirst().run();
  }

  MailRecord record(MailType type) {
    return new MailRecord(
        1,
        UUID.randomUUID(),
        "Sender",
        id,
        "Recipient",
        type,
        MailStatus.UNCLAIMED,
        new byte[] {1},
        1,
        1,
        1,
        true,
        false);
  }

  void open(MailRecord record) {
    when(repository.listInbox(id, record.type(), 0))
        .thenReturn(CompletableFuture.completedFuture(List.of(record)));
    when(repository.get(1)).thenReturn(CompletableFuture.completedFuture(record));
    service.open(player, record.type());
    drain();
  }

  @AfterEach
  void cleanup() {
    icons.close();
    codec.close();
    bukkit.close();
  }
  /** Verifies that delivered package queues receipt after inventory and never restores it. */

  @Test
  void deliveredPackageQueuesReceiptAfterInventoryAndNeverRestoresIt() {
    var journal = mock(DeliveryAcknowledgments.class);
    when(journal.record(1, id)).thenReturn(CompletableFuture.completedFuture(null));
    service = new MailboxService(plugin, repository, combat, main, sounds, journal);
    MailRecord record = record(MailType.PACKAGE);
    ItemStack stack = mock(ItemStack.class);
    when(stack.getItemMeta()).thenReturn(mock(ItemMeta.class));
    codec.when(() -> ItemCodec.decode(record.payload())).thenReturn(stack);
    when(repository.claim(1, id)).thenReturn(CompletableFuture.completedFuture(true));
    when(inventory.addItem(stack)).thenReturn(new HashMap<>());
    open(record);
    service.click(player, 9);
    drain();
    var order = inOrder(inventory, journal);
    order.verify(inventory).addItem(stack);
    order.verify(journal).record(1, id);
    verify(repository, never()).restoreClaim(any());
    verify(repository, never()).confirmDelivery(anyLong(), any());
  }
  /** Verifies that letters open as books and persist read without claiming. */

  @Test
  void lettersOpenAsBooksAndPersistReadWithoutClaiming() {
    MailRecord record = record(MailType.LETTER);
    ItemStack book = mock(ItemStack.class);
    codec.when(() -> ItemCodec.decode(record.payload())).thenReturn(book);
    when(repository.markRead(1, id)).thenReturn(CompletableFuture.completedFuture(true));
    open(record);
    service.click(player, 9);
    drain();
    verify(player).openBook(book);
    verify(repository).markRead(1, id);
    verify(repository, never()).claim(anyLong(), any());
    verify(sounds).play(player, SoundFeedback.Cue.LETTER_OPEN);
  }
  /** Verifies that rejected read does not produce success sound. */

  @Test
  void rejectedReadDoesNotProduceSuccessSound() {
    MailRecord record = record(MailType.LETTER);
    ItemStack book = mock(ItemStack.class);
    codec.when(() -> ItemCodec.decode(record.payload())).thenReturn(book);
    when(repository.markRead(1, id)).thenReturn(CompletableFuture.completedFuture(false));
    open(record);
    service.click(player, 9);
    drain();
    verify(player).openBook(book);
    verifyNoInteractions(sounds);
  }
  /** Verifies that successful package delivery produces claim sound. */

  @Test
  void successfulPackageDeliveryProducesClaimSound() {
    MailRecord record = record(MailType.PACKAGE);
    ItemStack stack = mock(ItemStack.class);
    when(stack.getItemMeta()).thenReturn(mock(ItemMeta.class));
    codec.when(() -> ItemCodec.decode(record.payload())).thenReturn(stack);
    when(repository.claim(1, id)).thenReturn(CompletableFuture.completedFuture(true));
    when(inventory.addItem(stack)).thenReturn(new HashMap<>());
    open(record);
    service.click(player, 9);
    drain();
    verify(sounds).play(player, SoundFeedback.Cue.PACKAGE_CLAIM);
    verify(repository).confirmDelivery(1, id);
  }
  /** Verifies that failed claim produces no success sound. */

  @Test
  void failedClaimProducesNoSuccessSound() {
    MailRecord record = record(MailType.PACKAGE);
    ItemStack stack = mock(ItemStack.class);
    when(stack.getItemMeta()).thenReturn(mock(ItemMeta.class));
    codec.when(() -> ItemCodec.decode(record.payload())).thenReturn(stack);
    when(repository.claim(1, id)).thenReturn(CompletableFuture.completedFuture(false));
    open(record);
    service.click(player, 9);
    drain();
    verifyNoInteractions(sounds);
    verify(inventory, never()).addItem(any(ItemStack.class));
  }
  /** Verifies that announcements use the same book reader. */

  @Test
  void announcementsUseTheSameBookReader() {
    MailRecord record = record(MailType.ANNOUNCEMENT);
    ItemStack book = mock(ItemStack.class);
    codec.when(() -> ItemCodec.decode(record.payload())).thenReturn(book);
    when(repository.markRead(1, id)).thenReturn(CompletableFuture.completedFuture(true));
    open(record);
    service.click(player, 9);
    drain();
    verify(player).openBook(book);
  }
  /** Verifies that disconnect during claim restores instead of losing package. */

  @Test
  void disconnectDuringClaimRestoresInsteadOfLosingPackage() {
    MailRecord record = record(MailType.PACKAGE);
    ItemStack stack = mock(ItemStack.class);
    when(stack.getItemMeta()).thenReturn(mock(ItemMeta.class));
    codec.when(() -> ItemCodec.decode(record.payload())).thenReturn(stack);
    CompletableFuture<Boolean> claim = new CompletableFuture<>();
    when(repository.claim(1, id)).thenReturn(claim);
    when(repository.restoreClaim(record)).thenReturn(CompletableFuture.completedFuture(true));
    open(record);
    service.click(player, 9);
    drain();
    when(player.isOnline()).thenReturn(false);
    claim.complete(true);
    drain();
    verify(repository).restoreClaim(record);
    verify(inventory, never()).addItem(any(ItemStack.class));
  }
  /** Verifies that combat starting during read prevents opening book. */

  @Test
  void combatStartingDuringReadPreventsOpeningBook() {
    MailRecord record = record(MailType.LETTER);
    open(record);
    service.click(player, 9);
    when(combat.mayUseMail(player)).thenReturn(false);
    drain();
    verify(player, never()).openBook(any(ItemStack.class));
    verify(repository, never()).markRead(anyLong(), any());
  }
  /** Verifies that closing inbox before load prevents stale result rendering. */

  @Test
  void closingInboxBeforeLoadPreventsStaleResultRendering() {
    CompletableFuture<List<MailRecord>> load = new CompletableFuture<>();
    when(repository.listInbox(id, MailType.LETTER, 0)).thenReturn(load);
    service.open(player, MailType.LETTER);
    service.close(player, top);
    load.complete(List.of(record(MailType.LETTER)));
    drain();
    verify(top, never()).setItem(eq(9), any());
  }
}
