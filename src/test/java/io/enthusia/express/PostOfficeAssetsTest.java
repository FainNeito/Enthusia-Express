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
    @Test void inventoryCaptionHasClearanceOnEveryChestBackground() throws Exception {
        for (String name : java.util.List.of("home", "mailbox", "sent", "catalog", "museum", "shipping")) {
            var image=ImageIO.read(Path.of("assets/nexo-postoffice/pack/assets/enthusiaexpress/textures/postoffice/bg-"+name+".png").toFile());
            int top=name.equals("shipping")?74:128;
            for(int y=top;y<=top+9;y++)for(int x=7;x<68;x++)
                assertEquals(0xfff1cf,image.getRGB(x,y)&0xffffff,name+" caption at "+x+","+y);
        }
    }

    @Test void sharedIconsAreReferencedWithoutPublishingTheirPixels() throws Exception {
        Path root=Path.of("assets/nexo-postoffice");
        String profile=Files.readString(root.resolve("express-profile.json"));
        assertTrue(profile.contains("lg_vault"),"Reuse the existing pack's chest icon");
        assertFalse(profile.contains("ee_postoffice_package"),"Do not generate a replacement icon family");
        try(var files=Files.list(root.resolve("pack/assets/enthusiaexpress/textures/postoffice"))) {
            assertTrue(files.allMatch(p->p.getFileName().toString().startsWith("bg-")||p.getFileName().toString().equals("blank.png")),"Private icons stay outside the public source tree");
        }
    }

    @Test void everyProfileBindingResolvesToSharedIconMetadataOrSupportTextureAndValidGlyph() throws Exception {
        Path root=Path.of("assets/nexo-postoffice");
        var items=YamlConfiguration.loadConfiguration(root.resolve("items/enthusiaexpress_postoffice.yml").toFile());
        var glyphs=YamlConfiguration.loadConfiguration(root.resolve("glyphs/enthusiaexpress/postoffice.yml").toFile());
        String profile=Files.readString(root.resolve("express-profile.json"));
        String sources=Files.readString(root.resolve("icon-sources.json"));
        var matches=Pattern.compile("\"gui\\.nexo\\.icons\\.[^\"]+\"\\s*:\\s*\"([a-z_]+)\"").matcher(profile);
        int bindings=0;
        while(matches.find()) {
            String id=matches.group(1);
            assertTrue(items.contains(id)||sources.contains("\"id\": \""+id+"\""),id);
            bindings++;
        }
        assertEquals(java.util.Set.of("ee_postoffice_blank"),items.getKeys(false));
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
