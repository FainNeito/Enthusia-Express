package io.enthusia.express;

import io.enthusia.express.application.DialogSessions;

import io.enthusia.express.infrastructure.gui.DialogMenus;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DialogSessionTest {
    @Test void openingInputFirstClosesInventorySoShippingRetainsItsCloseAndRefundLifecycle() {
        var plugin=org.mockito.Mockito.mock(org.bukkit.plugin.java.JavaPlugin.class);
        var player=org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        var config=new org.bukkit.configuration.file.YamlConfiguration();
        config.set("gui.dialogs.enabled",false);
        org.mockito.Mockito.when(plugin.getConfig()).thenReturn(config);
        assertFalse(new DialogMenus(plugin)
            .show(player,"Input","Details",java.util.List.of(),java.util.List.of()));
        org.mockito.Mockito.verify(player).closeInventory();
    }

    @Test void callbacksMustHaveOneShotSessionAndExpiryGuards() throws Exception {
        assertDoesNotThrow(()->Class.forName("io.enthusia.express.application.DialogSessions"),
            "Client input needs an explicit one-shot, owner-bound and expiring session gate");
    }

    @Test void aMenuActionIsOwnerBoundOneShotExpiringAndInvalidatedByReopen() {
        var gate=new DialogSessions();
        var owner=java.util.UUID.randomUUID(); var stranger=java.util.UUID.randomUUID();
        var first=gate.open(owner,100);
        assertFalse(gate.accept(stranger,first,101));
        assertTrue(gate.accept(owner,first,101));
        assertFalse(gate.accept(owner,first,102));
        var stale=gate.open(owner,200); var current=gate.open(owner,201);
        assertFalse(gate.accept(owner,stale,202));
        assertTrue(gate.accept(owner,current,202));
        var expired=gate.open(owner,300);
        assertFalse(gate.accept(owner,expired,180301));
        var closed=gate.open(owner,400); gate.close(owner);
        assertFalse(gate.accept(owner,closed,401));
    }
}
