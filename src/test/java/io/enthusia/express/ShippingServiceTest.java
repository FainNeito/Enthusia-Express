package io.enthusia.express;

import io.enthusia.express.domain.MailBlockedException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import io.enthusia.express.infrastructure.db.MailRepository;
import io.enthusia.express.infrastructure.gui.ShippingService;
import io.enthusia.express.infrastructure.hook.CombatLogXHook;
import io.enthusia.express.domain.MailType;
import io.enthusia.express.infrastructure.util.*;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class ShippingServiceTest {
  /** A recipient block is checked before withdrawing postage. */
  @Test
  void blockedRecipientTakesNoPayment() {
    try (Fixture f = new Fixture(OptionalLong.of(1))) {
      when(f.repository.isBlocked(f.targetId, f.senderId)).thenReturn(CompletableFuture.completedFuture(true));
      f.confirm();
      verify(f.playerInventory, never()).setStorageContents(any());
      verify(f.repository, never()).insertMailLimited(any(), anyString(), any(), anyString(), any(), any(), anyInt(), anyBoolean());
      verify(f.sender).sendMessage(contains("not accepting your mail"));
    }
  }

  /** A block committed after the payment precheck refunds both cargo and postage. */
  @Test
  void concurrentBlockRefundsPostageAndCargo() {
    try (Fixture f = new Fixture(CompletableFuture.failedFuture(new MailBlockedException()))) {
      f.confirm();
      verify(f.playerInventory).addItem(f.packageItem);
      verify(f.playerInventory).addItem(argThat((ItemStack item) ->
          item.getType() == Material.RAW_GOLD && item.getAmount() == 2));
      verify(f.sender).sendMessage(contains("not accepting your mail"));
      verifyNoInteractions(f.sounds);
    }
  }

  /** A custom-textured marker remains protected even when its material changes. */
  @Test
  void customPlaceholderCannotBeReturnedOrShipped() {
    try (Fixture f = new Fixture(OptionalLong.of(1))) {
      ItemStack marker = f.constructed.constructed().getLast();
      when(marker.getType()).thenReturn(Material.PAPER);
      when(f.top.getItem(ShippingService.PACKAGE_SLOT)).thenReturn(marker);
      f.confirm();
      verifyNoInteractions(f.repository);
      f.service.returnPackageOnClose(f.sender, f.top);
      verify(f.playerInventory, never()).addItem(any(ItemStack.class));
    }
  }

  /** A changed fee must be shown and confirmed again before charging. */
  @Test
  void changedPostageRequiresANewConfirmation() {
    try (Fixture f = new Fixture(OptionalLong.of(1))) {
      f.service.confirm(f.sender, f.top);
      f.plugin.getConfig().set("mail.raw-gold-per-item", 3);
      f.service.confirm(f.sender, f.top);
      verifyNoInteractions(f.repository);
      verify(f.playerInventory, never()).setStorageContents(any());
      verify(f.sender).sendMessage(contains("6 Raw Gold"));
    }
  }

  /** Swapping cargo invalidates the quote even when its item count and price match. */
  @Test
  void changedCargoRequiresANewConfirmation() {
    try (Fixture f = new Fixture(OptionalLong.of(1))) {
      f.service.confirm(f.sender, f.top);
      f.codec.when(() -> ItemCodec.encode(f.packageItem)).thenReturn(new byte[] {9, 8});
      f.service.confirm(f.sender, f.top);
      verifyNoInteractions(f.repository);
      verify(f.playerInventory, never()).setStorageContents(any());
      verify(f.sender, times(2)).sendMessage(contains("Click Send package to confirm"));
    }
  }

  /** A first click quotes postage and never charges or persists cargo. */
  @Test
  void firstClickQuotesBeforeCharging() {
    try (Fixture f = new Fixture(OptionalLong.of(1))) {
      f.service.confirm(f.sender, f.top);
      verifyNoInteractions(f.repository);
      verify(f.playerInventory, never()).setStorageContents(any());
      verify(f.sender).sendMessage(contains("2 Raw Gold"));
    }
  }

  /** Verifies that package sound occurs only after atomic persistence accepts. */
  @Test
  void packageSoundOccursOnlyAfterAtomicPersistenceAccepts() {
    try (Fixture accepted = new Fixture(OptionalLong.of(4))) {
      verify(accepted.top).setItem(eq(ShippingService.PACKAGE_SLOT), any(ItemStack.class));
      accepted.confirm();
      verify(accepted.sounds).play(accepted.sender, SoundFeedback.Cue.PACKAGE_SEND);
      verify(accepted.repository)
          .insertMailLimited(
              eq(accepted.senderId),
              eq("Sender"),
              eq(accepted.targetId),
              eq("Recipient"),
              eq(MailType.PACKAGE),
              any(),
              eq(2),
              eq(true));
    }

    try (Fixture rejected = new Fixture(OptionalLong.empty())) {
      rejected.confirm();
      verifyNoInteractions(rejected.sounds);
      verify(rejected.playerInventory).addItem(rejected.packageItem);
      verify(rejected.playerInventory).addItem(argThat((ItemStack item) ->
          item.getType() == Material.RAW_GOLD && item.getAmount() == 2));
    }

    try (Fixture failed = new Fixture(CompletableFuture.failedFuture(new SQLException("failed")))) {
      failed.confirm();
      verifyNoInteractions(failed.sounds);
      verify(failed.playerInventory).addItem(failed.packageItem);
      verify(failed.playerInventory).addItem(argThat((ItemStack item) ->
          item.getType() == Material.RAW_GOLD && item.getAmount() == 2));
    }
  }
  /** Verifies that closing with placeholder does not return or drop it. */

  @Test
  void closingWithPlaceholderDoesNotReturnOrDropIt() {
    try (Fixture fixture = new Fixture(OptionalLong.of(1))) {
      ItemStack placeholder = fixture.constructed.constructed().getLast();
      assertEquals(Material.GRAY_STAINED_GLASS_PANE, placeholder.getType());
      when(fixture.top.getItem(ShippingService.PACKAGE_SLOT)).thenReturn(placeholder);
      fixture.service.returnPackageOnClose(fixture.sender, fixture.top);
      verify(fixture.playerInventory, never()).addItem(any(ItemStack.class));
      verifyNoInteractions(fixture.world);
    }
  }
  /** Verifies that close returns only cargo and drops overflow. */

  @Test
  void closeReturnsOnlyCargoAndDropsOverflow() {
    try (Fixture fixture = new Fixture(OptionalLong.of(1))) {
      ItemStack cargo = fixture.packageItem;
      ItemStack overflow = mock(ItemStack.class);
      HashMap<Integer, ItemStack> remainder = new HashMap<>();
      remainder.put(0, overflow);
      when(fixture.sender.getInventory().addItem(cargo)).thenReturn(remainder);
      fixture.service.returnPackageOnClose(fixture.sender, fixture.top);
      verify(fixture.sender.getWorld()).dropItemNaturally(fixture.sender.getLocation(), overflow);
    }
  }

  static final class Fixture implements AutoCloseable {
    final JavaPlugin plugin = mock(JavaPlugin.class, invocation ->
        invocation.getMethod().getName().equals("namespace")
            ? "enthusiaexpress" : RETURNS_DEFAULTS.answer(invocation));
    final MailRepository repository = mock(MailRepository.class);
    final CombatLogXHook combat = mock(CombatLogXHook.class);
    final MainThread main = mock(MainThread.class);
    final SoundFeedback sounds = mock(SoundFeedback.class);
    final Player sender = mock(Player.class);
    final OfflinePlayer target = mock(OfflinePlayer.class);
    final PlayerInventory playerInventory = mock(PlayerInventory.class);
    final Inventory top = mock(Inventory.class);
    final World world = mock(World.class);
    final Location location = mock(Location.class);
    final UUID senderId = UUID.randomUUID();
    final UUID targetId = UUID.randomUUID();
    final ItemStack packageItem = mock(ItemStack.class);
    final Material packageMaterial = mock(Material.class);
    final ItemStack gold = mock(ItemStack.class);
    final MockedStatic<Bukkit> bukkit;
    final MockedStatic<ContainerScanner> scanner;
    final MockedStatic<ItemCodec> codec;
    final MockedConstruction<ItemStack> constructed;
    final ShippingService service;

    Fixture(OptionalLong result) {
      this(CompletableFuture.completedFuture(result));
    }

    Fixture(CompletableFuture<OptionalLong> result) {
      configureMocks(result);

      bukkit = mockStatic(Bukkit.class);
      scanner = mockStatic(ContainerScanner.class);
      codec = mockStatic(ItemCodec.class);
      configureStaticContext();
      constructed =
          mockConstruction(
              ItemStack.class,
              (item, context) -> {
                ItemMeta meta = mock(ItemMeta.class);
                PersistentDataContainer pdc = mock(PersistentDataContainer.class);
                when(pdc.has(any(), eq(PersistentDataType.BYTE))).thenReturn(true);
                when(meta.getPersistentDataContainer()).thenReturn(pdc);
                when(item.getItemMeta()).thenReturn(meta);
                when(item.getType()).thenReturn((Material) context.arguments().getFirst());
                when(item.getAmount()).thenReturn(context.arguments().size() > 1
                    ? (Integer) context.arguments().get(1) : 1);
              });
      service = new ShippingService(plugin, repository, combat, main, sounds);
      service.open(sender, target);
      when(top.getItem(ShippingService.PACKAGE_SLOT)).thenReturn(packageItem);
    }

    private void configureMocks(CompletableFuture<OptionalLong> result) {
      YamlConfiguration config = new YamlConfiguration();
      config.set("payments.provider", "physical");
      config.set("mail.limits.one-outstanding-package-per-recipient", true);
      when(plugin.getName()).thenReturn("EnthusiaExpress");
      when(plugin.getConfig()).thenReturn(config);
      when(plugin.isEnabled()).thenReturn(true);
      when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
      when(sender.getUniqueId()).thenReturn(senderId);
      when(sender.getName()).thenReturn("Sender");
      when(sender.hasPermission(anyString())).thenReturn(true);
      when(sender.getInventory()).thenReturn(playerInventory);
      when(sender.getWorld()).thenReturn(world);
      when(sender.getLocation()).thenReturn(location);
      when(sender.isOnline()).thenReturn(true);
      when(target.getUniqueId()).thenReturn(targetId);
      when(target.getName()).thenReturn("Recipient");
      when(target.isOnline()).thenReturn(false);
      when(combat.mayUseMail(sender)).thenReturn(true);
      when(packageItem.getType()).thenReturn(packageMaterial);
      when(packageMaterial.isAir()).thenReturn(false);
      when(packageItem.getAmount()).thenReturn(1);
      when(packageItem.clone()).thenReturn(packageItem);
      when(gold.getType()).thenReturn(Material.RAW_GOLD);
      when(gold.getAmount()).thenReturn(2);
      when(playerInventory.getStorageContents()).thenReturn(new ItemStack[] {gold});
      when(playerInventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
      when(repository.isBlocked(any(), any())).thenReturn(CompletableFuture.completedFuture(false));
      when(repository.insertMailLimited(any(), anyString(), any(), anyString(), any(), any(), anyInt(), anyBoolean()))
          .thenReturn(result);
      doAnswer(
              invocation -> {
                CompletableFuture<Object> future = invocation.getArgument(0);
                BiConsumer<Object, Throwable> callback = invocation.getArgument(1);
                future.whenComplete(callback);
                return null;
              })
          .when(main)
          .complete(any(), any());
    }

    private void configureStaticContext() {
      bukkit.when(() -> Bukkit.createInventory(isNull(), eq(27), anyString())).thenReturn(top);
      bukkit.when(() -> Bukkit.getOfflinePlayer(targetId)).thenReturn(target);
      bukkit.when(() -> Bukkit.getPlayer(senderId)).thenReturn(sender);
      scanner.when(() -> ContainerScanner.isAllowedShippingContainer(packageItem)).thenReturn(true);
      scanner.when(() -> ContainerScanner.countPackedItems(packageItem, 8)).thenReturn(2);
      codec.when(() -> ItemCodec.encode(packageItem)).thenReturn(new byte[] {1, 2});
    }

    void confirm() {
      service.confirm(sender, top);
      service.confirm(sender, top);
    }

    @Override
    public void close() {
      constructed.close();
      codec.close();
      scanner.close();
      bukkit.close();
    }
  }
}
