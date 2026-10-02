package io.enthusia.express;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.enthusia.express.domain.MailType;
import io.enthusia.express.domain.SentMailRecord;
import io.enthusia.express.infrastructure.db.MailRepository;
import io.enthusia.express.infrastructure.gui.SentMailDisplay;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class SentMailDisplayTest {
  @TempDir Path directory;

  /** Payload-free page snapshots must show retained contents and the available book action. */
  @Test void retainedHistoryUsesStatusRatherThanOmittedPayload() {
    var repository = new MailRepository(null, directory.resolve("mail.db").toFile(), 5000);
    UUID sender = UUID.randomUUID();
    try {
      repository.initialize().join();
      for (MailType type : MailType.values()) {
        repository.insertMail(sender, "Sender", UUID.randomUUID(), "Recipient", type,
            new byte[] {1}, 3, false).join();
        var entry = repository.listSent(sender, type, 0).join().getFirst();
        assertEquals(0, entry.getMail().payload().length);
        var lore = renderLore(entry);
        assertFalse(lore.stream().anyMatch(line -> line.contains("Expired")));
        assertEquals(type != MailType.PACKAGE, lore.contains("§eLeft-click to read sent copy"));
        assertTrue(lore.contains(type == MailType.PACKAGE
            ? "§7Contents: §f3 packed items" : "§7Contents: §fSent book copy retained"));
      }
    } finally { repository.close(); }
  }

  /** Purged book history shows expiration and no read action despite the same empty page payload. */
  @Test void purgedHistoryHasNoReadHint() {
    var repository = new MailRepository(null, directory.resolve("mail.db").toFile(), 5000);
    UUID sender = UUID.randomUUID();
    try {
      repository.initialize().join();
      repository.insertMail(sender, "Sender", UUID.randomUUID(), "Recipient", MailType.LETTER,
          new byte[] {1}, 0, false).join();
      repository.expire(System.currentTimeMillis(), 0, 0, Long.MAX_VALUE).join();
      var lore = renderLore(repository.listSent(sender, MailType.LETTER, 0).join().getFirst());
      assertTrue(lore.contains("§7Contents: §8Expired"));
      assertFalse(lore.contains("§eLeft-click to read sent copy"));
    } finally { repository.close(); }
  }

  /** Inspect the metadata actually written to the card while keeping the Paper runtime mocked. */
  private List<String> renderLore(SentMailRecord entry) {
    var meta = mock(ItemMeta.class);
    try (var icons = mockConstruction(ItemStack.class,
        (item, context) -> when(item.getItemMeta()).thenReturn(meta))) {
      SentMailDisplay.INSTANCE.icon(entry);
      @SuppressWarnings("unchecked") ArgumentCaptor<List<String>> lore = ArgumentCaptor.forClass(List.class);
      verify(meta).setLore(lore.capture());
      return lore.getValue();
    }
  }
}
