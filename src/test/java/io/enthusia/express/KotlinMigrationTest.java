package io.enthusia.express;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class KotlinMigrationTest {
  /** Verifies that production source is kotlin. */
  @Test
  void productionSourceIsKotlin() throws Exception {
    try (var files = Files.walk(Path.of("src/main"))) {
      assertEquals(0, files.filter(p -> p.toString().endsWith(".java")).count(),
          "REQ-001: production Java remains");
    }
    try (var files = Files.walk(Path.of("src/main/kotlin"))) {
      assertTrue(files.filter(p -> p.toString().endsWith(".kt")).count() >= 17,
          "REQ-001: Kotlin implementation is missing");
    }
  }
  /** Verifies that installable jar contains kotlin runtime and entry point. */

  @Test
  void installableJarContainsKotlinRuntimeAndEntryPoint() throws Exception {
    try (var jar = new JarFile(System.getProperty("pluginJar"))) {
      assertNotNull(jar.getJarEntry("kotlin/jvm/internal/Intrinsics.class"), "REQ-001: runtime missing");
      assertNotNull(jar.getJarEntry("io/enthusia/express/infrastructure/EnthusiaExpressPlugin.class"));
      assertNotNull(jar.getJarEntry("io/enthusia/express/domain/MailRecord.class"));
      assertNotNull(jar.getJarEntry("io/enthusia/express/application/MailStore.class"));
    }
  }
}
