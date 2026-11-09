package io.enthusia.express;

import io.enthusia.express.infrastructure.gui.ReflectiveDialogsKt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Mirrors ViaAPI's erased player overload beside the typed UUID overload. */
public class DialogReflectionTest {
    public interface VersionApi<T> {
        int getPlayerVersion(T player);
        int getPlayerVersion(UUID id);
    }

    public static final class PlayerVersionApi implements VersionApi<String> {
        @Override public int getPlayerVersion(String player) {
            throw new AssertionError("The player bridge must not receive a UUID");
        }
        @Override public int getPlayerVersion(UUID id) { return 776; }
    }

    @Test void typedUuidOverloadWinsOverErasedPlayerBridge() {
        assertDoesNotThrow(() -> assertEquals(776, ReflectiveDialogsKt.dialogCall(
            VersionApi.class.getName(), new PlayerVersionApi(), "getPlayerVersion", UUID.randomUUID())));
    }
}
