package io.enthusia.express;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.aryEq;
import io.enthusia.express.domain.MapartQueue;
import io.enthusia.express.infrastructure.util.ContainerScanner;
import io.enthusia.express.infrastructure.payment.ShippingPayments;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import net.milkbowl.vault.economy.EconomyResponse;
import org.junit.jupiter.api.Test;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;

class MapartPostageTest {
  @TempDir Path directory;
  @Test void ambiguousRefundCannotCreditTwiceWhenRetried() {
    try (var f = new ShippingServiceTest.Fixture(OptionalLong.of(1))) {
      var economy = install(f);
      var result = new ShippingPayments(f.plugin).charge(f.sender, 2);
      when(economy.depositPlayer((OfflinePlayer) f.sender, 2.0)).thenThrow(new IllegalStateException("credited then response failed"));
      assertFalse(result.getReceipt().refund());
      assertFalse(result.getReceipt().refund());
      verify(economy, times(1)).depositPlayer((OfflinePlayer) f.sender, 2.0);
    }
  }
  private net.milkbowl.vault.economy.Economy install(ShippingServiceTest.Fixture f) {
    var fixture = new CurrencyShippingTest();
    fixture.paymentDirectory = directory;
    return fixture.install(f);
  }
  private void museum(ShippingServiceTest.Fixture f, CompletableFuture<Long> result) {
    f.plugin.getConfig().set("mapart.raw-gold-postage", 2);
    f.bukkit.when(() -> Bukkit.getOfflinePlayer(MapartQueue.ID)).thenReturn(f.target);
    f.scanner.when(() -> ContainerScanner.countMapartItems(f.packageItem, 8)).thenReturn(128);
    when(f.repository.insertMapart(any(), anyString(), any(), any(), anyString(), any())).thenReturn(result);
    f.service.openMapart(f.sender);
    f.top.setItem(13, f.packageItem);
  }
  @Test void museumUsesCombinedBalanceExactlyOnceAndPreservesPackage() {
    try (var f = new ShippingServiceTest.Fixture(OptionalLong.of(1))) {
      var economy = install(f);
      when(f.playerInventory.getStorageContents()).thenReturn(new ItemStack[0]);
      var result = new CompletableFuture<Long>();
      museum(f, result);
      f.confirm();
      f.service.confirm(f.sender, f.top);
      verify(economy, times(1)).withdrawPlayer((OfflinePlayer) f.sender, 2.0);
      verify(f.playerInventory, never()).setStorageContents(any());
      verify(f.repository, times(1)).insertMapart(eq(f.senderId), eq("Sender"), aryEq(new byte[]{1,2}), isNull(), eq("Mapart package (128 maps)"), any());
      result.complete(1L);
      verify(economy, never()).depositPlayer(any(OfflinePlayer.class), anyDouble());
    }
  }
  @Test void rejectedMuseumDebitDoesNotFallBackToPhysicalOrPublish() {
    try (var f = new ShippingServiceTest.Fixture(OptionalLong.of(1))) {
      var economy = install(f);
      when(economy.withdrawPlayer((OfflinePlayer) f.sender, 2.0)).thenReturn(new EconomyResponse(0,0,EconomyResponse.ResponseType.FAILURE,"insufficient"));
      museum(f, CompletableFuture.completedFuture(1L));
      f.confirm();
      assertSame(f.packageItem, f.top.getItem(13));
      verify(f.playerInventory, never()).setStorageContents(any());
      verify(f.repository, never()).insertMapart(any(), anyString(), any(), any(), anyString(), any());
    }
  }
  @Test void failedMuseumWriteRefundsCurrencyOnceInsteadOfMintingGold() {
    try (var f = new ShippingServiceTest.Fixture(OptionalLong.of(1))) {
      var economy = install(f);
      museum(f, CompletableFuture.failedFuture(new IllegalStateException("write refused")));
      f.confirm();
      f.service.retryCompensations();
      verify(economy, times(1)).depositPlayer((OfflinePlayer) f.sender, 2.0);
      verify(f.playerInventory, times(1)).addItem(f.packageItem);
      verify(f.playerInventory, never()).addItem(argThat((ItemStack i) -> i != f.packageItem));
    }
  }
}
