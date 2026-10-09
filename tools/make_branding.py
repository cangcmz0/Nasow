"""Sky Survival logo, sunucu ikonu ve bannerlarini piksel-sanat olarak cizer.

Kullanim:  python3 tools/make_branding.py
Cikti:     branding/*.png ve server/server-icon.png (Minecraft sunucu listesindeki ikon, 64x64)
"""
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "branding"

# ---------------------------------------------------------------------------
# 5x7 piksel yazi tipi (buyuk harf, Turkce karakterler dahil)
# ---------------------------------------------------------------------------
FONT = {
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "B": ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
    "C": ["01110", "10001", "10000", "10000", "10000", "10001", "01110"],
    "Ç": ["01110", "10001", "10000", "10000", "10001", "01110", "00100"],
    "D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "F": ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
    "G": ["01110", "10001", "10000", "10111", "10001", "10001", "01111"],
    "Ğ": ["01110", "00000", "01111", "10000", "10111", "10001", "01111"],
    "H": ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
    "I": ["01110", "00100", "00100", "00100", "00100", "00100", "01110"],
    "İ": ["00100", "00000", "01110", "00100", "00100", "00100", "01110"],
    "J": ["00111", "00010", "00010", "00010", "00010", "10010", "01100"],
    "K": ["10001", "10010", "10100", "11000", "10100", "10010", "10001"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "M": ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "Ö": ["01010", "00000", "01110", "10001", "10001", "10001", "01110"],
    "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "Ş": ["01111", "10000", "01110", "00001", "11110", "00100", "01000"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "U": ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    "Ü": ["01010", "00000", "10001", "10001", "10001", "10001", "01110"],
    "V": ["10001", "10001", "10001", "10001", "10001", "01010", "00100"],
    "Y": ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
    "Z": ["11111", "00001", "00010", "00100", "01000", "10000", "11111"],
    "0": ["01110", "10001", "10011", "10101", "11001", "10001", "01110"],
    "1": ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
    "2": ["01110", "10001", "00001", "00010", "00100", "01000", "11111"],
    "6": ["00110", "01000", "10000", "11110", "10001", "10001", "01110"],
    "8": ["01110", "10001", "10001", "01110", "10001", "10001", "01110"],
    ".": ["00000", "00000", "00000", "00000", "00000", "01100", "01100"],
    "-": ["00000", "00000", "00000", "01110", "00000", "00000", "00000"],
    "&": ["01100", "10010", "10100", "01000", "10101", "10010", "01101"],
    "•": ["00000", "00000", "01110", "01110", "01110", "00000", "00000"],
    " ": ["000", "000", "000", "000", "000", "000", "000"],
}


def text_width(text, scale):
    return sum((len(FONT[c][0]) + 1) * scale for c in text) - scale


def draw_text(px, text, x, y, scale, color, outline=(20, 30, 60), shadow=True):
    """Yazinin altina koyu golge ve etrafina kontur cizer."""
    w, h = len(px[0]), len(px)
    assert x + text_width(text, scale) < w, f"yazi tasiyor: {text}"

    def put(cx, cy, col):
        if 0 <= cx < w and 0 <= cy < h:
            px[cy][cx] = col

    def glyph_cells(ox, oy):
        cells = []
        cursor = ox
        for ch in text:
            g = FONT[ch]
            for gy, row in enumerate(g):
                for gx, bit in enumerate(row):
                    if bit == "1":
                        for sy in range(scale):
                            for sx in range(scale):
                                cells.append((cursor + gx * scale + sx, oy + gy * scale + sy))
            cursor += (len(g[0]) + 1) * scale
        return cells

    cells = glyph_cells(x, y)
    if shadow:
        for cx, cy in cells:
            put(cx + max(1, scale // 2), cy + max(1, scale // 2), (12, 18, 40))
    for cx, cy in cells:
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, 1), (-1, 1), (1, -1)):
            put(cx + dx, cy + dy, outline)
    for cx, cy in cells:
        # ust yari acik, alt yari koyu: Minecraft logo hissi
        top = cy < y + 3.5 * scale
        put(cx, cy, color[0] if top else color[1])


# ---------------------------------------------------------------------------
# Sahne: gokyuzu, gunes, bulutlar, ucan ada, agac, selale
# ---------------------------------------------------------------------------
SKY_TOP = (38, 104, 214)
SKY_BOTTOM = (150, 214, 255)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def new_canvas(w, h, bands=10):
    px = []
    for y in range(h):
        t = min(bands - 1, int(y / h * bands)) / (bands - 1)
        px.append([lerp(SKY_TOP, SKY_BOTTOM, t)] * w)
        px[-1] = list(px[-1])
    return px


def rect(px, x0, y0, x1, y1, col):
    for y in range(max(0, y0), min(len(px), y1 + 1)):
        for x in range(max(0, x0), min(len(px[0]), x1 + 1)):
            px[y][x] = col


def cloud(px, x, y, size, rng):
    white, shade = (250, 252, 255), (214, 228, 245)
    parts = [(0, 2, size * 3, size + 2), (size // 2, 0, size * 2, size + 1), (size * 2, 1, size * 3 + size // 2, size + 2)]
    for ox, oy, ex, ey in parts:
        rect(px, x + ox, y + oy, x + ex, y + ey, white)
    rect(px, x, y + size + 2, x + size * 3 + size // 2, y + size + 2, shade)


def sun(px, x, y, s):
    rect(px, x, y, x + s - 1, y + s - 1, (255, 214, 64))
    rect(px, x + 1, y + 1, x + s - 2, y + s - 2, (255, 238, 140))


def island(px, cx, top, half_w, depth, rng, tree=True, waterfall=True, house=False):
    grass_hi, grass, grass_dk = (124, 214, 82), (92, 184, 58), (66, 146, 42)
    dirt, dirt_dk = (139, 94, 52), (110, 72, 38)
    stone, stone_dk, stone_lt = (125, 125, 130), (92, 92, 98), (150, 150, 156)
    ores = [(64, 214, 214), (230, 180, 40), (220, 70, 70), (60, 180, 90)]
    w, h = len(px[0]), len(px)
    # Ust yuzey ve alt koni
    for dy in range(depth + 6):
        y = top + dy
        if y >= h:
            break
        if dy < 6:
            hw = half_w - (1 if dy == 0 else 0)
        else:
            k = (dy - 6) / max(1, depth)
            hw = int(half_w * (1 - k) ** 1.25 + rng.choice([-1, 0, 0, 1]))
        for x in range(cx - hw, cx + hw + 1):
            if not 0 <= x < w:
                continue
            if dy == 0:
                col = grass_hi
            elif dy == 1:
                col = grass if rng.random() > 0.15 else grass_hi
            elif dy == 2:
                col = grass_dk if rng.random() > 0.4 else dirt
            elif dy < 6:
                col = dirt if rng.random() > 0.25 else dirt_dk
            else:
                r = rng.random()
                col = stone if r > 0.35 else (stone_dk if r > 0.12 else stone_lt)
                if rng.random() < 0.012:
                    col = rng.choice(ores)
            px[y][x] = col
    # Kenardan sarkan sarmasiklar
    for x in range(cx - half_w + 2, cx + half_w - 1, 4):
        if rng.random() < 0.5:
            for vy in range(2, 2 + rng.randint(2, 5)):
                if top + vy < h and px[top + vy][x] in (SKY_TOP,):
                    pass
            ln = rng.randint(2, 4)
            for vy in range(ln):
                yy = top + 6 + vy
                xx = x
                if 0 <= yy < h and 0 <= xx < w:
                    # sadece adanin hemen disina
                    pass
    if waterfall:
        wx = cx + half_w - 3
        for y in range(top + 1, h):
            for x in (wx, wx + 1):
                if 0 <= x < w and (y > top + 5 or x == wx):
                    px[y][x] = (90, 160, 255) if (y + x) % 3 else (200, 230, 255)
        rect(px, wx - 1, top, wx + 1, top, (70, 140, 240))
    if house:
        hx = cx + half_w // 3
        rect(px, hx - 4, top - 6, hx + 4, top - 1, (176, 132, 80))
        rect(px, hx - 1, top - 3, hx + 1, top - 1, (94, 62, 30))
        rect(px, hx - 3, top - 5, hx - 2, top - 4, (180, 220, 255))
        rect(px, hx + 2, top - 5, hx + 3, top - 4, (180, 220, 255))
        for i in range(6):
            rect(px, hx - 5 + i, top - 7 - i, hx + 5 - i, top - 7 - i, (150, 60, 50) if i % 2 == 0 else (130, 50, 42))
    if tree:
        tx = cx - half_w // 3
        trunk, trunk_dk = (110, 76, 44), (86, 58, 32)
        th = max(5, half_w // 3)
        for y in range(top - th, top):
            px[y][tx] = trunk
            px[y][tx + 1] = trunk_dk
        leaf = [(52, 150, 44), (70, 176, 56), (40, 120, 36)]
        lr = max(4, half_w // 4)
        ly = top - th - lr // 2
        for y in range(ly - lr, ly + lr):
            for x in range(tx - lr, tx + lr + 2):
                d = ((x - tx - 0.5) / (lr + 0.5)) ** 2 + ((y - ly) / lr) ** 2
                if d <= 1.0 and 0 <= y < h and 0 <= x < w:
                    px[y][x] = leaf[0] if rng.random() > 0.3 else rng.choice(leaf)
        # elma
        for _ in range(max(1, lr // 2)):
            ax, ay = tx + rng.randint(-lr + 1, lr), ly + rng.randint(-lr + 1, lr - 1)
            if 0 <= ay < h and 0 <= ax < w:
                px[ay][ax] = (220, 50, 50)


def to_image(px, scale):
    h, w = len(px), len(px[0])
    img = Image.new("RGB", (w, h))
    img.putdata([c for row in px for c in row])
    return img.resize((w * scale, h * scale), Image.NEAREST)


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    from PIL import ImageDraw
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.size[0] - 1, img.size[1] - 1], radius, fill=255)
    out = Image.new("RGBA", img.size)
    out.paste(img, (0, 0), mask)
    return out


GOLD = ((255, 230, 120), (240, 170, 40))
WHITE = ((255, 255, 255), (205, 225, 245))


def make_icon():
    rng = random.Random(7)
    px = new_canvas(64, 64, bands=8)
    sun(px, 48, 6, 8)
    cloud(px, 4, 8, 3, rng)
    cloud(px, 30, 3, 2, rng)
    island(px, 30, 34, 22, 22, rng, tree=True, waterfall=True, house=True)
    return to_image(px, 1)


def make_logo():
    rng = random.Random(11)
    px = new_canvas(128, 128, bands=12)
    sun(px, 108, 34, 10)
    cloud(px, 6, 34, 4, rng)
    cloud(px, 92, 44, 3, rng)
    island(px, 62, 66, 40, 34, rng, tree=True, waterfall=True, house=True)
    draw_text(px, "SKY", (128 - text_width("SKY", 4)) // 2, 6, 4, GOLD)
    draw_text(px, "SURVIVAL", (128 - text_width("SURVIVAL", 2)) // 2, 108, 2, WHITE)
    return rounded(to_image(px, 4), 48)


def make_banner_small():
    """Sunucu listesi siteleri icin klasik 468x60 banner."""
    rng = random.Random(3)
    px = new_canvas(234, 30, bands=5)
    cloud(px, 150, 2, 2, rng)
    cloud(px, 205, 6, 2, rng)
    island(px, 20, 17, 15, 12, rng, tree=True, waterfall=False, house=False)
    draw_text(px, "SKY SURVIVAL", 44, 3, 2, GOLD)
    draw_text(px, "MESLEK • KLAN • EKONOMİ • CRACK", 44, 21, 1, WHITE, shadow=False)
    return to_image(px, 2)


def make_banner_large():
    """Discord / site icin 1500x500 banner."""
    rng = random.Random(5)
    px = new_canvas(300, 100, bands=10)
    sun(px, 270, 8, 12)
    cloud(px, 140, 6, 4, rng)
    cloud(px, 230, 30, 3, rng)
    cloud(px, 10, 10, 3, rng)
    island(px, 58, 52, 40, 34, rng, tree=True, waterfall=True, house=True)
    title_x = 118
    draw_text(px, "SKY", title_x, 18, 4, GOLD)
    draw_text(px, "SURVIVAL", title_x, 52, 2, WHITE)
    draw_text(px, "MESLEK • KLAN • EKONOMİ", title_x, 74, 1, WHITE, shadow=False)
    draw_text(px, "JAVA & BEDROCK • 1.8 - 26.1", title_x, 84, 1, ((180, 240, 255), (150, 210, 240)), shadow=False)
    return to_image(px, 5)


def main():
    OUT.mkdir(exist_ok=True)
    icon = make_icon()
    icon.save(OUT / "server-icon.png")
    icon.save(ROOT / "server" / "server-icon.png")
    icon.resize((256, 256), Image.NEAREST).save(OUT / "server-icon-buyuk.png")
    make_logo().save(OUT / "logo-512.png")
    make_banner_small().save(OUT / "banner-468x60.png")
    make_banner_large().save(OUT / "banner-1500x500.png")
    print("branding hazir:", sorted(p.name for p in OUT.glob("*.png")))


if __name__ == "__main__":
    main()
