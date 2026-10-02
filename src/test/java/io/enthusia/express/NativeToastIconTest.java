package io.enthusia.express;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Reflection contract fixtures, not substitutes for actual server packet tests. */
class NativeToastIconTest {
    public static class Legacy {
        public static String asNMSCopy(ItemStack item) { return "legacy"; }
    }
    public static class Modern extends Legacy {
        public static Integer asTemplate(ItemStack item) { return 26; }
    }

    private Object binding(Class<?> craft) throws Exception {
        return Class.forName("io.enthusia.express.infrastructure.hook.NativeToastIcon")
            .getConstructor(Class.class).newInstance(craft);
    }

    @Test void modernServersUseTemplateIconType() throws Exception {
        var icon = binding(Modern.class);
        assertEquals(Integer.class, icon.getClass().getMethod("getType").invoke(icon));
        assertEquals(26, icon.getClass().getMethod("convert", ItemStack.class)
            .invoke(icon, org.mockito.Mockito.mock(ItemStack.class)));
    }

    @Test void legacyServersRetainItemStackConversion() throws Exception {
        var icon = binding(Legacy.class);
        assertEquals(String.class, icon.getClass().getMethod("getType").invoke(icon));
        assertEquals("legacy", icon.getClass().getMethod("convert", ItemStack.class)
            .invoke(icon, org.mockito.Mockito.mock(ItemStack.class)));
    }
}
