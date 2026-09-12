package io.enthusia.express;

import io.enthusia.express.infrastructure.util.SoundFeedback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.*;
import org.junit.jupiter.api.Test;

class CurrencyShippingTest {
  /** A virtual-currency quote is visible before the provider is asked to withdraw. */
  @Test
  void currencyQuotePrecedesWithdrawal() {
    try (var f = new ShippingServiceTest.Fixture(OptionalLong.of(1))) {
      Economy economy = install(f);
      f.service.confirm(f.sender, f.top);
      verify(f.sender).sendMessage(contains("2 currency"));
      verifyNoInteractions(economy, f.repository);
      f.service.confirm(f.sender, f.top);
      verify(economy).withdrawPlayer((OfflinePlayer) f.sender, 2.0);
    }
  }

  private Economy install(ShippingServiceTest.Fixture f) {
    f.plugin.getConfig().set("payments.provider", "auto");
    PluginManager manager = mock(PluginManager.class);
    ServicesManager services = mock(ServicesManager.class);
    Plugin currency = mock(Plugin.class);
    when(currency.getName()).thenReturn("EnthusiaCurrency");
    when(currency.isEnabled()).thenReturn(true);
    when(manager.getPlugin("EnthusiaCurrency")).thenReturn(currency);
    when(manager.isPluginEnabled("Vault")).thenReturn(true);
    f.bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
    f.bukkit.when(Bukkit::getServicesManager).thenReturn(services);
    Economy economy = mock(Economy.class);
    when(economy.getName()).thenReturn("EnthusiaCurrency");
    when(economy.isEnabled()).thenReturn(true);
    when(services.getRegistrations(Economy.class)).thenReturn(List.of(
        new RegisteredServiceProvider<>(Economy.class, economy, ServicePriority.Normal, currency)));
    when(economy.getBalance((OfflinePlayer) f.sender)).thenReturn(100.0);
    when(economy.withdrawPlayer((OfflinePlayer) f.sender, 2.0)).thenReturn(success(2));
    when(economy.depositPlayer((OfflinePlayer) f.sender, 2.0)).thenReturn(success(2));
    return economy;
  }

  private static EconomyResponse success(double amount) {
    return new EconomyResponse(amount, 98, EconomyResponse.ResponseType.SUCCESS, null);
  }
  /** Verifies that insufficient currency message preserves fractional balance. */

  @Test void insufficientCurrencyMessagePreservesFractionalBalance() {
    try (var f = new ShippingServiceTest.Fixture(OptionalLong.of(1))) {
      Economy economy = install(f);
      f.plugin.getConfig().set("messages.insufficient-currency", "Need {cost} currency; balance {have}");
      f.plugin.getConfig().set("messages.insufficient-gold", "WRONG PHYSICAL MESSAGE");
      when(economy.withdrawPlayer((OfflinePlayer) f.sender, 2.0)).thenReturn(
          new EconomyResponse(0, 1.75, EconomyResponse.ResponseType.FAILURE, "insufficient"));
      f.confirm();
      verify(f.sender).sendMessage("Need 2 currency; balance 1.75");
      verifyNoInteractions(f.sounds);
      verify(f.repository, never()).insertMailLimited(any(), anyString(), any(), anyString(), any(), any(), anyInt(), anyBoolean());
      verify(f.playerInventory, never()).setStorageContents(any());
    }
  }
  /** Verifies that bank only balance pays for shipping without physical gold. */

  @Test void bankOnlyBalancePaysForShippingWithoutPhysicalGold() {
    try (var f = new ShippingServiceTest.Fixture(OptionalLong.of(1))) {
      Economy economy = install(f);
      when(f.playerInventory.getStorageContents()).thenReturn(new ItemStack[0]);
      f.confirm();
      verify(economy).withdrawPlayer((OfflinePlayer) f.sender, 2.0);
      verify(f.sounds).play(f.sender, SoundFeedback.Cue.PACKAGE_SEND);
      verify(f.playerInventory, never()).setStorageContents(any());
    }
  }
  /** Verifies that mixed balance uses one provider withdrawal without second item charge. */

  @Test void mixedBalanceUsesOneProviderWithdrawalWithoutSecondItemCharge() {
    try (var f = new ShippingServiceTest.Fixture(OptionalLong.of(1))) {
      Economy economy = install(f);
      when(economy.getBalance((OfflinePlayer) f.sender)).thenReturn(2.0);
      f.confirm();
      verify(economy, times(1)).withdrawPlayer((OfflinePlayer) f.sender, 2.0);
      verify(f.playerInventory, never()).setStorageContents(any());
      verify(f.gold, never()).setAmount(anyInt());
    }
  }
  /** Verifies that rejected withdrawal keeps cargo even when physical gold is available. */

  @Test void rejectedWithdrawalKeepsCargoEvenWhenPhysicalGoldIsAvailable() {
    try (var f = new ShippingServiceTest.Fixture(OptionalLong.of(1))) {
      Economy economy = install(f);
      when(economy.withdrawPlayer((OfflinePlayer) f.sender, 2.0)).thenReturn(
          new EconomyResponse(0, 1, EconomyResponse.ResponseType.FAILURE, "insufficient"));
      f.confirm();
      verifyNoInteractions(f.sounds);
      verify(f.repository, never()).insertMailLimited(any(), anyString(), any(), anyString(), any(), any(), anyInt(), anyBoolean());
      verify(f.top, never()).setItem(eq(13), isNull());
      verify(f.playerInventory, never()).setStorageContents(any());
      verify(economy, never()).depositPlayer(any(OfflinePlayer.class), anyDouble());
    }
  }
  /** Verifies that database failure and limit rejection refund through original provider. */

  @Test void databaseFailureAndLimitRejectionRefundThroughOriginalProvider() {
    for (var outcome : List.of(CompletableFuture.completedFuture(OptionalLong.empty()),
        CompletableFuture.<OptionalLong>failedFuture(new SQLException("failed")))) {
      try (var f = new ShippingServiceTest.Fixture(outcome)) {
        Economy economy = install(f);
        f.confirm();
        verify(economy).depositPlayer((OfflinePlayer) f.sender, 2.0);
        verify(f.playerInventory).addItem(f.packageItem);
        verify(f.playerInventory, never()).setStorageContents(any());
        verifyNoInteractions(f.sounds);
        assertTrue(f.constructed.constructed().stream().noneMatch(item -> item.getType() == org.bukkit.Material.RAW_GOLD));
      }
    }
  }
  /** Verifies that disabled currency provider fails closed. */

  @Test void disabledCurrencyProviderFailsClosed() {
    try (var f = new ShippingServiceTest.Fixture(OptionalLong.of(1))) {
      Economy economy = install(f);
      when(economy.isEnabled()).thenReturn(false);
      f.confirm();
      verifyNoInteractions(f.sounds);
      verify(f.repository, never()).insertMailLimited(any(), anyString(), any(), anyString(), any(), any(), anyInt(), anyBoolean());
      verify(economy, never()).withdrawPlayer(any(OfflinePlayer.class), anyDouble());
      verify(f.playerInventory, never()).setStorageContents(any());
    }
  }
  /** Verifies that failed refund is reported and does not mint physical gold. */

  @Test void failedRefundIsReportedAndDoesNotMintPhysicalGold() {
    try (var f = new ShippingServiceTest.Fixture(OptionalLong.empty())) {
      Economy economy = install(f);
      when(economy.depositPlayer((OfflinePlayer) f.sender, 2.0)).thenReturn(
          new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "unavailable"));
      f.confirm();
      verify(f.sender).sendMessage(contains("fee refund failed"));
      verify(f.playerInventory).addItem(f.packageItem);
      assertTrue(f.constructed.constructed().stream().noneMatch(item -> item.getType() == org.bukkit.Material.RAW_GOLD));
    }
  }
  /** Verifies that zero postage does not call provider which rejects zero withdrawals. */

  @Test void zeroPostageDoesNotCallProviderWhichRejectsZeroWithdrawals() {
    try (var f = new ShippingServiceTest.Fixture(OptionalLong.of(1))) {
      Economy economy = install(f);
      f.plugin.getConfig().set("mail.raw-gold-per-item", 0);
      when(f.playerInventory.getStorageContents()).thenReturn(new ItemStack[0]);
      f.confirm();
      verify(economy, never()).withdrawPlayer(any(OfflinePlayer.class), anyDouble());
      verify(f.sounds).play(f.sender, SoundFeedback.Cue.PACKAGE_SEND);
    }
  }
  /** Verifies that absent currency uses physical gold only in auto mode. */

  @Test void absentCurrencyUsesPhysicalGoldOnlyInAutoMode() {
    for (String mode : List.of("auto", "enthusia-currency")) {
      try (var f = new ShippingServiceTest.Fixture(OptionalLong.of(1))) {
        f.plugin.getConfig().set("payments.provider", mode);
        PluginManager manager = mock(PluginManager.class);
        f.bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
        f.confirm();
        if (mode.equals("auto")) verify(f.playerInventory).setStorageContents(any());
        else {
          verifyNoInteractions(f.sounds);
      verify(f.repository, never()).insertMailLimited(any(), anyString(), any(), anyString(), any(), any(), anyInt(), anyBoolean());
          verify(f.playerInventory, never()).setStorageContents(any());
        }
      }
    }
  }
  /** Verifies that refund uses original provider after registration changes. */

  @Test void refundUsesOriginalProviderAfterRegistrationChanges() {
    CompletableFuture<OptionalLong> pending = new CompletableFuture<>();
    try (var f = new ShippingServiceTest.Fixture(pending)) {
      Economy original = install(f);
      f.confirm();
      Economy replacement = install(f);
      pending.complete(OptionalLong.empty());
      verify(original).depositPlayer((OfflinePlayer) f.sender, 2.0);
      verify(replacement, never()).depositPlayer(any(OfflinePlayer.class), anyDouble());
    }
  }
  /** Verifies that offline refund still credits the original account. */

  @Test void offlineRefundStillCreditsTheOriginalAccount() {
    CompletableFuture<OptionalLong> pending = new CompletableFuture<>();
    try (var f = new ShippingServiceTest.Fixture(pending)) {
      Economy economy = install(f);
      f.confirm();
      when(f.sender.isOnline()).thenReturn(false);
      pending.complete(OptionalLong.empty());
      verify(economy).depositPlayer((OfflinePlayer) f.sender, 2.0);
      verify(f.sender).saveData();
    }
  }
  /** Verifies that physical mode loads without vault classes. */

  @Test void physicalModeLoadsWithoutVaultClasses() throws Exception {
    try (var f = new ShippingServiceTest.Fixture(OptionalLong.of(1));
        var loader = new java.net.URLClassLoader(
            new java.net.URL[] {java.nio.file.Path.of(System.getProperty("pluginJar")).toUri().toURL()},
            getClass().getClassLoader()) {
          @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("net.milkbowl.vault.")) throw new ClassNotFoundException(name);
            if (name.startsWith("io.enthusia.express.infrastructure.payment.")) {
              Class<?> type = findLoadedClass(name);
              if (type == null) type = findClass(name);
              if (resolve) resolveClass(type);
              return type;
            }
            return super.loadClass(name, resolve);
          }
        }) {
      Class<?> payments = loader.loadClass("io.enthusia.express.infrastructure.payment.ShippingPayments");
      Object adapter = payments.getConstructor(org.bukkit.plugin.java.JavaPlugin.class).newInstance(f.plugin);
      Object result = payments.getMethod("charge", org.bukkit.entity.Player.class, int.class).invoke(adapter, f.sender, 2);
      assertNotNull(result.getClass().getMethod("getReceipt").invoke(result));
      verify(f.playerInventory).setStorageContents(any());
    }
  }
}
