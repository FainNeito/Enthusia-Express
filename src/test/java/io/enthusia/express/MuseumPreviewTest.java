package io.enthusia.express;

import io.enthusia.express.application.MailStore;
import io.enthusia.express.domain.*;
import io.enthusia.express.infrastructure.gui.*;
import io.enthusia.express.infrastructure.hook.CombatLogXHook;
import io.enthusia.express.infrastructure.util.MainThread;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MuseumPreviewTest {
    @Test void legacyContainerUsesReadOnlyPayloadManifestWithoutClaiming() {
        try (var f = new Fixture(); var codec = mockStatic(io.enthusia.express.infrastructure.util.ItemCodec.class);
             var scanner = mockStatic(io.enthusia.express.infrastructure.util.ContainerScanner.class)) {
            byte[] payload = {1, 2, 3}; var cargo = mock(ItemStack.class);
            when(f.entry.getTiles()).thenReturn(Map.of()); when(f.mail.payload()).thenReturn(payload);
            codec.when(() -> io.enthusia.express.infrastructure.util.ItemCodec.decode(payload)).thenReturn(cargo);
            scanner.when(() -> io.enthusia.express.infrastructure.util.ContainerScanner.mapartManifest(cargo, 8)).thenReturn(Map.of(7, 2, 11, 1));
            f.command("preview", "42", "1");
            verify(f.player).sendMap(f.map);
            f.bukkit.verify(() -> Bukkit.getMap(11));
            verifyNoInteractions(f.inventory, f.mailbox, f.shipping);
            verify(f.store, never()).claimMapart(any(), any(), anyString());
        }
    }

    @Test void legacyDecodeRespectsPayloadLimitAndAuthorization() {
        try (var f = new Fixture(); var codec = mockStatic(io.enthusia.express.infrastructure.util.ItemCodec.class)) {
            when(f.entry.getTiles()).thenReturn(Map.of()); when(f.mail.payload()).thenReturn(new byte[262145]);
            f.command("preview", "42");
            when(f.mail.payload()).thenReturn(new byte[]{1});
            when(f.player.hasPermission("enthusiaexpress.mapart.manage")).thenReturn(false);
            f.command("preview", "42");
            codec.verifyNoInteractions(); verify(f.player, never()).sendMap(any());
        }
    }

    @Test void invalidLegacyBytesDoNotChangeInventoryOrClaimCargo() {
        try (var f = new Fixture(); var codec = mockStatic(io.enthusia.express.infrastructure.util.ItemCodec.class)) {
            when(f.entry.getTiles()).thenReturn(Map.of()); byte[] payload = {1}; when(f.mail.payload()).thenReturn(payload);
            codec.when(() -> io.enthusia.express.infrastructure.util.ItemCodec.decode(payload)).thenThrow(new IllegalArgumentException("Invalid bytes"));
            f.command("preview", "42");
            codec.verify(() -> io.enthusia.express.infrastructure.util.ItemCodec.decode(payload));
            verify(f.player, never()).sendMap(any()); verifyNoInteractions(f.inventory, f.mailbox, f.shipping);
        }
    }

    @Test void reviewerSeesExistingMapWithoutClaimingOrChangingInventory() {
        try (var f = new Fixture()) {
            f.command("preview", "42");
            verify(f.store).artwork(42L);
            verify(f.player).sendMap(f.map);
            verify(f.player).sendEquipmentChange(eq(f.player), argThat((Map<EquipmentSlot, ItemStack> items) ->
                items.containsKey(EquipmentSlot.HAND) && items.containsKey(EquipmentSlot.OFF_HAND)));
            verify(f.meta).setMapView(f.map);
            verifyNoInteractions(f.inventory, f.mailbox, f.shipping);
            verify(f.store, never()).claimMapart(any(), any(), anyString());
            verify(f.store, never()).reviewArtwork(anyLong(), anyLong(), any(), any(), anyString(), anyString());
            f.menus.shutdown();
            verify(f.player).updateInventory();
        }
    }

    @Test void copiesShareOnePageAndTileNavigationUsesSortedMapIds() {
        try (var f = new Fixture()) {
            f.command("preview", "42", "1");
            f.bukkit.verify(() -> Bukkit.getMap(11));
            f.bukkit.verify(() -> Bukkit.getMap(7), never());
            verify(f.player).sendMap(f.map);
        }
    }

    @Test void donorCanPreviewOwnSubmissionButUnrelatedPlayersCannot() {
        try (var f = new Fixture()) {
            when(f.player.hasPermission("enthusiaexpress.mapart.manage")).thenReturn(false);
            f.command("preview", "42");
            verify(f.player, never()).sendMap(any());
            when(f.mail.sender()).thenReturn(f.actor);
            f.command("preview", "42");
            verify(f.player).sendMap(f.map);
        }
    }

    @Test void missingMapsAndInvalidPageNeverCreateReplacementMaps() {
        try (var f = new Fixture()) {
            f.bukkit.when(() -> Bukkit.getMap(7)).thenReturn(null);
            f.command("preview", "42");
            f.command("preview", "42", "-1");
            f.command("preview", "42", "2");
            f.command("preview", "42", "invalid");
            verify(f.player, never()).sendMap(any());
            f.bukkit.verify(() -> Bukkit.createMap(any()), never());
            verifyNoInteractions(f.inventory);
        }
    }

    @Test void permissionLossCombatAndExpiryRestoreTheClientDisplay() {
        try (var f = new Fixture()) {
            f.command("preview", "42");
            when(f.combat.mayUseMail(f.player)).thenReturn(false);
            f.tick();
            verify(f.player).updateInventory();
            when(f.combat.mayUseMail(f.player)).thenReturn(true);
            f.command("preview", "42");
            when(f.player.hasPermission("enthusiaexpress.use")).thenReturn(false);
            f.tick();
            verify(f.player, times(2)).updateInventory();
            when(f.player.hasPermission("enthusiaexpress.use")).thenReturn(true);
            f.command("preview", "42");
            for (int i = 0; i < 1201; i++) f.tick();
            verify(f.player, times(3)).updateInventory();
        }
    }

    @Test void delayedResultsRecheckOwnershipAndNewestRequest() {
        try (var f = new Fixture()) {
            var pending = new CompletableFuture<MuseumEntry>();
            when(f.store.artwork(42L)).thenReturn(pending);
            f.command("preview", "42");
            f.command("preview", "42", "1");
            when(f.player.hasPermission("enthusiaexpress.mapart.manage")).thenReturn(false);
            pending.complete(f.entry);
            verify(f.player, never()).sendMap(any());
        }
    }

    @Test void explicitExitRestoresWithoutClaiming() {
        try (var f = new Fixture()) {
            f.command("preview", "42");
            f.command("preview-close");
            verify(f.player).updateInventory();
            verifyNoInteractions(f.inventory, f.mailbox, f.shipping);
        }
    }

    @Test void newerNavigationWinsOverAnEarlierDelayedPreview() {
        try (var f = new Fixture()) {
            var pending = new CompletableFuture<MuseumEntry>();
            when(f.store.artwork(42L)).thenReturn(pending);
            f.command("preview", "42");
            f.command("preview", "42", "1");
            pending.complete(f.entry);
            verify(f.player, times(1)).sendMap(f.map);
            f.bukkit.verify(() -> Bukkit.getMap(11));
            f.bukkit.verify(() -> Bukkit.getMap(7), never());
        }
    }

    @Test void usingOrDroppingHiddenRealItemsIsCancelledAndEndsPreview() {
        try (var f = new Fixture()) {
            var events = (MuseumPreviewInteractions) f.menus.getPreviewInteractionListener();
            f.command("preview", "42");
            var drop = mock(org.bukkit.event.player.PlayerDropItemEvent.class);
            when(drop.getPlayer()).thenReturn(f.player);
            events.drop(drop);
            verify(drop).setCancelled(true);
            verify(f.player).updateInventory();
            f.command("preview", "42");
            var interact = mock(org.bukkit.event.player.PlayerInteractEvent.class);
            when(interact.getPlayer()).thenReturn(f.player);
            events.interact(interact);
            verify(interact).setCancelled(true);
            verify(f.player, times(2)).updateInventory();
            verifyNoInteractions(f.inventory);
        }
    }

    @Test void disconnectAndNewInventoryInvalidatePendingPreviewResults() {
        try (var f = new Fixture()) {
            var pending = new CompletableFuture<MuseumEntry>();
            when(f.store.artwork(42L)).thenReturn(pending);
            f.command("preview", "42");
            var event = mock(org.bukkit.event.inventory.InventoryOpenEvent.class);
            when(event.getPlayer()).thenReturn(f.player);
            ((MuseumPreviewEvents) f.menus.getPreviewListener()).open(event);
            pending.complete(f.entry);
            verify(f.player, never()).sendMap(any());
            f.command("preview", "42");
            when(f.player.isOnline()).thenReturn(false);
            f.tick();
            verify(f.player, never()).updateInventory();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final UUID actor = UUID.randomUUID();
        final JavaPlugin plugin = mock(JavaPlugin.class);
        final MailStore store = mock(MailStore.class);
        final Player player = mock(Player.class);
        final PlayerInventory inventory = mock(PlayerInventory.class);
        final CombatLogXHook combat = mock(CombatLogXHook.class);
        final ShippingService shipping = mock(ShippingService.class);
        final MailboxService mailbox = mock(MailboxService.class);
        final MainThread main = mock(MainThread.class);
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final MapView map = mock(MapView.class);
        final MapMeta meta = mock(MapMeta.class);
        final MailRecord mail = mock(MailRecord.class);
        final MuseumEntry entry = mock(MuseumEntry.class);
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final MockedConstruction<ItemStack> stacks = mockConstruction(ItemStack.class, (item, context) -> {
            when(item.getItemMeta()).thenReturn(meta);
        });
        final MuseumMenus menus;
        Runnable timer;
        Fixture() {
            when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
            when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
            when(player.getUniqueId()).thenReturn(actor);
            when(player.isOnline()).thenReturn(true);
            when(player.hasPermission(anyString())).thenReturn(true);
            when(player.getInventory()).thenReturn(inventory);
            var inventoryView = mock(InventoryView.class);
            when(inventoryView.getTopInventory()).thenReturn(mock(Inventory.class));
            when(player.getOpenInventory()).thenReturn(inventoryView);
            when(combat.mayUseMail(player)).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(actor)).thenReturn(player);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getMap(anyInt())).thenReturn(map);
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
                timer = call.getArgument(1); return mock(BukkitTask.class);
            });
            when(mail.id()).thenReturn(42L);
            when(mail.sender()).thenReturn(UUID.randomUUID());
            when(entry.getSubmission()).thenReturn(new MapartSubmission(mail, 7, "Artwork", 0, null, false, null));
            when(entry.getDetails()).thenReturn(new MuseumDetails("Artwork", "Artist", 2, 1, ""));
            when(entry.getTiles()).thenReturn(Map.of(11, 1, 7, 3));
            when(store.artwork(42L)).thenReturn(CompletableFuture.completedFuture(entry));
            doAnswer(call -> {
                CompletableFuture<Object> future = call.getArgument(0);
                BiConsumer<Object, Throwable> callback = call.getArgument(1);
                future.whenComplete(callback); return null;
            }).when(main).complete(any(), any());
            menus = new MuseumMenus(plugin, store, main, combat, shipping, mailbox);
        }
        void command(String... args) {
            String[] full = new String[args.length + 1]; full[0] = "mapart";
            System.arraycopy(args, 0, full, 1, args.length); menus.command(player, full);
        }
        void tick() { if (timer != null) timer.run(); }
        public void close() { menus.shutdown(); stacks.close(); bukkit.close(); }
    }
}
