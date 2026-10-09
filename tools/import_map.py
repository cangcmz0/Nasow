#!/usr/bin/env python3
"""Eski (1.8) PGM/Overcast KOTH haritasini SkyCore blueprint'ine cevirir.

Kaynak: "The Hill" - Articray, TheZaner (Zan), xXFracXx; katkida bulunanlar ItsMiiOlly, ElectroidFilms.
Lisans: CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/)
Kaynak depo: https://github.com/OvercastCommunity/PublicMaps (koth/the_hill), https://mcresourcepile.github.io/

Kullanim:
    # 1) Haritayi indir (sadece bu klasor):
    git clone --filter=blob:none --no-checkout --depth 1 https://github.com/OvercastCommunity/PublicMaps
    cd PublicMaps && git sparse-checkout set --no-cone /koth/the_hill/ && git checkout && cd ..
    # 2) Eski blok kimligi -> yeni blok tablosu (PrismarineJS minecraft-data, MIT):
    curl -o legacy.json https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/common/legacy.json
    # 3) Cevir:
    python3 tools/import_map.py PublicMaps/koth/the_hill legacy.json

Yapilan degisiklikler (CC BY-SA geregi belirtilir): bloklar 1.8 kimliklerinden guncel surume cevrildi; citlerin,
cam panellerin ve merdivenlerin baglantilari yeniden hesaplandi; tabelalar ve sandik icerikleri kaldirildi;
yapimci tabelalari Turkceye cevrildi; fener isini icin 3 piston kaldirildi; harita Sky Survival'da gokyuzunde
KOTH etkinlik arenasi olarak kullanilir.
"""
import gzip
import json
import math
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mcworld  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "custom-plugins" / "SkyCore" / "src" / "main" / "resources" / "koth"
BRANDING = ROOT / "branding"

# Haritanin tepe merkezi (map.xml: capture cylinder base 1101.5,31,-538.5) -> blueprint'te (0, 0, 0)
ORIGIN = (1101, 31, -539)

# 1.13 adlari -> guncel adlar
RENAMES = {
    "minecraft:grass": "minecraft:short_grass",
    "minecraft:stone_slab": "minecraft:smooth_stone_slab",
    "minecraft:grass_path": "minecraft:dirt_path",
}
# Elle yapilan duzenlemeler: tepenin altindaki fenerin (beacon) isini gokyuzune ciksin diye
# yolundaki pistonlar kaldirildi, tepenin ortasi beyaz camla kapatildi.
EDITS = {(1101, 8, -539): None, (1101, 9, -539): None, (1101, 31, -539): ("minecraft:white_stained_glass", {})}
# Kaldirilan bloklar (tabelalar SkyCore tarafindan Turkce yeniden yazilir)
REMOVE = {"minecraft:sign", "minecraft:wall_sign", "minecraft:piston_head", "minecraft:moving_piston"}

SKULL_TYPES = {0: "skeleton", 1: "wither_skeleton", 2: "zombie", 3: "player", 4: "creeper", 5: "dragon"}
WALL_FACING = {2: "north", 3: "south", 4: "west", 5: "east"}

DIRS = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
CCW = {v: k for k, v in CW.items()}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}

NOT_FULL = ("slab", "stairs", "fence", "wall", "pane", "iron_bars", "torch", "sign", "ladder", "trapdoor", "door",
            "carpet", "flower", "sapling", "short_grass", "tall_grass", "fern", "bush", "button", "lever",
            "pressure_plate", "rail", "redstone", "repeater", "comparator", "vine", "lily", "skull", "head", "chest",
            "cake", "bed", "brewing", "cauldron", "hopper", "anvil", "enchanting", "cactus", "cobweb", "banner",
            "pot", "water", "lava", "air", "piston", "end_rod", "daylight", "snow[", "glass_pane", "leaves",
            "pumpkin", "melon", "barrier", "beacon", "poppy", "dandelion", "orchid", "allium", "bluet", "tulip",
            "daisy", "mushroom[", "sugar_cane", "wheat", "carrots", "potatoes", "nether_wart", "dead_bush")


def parse(state):
    if "[" not in state:
        return state, {}
    name, rest = state[:-1].split("[", 1)
    props = dict(part.split("=", 1) for part in rest.split(","))
    return name, props


def fmt(name, props):
    if not props:
        return name
    return name + "[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]"


def is_full(name, props):
    if name.endswith("_slab"):
        return props.get("type") == "double"
    short = name.replace("minecraft:", "")
    key = short + "["
    return not any(part in key for part in NOT_FULL)


def convert(blocks, tiles, legacy):
    world = {}
    missing = set()
    for pos, (block_id, data) in blocks.items():
        state = legacy.get(f"{block_id}:{data}") or legacy.get(f"{block_id}:0")
        if state is None:
            missing.add(block_id)
            continue
        name, props = parse(state)
        name = RENAMES.get(name, name)
        if name in REMOVE or name == "minecraft:air":
            continue
        if name.endswith("_leaves"):
            props["persistent"] = "true"
            props.setdefault("distance", "1")
        if name.endswith("_wall") and name != "minecraft:wall_torch":
            props = {k: ("low" if v == "true" else "none") if k in DIRS else v for k, v in props.items()}
        world[pos] = (name, props)
    # Kafalar: tur ve yon tile entity'de
    heads = []
    for tile in tiles:
        pos = (tile["x"], tile["y"], tile["z"])
        if tile.get("id") != "Skull" or pos not in world:
            continue
        kind = SKULL_TYPES.get(tile.get("SkullType", 0), "skeleton")
        data = blocks[pos][1]
        if data == 1:
            world[pos] = (f"minecraft:{kind}_head" if kind not in ("skeleton", "wither_skeleton") else f"minecraft:{kind}_skull",
                          {"rotation": str(tile.get("Rot", 0) & 15)})
        else:
            world[pos] = (f"minecraft:{kind}_wall_head" if kind not in ("skeleton", "wither_skeleton") else f"minecraft:{kind}_wall_skull",
                          {"facing": WALL_FACING.get(data, "north")})
        owner = tile.get("Owner")
        if kind == "player" and owner:
            textures = owner.get("Properties", {}).get("textures", [{}])[0]
            heads.append({"pos": pos, "name": owner.get("Name", ""), "id": owner.get("Id", ""),
                          "value": textures.get("Value", ""), "signature": textures.get("Signature", "")})
    if missing:
        print("  bilinmeyen eski blok kimlikleri atlandi:", sorted(missing))
    for pos, block in EDITS.items():
        if block is None:
            world.pop(pos, None)
        else:
            world[pos] = block
    fix_connections(world)
    return world, heads


def neighbor(world, pos, direction, dy=0):
    dx, _, dz = DIRS[direction]
    return world.get((pos[0] + dx, pos[1] + dy, pos[2] + dz))


def fix_connections(world):
    for pos, (name, props) in list(world.items()):
        short = name.replace("minecraft:", "")
        if short.endswith("_fence") or short.endswith("glass_pane") or short == "iron_bars" or short.endswith("_wall"):
            new = dict(props)
            for direction in DIRS:
                other = neighbor(world, pos, direction)
                connect = False
                if other:
                    oname, oprops = other
                    oshort = oname.replace("minecraft:", "")
                    if short.endswith("_fence"):
                        wooden = "nether_brick" not in short
                        connect = (oshort.endswith("_fence") and (("nether_brick" not in oshort) == wooden)) \
                            or (oshort.endswith("_fence_gate") and DIRS[oprops.get("facing", "north")][0] == 0 and direction in ("east", "west")) \
                            or (oshort.endswith("_fence_gate") and DIRS[oprops.get("facing", "north")][0] != 0 and direction in ("north", "south")) \
                            or is_full(oname, oprops)
                    elif short.endswith("_wall"):
                        connect = oshort.endswith("_wall") or oshort.endswith("_fence_gate") or is_full(oname, oprops) \
                            or oshort.endswith("pane") or oshort == "iron_bars"
                    else:
                        connect = oshort.endswith("pane") or oshort == "iron_bars" or oshort.endswith("_wall") \
                            or "glass" in oshort or is_full(oname, oprops)
                if short.endswith("_wall"):
                    new[direction] = "low" if connect else "none"
                else:
                    new[direction] = "true" if connect else "false"
            if short.endswith("_wall"):
                ns = new["north"] != "none" and new["south"] != "none" and new["east"] == "none" and new["west"] == "none"
                ew = new["east"] != "none" and new["west"] != "none" and new["north"] == "none" and new["south"] == "none"
                new["up"] = "false" if (ns or ew) else "true"
            world[pos] = (name, new)
    for pos, (name, props) in list(world.items()):
        if name.endswith("_stairs"):
            world[pos] = (name, dict(props, shape=stair_shape(world, pos, props)))
        elif name.endswith("chest") and "type" in props:
            facing = props.get("facing", "north")
            kind = "single"
            for side, value in ((CW[facing], "left"), (CCW[facing], "right")):
                other = neighbor(world, pos, side)
                if other and other[0] == name and other[1].get("facing") == facing:
                    kind = value
            world[pos] = (name, dict(props, type=kind))
        elif name.endswith("_door") and props.get("half") == "lower":
            upper = world.get((pos[0], pos[1] + 1, pos[2]))
            if upper and upper[0] == name:
                world[pos] = (name, dict(props, hinge=upper[1].get("hinge", "left")))
                world[(pos[0], pos[1] + 1, pos[2])] = (name, dict(upper[1], facing=props.get("facing", "north"),
                                                                  open=props.get("open", "false")))
        elif props.get("half") == "upper" and name in ("minecraft:sunflower", "minecraft:lilac", "minecraft:rose_bush",
                                                       "minecraft:peony", "minecraft:tall_grass", "minecraft:large_fern"):
            lower = world.get((pos[0], pos[1] - 1, pos[2]))
            if lower:
                world[pos] = (lower[0], dict(lower[1], half="upper"))


def stair_shape(world, pos, props):
    facing = props.get("facing", "north")
    half = props.get("half", "bottom")

    def stairs_at(direction):
        other = neighbor(world, pos, direction)
        if other and other[0].endswith("_stairs") and other[1].get("half", "bottom") == half:
            return other[1].get("facing", "north")
        return None

    def can_take(direction):
        other = neighbor(world, pos, direction)
        return not (other and other[0].endswith("_stairs") and other[1].get("facing") == facing
                    and other[1].get("half", "bottom") == half)

    behind = stairs_at(facing)
    if behind and (DIRS[behind][0] == 0) != (DIRS[facing][0] == 0) and can_take(OPPOSITE[behind]):
        return "outer_left" if behind == CCW[facing] else "outer_right"
    front = stairs_at(OPPOSITE[facing])
    if front and (DIRS[front][0] == 0) != (DIRS[facing][0] == 0) and can_take(front):
        return "inner_left" if front == CCW[facing] else "inner_right"
    return "straight"


# ---------------------------------------------------------------------------
# Ciktilar
# ---------------------------------------------------------------------------
def to_relative(world):
    ox, oy, oz = ORIGIN
    return {(x - ox, y - oy, z - oz): fmt(name, props) for (x, y, z), (name, props) in world.items()}


def write_blueprint(rel, path):
    order = sorted(set(rel.values()))
    index = {b: i for i, b in enumerate(order)}
    rows = {}
    for (x, y, z), b in rel.items():
        rows.setdefault((y, z), []).append((x, index[b]))
    runs = []
    for (y, z) in sorted(rows):
        row = sorted(rows[(y, z)])
        start, prev_x, prev_i = row[0][0], row[0][0], row[0][1]
        for x, i in row[1:] + [(None, None)]:
            if x is not None and x == prev_x + 1 and i == prev_i:
                prev_x = x
                continue
            runs.append(f"{y} {z} {start} {prev_x - start + 1} {prev_i}")
            if x is not None:
                start, prev_x, prev_i = x, x, i
    text = "\n".join(["SKYBP1", f"palette {len(order)}", *order, f"runs {len(runs)}", *runs]) + "\n"
    with open(path, "wb") as out, gzip.GzipFile(fileobj=out, mode="wb", compresslevel=9, mtime=0) as f:
        f.write(text.encode("utf-8"))
    return len(order), len(runs)


def rel_point(x, y, z):
    return (x - ORIGIN[0], y - ORIGIN[1], z - ORIGIN[2])


def sign_block(blocks, pos):
    """Orijinal tabelanin yonu: 68 = duvar tabelasi (2-5), 63 = ayakli tabela (0-15)."""
    block_id, data = blocks.get(pos, (68, 2))
    if block_id == 63:
        return f"minecraft:oak_sign[rotation={data & 15}]"
    return f"minecraft:oak_wall_sign[facing={WALL_FACING.get(data, 'north')}]"


def write_layout(rel, heads, path, blocks):
    xs = [p[0] for p in rel]
    ys = [p[1] for p in rel]
    zs = [p[2] for p in rel]
    orange = rel_point(1101.5, 11, -604.5)
    purple = rel_point(1101.5, 11, -472.5)
    lobby = rel_point(1027.5, 40, -538.5)
    head_lines = []
    for head in heads:
        x, y, z = rel_point(*head["pos"])
        head_lines.append(f'  - {{x: {x}, y: {y}, z: {z}, isim: "{head["name"]}", uuid: "{head["id"]}",\n'
                          f'     doku: "{head["value"]}",\n     imza: "{head["signature"]}"}}')

    def sign(pos, lines, _facing=None):
        x, y, z = rel_point(*pos)
        quoted = ", ".join(json.dumps(line, ensure_ascii=False) for line in lines)
        return f'  - {{x: {x}, y: {y}, z: {z}, blok: "{sign_block(blocks, pos)}", satirlar: [{quoted}]}}'

    # Yapimci odasindaki tabelalar (orijinal yerlerinde, Turkce)
    signs = [
        sign((1020, 42, -537), ["", "&2&lxXFracXx", "&aYapımcı", "İnşa ve plan"], "east"),
        sign((1021, 42, -535), ["&2&lElectroid", "&2&lFilms", "&aKatkı", "İnşa ve fikir"], "east"),
        sign((1021, 42, -543), ["", "&2&lItsMiiOlly", "&aKatkı", "MCEdit / görüş"], "east"),
        sign((1020, 42, -541), ["", "&2&lTheZaner", "&aYapımcı", "XML ve plan"], "east"),
        sign((1020, 42, -539), ["", "&2&lArticray", "&aYapımcı", "Harita sahibi"], "east"),
        sign((1027, 42, -531), ["Hoş geldin!", "&2&lThe Hill", "Articray ve", "arkadaşları"], "north"),
        sign((1027, 41, -531), ["&2Tepenin Kralı", "Tepeyi tek", "başına tut,", "ödülü kap!"], "north"),
        sign((1026, 41, -531), ["Lisans:", "CC BY-SA 4.0", "Overcast", "Community"], "north"),
        sign((1028, 41, -531), ["Arenaya git:", "&2/koth katil", "", "İyi şanslar!"], "north"),
    ]
    text = f"""# Sky Survival KOTH arenasi: "The Hill" (Articray, TheZaner, xXFracXx; ItsMiiOlly, ElectroidFilms)
# Lisans: CC BY-SA 4.0 - https://creativecommons.org/licenses/by-sa/4.0/
# Kaynak: https://github.com/OvercastCommunity/PublicMaps (koth/the_hill) - tools/import_map.py ile cevrildi.
# Koordinatlar tepenin merkezine gore.
boyut: {{min: [{min(xs)}, {min(ys)}, {min(zs)}], max: [{max(xs)}, {max(ys)}, {max(zs)}]}}

# Tepe (KOTH alani): orijinalde 6.5 yaricapli silindir
tepe: {{min: [-6, 0, -6], max: [6, 4, 6]}}

# Oyuncularin indigi yerler (iki uctaki ussler); /koth katil rastgele birini secer
dogus-noktalari:
  - {{x: {orange[0]}, y: {orange[1]}, z: {orange[2]}, yaw: 0}}
  - {{x: {purple[0]}, y: {purple[1]}, z: {purple[2]}, yaw: 180}}

# Yapimcilar adasi (haritanin sahiplerinin kafalari ve tabelalari burada): /warp koth buraya gelir.
# Ortadaki isaretin ustune basan arenaya (rastgele bir usse) gecer.
tanitim: {{x: {lobby[0] + 4}, y: {lobby[1]}, z: {lobby[2]}, yaw: 90}}
portal: {{min: [{lobby[0] - 1.5:.0f}, {lobby[1]:.0f}, {lobby[2] - 1.5:.0f}], max: [{lobby[0] + 0.5:.0f}, {lobby[1] + 2:.0f}, {lobby[2] + 0.5:.0f}]}}

# Orijinal yapimci kafalari (Paper API ile kaplamalari geri yuklenir)
kafalar:
{chr(10).join(head_lines)}

# Tabelalar (orijinal yerlerinde, Turkce)
tabelalar:
{chr(10).join(signs)}

# Hologramlar (DecentHolograms)
hologramlar:
  sky_koth_arena:
    konum: {{x: 0.5, y: 9.5, z: 0.5}}
    satirlar:
      - "&6&l♛ TEPENİN KRALI ♛"
      - "&7Etkinlikte tepeyi tek başına tut, ödülü kap!"
      - "&7Harita: &fThe Hill &7- Articray, TheZaner, xXFracXx &8(CC BY-SA 4.0)"
"""
    path.write_text(text, encoding="utf-8")


COLORS = {
    "oak_planks": (162, 130, 78), "lime_terracotta": (103, 117, 53), "jungle_wood": (85, 67, 25),
    "oak_slab": (162, 130, 78), "green_terracotta": (76, 83, 42), "cyan_terracotta": (87, 92, 92),
    "oak_fence": (162, 130, 78), "yellow_terracotta": (186, 133, 36), "oak_stairs": (162, 130, 78),
    "white_stained_glass": (230, 230, 230), "orange_terracotta": (161, 83, 37), "purple_terracotta": (118, 70, 86),
    "light_blue_terracotta": (113, 109, 138), "emerald_block": (42, 200, 100), "diamond_block": (98, 219, 214),
    "lapis_block": (31, 67, 140), "glowstone": (250, 215, 120), "beacon": (120, 220, 215), "chest": (160, 110, 40),
}


def color_of(state):
    name = state.split("[")[0].replace("minecraft:", "")
    return COLORS.get(name, (140, 140, 140))


def shade(color, f):
    return tuple(max(0, min(255, int(v * f))) for v in color)


def render_iso(rel, path, scale=3):
    from PIL import Image, ImageDraw
    solid = set(rel)
    visible = [(x, y, z, b) for (x, y, z), b in rel.items()
               if (x, y + 1, z) not in solid or (x + 1, y, z) not in solid or (x, y, z + 1) not in solid]
    visible.sort(key=lambda t: (t[0] + t[2], t[1]))
    points = [((x - z) * 2, (x + z) - y * 2) for x, y, z, _ in visible]
    minx = min(p[0] for p in points) - 4
    miny = min(p[1] for p in points) - 6
    maxx = max(p[0] for p in points) + 4
    maxy = max(p[1] for p in points) + 6
    image = Image.new("RGB", ((maxx - minx) * scale, (maxy - miny) * scale), (150, 200, 245))
    draw = ImageDraw.Draw(image)
    for (x, y, z, b), (sx, sy) in zip(visible, points):
        c = color_of(b)
        px, py = (sx - minx) * scale, (sy - miny) * scale
        s = scale
        draw.polygon([(px - 2 * s, py), (px, py + s), (px, py + 3 * s), (px - 2 * s, py + 2 * s)], fill=shade(c, 0.72))
        draw.polygon([(px + 2 * s, py), (px, py + s), (px, py + 3 * s), (px + 2 * s, py + 2 * s)], fill=shade(c, 0.86))
        draw.polygon([(px, py - s), (px + 2 * s, py), (px, py + s), (px - 2 * s, py)], fill=c)
    image.save(path, optimize=True)


def render_top(rel, path, scale=5):
    from PIL import Image, ImageDraw
    top = {}
    for (x, y, z), b in rel.items():
        if (x, z) not in top or y > top[(x, z)][0]:
            top[(x, z)] = (y, b)
    xs = [p[0] for p in top]
    zs = [p[1] for p in top]
    x0, z0 = min(xs), min(zs)
    image = Image.new("RGB", ((max(xs) - x0 + 1) * scale, (max(zs) - z0 + 1) * scale), (150, 200, 245))
    draw = ImageDraw.Draw(image)
    for (x, z), (y, b) in top.items():
        c = shade(color_of(b), 0.7 + 0.012 * (y + 29))
        draw.rectangle([(x - x0) * scale, (z - z0) * scale, (x - x0 + 1) * scale - 1, (z - z0 + 1) * scale - 1], fill=c)
    image.save(path, optimize=True)


# --- WorldEdit sematigi (Sponge v2) ---
def nbt_string(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def nbt_named(tag, name, payload):
    return struct.pack(">b", tag) + nbt_string(name) + payload


def varint(v):
    out = bytearray()
    while True:
        b = v & 0x7F
        v >>= 7
        if v:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def write_schem(rel, path):
    xs = [p[0] for p in rel]
    ys = [p[1] for p in rel]
    zs = [p[2] for p in rel]
    x0, y0, z0 = min(xs), min(ys), min(zs)
    w, h, l = max(xs) - x0 + 1, max(ys) - y0 + 1, max(zs) - z0 + 1
    palette = {"minecraft:air": 0}
    for b in sorted(set(rel.values())):
        palette.setdefault(b, len(palette))
    data = bytearray()
    for y in range(h):
        for z in range(l):
            for x in range(w):
                data += varint(palette[rel.get((x + x0, y + y0, z + z0), "minecraft:air")])
    pal = b"".join(nbt_named(3, k, struct.pack(">i", v)) for k, v in palette.items()) + b"\x00"
    meta = (nbt_named(3, "WEOffsetX", struct.pack(">i", x0)) + nbt_named(3, "WEOffsetY", struct.pack(">i", y0))
            + nbt_named(3, "WEOffsetZ", struct.pack(">i", z0)) + nbt_named(8, "Name", nbt_string("The Hill (CC BY-SA 4.0)"))
            + nbt_named(8, "Author", nbt_string("Articray, TheZaner, xXFracXx")) + b"\x00")
    body = (nbt_named(3, "Version", struct.pack(">i", 2)) + nbt_named(3, "DataVersion", struct.pack(">i", 4189))
            + nbt_named(2, "Width", struct.pack(">h", w)) + nbt_named(2, "Height", struct.pack(">h", h))
            + nbt_named(2, "Length", struct.pack(">h", l)) + nbt_named(3, "PaletteMax", struct.pack(">i", len(palette)))
            + nbt_named(10, "Palette", pal) + nbt_named(7, "BlockData", struct.pack(">i", len(data)) + bytes(data))
            + nbt_named(11, "Offset", struct.pack(">iii", x0, y0, z0)) + nbt_named(10, "Metadata", meta) + b"\x00")
    with open(path, "wb") as out, gzip.GzipFile(fileobj=out, mode="wb", mtime=0) as f:
        f.write(nbt_named(10, "Schematic", body))
    return w, h, l


LICENSE_TEXT = """"The Hill" haritasi / map
Yapimcilar / Authors: Articray, TheZaner (Zan), xXFracXx
Katkida bulunanlar / Contributors: ItsMiiOlly, ElectroidFilms
Kaynak / Source: https://github.com/OvercastCommunity/PublicMaps (koth/the_hill), https://mcresourcepile.github.io/

Bu harita ve Sky Survival icin degistirilmis hali Creative Commons Attribution-ShareAlike 4.0 International
lisansiyla dagitilir: https://creativecommons.org/licenses/by-sa/4.0/
This map and its modified version are licensed under CC BY-SA 4.0: https://creativecommons.org/licenses/by-sa/4.0/

Degisiklikler / Changes: bloklar Minecraft 1.8 kimliklerinden guncel surume cevrildi (tools/import_map.py);
cit/panel/merdiven baglantilari yeniden hesaplandi; tabelalar ve sandik icerikleri kaldirildi, yapimci tabelalari
Turkceye cevrildi; tepenin altindaki fenerin isini acilsin diye 3 piston kaldirildi; harita Sky Survival
sunucusunda gokyuzunde KOTH etkinlik arenasi olarak kurulur.
Blocks converted from Minecraft 1.8 IDs to the current version; fence/pane/stair connections recomputed; signs and
chest contents removed, author signs translated to Turkish; 3 pistons removed so the beacon beam shows above the
hill; used as a floating KOTH event arena on Sky Survival.
"""


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 1
    world_dir, legacy_path = sys.argv[1], sys.argv[2]
    legacy = json.load(open(legacy_path))["blocks"]
    blocks, tiles = mcworld.legacy_blocks(world_dir)
    world, heads = convert(blocks, tiles, legacy)
    rel = to_relative(world)
    RES.mkdir(parents=True, exist_ok=True)
    palette, runs = write_blueprint(rel, RES / "arena.bp.gz")
    write_layout(rel, heads, RES / "arena.yml", blocks)
    (RES / "LICENSE.txt").write_text(LICENSE_TEXT, encoding="utf-8")
    size = write_schem(rel, BRANDING / "KothArena-TheHill.schem")
    render_iso(rel, BRANDING / "koth-arena-onizleme.png")
    render_top(rel, BRANDING / "koth-arena-harita.png")
    print(f"blok: {len(rel)}, palet: {palette}, satir: {runs}, kafa: {len(heads)}, schem: {size}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
