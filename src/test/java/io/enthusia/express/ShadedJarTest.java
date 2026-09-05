package io.enthusia.express;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URLClassLoader;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShadedJarTest {
  @TempDir Path directory;

  @Test
  void distributableContainsMetadataAndLoadsItsOwnNativeSqliteDriver() throws Exception {
    Path jar = Path.of(System.getProperty("pluginJar"));
    try (JarFile archive = new JarFile(jar.toFile())) {
      String metadata =
          new String(
              archive.getInputStream(archive.getJarEntry("plugin.yml")).readAllBytes(),
              java.nio.charset.StandardCharsets.UTF_8);
      assertTrue(metadata.contains("version: '1.1.0'"));
      assertTrue(metadata.contains("api-version: '1.21'"));
      assertNotNull(archive.getJarEntry("org/sqlite/native/Windows/x86_64/sqlitejdbc.dll"));
      assertNotNull(archive.getJarEntry("org/sqlite/native/Linux/x86_64/libsqlitejdbc.so"));
      assertNotNull(archive.getJarEntry("META-INF/services/java.sql.Driver"));
      assertNull(archive.getJarEntry("org/bukkit/Bukkit.class"));
      assertNull(archive.getJarEntry("com/github/sirblobman/combatlogx/api/ICombatLogX.class"));
    }
    try (URLClassLoader loader =
        new URLClassLoader(
            new java.net.URL[] {jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
      Driver driver =
          (Driver) Class.forName("org.sqlite.JDBC", true, loader).getConstructor().newInstance();
      try (Connection connection =
              driver.connect("jdbc:sqlite:" + directory.resolve("shaded.db"), new Properties());
          Statement st = connection.createStatement()) {
        st.execute("CREATE TABLE smoke(value TEXT)");
        st.execute("INSERT INTO smoke VALUES ('ok')");
        try (ResultSet rs = st.executeQuery("SELECT value FROM smoke")) {
          assertEquals("ok", rs.getString(1));
        }
      }
    }
  }
}
