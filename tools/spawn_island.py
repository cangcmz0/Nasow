"""Sky Survival gokyuzu spawn adasini tasarlar.

Tek kaynak bu dosyadir. Cikti:
  custom-plugins/SkyCore/src/main/resources/spawn/ada.bp.gz   SkyCore'un adayi kurdugu blok listesi
  custom-plugins/SkyCore/src/main/resources/spawn/ada.yml     spawn, warp, portal, hologram, kasa, KOTH noktalari
  branding/spawn-adasi-onizleme.png                           egik (izometrik) onizleme
  branding/spawn-adasi-harita.png                             ustten etiketli harita
  branding/SkySpawn.schem                                     WorldEdit icin sematik (//schem load SkySpawn)

Koordinatlar adanin merkezine goredir: (0,0,0) = meydanin zemini. x dogu, z guney, y yukari.
Kullanim:  python3 tools/spawn_island.py
"""
import gzip
import io
import math
import random
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "custom-plugins" / "SkyCore" / "src" / "main" / "resources" / "spawn"
BRANDING = ROOT / "branding"

rng = random.Random(2026)
W = {}  # (x, y, z) -> blok


def setb(x, y, z, b):
    if b is None:
        W.pop((x, y, z), None)
    else:
        W[(x, y, z)] = b


def getb(x, y, z):
    return W.get((x, y, z))


def fill(x0, y0, z0, x1, y1, z1, b):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                setb(x, y, z, b)


def pick(options):
    """[(blok, agirlik), ...] icinden rastgele secer."""
    total = sum(w for _, w in options)
    r = rng.random() * total
    for b, w in options:
        r -= w
        if r <= 0:
            return b
    return options[-1][0]


# ---------------------------------------------------------------------------
# 1) Ada govdesi
# ---------------------------------------------------------------------------
BASE_R = 46.0


def radius(angle):
    return BASE_R + 3.0 * math.sin(3 * angle + 0.7) + 2.0 * math.sin(5 * angle + 2.1) + 1.2 * math.sin(9 * angle)


SURFACE = {}  # (x, z) -> True: ada ustu
for x in range(-56, 57):
    for z in range(-56, 57):
        r = math.hypot(x, z)
        R = radius(math.atan2(z, x))
        if r <= R:
            SURFACE[(x, z)] = True
            k = r / R
            depth = int(4 + 40 * (1 - k ** 1.35) + rng.choice([-1, 0, 0, 1]))
            for y in range(0, -depth - 1, -1):
                if y == 0:
                    b = "minecraft:grass_block[snowy=false]"
                elif y >= -3:
                    b = pick([("minecraft:dirt", 8), ("minecraft:coarse_dirt", 1), ("minecraft:rooted_dirt", 1)])
                else:
                    b = pick([("minecraft:stone", 10), ("minecraft:andesite", 4), ("minecraft:tuff", 3),
                              ("minecraft:cobblestone", 2), ("minecraft:mossy_cobblestone", 1)])
                    if rng.random() < 0.012:
                        b = pick([("minecraft:coal_ore", 5), ("minecraft:iron_ore", 4), ("minecraft:copper_ore", 3),
                                  ("minecraft:gold_ore", 1), ("minecraft:diamond_ore", 1), ("minecraft:emerald_ore", 0.5)])
                setb(x, y, z, b)

# Kenarlardan sarkan sarmasiklar ve alttaki parlayan likenler, sarkitlar
FACES = {(1, 0): "west", (-1, 0): "east", (0, 1): "north", (0, -1): "south"}
for (x, y, z), b in list(W.items()):
    if y > -2 or y < -30:
        continue
    for (dx, dz), face in FACES.items():
        nx, nz = x + dx, z + dz
        if getb(nx, y, nz) is None and (nx, nz) not in SURFACE and rng.random() < 0.18:
            length = rng.randint(1, 5)
            for i in range(length):
                if getb(nx, y - i, nz) is None:
                    setb(nx, y - i, nz, f"minecraft:vine[{face}=true]")
lowest = {}
for (x, y, z) in W:
    if W[(x, y, z)].startswith("minecraft:vine"):
        continue
    if (x, z) not in lowest or y < lowest[(x, z)]:
        lowest[(x, z)] = y
for (x, z), y in lowest.items():
    r = rng.random()
    if r < 0.05:
        n = rng.randint(2, 4)
        for i in range(n):
            thickness = "tip" if i == n - 1 else ("frustum" if i == n - 2 else "middle")
            if n == 2 and i == 0:
                thickness = "frustum"
            setb(x, y - 1 - i, z, f"minecraft:pointed_dripstone[thickness={thickness},vertical_direction=down,waterlogged=false]")
    elif r < 0.12:
        setb(x, y - 1, z, "minecraft:glow_lichen[up=true,waterlogged=false]")
    elif r < 0.16:
        setb(x, y - 1, z, "minecraft:hanging_roots[waterlogged=false]")

# ---------------------------------------------------------------------------
# 2) Yardimcilar: ustteki dekorlar
# ---------------------------------------------------------------------------
FLOWERS = ["minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:allium",
           "minecraft:azure_bluet", "minecraft:oxeye_daisy", "minecraft:lily_of_the_valley", "minecraft:pink_tulip"]


def lamp_post(x, z, h=3):
    for y in range(1, h + 1):
        setb(x, y, z, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]")
    setb(x, h + 1, z, "minecraft:lantern[hanging=false,waterlogged=false]")


def bench(x, z, facing, length=3, axis="x"):
    for i in range(length):
        bx, bz = (x + i, z) if axis == "x" else (x, z + i)
        shape = "straight"
        setb(bx, 1, bz, f"minecraft:spruce_stairs[facing={facing},half=bottom,shape={shape},waterlogged=false]")


def oak_tree(x, z, h=None, cherry=False):
    h = h or rng.randint(5, 7)
    log = "minecraft:cherry_log[axis=y]" if cherry else "minecraft:oak_log[axis=y]"
    leaves = ("minecraft:cherry_leaves[distance=1,persistent=true,waterlogged=false]" if cherry
              else "minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]")
    for y in range(1, h + 1):
        setb(x, y, z, log)
    for dy in range(h - 2, h + 2):
        rr = 2 if dy < h + 1 else 1
        for dx in range(-rr - 1, rr + 2):
            for dz in range(-rr - 1, rr + 2):
                if dx == 0 and dz == 0 and dy <= h:
                    continue
                if dx * dx + dz * dz <= rr * rr + 1 + (1 if dy < h else 0) and rng.random() > 0.08:
                    if getb(x + dx, dy, z + dz) is None:
                        setb(x + dx, dy, z + dz, leaves)
    setb(x, h + 2, z, leaves)


def is_free_grass(x, z):
    return getb(x, 0, z) == "minecraft:grass_block[snowy=false]" and getb(x, 1, z) is None


# ---------------------------------------------------------------------------
# 3) Spawn meydani ve fiskiye (merkez)
# ---------------------------------------------------------------------------
for x in range(-11, 12):
    for z in range(-11, 12):
        r = math.hypot(x, z)
        if r <= 11.2:
            if r > 10.3:
                b = "minecraft:stone_bricks"
            elif abs(r - 7) < 0.6:
                b = "minecraft:chiseled_stone_bricks" if (x == 0 or z == 0) else "minecraft:polished_andesite"
            else:
                b = pick([("minecraft:stone_bricks", 6), ("minecraft:mossy_stone_bricks", 1),
                          ("minecraft:cracked_stone_bricks", 1)]) if r > 7.5 else "minecraft:polished_andesite"
            setb(x, 0, z, b)
            setb(x, 1, z, None)

# Fiskiye havuzu: icerisi su, kenari kuvars
for x in range(-4, 5):
    for z in range(-4, 5):
        r = math.hypot(x, z)
        if r <= 3.3:
            setb(x, -1, z, "minecraft:quartz_block")
            setb(x, 0, z, "minecraft:water[level=0]")
        elif r <= 4.4:
            setb(x, 0, z, "minecraft:quartz_block")
            setb(x, 1, z, "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]")
for y in range(0, 5):
    setb(0, y, 0, "minecraft:quartz_pillar[axis=y]")
setb(0, 5, 0, "minecraft:sea_lantern")
for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
    setb(dx, 5, dz, "minecraft:water[level=0]")
    setb(dx, 4, dz, None)
setb(0, 6, 0, "minecraft:end_rod[facing=up]")

# Meydan cevresinde fenerler ve banklar
for ang in range(0, 360, 45):
    if ang % 90 == 0:
        continue
    a = math.radians(ang)
    lamp_post(int(round(9.5 * math.cos(a))), int(round(9.5 * math.sin(a))), 3)
# Banklar yollarin uzerine gelmesin diye capraz konumlarda
for (x, z, facing, axis) in [(4, 8, "north", "x"), (-6, 8, "north", "x"), (4, -8, "south", "x"), (-6, -8, "south", "x")]:
    bench(x, z, facing, 3, axis)

# ---------------------------------------------------------------------------
# 4) Yollar (dort yon)
# ---------------------------------------------------------------------------
PATHS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
PATH_END = {"north": 22, "south": 20, "east": 21, "west": 21}
for name, (dx, dz) in PATHS.items():
    for d in range(11, PATH_END[name] + 1):
        for w in range(-2, 3):
            x = dx * d + (w if dx == 0 else 0)
            z = dz * d + (w if dz == 0 else 0)
            edge = abs(w) == 2
            setb(x, 0, z, "minecraft:stone_bricks" if edge else pick([
                ("minecraft:polished_andesite", 5), ("minecraft:stone_bricks", 2), ("minecraft:mossy_stone_bricks", 1)]))
            setb(x, 1, z, None)
        if d % 5 == 0:
            for w in (-3, 3):
                x = dx * d + (w if dx == 0 else 0)
                z = dz * d + (w if dz == 0 else 0)
                lamp_post(x, z, 2)

# ---------------------------------------------------------------------------
# 5) KUZEY: Vahsi Doga portali (rastgele isinlanma)
# ---------------------------------------------------------------------------
PZ = -24
for x in range(-4, 5):
    for y in range(1, 10):
        inner = -2 <= x <= 2 and 1 <= y <= 7
        if inner:
            continue
        b = "minecraft:chiseled_stone_bricks" if (x in (-4, 4) and y % 3 == 0) else "minecraft:stone_bricks"
        if y == 9:
            b = "minecraft:stone_brick_slab[type=bottom,waterlogged=false]" if x in (-4, 4) else "minecraft:stone_bricks"
        setb(x, y, PZ, b)
for x in range(-2, 3):
    setb(x, 8, PZ, "minecraft:sea_lantern" if x % 2 == 0 else "minecraft:light_blue_stained_glass")
for x in range(-2, 3):
    for z in (PZ - 1, PZ, PZ + 1):
        setb(x, 0, z, "minecraft:light_blue_glazed_terracotta[facing=north]")
setb(0, 10, PZ, "minecraft:end_rod[facing=up]")
# Portalin ici bos; SkyCore icine parcacik efekti cizer.
PORTAL_TPR = {"min": [-2, 1, PZ - 1], "max": [2, 7, PZ + 1]}

# ---------------------------------------------------------------------------
# 6) DOGU: Market binasi
# ---------------------------------------------------------------------------
MX0, MX1, MZ0, MZ1 = 22, 32, -6, 6
for x in range(MX0, MX1 + 1):
    for z in range(MZ0, MZ1 + 1):
        setb(x, 0, z, "minecraft:spruce_planks")
        for y in range(1, 8):
            setb(x, y, z, None)
# Kose direkleri ve duvarlar (on taraf bati, acik)
for x in range(MX0, MX1 + 1):
    for z in range(MZ0, MZ1 + 1):
        corner = x in (MX0, MX1) and z in (MZ0, MZ1)
        wall = x == MX1 or z in (MZ0, MZ1)
        if corner:
            for y in range(1, 6):
                setb(x, y, z, "minecraft:spruce_log[axis=y]")
        elif wall and x != MX0:
            for y in range(1, 6):
                window = y in (2, 3) and (x + z) % 3 == 0 and x != MX1
                setb(x, y, z, "minecraft:glass_pane[east=false,north=false,south=false,waterlogged=false,west=false]"
                     if window else "minecraft:spruce_planks")
        elif x == MX0 and z in (MZ0 + 3, MZ1 - 3):
            for y in range(1, 6):
                setb(x, y, z, "minecraft:spruce_log[axis=y]")
# Cati: kademeli basamaklar
for level in range(0, 4):
    y = 6 + level
    for x in range(MX0 - 1 + level, MX1 + 2 - level):
        for z in (MZ0 - 1 + level, MZ1 + 1 - level):
            facing = "south" if z == MZ0 - 1 + level else "north"
            setb(x, y, z, f"minecraft:dark_oak_stairs[facing={facing},half=bottom,shape=straight,waterlogged=false]")
    for x in range(MX0 - 1 + level, MX1 + 2 - level):
        for z in range(MZ0 + level, MZ1 + 1 - level):
            if getb(x, y, z) is None:
                setb(x, y, z, "minecraft:dark_oak_planks")
for z in range(MZ0 + 4, MZ1 - 3):
    for x in range(MX0 + 3, MX1 - 2):
        setb(x, 10, z, "minecraft:dark_oak_slab[type=bottom,waterlogged=false]")
# Ic: tezgahlar (variller) ve market pedi
for z in range(MZ0 + 2, MZ1 - 1):
    setb(MX1 - 1, 1, z, "minecraft:barrel[facing=west,open=false]")
for x in range(MX0 + 2, MX1 - 2, 2):
    setb(x, 1, MZ0 + 1, "minecraft:barrel[facing=south,open=false]")
    setb(x, 1, MZ1 - 1, "minecraft:barrel[facing=north,open=false]")
for x in range(MX0 + 4, MX0 + 7):
    for z in range(-1, 2):
        setb(x, 0, z, "minecraft:emerald_block")
for z in (MZ0 + 2, MZ1 - 2):
    setb(MX0 + 5, 5, z, "minecraft:lantern[hanging=true,waterlogged=false]")
setb(MX0 + 5, 5, 0, "minecraft:lantern[hanging=true,waterlogged=false]")
PORTAL_MARKET = {"min": [MX0 + 4, 1, -1], "max": [MX0 + 6, 3, 1]}

# ---------------------------------------------------------------------------
# 7) GUNEY: Arena (PvP) ve ortasinda KOTH tepesi
# ---------------------------------------------------------------------------
AZ = 33  # arena merkezi z
AR = 11
for x in range(-AR - 2, AR + 3):
    for z in range(AZ - AR - 2, AZ + AR + 3):
        r = math.hypot(x, z - AZ)
        if r <= AR - 0.5:
            for y in range(-3, 6):
                setb(x, y, z, None)
            setb(x, -4, z, pick([("minecraft:sand", 5), ("minecraft:coarse_dirt", 2), ("minecraft:gravel", 1)]))
        elif r <= AR + 0.6:
            for y in range(-4, 1):
                setb(x, y, z, "minecraft:stone_bricks" if (y + x + z) % 5 else "minecraft:chiseled_stone_bricks")
            setb(x, 1, z, "minecraft:stone_brick_wall[east=low,north=low,south=low,up=true,waterlogged=false,west=low]")
        elif r <= AR + 2.6:
            setb(x, 0, z, "minecraft:stone_bricks")
            setb(x, 1, z, None)
# Kuzeyden giris: arenaya inen merdiven
for i, y in enumerate(range(0, -4, -1)):
    z = AZ - AR + i
    for x in range(-1, 2):
        setb(x, y, z, "minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]")
        for yy in range(y + 1, y + 4):
            setb(x, yy, z, None)
    setb(-2, y, z, "minecraft:stone_bricks")
    setb(2, y, z, "minecraft:stone_bricks")
for x in range(-1, 2):
    setb(x, 1, AZ - AR - 1, None)
    setb(x, 1, AZ - AR, None)
# KOTH tepesi: ortada yukseltilmis altin platform
for x in range(-2, 3):
    for z in range(AZ - 2, AZ + 3):
        setb(x, -4, z, "minecraft:polished_andesite")
        setb(x, -3, z, "minecraft:gold_block" if abs(x) <= 1 and abs(z - AZ) <= 1 else
             "minecraft:smooth_stone_slab[type=bottom,waterlogged=false]")
for (x, z) in ((-2, AZ - 2), (2, AZ - 2), (-2, AZ + 2), (2, AZ + 2)):
    setb(x, -3, z, "minecraft:polished_andesite")
    lamp = ["minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]"] * 2
    setb(x, -2, z, lamp[0])
    setb(x, -1, z, lamp[1])
    setb(x, 0, z, "minecraft:lantern[hanging=false,waterlogged=false]")
# Isik: duvarin ic tarafinda deniz fenerleri
for ang in range(0, 360, 30):
    a = math.radians(ang)
    x, z = int(round((AR) * math.cos(a))), int(round(AZ + (AR) * math.sin(a)))
    if getb(x, -2, z) and "stone_brick" in getb(x, -2, z):
        setb(x, -2, z, "minecraft:sea_lantern")
ARENA = {"min": [-AR, -3, AZ - AR], "max": [AR, 8, AZ + AR]}
KOTH = {"min": [-1, -3, AZ - 1], "max": [1, 0, AZ + 1]}

# ---------------------------------------------------------------------------
# 8) BATI: Kasa koskü (3 kasa) ve rehber
# ---------------------------------------------------------------------------
KX = -27
for x in range(KX - 5, KX + 6):
    for z in range(-6, 7):
        if math.hypot(x - KX, z) <= 6.2:
            setb(x, 0, z, "minecraft:smooth_quartz" if math.hypot(x - KX, z) <= 5 else "minecraft:quartz_bricks")
            for y in range(1, 9):
                setb(x, y, z, None)
for (x, z) in ((KX - 4, -4), (KX + 4, -4), (KX - 4, 4), (KX + 4, 4)):
    for y in range(1, 6):
        setb(x, y, z, "minecraft:quartz_pillar[axis=y]")
for x in range(KX - 5, KX + 6):
    for z in range(-5, 6):
        d = max(abs(x - KX), abs(z))
        if d <= 5:
            y = 6 + (5 - d) // 2
            if d in (4, 5) or (d <= 3 and y == 8):
                setb(x, 6 if d >= 4 else y, z, "minecraft:white_concrete" if (x + z) % 2 else "minecraft:light_blue_concrete")
for d in range(0, 4):
    for x in range(KX - d, KX + d + 1):
        for z in range(-d, d + 1):
            if max(abs(x - KX), abs(z)) == d:
                setb(x, 9 - d // 2 if d < 3 else 7, z, "minecraft:white_concrete" if (x + z) % 2 else "minecraft:light_blue_concrete")
setb(KX, 8, 0, "minecraft:sea_lantern")
CRATES = []
for i, z in enumerate((-3, 0, 3)):
    setb(KX - 1, 1, z, "minecraft:chiseled_quartz_block")
    setb(KX - 1, 2, z, "minecraft:ender_chest[facing=east,waterlogged=false]")
    CRATES.append([KX - 1, 2, z])

# ---------------------------------------------------------------------------
# 9) Atlama iskelesi (kuzeydogu kenari)
# ---------------------------------------------------------------------------
ang = math.radians(-45)
edge = radius(ang)
JX, JZ = int(round((edge - 6) * math.cos(ang))), int(round((edge - 6) * math.sin(ang)))
for i in range(0, 13):
    x, z = JX + int(round(i * math.cos(ang))), JZ + int(round(i * math.sin(ang)))
    for w in (0, 1):
        setb(x + w, 0, z, "minecraft:spruce_planks")
        setb(x + w, 1, z, None)
    if i < 10 and i % 2 == 0:
        setb(x - 1, 1, z, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]")
JUMP_END = [JX + int(round(12 * math.cos(ang))), 1, JZ + int(round(12 * math.sin(ang)))]
JUMP_START = [JX, 1, JZ]

# ---------------------------------------------------------------------------
# 10) Agaclar, cicekler, cimen
# ---------------------------------------------------------------------------
TREE_SPOTS = [(-16, -16, True), (16, -16, False), (-16, 16, False), (16, 16, True), (-34, -18, False),
              (36, -20, True), (-36, 20, True), (12, -36, False), (-14, -36, True), (36, 18, False),
              (-20, 40, False), (22, 34, True), (-40, -4, False), (40, 4, True)]
for (x, z, cherry) in TREE_SPOTS:
    if (x, z) in SURFACE and is_free_grass(x, z):
        oak_tree(x, z, cherry=cherry)
for (x, z) in list(SURFACE):
    if not is_free_grass(x, z):
        continue
    r = rng.random()
    if r < 0.035:
        setb(x, 1, z, rng.choice(FLOWERS))
    elif r < 0.19:
        setb(x, 1, z, "minecraft:short_grass")
    elif r < 0.205:
        setb(x, 1, z, "minecraft:pink_petals[facing=north,flower_amount=3]")

# Siralama tablolari: kuzey yolunun (portala giden yol) iki yaninda altin ortali kursuler; ustlerinde
# %skycore_top_...% yer tutuculu hologramlar durur. Rastgelelik kullanmaz, adanin geri kalani degismez.
BOARDS = [("sky_siralama_para", -8, -14, "para", "&6&l✦ EN ZENGİNLER ✦"),
          ("sky_siralama_oy", -8, -20, "oy", "&a&l✦ BU AYIN OYCULARI ✦"),
          ("sky_siralama_koth", 9, -14, "koth", "&c&l♛ KOTH ŞAMPİYONLARI ♛"),
          ("sky_siralama_gorev", 9, -20, "gorev", "&e&l✦ EN ÇOK GÖREV ✦")]
for (_, bx, bz, _, _) in BOARDS:
    for x in range(bx - 1, bx + 2):
        for z in range(bz - 1, bz + 2):
            ring = abs(x - bx) + abs(z - bz)
            setb(x, 0, z, "minecraft:gold_block" if ring == 0 else
                 "minecraft:chiseled_stone_bricks" if ring == 2 else "minecraft:polished_andesite")
            setb(x, 1, z, None)


def board_yaml():
    out = []
    for (name, bx, bz, board, title) in BOARDS:
        out.append(f"  {name}:")
        out.append(f"    konum: {yaml_point([bx + 0.5, 5.4, bz + 0.5])}")
        out.append("    satirlar:")
        out.append(f'      - "{title}"')
        out += [f'      - "%skycore_top_{board}_{i}%"' for i in range(1, 11)]
        out.append(f'      - "&7/siralama {board}"')
    return "\n".join(out)


# ---------------------------------------------------------------------------
# 11) Noktalar: spawn, warp, portal, hologram, kasa, KOTH, koruma
# ---------------------------------------------------------------------------
xs = [p[0] for p in W]
ys = [p[1] for p in W]
zs = [p[2] for p in W]
BOUNDS = [min(xs), min(ys), min(zs), max(xs), max(ys), max(zs)]


def yaml_point(p, yaw=None):
    s = f"{{x: {p[0]}, y: {p[1]}, z: {p[2]}"
    if yaw is not None:
        s += f", yaw: {yaw}"
    return s + "}"


def yaml_box(b):
    return f"{{min: [{b['min'][0]}, {b['min'][1]}, {b['min'][2]}], max: [{b['max'][0]}, {b['max'][1]}, {b['max'][2]}]}}"


MARKERS = f"""# Sky Survival spawn adasi noktalari (tools/spawn_island.py uretti).
# Koordinatlar adanin merkezine (meydanin zemini) gore. /skycore kurulum adayi config.yml'deki
# spawn-adasi.konum noktasina kurar ve bu noktalari oraya gore hesaplar.
boyut: {{min: [{BOUNDS[0]}, {BOUNDS[1]}, {BOUNDS[2]}], max: [{BOUNDS[3]}, {BOUNDS[4]}, {BOUNDS[5]}]}}

# Yeni oyuncular ve /spawn buraya gelir (fiskiyeye bakar)
spawn: {yaml_point([0.5, 1, 7.5], 180)}

# Essentials warplari (/warp <isim>)
warplar:
  market: {yaml_point([MX0 - 2.5, 1, 0.5], -90)}
  arena: {yaml_point([0.5, -3, AZ - AR + 4.5], 0)}
  kasalar: {yaml_point([KX + 6.5, 1, 0.5], 90)}
  portal: {yaml_point([0.5, 1, PZ + 4.5], 180)}
  atla: {yaml_point([JUMP_START[0] + 0.5, 1, JUMP_START[2] + 0.5], -135)}

# Icine girilince komut calisan alanlar
portallar:
  vahsi-doga: {{alan: {yaml_box(PORTAL_TPR)}, komut: "vahsi"}}
  market: {{alan: {yaml_box(PORTAL_MARKET)}, komut: "market"}}

# PvP serbest alan (adanin geri kalaninda PvP kapali)
arena: {yaml_box(ARENA)}

# Tepenin Krali (KOTH) bolgesi: arenanin ortasindaki altin platform
koth: {yaml_box(KOTH)}

# Kasalar (sag tik + anahtar)
kasalar:
  gunluk: {yaml_point(CRATES[0])}
  nadir: {yaml_point(CRATES[1])}
  efsane: {yaml_point(CRATES[2])}

# Hologramlar (DecentHolograms)
hologramlar:
  sky_hosgeldin:
    konum: {yaml_point([0.5, 9.2, 0.5])}
    satirlar:
      - "&b&lSKY SURVIVAL"
      - "&fGökyüzüne hoş geldin, &e%player_name%&f!"
      - "&7Dünyaya inmek için portala gir ya da adadan atla"
  sky_portal:
    konum: {yaml_point([0.5, 11.5, PZ + 0.5])}
    satirlar:
      - "&a&lVAHŞİ DOĞA"
      - "&7Rastgele bir yere ışınlanmak için içinden geç"
  sky_market:
    konum: {yaml_point([MX0 - 1.5, 5.5, 0.5])}
    satirlar:
      - "&e&lSUNUCU MARKETİ"
      - "&7Al-sat için içerideki zümrüt zemine bas"
      - "&7ya da her yerden &e/market"
  sky_arena:
    konum: {yaml_point([0.5, 4.5, AZ - AR - 2.5])}
    satirlar:
      - "&c&lPVP ARENASI"
      - "&7Burada PvP serbest!"
      - "&6♛ KOTH etkinliği: &e/koth katil"
  sky_kasalar:
    konum: {yaml_point([KX + 0.5, 10.5, 0.5])}
    satirlar:
      - "&d&lKASALAR"
      - "&7Anahtarla sağ tıkla, ödülünü kazan"
      - "&7Anahtarlar: günlük ödül, aktiflik, KOTH"
  sky_kasa_gunluk:
    konum: {yaml_point([CRATES[0][0] + 0.5, 3.9, CRATES[0][2] + 0.5])}
    satirlar:
      - "&a&lGÜNLÜK KASA"
      - "&7Anahtar: &f/odul"
  sky_kasa_nadir:
    konum: {yaml_point([CRATES[1][0] + 0.5, 3.9, CRATES[1][2] + 0.5])}
    satirlar:
      - "&b&lNADİR KASA"
      - "&7Anahtar: &faktiflik ödülü"
  sky_kasa_efsane:
    konum: {yaml_point([CRATES[2][0] + 0.5, 3.9, CRATES[2][2] + 0.5])}
    satirlar:
      - "&6&lEFSANE KASA"
      - "&7Anahtar: &fKOTH, 7 günlük seri"
  sky_atla:
    konum: {yaml_point([JUMP_END[0] + 0.5, 3.5, JUMP_END[2] + 0.5])}
    satirlar:
      - "&b&lATLAMA NOKTASI"
      - "&7Buradan atla, süzülerek dünyaya in!"
  sky_rehber:
    konum: {yaml_point([-6.5, 3.5, -6.5])}
    satirlar:
      - "&6&lREHBER"
      - "&e/jobs &7meslek • &e/skills &7yetenek"
      - "&e/klan &7klan • &e/market &7market"
      - "&e/odul &7günlük ödül • &e/kelle &7kelle avı"
      - "&e/gorev &7günlük görev • &e/takas &7güvenli takas"
      - "&e/oy &7oy ver, ödül al • &e/siralama &7sıralamalar"
      - "&7Arazi koruma: &6altın kürek"
{board_yaml()}
"""

# ---------------------------------------------------------------------------
# 12) Ciktilar
# ---------------------------------------------------------------------------


def write_blueprint(path):
    palette = {}
    order = []
    for b in sorted(set(W.values())):
        palette[b] = len(order)
        order.append(b)
    lines = ["SKYBP1", f"palette {len(order)}"]
    lines += order
    runs = []
    by_row = {}
    for (x, y, z), b in W.items():
        by_row.setdefault((y, z), []).append((x, palette[b]))
    for (y, z) in sorted(by_row):
        row = sorted(by_row[(y, z)])
        start, prev_x, prev_i = row[0][0], row[0][0], row[0][1]
        for x, i in row[1:] + [(None, None)]:
            if x is not None and x == prev_x + 1 and i == prev_i:
                prev_x = x
                continue
            runs.append(f"{y} {z} {start} {prev_x - start + 1} {prev_i}")
            if x is not None:
                start, prev_x, prev_i = x, x, i
    lines.append(f"runs {len(runs)}")
    lines += runs
    data = ("\n".join(lines) + "\n").encode("utf-8")
    with open(path, "wb") as out, gzip.GzipFile(fileobj=out, mode="wb", compresslevel=9, mtime=0) as f:
        f.write(data)
    return len(order), len(runs)


# --- Onizleme renkleri ---
COLORS = {
    "grass_block": (98, 168, 64), "dirt": (134, 96, 67), "coarse_dirt": (119, 85, 59), "rooted_dirt": (144, 106, 76),
    "stone": (125, 125, 125), "andesite": (136, 136, 137), "tuff": (108, 109, 102), "cobblestone": (116, 116, 116),
    "mossy_cobblestone": (101, 118, 88), "coal_ore": (60, 60, 60), "iron_ore": (190, 160, 140),
    "copper_ore": (150, 110, 80), "gold_ore": (220, 200, 90), "diamond_ore": (90, 210, 210),
    "emerald_ore": (60, 200, 100), "vine": (60, 120, 40), "glow_lichen": (110, 140, 120),
    "hanging_roots": (160, 120, 100), "pointed_dripstone": (130, 100, 80), "stone_bricks": (122, 121, 122),
    "mossy_stone_bricks": (115, 121, 105), "cracked_stone_bricks": (118, 117, 118),
    "chiseled_stone_bricks": (120, 119, 120), "polished_andesite": (132, 135, 134), "quartz_block": (236, 230, 223),
    "smooth_quartz_slab": (236, 230, 223), "quartz_pillar": (236, 232, 226), "sea_lantern": (172, 199, 190),
    "end_rod": (240, 240, 240), "water": (60, 110, 230), "spruce_fence": (100, 75, 46), "lantern": (230, 180, 80),
    "spruce_stairs": (114, 84, 48), "oak_log": (109, 85, 50), "oak_leaves": (60, 140, 40),
    "cherry_log": (54, 33, 44), "cherry_leaves": (235, 165, 200), "light_blue_stained_glass": (100, 160, 220),
    "light_blue_glazed_terracotta": (90, 160, 210), "spruce_planks": (114, 84, 48), "spruce_log": (58, 37, 16),
    "glass_pane": (200, 220, 230), "dark_oak_stairs": (66, 43, 20), "dark_oak_planks": (66, 43, 20),
    "dark_oak_slab": (66, 43, 20), "barrel": (140, 105, 60), "emerald_block": (42, 200, 100),
    "sand": (219, 207, 163), "gravel": (131, 127, 126), "stone_brick_wall": (122, 121, 122),
    "stone_brick_stairs": (122, 121, 122), "gold_block": (246, 208, 61), "smooth_stone_slab": (158, 158, 158),
    "smooth_quartz": (236, 230, 223), "quartz_bricks": (234, 229, 221), "white_concrete": (207, 213, 214),
    "light_blue_concrete": (36, 137, 199), "chiseled_quartz_block": (231, 226, 218), "ender_chest": (40, 60, 60),
    "stone_brick_slab": (122, 121, 122), "short_grass": (110, 170, 70), "pink_petals": (240, 170, 200),
    "poppy": (200, 40, 40), "dandelion": (240, 220, 50), "cornflower": (70, 100, 220), "allium": (180, 110, 220),
    "azure_bluet": (220, 230, 240), "oxeye_daisy": (240, 240, 230), "lily_of_the_valley": (240, 240, 240),
    "pink_tulip": (240, 160, 190),
}


def base_name(b):
    return b.split("[")[0].replace("minecraft:", "")


def color_of(b):
    return COLORS.get(base_name(b), (255, 0, 255))


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def render_iso(path, scale=4):
    from PIL import Image, ImageDraw
    solid = {p for p, b in W.items()}
    vis = []
    for (x, y, z), b in W.items():
        if (x, y + 1, z) not in solid or (x + 1, y, z) not in solid or (x, y, z + 1) not in solid:
            vis.append((x, y, z, b))
    vis.sort(key=lambda t: (t[0] + t[2], t[1]))
    pts = [((x - z) * 2, (x + z) - y * 2) for x, y, z, _ in vis]
    minx = min(p[0] for p in pts) - 4
    miny = min(p[1] for p in pts) - 6
    maxx = max(p[0] for p in pts) + 4
    maxy = max(p[1] for p in pts) + 6
    img = Image.new("RGB", ((maxx - minx) * scale, (maxy - miny) * scale), (150, 200, 245))
    d = ImageDraw.Draw(img)
    for (x, y, z, b), (sx, sy) in zip(vis, pts):
        c = color_of(b)
        ox, oy = (sx - minx) * scale, (sy - miny) * scale
        s = scale
        top = [(ox, oy - s), (ox + 2 * s, oy), (ox, oy + s), (ox - 2 * s, oy)]
        left = [(ox - 2 * s, oy), (ox, oy + s), (ox, oy + 3 * s), (ox - 2 * s, oy + 2 * s)]
        right = [(ox + 2 * s, oy), (ox, oy + s), (ox, oy + 3 * s), (ox + 2 * s, oy + 2 * s)]
        d.polygon(left, fill=shade(c, 0.72))
        d.polygon(right, fill=shade(c, 0.86))
        d.polygon(top, fill=c)
    img.save(path)


def render_map(path, scale=8):
    from PIL import Image, ImageDraw, ImageFont
    top = {}
    for (x, y, z), b in W.items():
        if (x, z) not in top or y > top[(x, z)][0]:
            top[(x, z)] = (y, b)
    x0, z0 = BOUNDS[0], BOUNDS[2]
    wid, hei = (BOUNDS[3] - x0 + 1) * scale, (BOUNDS[5] - z0 + 1) * scale
    img = Image.new("RGB", (wid, hei + 40), (150, 200, 245))
    d = ImageDraw.Draw(img)
    for (x, z), (y, b) in top.items():
        c = shade(color_of(b), 0.85 + 0.03 * max(-5, min(5, y)))
        d.rectangle([(x - x0) * scale, (z - z0) * scale, (x - x0 + 1) * scale - 1, (z - z0 + 1) * scale - 1], fill=c)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 22)
        small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 16)
    except OSError:
        font = small = ImageFont.load_default()

    def label(text, x, z, color=(255, 255, 255)):
        px, pz = (x - x0) * scale, (z - z0) * scale
        tw = d.textlength(text, font=font)
        d.rounded_rectangle([px - tw / 2 - 8, pz - 16, px + tw / 2 + 8, pz + 16], 8, fill=(20, 30, 60))
        d.text((px - tw / 2, pz - 13), text, font=font, fill=color)

    label("SPAWN", 0, 4)
    label("VAHŞİ DOĞA PORTALI", 0, PZ - 4, (150, 255, 150))
    label("MARKET", (MX0 + MX1) // 2, 0, (255, 230, 120))
    label("ARENA + KOTH", 0, AZ, (255, 140, 140))
    label("KASALAR", KX, -8, (230, 160, 255))
    label("ATLAMA", JUMP_END[0], JUMP_END[2] + 4, (150, 220, 255))
    label("SIRALAMA", -20, -17, (255, 215, 90))
    label("SIRALAMA", 21, -17, (255, 215, 90))
    d.text((10, hei + 8), "Sky Survival spawn adası — üstten görünüm (1 kare = 1 blok)", font=small, fill=(20, 30, 60))
    img.save(path)


# --- WorldEdit sematigi (Sponge Schematic v2, NBT) ---
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


def write_schem(path):
    x0, y0, z0, x1, y1, z1 = BOUNDS
    width, height, length = x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1
    palette = {"minecraft:air": 0}
    for b in sorted(set(W.values())):
        palette.setdefault(b, len(palette))
    data = bytearray()
    for y in range(height):
        for z in range(length):
            for x in range(width):
                data += varint(palette[W.get((x + x0, y + y0, z + z0), "minecraft:air")])
    pal = b"".join(nbt_named(3, k, struct.pack(">i", v)) for k, v in palette.items()) + b"\x00"
    body = b"".join([
        nbt_named(3, "Version", struct.pack(">i", 2)),
        nbt_named(3, "DataVersion", struct.pack(">i", 4189)),  # 1.21.4; WorldEdit yeni surume kendisi cevirir
        nbt_named(2, "Width", struct.pack(">h", width)),
        nbt_named(2, "Height", struct.pack(">h", height)),
        nbt_named(2, "Length", struct.pack(">h", length)),
        nbt_named(3, "PaletteMax", struct.pack(">i", len(palette))),
        nbt_named(10, "Palette", pal),
        nbt_named(7, "BlockData", struct.pack(">i", len(data)) + bytes(data)),
        nbt_named(11, "Offset", struct.pack(">i", 3) + struct.pack(">iii", x0, y0, z0)),
        nbt_named(10, "Metadata", nbt_named(3, "WEOffsetX", struct.pack(">i", x0))
                  + nbt_named(3, "WEOffsetY", struct.pack(">i", y0))
                  + nbt_named(3, "WEOffsetZ", struct.pack(">i", z0)) + b"\x00"),
    ]) + b"\x00"
    raw = nbt_named(10, "Schematic", body)
    with open(path, "wb") as out, gzip.GzipFile(fileobj=out, mode="wb", mtime=0) as f:
        f.write(raw)
    return width, height, length


def main():
    RES.mkdir(parents=True, exist_ok=True)
    BRANDING.mkdir(exist_ok=True)
    pal, runs = write_blueprint(RES / "ada.bp.gz")
    (RES / "ada.yml").write_text(MARKERS, encoding="utf-8")
    render_iso(BRANDING / "spawn-adasi-onizleme.png")
    render_map(BRANDING / "spawn-adasi-harita.png")
    size = write_schem(BRANDING / "SkySpawn.schem")
    print(f"blok: {len(W)}, palet: {pal}, satir: {runs}, sinirlar: {BOUNDS}, schem: {size}")


if __name__ == "__main__":
    main()
