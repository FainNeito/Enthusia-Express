package io.enthusia.express;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Missing registry bindings otherwise appear only after a real pack build. */
class PostOfficeAssetsTest {
    @Test void everyProfileBindingResolvesToAnOriginalTextureAndValidGlyph() throws Exception {
        Path root=Path.of("assets/nexo-postoffice");
        var items=YamlConfiguration.loadConfiguration(root.resolve("items/enthusiaexpress_postoffice.yml").toFile());
        var glyphs=YamlConfiguration.loadConfiguration(root.resolve("glyphs/enthusiaexpress/postoffice.yml").toFile());
        String profile=Files.readString(root.resolve("express-profile.json"));
        var matches=Pattern.compile("\"gui\\.nexo\\.icons\\.[^\"]+\"\\s*:\\s*\"([a-z_]+)\"").matcher(profile);
        int bindings=0;
        while(matches.find()) {assertTrue(items.contains(matches.group(1)),matches.group(1));bindings++;}
        assertTrue(bindings>25,"An incomplete profile cannot be shipped as an installable menu");
        for(String id:items.getKeys(false)) {
            String texture=items.getString(id+".Pack.texture");
            assertTrue(texture.startsWith("enthusiaexpress:postoffice/"));
            var image=ImageIO.read(root.resolve("pack/assets/enthusiaexpress/textures/"+texture.split(":")[1]+".png").toFile());
            assertEquals(16,image.getWidth());assertEquals(16,image.getHeight());
        }
        assertEquals(7,glyphs.getKeys(false).size());
        for(String id:glyphs.getKeys(false)) {
            String texture=glyphs.getString(id+".texture");
            var image=ImageIO.read(root.resolve("pack/assets/enthusiaexpress/textures/"+texture.split(":")[1]+".png").toFile());
            int height=glyphs.getInt(id+".height");
            assertEquals(height,image.getHeight());
            assertTrue(glyphs.getInt(id+".ascent")<=height);
            assertFalse(glyphs.getBoolean(id+".is_emoji"));
        }
        var titles=Pattern.compile("<glyph:([a-z_]+):").matcher(profile);
        while(titles.find())assertTrue(glyphs.contains(titles.group(1)),titles.group(1));
    }
}
