#!/usr/bin/env python3
"""Generates the RubySteal resource pack (16x16 pixel art) into ./pack."""
import json, os, zipfile, sys
from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else "pack"
P = {  # palette
 '.': None,
 'K': (14, 3, 8), 'O': (42, 5, 12), 'D': (92, 10, 24), 'M': (140, 14, 34),
 'R': (186, 24, 48), 'L': (222, 52, 74), 'H': (255, 120, 138), 'W': (255, 226, 232),
 'g': (122, 78, 22), 'G': (204, 154, 52), 'Y': (255, 218, 120), 'Z': (255, 246, 200),
 'B': (60, 8, 20), 'E': (255, 90, 40),
}

def canvas(): return [['.'] * 16 for _ in range(16)]
def put(c, x, y, ch):
    if 0 <= x < 16 and 0 <= y < 16: c[y][x] = ch
def stamp(c, rows, ox=0, oy=0):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch != ' ': put(c, ox + i, oy + j, ch)

# ---- gem bodies (hand drawn, 16 wide) ----
GEM_S = [  # small cut gem, level 1
 "     OOOO     ",
 "    OHLLRO    ",
 "   OHLRRMDO   ",
 "   OLRRMMDO   ",
 "   OLRMMDDO   ",
 "    ORMDDO    ",
 "     ODDO     ",
 "      OO      "]
GEM_M = [  # brilliant cut, levels 2-4
 "    OOOOOO    ",
 "   OHWLLRMO   ",
 "  OHLLRRRMDO  ",
 " OHLRRMMMMDDO ",
 " OLRRMMDDMDDO ",
 "  OLRMMDDMDO  ",
 "   OLMDDMDO   ",
 "    OMDDDO    ",
 "     ODDO     ",
 "      OO      "]
GEM_L = [  # large faceted, levels 5-8
 "    OOOOOOOO    ",
 "   OHWLLLRRMO   ",
 "  OHWLLRRRMMDO  ",
 " OHLLRRLRMMMDDO ",
 " OLLRRMLRMMDDDO ",
 " OLRRMMLMMDDDDO ",
 "  OLRMMMMMMDDO  ",
 "   OLRMMMMMDO   ",
 "    ORMMMMDO    ",
 "     OMMMDO     ",
 "      ODDO      ",
 "       OO       "]
GEM_XL = [  # supreme-grade, levels 9-10
 "     OOOOOOOO     ",
 "    OHWWLLLRRMO   ",
 "   OHWLLLRRRMMDO  ",
 "  OHLLRRLLRRMMDDO ",
 "  OLLRRLRRMRMMDDO ",
 "  OLRRLMRRMMMDDDO ",
 "   OLRMMRMMMMDDO  ",
 "    OLRMMMMMMDO   ",
 "     ORMMMMMDO    ",
 "      OMMMMDO     ",
 "       ODDO       ",
 "        OO        "]

def center(rows, c, oy):
    w = max(len(r) for r in rows)
    ox = (16 - w) // 2
    stamp(c, rows, ox, oy)

def sparkle(c, x, y, big=False):
    put(c, x, y, 'W')
    if big:
        for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)): put(c, x+dx, y+dy, 'H')

def gold_base(c, y):  # little gold setting under gem
    stamp(c, ["OOOOOO","OGYGgO"," OggO "], 5, y)

def gold_crown(c, oy, wide=False):
    if wide:
        stamp(c, ["O  O  O  O","OGOGOYOGOGO","OGYGGGGGYGO","OggggggggggO"][:4], 2, oy)
    else:
        stamp(c, ["O O O","OGOYOGO"[0:5],"OGGGO"], 5, oy)

def level(n):
    c = canvas()
    if n == 1:
        center(GEM_S, c, 4)
    elif n == 2:
        center(GEM_M, c, 3); sparkle(c, 3, 3)
    elif n == 3:
        center(GEM_M, c, 3); gold_base(c, 13); sparkle(c, 3, 3); sparkle(c, 12, 5)
    elif n == 4:
        center(GEM_M, c, 3)
        for y in range(4, 11): put(c, 0 if y < 8 else 1, y, '.')
        # gold prongs left/right
        for (x, y) in ((1,6),(1,7),(14,6),(14,7)): put(c, x, y, 'G')
        for (x, y) in ((1,5),(14,5)): put(c, x, y, 'Y')
        for (x, y) in ((1,8),(14,8)): put(c, x, y, 'g')
        gold_base(c, 13); sparkle(c, 3, 3, True)
    elif n == 5:
        center(GEM_L, c, 2); gold_base(c, 13)
        for x in (3, 12): put(c, x, 5, 'Y'); put(c, x, 6, 'G'); put(c, x, 7, 'g')
        sparkle(c, 4, 3, True)
    elif n == 6:
        center(GEM_L, c, 2)
        # dark runic notches
        for (x, y) in ((8,5),(7,6),(8,7),(7,8)): put(c, x, y, 'B')
        for x in (2, 13):
            for y in range(4, 10): put(c, x, y, 'G' if y % 2 else 'g')
        gold_base(c, 13); sparkle(c, 4, 3, True)
    elif n == 7:
        center(GEM_L, c, 3)
        stamp(c, ["Y  Y  Y  Y","GO GO GO GO","GGGGGGGGGG"], 3, 0)  # royal crown
        for x in (3, 12): put(c, x, 6, 'G')
        sparkle(c, 4, 5, True); sparkle(c, 12, 9)
    elif n == 8:
        center(GEM_L, c, 3)
        # divine halo: gold ring with light rays
        for x in range(4, 12): put(c, x, 0, 'Y' if x in (7, 8) else 'G')
        for (x, y) in ((3,1),(12,1)): put(c, x, y, 'G')
        put(c, 2, 2, 'g'); put(c, 13, 2, 'g')
        for (x, y) in ((1,6),(14,6),(2,10),(13,10)): sparkle(c, x, y)
        put(c, 7, 6, 'W'); put(c, 8, 6, 'W'); put(c, 7, 7, 'H'); put(c, 8, 7, 'H')
        gold_base(c, 13)
    elif n == 9:
        center(GEM_XL, c, 2)
        # immortal: ember flames licking the gem + dark wings
        for (x, y) in ((1,8),(0,9),(1,10),(14,8),(15,9),(14,10)): put(c, x, y, 'B')
        for (x, y) in ((2,7),(13,7),(1,9),(14,9)): put(c, x, y, 'E')
        for (x, y) in ((4,0),(11,0),(6,1),(9,1)): put(c, x, y, 'E' if x in (4,11) else 'Y')
        for (x, y) in ((4,1),(11,1)): put(c, x, y, 'M')
        put(c, 7, 6, 'W'); put(c, 8, 6, 'W'); put(c, 7, 7, 'H'); put(c, 8, 7, 'H')
        gold_base(c, 13)
    elif n == 10:
        center(GEM_XL, c, 3)
        # supreme: golden wings, crown, glowing core
        stamp(c, ["Y   Y  Y   Y","GO  GO OG  OG","GGOGGGGGGOGG"], 2, 0)
        stamp(c, ["GY","gG","gG"], 0, 5); stamp(c, ["YG","Gg","Gg"], 14, 5)
        stamp(c, ["gG","Gg"], 0, 8); stamp(c, ["Gg","gG"], 14, 8)
        for (x, y) in ((7,7),(8,7),(7,8),(8,8)): put(c, x, y, 'W')
        for (x, y) in ((6,7),(9,7),(7,6),(8,6),(6,8),(9,8),(7,9),(8,9)): put(c, x, y, 'H')
        sparkle(c, 4, 6, True); sparkle(c, 12, 11)
        gold_base(c, 14)
    return c

def heart():
    c = canvas()
    stamp(c, [
     " OOO    OOO ",
     "OHWLO  OLRRO",
     "OHLRROORRRMO",
     "OLRRRRRRRMMO",
     "OLRRRWHRRMDO",
     " ORRRRRRMMDO",
     "  ORRRRMMDO ",
     "   ORRMMDO  ",
     "    ORMDO   ",
     "     ODO    ",
     "      O     "], 2, 2)
    stamp(c, ["G  G","GYYG","g  g"], 6, 12 - 11 + 0) if False else None
    for (x, y) in ((7,3),(8,3)): put(c, x, y, 'G')
    put(c, 4, 4, 'W'); put(c, 11, 11, 'Y'); put(c, 3, 12, 'g')
    return c

def fragment():
    c = canvas()
    stamp(c, [
     "      OO      ",
     "     OHWO     ",
     "    OHLLRO    ",
     "    OLLRMDO   ",
     "   OLRRMMDO   ",
     "   OLRMMDDO   ",
     "  OHLRMMDO    ",
     "  OLRMDDO     ",
     "   ORMDO   OO ",
     "    OMDO  OHLO",
     "     OO    OMO",
     "            OO"], 1, 2)
    put(c, 13, 3, 'W'); put(c, 3, 11, 'H')
    return c

def enchanted():
    c = level(3)
    # enchanted glow: pink-white rim sparkles and rune ring
    for (x, y) in ((1,3),(14,3),(0,8),(15,8),(2,13),(13,13),(7,0),(8,0)):
        put(c, x, y, 'H')
    for (x, y) in ((1,2),(14,2),(7,1),(8,1),(0,7),(15,7)):
        put(c, x, y, 'W')
    for (x, y) in ((2,3),(13,3),(1,8),(14,8)):
        put(c, x, y, 'L')
    return c

def save(c, path):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            col = P[c[y][x]]
            if col: img.putpixel((x, y), col + (255,))
    img.save(path)
    return img

items = {f"ruby_level_{n}": level(n) for n in range(1, 11)}
items["ruby_heart"] = heart(); items["ruby_fragment"] = fragment(); items["enchanted_ruby"] = enchanted()

tex = f"{OUT}/assets/rubysteal/textures/item"; mod = f"{OUT}/assets/rubysteal/models/item"; defs = f"{OUT}/assets/rubysteal/items"
for d in (tex, mod, defs): os.makedirs(d, exist_ok=True)
for name, c in items.items():
    save(c, f"{tex}/{name}.png")
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"rubysteal:item/{name}"}}, open(f"{mod}/{name}.json", "w"), indent=2)
    json.dump({"model": {"type": "minecraft:model", "model": f"rubysteal:item/{name}"}}, open(f"{defs}/{name}.json", "w"), indent=2)

json.dump({"pack": {"pack_format": 46, "description": "§4§lRubySteal §r§cResource Pack §7- 1.21.4"}}, open(f"{OUT}/pack.mcmeta", "w"), indent=2, ensure_ascii=False)
# pack icon: supreme ruby upscaled with nearest neighbour
big = Image.open(f"{tex}/ruby_level_10.png").resize((128, 128), Image.NEAREST)
bg = Image.new("RGBA", (128, 128), (20, 4, 10, 255)); bg.alpha_composite(big); bg.save(f"{OUT}/pack.png")
# preview sheet (not shipped in pack)
sheet = Image.new("RGBA", (13 * 80, 96), (24, 8, 14, 255))
for i, name in enumerate(items):
    im = Image.open(f"{tex}/{name}.png").resize((64, 64), Image.NEAREST); sheet.alpha_composite(im, (i * 80 + 8, 16))
sheet.save("preview.png")
print("ok", len(items))
