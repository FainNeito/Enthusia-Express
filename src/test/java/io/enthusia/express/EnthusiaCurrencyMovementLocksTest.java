package io.enthusia.express;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.enthusia.express.infrastructure.hook.EnthusiaCurrencyMovementLocks;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class EnthusiaCurrencyMovementLocksTest {
    @Test
    void missingModerationApiIsDiagnosedOnceAndClaimsStayFailClosed() {
        Logger logger = Logger.getAnonymousLogger();
        var messages = new ArrayList<String>();
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.SEVERE.intValue()) messages.add(record.getMessage());
            }
            @Override public void flush() {}
            @Override public void close() {}
        });

        JavaPlugin express = mock(JavaPlugin.class);
        when(express.getLogger()).thenReturn(logger);
        Plugin currency = mock(Plugin.class);
        when(currency.isEnabled()).thenReturn(true);
        PluginDescriptionFile description = mock(PluginDescriptionFile.class);
        when(description.getVersion()).thenReturn("1.4.3-test");
        when(currency.getDescription()).thenReturn(description);
        PluginManager plugins = mock(PluginManager.class);
        when(plugins.getPlugin("EnthusiaCurrency")).thenReturn(currency);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            var locks = new EnthusiaCurrencyMovementLocks(express);

            locks.bindIfPresent();
            assertNull(locks.acquire(UUID.randomUUID()));
            assertNull(locks.acquire(UUID.randomUUID()));
            assertEquals(1, messages.size());
            assertTrue(messages.getFirst().contains("does not publish"));
            assertTrue(messages.getFirst().contains("CurrencyModerationApi"));
            assertTrue(messages.getFirst().contains("1.4.3-test"));
        }
    }
}
