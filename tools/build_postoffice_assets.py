"""Regenerate original approved Post Office assets; requires Pillow and an official client JAR.
No runtime plugin dependency. MINECRAFT_CLIENT_JAR can override the local asset source.
"""

from pathlib import Path
from zipfile import ZipFile
from PIL import Image, ImageDraw
import json, io, math, os

ROOT = Path(__file__).resolve().parents[1]
JAR = Path(
    os.environ.get("MINECRAFT_CLIENT_JAR", str(Path.home() / "AppData/Roaming/.minecraft/versions/26.3/26.3.jar"))
)
z = ZipFile(JAR)


def asset(path):
    return Image.open(io.BytesIO(z.read("assets/minecraft/" + path))).convert("RGBA")


glyphs = {}
for p in json.loads(z.read("assets/minecraft/font/include/default.json"))["providers"]:
    if p["type"] != "bitmap":
        continue
    sheet = asset("textures/" + p["file"].split(":")[1])
    rows = p["chars"]
    cw = sheet.width // len(rows[0])
    ch = sheet.height // len(rows)
    scale = p.get("height", 8) / ch
    for row, chars in enumerate(rows):
        for col, char in enumerate(chars):
            if char == "\0":
                continue
            tile = sheet.crop((col * cw, row * ch, (col + 1) * cw, (row + 1) * ch))
            bbox = tile.getchannel("A").getbbox()
            advance = math.floor((bbox[2] if bbox else 0) * scale + 0.5) + 1
            tile = tile.resize((round(cw * scale), round(ch * scale)), Image.Resampling.NEAREST)
            glyphs[char] = (tile, advance, 7 - p.get("ascent", 7))
glyphs[" "] = (Image.new("RGBA", (4, 8)), 4, 0)
colors = {"f": "#FFFFFF", "7": "#AAAAAA", "e": "#FFFF55", "a": "#55FF55", "c": "#FF5555", "5": "#AA00AA"}


def width(t):
    return sum(glyphs.get(c, glyphs["?"])[1] for c in t)


def text(im, t, x, y, color="#FFFFFF", shadow=False, italic=False):
    if shadow:
        rgb = tuple(int(color[i : i + 2], 16) // 4 for i in (1, 3, 5))
        text(im, t, x + 1, y + 1, "#" + "".join(f"{c:02x}" for c in rgb), False, italic)
    for c in t:
        tile, adv, dy = glyphs.get(c, glyphs["?"])
        tinted = Image.new("RGBA", tile.size, color)
        tinted.putalpha(tile.getchannel("A"))
        if italic:
            skew = Image.new("RGBA", (tile.width + 3, tile.height))
            for ry in range(tile.height):
                skew.alpha_composite(tinted.crop((0, ry, tile.width, ry + 1)), (int((tile.height - ry - 1) * 0.25), ry))
            tinted = skew
        im.alpha_composite(tinted, (int(x), int(y + dy)))
        x += adv


def wrap(t, limit):
    lines = []
    for paragraph in t.split("\n"):
        line = ""
        for word in paragraph.split(" "):
            candidate = (line + " " + word).strip()
            if width(candidate) > limit and line:
                lines.append(line)
                line = word
            else:
                line = candidate
        lines.append(line)
    return lines


def label(im, value, box, color):
    """Draw unscaled vanilla glyphs only when their ink fits the reserved panel."""
    left, top, right, bottom = box
    ink_width = width(value) - 1
    if ink_width > right - left + 1 or bottom - top + 1 < 8:
        raise ValueError(f"Label {value!r} exceeds reserved panel {box}")
    text(im, value, left + (right - left + 1 - ink_width) // 2, top, color)


THEMES = [
    dict(
        id="postoffice",
        name="Enthusia Post Office",
        tag="Recommended",
        bg="#690F10",
        slot="#901923",
        slotHi="#BA3841",
        slotLo="#410B0F",
        edge="#340A0C",
        gold="#FFCF1F",
        hi="#FFE9A8",
        lo="#B67D22",
        cream="#FFF1CF",
        paper="#FFF8E6",
        ink="#3A0D0A",
        accent="#B3201A",
        muted="#E6BF9B",
        copy="The strongest match to Enthusia: crimson recesses, gilded corners and cream postal panels. Website warmth inside the familiar menu shell.",
    )
]


def bevel(im, box, fill, t, light=None, dark=None):
    d = ImageDraw.Draw(im)
    x, y, x2, y2 = map(int, box)
    d.rectangle((x, y, x2, y2), fill=t["edge"])
    d.rectangle((x + 1, y + 1, x2 - 1, y2 - 1), fill=fill)
    d.line((x + 1, y + 1, x2 - 1, y + 1), fill=light or t["slotHi"])
    d.line((x + 1, y + 1, x + 1, y2 - 1), fill=light or t["slotHi"])
    d.line((x + 1, y2 - 1, x2 - 1, y2 - 1), fill=dark or t["slotLo"])
    d.line((x2 - 1, y + 1, x2 - 1, y2 - 1), fill=dark or t["slotLo"])


def fret(im, x, y, t, flipx=False, flipy=False):
    art = Image.new("RGBA", (8, 8))
    d = ImageDraw.Draw(art)
    g = t["gold"]
    h = t["hi"]
    d.line([(0, 7), (0, 0), (7, 0), (7, 4), (3, 4), (3, 1)], fill=g, width=1)
    d.line([(1, 7), (4, 7), (4, 3), (1, 3), (1, 1), (6, 1)], fill=h, width=1)
    if flipx:
        art = art.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    if flipy:
        art = art.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
    im.alpha_composite(art, (x, y))


def card(im, box, caption, t, cream=False):
    bevel(
        im,
        box,
        t["cream"] if cream else t["slot"],
        t,
        t["paper"] if cream else t["slotHi"],
        t["lo"] if cream else t["slotLo"],
    )
    if caption:
        text_color = t["ink"] if cream else t["hi"]
        label(im, caption, (box[0] + 3, box[1] + 5, box[2] - 3, box[1] + 12), text_color)


def base(t, rows, title):
    h = 114 + rows * 18
    im = Image.new("RGBA", (176, h), t["bg"])
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 175, h - 1), outline=t["edge"], width=2)
    d.rectangle((2, 2, 173, h - 3), outline=t["gold"])
    d.rectangle((4, 4, 171, 14), fill=t["edge"])
    text(im, title, (176 - width(title)) // 2, 5, t["hi"])
    for row in range(rows):
        for col in range(9):
            bevel(im, (7 + col * 18, 17 + row * 18, 24 + col * 18, 34 + row * 18), t["slot"], t)
    invy = 31 + 18 * rows
    d.rectangle((4, invy - 13, 171, invy + 1), fill=t["cream"])
    d.line((4, invy - 13, 171, invy - 13), fill=t["hi"])
    for row in range(3):
        for col in range(9):
            bevel(
                im,
                (7 + col * 18, invy - 1 + row * 18, 24 + col * 18, invy + 16 + row * 18),
                t["slotLo"],
                t,
                t["slotHi"],
                t["edge"],
            )
    for col in range(9):
        bevel(im, (7 + col * 18, invy + 57, 24 + col * 18, invy + 74), t["slotLo"], t, t["slotHi"], t["edge"])
    for x, y, fx, fy in [
        (1, 1, False, False),
        (167, 1, True, False),
        (1, h - 9, False, True),
        (167, h - 9, True, True),
    ]:
        fret(im, x, y, t, fx, fy)
    return im


OUT = ROOT / "assets/nexo-postoffice"
tex = OUT / "pack/assets/enthusiaexpress/textures/postoffice"
tex.mkdir(parents=True, exist_ok=True)
t = THEMES[0]
backgrounds = {}
home = base(t, 6, "Enthusia Express")
card(home, (7, 17, 168, 34), "Your post office", t, True)
for i, caption in enumerate(("Packages", "Letters", "News")):
    x = 7 + i * 54
    card(home, (x, 35, x + 53, 70), "", t, True)
    label(home, caption, (x + 3, 59, x + 50, 66), t["ink"])
card(home, (7, 71, 168, 88), "Handle with care.", t, True)
for i, caption in enumerate(("Sent", "Help", "Museum")):
    x = 7 + i * 54
    card(home, (x, 89, x + 53, 106), "", t)
    label(home, caption, (x + 18, 94, x + 52, 101), t["hi"])
card(home, (7, 107, 132, 124), "", t, True)
label(home, "My submissions", (49, 112, 129, 119), t["ink"])
backgrounds["home"] = home
for name, title in [("mailbox", "Express - Inbox"), ("sent", "Express - Sent")]:
    im = base(t, 6, title)
    for i in range(3):
        card(im, (7 + i * 54, 17, 60 + i * 54, 34), "", t)
    card(im, (7, 35, 168, 52), "", t, True)
    for i, caption in enumerate(("Packages", "Letters", "News")):
        label(im, caption, (10 + i * 54, 36, 57 + i * 54, 43), t["ink"])
    label(im, "Hover for counts and page", (10, 44, 165, 51), t["accent"])
    backgrounds[name] = im
catalog = base(t, 6, "Mapart Museum")
card(catalog, (7, 17, 168, 34), "Artwork and review", t, True)
card(catalog, (7, 35, 168, 52), "Hover for credit and status", t, True)
backgrounds["catalog"] = catalog
backgrounds["museum"] = base(t, 6, "Express - Details")
shipping = base(t, 3, "Express - Postage")
card(shipping, (7, 17, 168, 34), "", t, True)
label(shipping, "To", (18, 22, 39, 29), t["ink"])
label(shipping, "Details", (101, 22, 165, 29), t["ink"])
for slot in (11, 13, 15):
    x = 7 + (slot % 9) * 18
    bevel(shipping, (x, 35, x + 17, 52), t["cream"], t, t["paper"], t["lo"])
for caption, x in [("Cancel", 52), ("Item", 88), ("Confirm", 124)]:
    label(shipping, caption, (x - 18, 56, x + 18, 63), t["hi"])
backgrounds["shipping"] = shipping
for name, im in backgrounds.items():
    padded = Image.new("RGBA", (256, 256))
    padded.alpha_composite(im, (0, 0))
    padded.save(tex / ("bg-" + name + ".png"))
# Only the transparent support item is generated; visible icons belong to the private pack.
Image.new("RGBA", (16, 16)).save(tex / "blank.png")

glyph_configs = []
banner = Image.new("RGBA", (176, 42), t["bg"])
d = ImageDraw.Draw(banner)
d.rectangle((1, 1, 174, 40), outline=t["gold"])
card(banner, (5, 5, 170, 36), "", t, True)
text(banner, "Enthusia Express", (176 - width("Enthusia Express")) // 2, 12, t["ink"])
label(banner, "Handle with care.", (8, 24, 167, 31), t["accent"])
banner.save(tex / "bg-banner.png")
glyph_configs.append(
    "ee_postoffice_banner:\n  texture: enthusiaexpress:postoffice/bg-banner\n  font: enthusiaexpress:menus\n  height: 42\n  ascent: 7\n  is_emoji: false\n"
)
for name in backgrounds:
    glyph_configs.append(
        f"ee_postoffice_{name}:\n  texture: enthusiaexpress:postoffice/bg-{name}\n"
        "  font: enthusiaexpress:menus\n  height: 256\n  ascent: 13\n  is_emoji: false\n"
    )
(OUT / "glyphs/enthusiaexpress").mkdir(parents=True, exist_ok=True)
(OUT / "glyphs/enthusiaexpress/postoffice.yml").write_text("\n".join(glyph_configs), encoding="utf-8", newline="\n")
(OUT / "items").mkdir(exist_ok=True)
(OUT / "items/enthusiaexpress_postoffice.yml").write_text(
    "ee_postoffice_blank:\n  material: PAPER\n  excludeFromInventory: true\n  Pack:\n"
    "    parent_model: item/generated\n    texture: enthusiaexpress:postoffice/blank\n",
    encoding="utf-8", newline="\n")
sources = json.loads((OUT / "icon-sources.json").read_text(encoding="utf-8"))["icons"]

bindings = {
    "chrome.blank": "blank",
    "mailbox.packages": "package",
    "mailbox.packages-selected": "package-selected",
    "mailbox.letters": "letter",
    "mailbox.letters-selected": "letter-selected",
    "mailbox.announcements": "announcement",
    "mailbox.announcements-selected": "announcement-selected",
    "mailbox.previous": "back",
    "mailbox.next": "next",
    "mailbox.inbox": "letter",
    "mailbox.sent": "sent",
    "mailbox.page": "map",
    "mailbox.summary": "blank",
    "mailbox.mark-all-read": "check",
    "mailbox.refresh": "refresh",
    "mailbox.close": "close",
    "mailbox.entry.package": "package",
    "mailbox.entry.letter": "letter",
    "mailbox.entry.letter-read": "read",
    "mailbox.entry.announcement": "announcement",
    "mailbox.entry.announcement-read": "read",
    "shipping.cancel": "close",
    "shipping.recipient": "letter",
    "shipping.quote": "sent",
    "shipping.confirm": "check",
    "shipping.placeholder": "package",
}
for key, name in [
    ("packages", "package"),
    ("letters", "letter"),
    ("announcements", "announcement"),
    ("sent", "sent"),
    ("settings", "settings"),
    ("map", "map"),
    ("museum", "museum"),
    ("seal", "seal"),
    ("check", "check"),
    ("back", "back"),
    ("next", "next"),
    ("control", "map"),
]:
    bindings["museum." + key] = name
# Flat paths are easy to merge with Bukkit's ConfigurationSection#set without replacing unrelated settings.
profile = {"gui.nexo.enabled": True, "gui.nexo.post-office": True}
profile.update(
    {"gui.nexo.titles." + name: f"<shift:-8><glyph:ee_postoffice_{name}:shadow:#00000000>" for name in backgrounds}
)
profile.update({"gui.nexo.icons." + key:
    "ee_postoffice_blank" if value == "blank" else sources[value.removesuffix("-selected")]["id"]
    for key, value in bindings.items()})
(OUT / "express-profile.json").write_text(json.dumps(profile, indent=2) + "\n", encoding="utf-8", newline="\n")
manifest = {
    str(p.relative_to(OUT)).replace("\\", "/"): __import__("hashlib").sha256(p.read_bytes()).hexdigest()
    for p in OUT.rglob("*")
    if p.is_file() and p.name != "manifest.json"
}
(OUT / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8", newline="\n")
print(f"Generated {len(backgrounds)} inventory backgrounds, a banner and transparent chrome; existing pack icon IDs retained.")
