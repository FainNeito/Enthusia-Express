package io.enthusia.express;

import io.enthusia.express.application.MailStore;
import io.enthusia.express.domain.MuseumFilter;
import io.enthusia.express.infrastructure.gui.*;
import io.enthusia.express.infrastructure.hook.CombatLogXHook;
import io.enthusia.express.infrastructure.util.MainThread;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;

class MuseumMenusTest {
    @Test void nonManagersCannotReviewOrChooseAnotherDonorsHistory() {
        try(var f=new Fixture()) {
            f.menus.command(f.player,new String[]{"mapart","accept","1","0"});
            verify(f.store,never()).reviewArtwork(anyLong(),anyLong(),any(),any(),anyString(),anyString());
            f.menus.browse(f.player,new MuseumFilter(UUID.randomUUID()),0);
            verify(f.store).listArtwork(eq(0),eq(new MuseumFilter(f.owner)));
        }
    }

    @Test void duplicateOverrideCannotBeRequestedWithoutItsSeparatePermission() {
        try(var f=new Fixture()) {
            f.menus.command(f.player,new String[]{"mapart","override","Title|Artist|1x1|Description"});
            verify(f.shipping,never()).openArtwork(any(),any(),anyBoolean());
            f.menus.command(f.player,new String[]{"mapart","draft","Title|Artist|1x1|Description"});
            verify(f.shipping).openArtwork(eq(f.player),any(),eq(false));
        }
    }

    @Test void combatAndDisconnectRefuseEveryMuseumCommandBeforeStorage() {
        try(var f=new Fixture()) {
            when(f.combat.mayUseMail(f.player)).thenReturn(false);
            f.menus.command(f.player,new String[]{"mapart","mine"});
            verifyNoInteractions(f.store);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final UUID owner=UUID.randomUUID();
        final JavaPlugin plugin=mock(JavaPlugin.class);
        final MailStore store=mock(MailStore.class);
        final Player player=mock(Player.class);
        final CombatLogXHook combat=mock(CombatLogXHook.class);
        final ShippingService shipping=mock(ShippingService.class);
        final org.mockito.MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);
        final MuseumMenus menus;
        Fixture() {
            when(plugin.isEnabled()).thenReturn(true);when(plugin.getConfig()).thenReturn(new YamlConfiguration());
            when(player.getUniqueId()).thenReturn(owner);when(player.getName()).thenReturn("Donor");when(player.isOnline()).thenReturn(true);
            when(player.hasPermission("enthusiaexpress.use")).thenReturn(true);
            when(player.hasPermission("enthusiaexpress.mapart.submit")).thenReturn(true);
            when(combat.mayUseMail(player)).thenReturn(true);
            bukkit.when(()->Bukkit.getPlayer(owner)).thenReturn(player);
            when(store.listArtwork(anyInt(),any())).thenReturn(CompletableFuture.completedFuture(List.of()));
            menus=new MuseumMenus(plugin,store,mock(MainThread.class),combat,shipping,mock(MailboxService.class));
        }
        public void close() { bukkit.close(); }
    }
}
