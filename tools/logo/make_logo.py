#!/usr/bin/env python3
"""Sky Survival logosu ve marka gorselleri (vektor, SVG -> PNG).

Ravun logosunun stilinden esinlenildi: kalin yuvarlak harfler (Nunito Black), kalin beyaz "cikartma"
dis cercevesi, koyu kontur, yumusak golge ve altta bant. Amblem: gokyuzunde yuzen ada (kiraz agaci,
selale, bulutlar) - sunucunun spawn adasini anlatir.

Kullanim (depo kokunden):
    pip install fonttools          # istege bagli: yazi tipini SVG'ye kucultup gomer
    python3 tools/logo/make_logo.py

PNG'ler icin Node + Playwright (Chromium) gerekir: tools/logo/render.js.
Cikti: branding/ klasoru ve server/server-icon.png
"""
import base64
import io
import json
import math
import os
import random
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
OUT = ROOT / "branding"
FONT = HERE / "fonts" / "Nunito.ttf"

# Renkler
NAVY = "#12335e"
NAVY_DARK = "#0b2545"
WHITE = "#ffffff"
GOLD = "#ffd84d"

TAGLINE = "MESLEK • KLAN • EKONOMİ • KOTH"
SUBLINE = "Java & Bedrock  •  1.8 – 26.x  •  Crack & Premium"
SUBLINE_XML = SUBLINE.replace("&", "&amp;")
ALL_TEXT = "SKY SURVIVAL" + TAGLINE + SUBLINE + "0123456789.:-–& •SUNUCU ADRESİ"


# ---------------------------------------------------------------------------
# Yazi tipi (SVG'ye gomulur; fontTools varsa sadece kullanilan harfler)
# ---------------------------------------------------------------------------
def font_css():
    data = FONT.read_bytes()
    try:
        from fontTools import subset
        from fontTools.ttLib import TTFont

        font = TTFont(io.BytesIO(data))
        options = subset.Options()
        options.layout_features = ["kern", "liga", "calt", "ccmp", "locl", "mark", "mkmk"]
        subsetter = subset.Subsetter(options)
        subsetter.populate(text=ALL_TEXT + ALL_TEXT.lower())
        subsetter.subset(font)
        buffer = io.BytesIO()
        font.save(buffer)
        data = buffer.getvalue()
    except ImportError:
        print("  (fontTools yok, yazi tipi tamami gomuluyor)")
    encoded = base64.b64encode(data).decode("ascii")
    return ("@font-face{font-family:'SkyNunito';font-weight:200 1000;"
            f"src:url(data:font/ttf;base64,{encoded}) format('truetype');}}")


# ---------------------------------------------------------------------------
# Ortak tanimlar: gradyanlar ve filtreler
# ---------------------------------------------------------------------------
def defs():
    return f"""
  <style>{font_css()}</style>
  <linearGradient id="grass" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#b4ef6a"/><stop offset="1" stop-color="#5dbb34"/>
  </linearGradient>
  <linearGradient id="grassSide" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#4fa832"/><stop offset="1" stop-color="#3a8526"/>
  </linearGradient>
  <linearGradient id="dirt" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#a26b37"/><stop offset="1" stop-color="#6b4321"/>
  </linearGradient>
  <linearGradient id="stone" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#8d949e"/><stop offset="1" stop-color="#555c66"/>
  </linearGradient>
  <linearGradient id="water" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#8fdcff"/><stop offset="0.75" stop-color="#4fb6ff"/>
    <stop offset="1" stop-color="#4fb6ff" stop-opacity="0"/>
  </linearGradient>
  <radialGradient id="sun" cx="0.5" cy="0.5" r="0.5">
    <stop offset="0" stop-color="#fffbe0"/><stop offset="0.55" stop-color="#ffe066"/><stop offset="1" stop-color="#ffc233"/>
  </radialGradient>
  <radialGradient id="sunGlow" cx="0.5" cy="0.5" r="0.5">
    <stop offset="0" stop-color="#fff3b0" stop-opacity="0.85"/><stop offset="1" stop-color="#fff3b0" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="skyBg" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#3f9df2"/><stop offset="0.6" stop-color="#7fc6ff"/><stop offset="1" stop-color="#cdeeff"/>
  </linearGradient>
  <linearGradient id="skyText" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#ffffff"/><stop offset="0.45" stop-color="#c4ecff"/><stop offset="1" stop-color="#46acf7"/>
  </linearGradient>
  <linearGradient id="goldText" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#fff6c4"/><stop offset="0.45" stop-color="#ffd84d"/><stop offset="1" stop-color="#ff9d1a"/>
  </linearGradient>
  <filter id="sticker" x="-25%" y="-25%" width="150%" height="150%" color-interpolation-filters="sRGB">
    <!-- Lacivert kontur: her parcanin etrafinda ince -->
    <feMorphology in="SourceAlpha" operator="dilate" radius="4" result="d1"/>
    <feGaussianBlur in="d1" stdDeviation="2.5" result="b1"/>
    <feComponentTransfer in="b1" result="m1"><feFuncA type="linear" slope="6" intercept="-2.5"/></feComponentTransfer>
    <feFlood flood-color="{NAVY}"/><feComposite in2="m1" operator="in" result="navy"/>
    <!-- Beyaz cikartma: once girintiler doldurulur (kapama), sonra genisletilip yumusatilir -->
    <feMorphology in="SourceAlpha" operator="dilate" radius="36" result="c1"/>
    <feMorphology in="c1" operator="erode" radius="34" result="closed"/>
    <feMorphology in="closed" operator="dilate" radius="12" result="d2"/>
    <feGaussianBlur in="d2" stdDeviation="6" result="b2"/>
    <feComponentTransfer in="b2" result="m2"><feFuncA type="linear" slope="6" intercept="-2.5"/></feComponentTransfer>
    <feFlood flood-color="{WHITE}"/><feComposite in2="m2" operator="in" result="white"/>
    <feGaussianBlur in="m2" stdDeviation="9" result="s"/><feOffset in="s" dy="12" result="so"/>
    <feFlood flood-color="{NAVY_DARK}" flood-opacity="0.38"/><feComposite in2="so" operator="in" result="shadow"/>
    <feMerge><feMergeNode in="shadow"/><feMergeNode in="white"/><feMergeNode in="navy"/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
  <filter id="softShadow" x="-20%" y="-20%" width="140%" height="160%">
    <feGaussianBlur in="SourceAlpha" stdDeviation="8"/><feOffset dy="12" result="o"/>
    <feFlood flood-color="{NAVY_DARK}" flood-opacity="0.38"/><feComposite in2="o" operator="in"/>
    <feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
"""


# ---------------------------------------------------------------------------
# Amblem: yuzen ada (600x600 koordinat)
# ---------------------------------------------------------------------------
def cloud(x, y, s=1.0, shade=True):
    parts = []
    if shade:
        parts.append(f'<ellipse cx="{x + 30 * s}" cy="{y + 26 * s}" rx="{92 * s}" ry="{14 * s}" fill="#cfe7ff"/>')
    for cx, cy, r in ((0, 0, 34), (40, -22, 44), (88, -6, 36), (118, 10, 24), (-30, 12, 24)):
        parts.append(f'<circle cx="{x + cx * s}" cy="{y + cy * s}" r="{r * s}" fill="#ffffff"/>')
    parts.append(f'<rect x="{x - 30 * s}" y="{y + 4 * s}" width="{150 * s}" height="{30 * s}" rx="{15 * s}" fill="#ffffff"/>')
    parts.append(f'<ellipse cx="{x + 44 * s}" cy="{y + 28 * s}" rx="{70 * s}" ry="{7 * s}" fill="#e3f1ff"/>')
    return "\n".join(parts)


def emblem(detail=True):
    rng = random.Random(7)
    body = [(92, 318), (508, 318), (500, 352), (470, 352), (470, 392), (436, 392), (436, 432), (398, 432),
            (398, 472), (362, 472), (362, 512), (330, 512), (330, 548), (306, 548), (306, 580), (290, 580),
            (290, 548), (262, 548), (262, 516), (228, 516), (228, 476), (192, 476), (192, 436), (158, 436),
            (158, 396), (126, 396), (126, 356), (98, 356)]
    body_path = "M" + " L".join(f"{x},{y}" for x, y in body) + " Z"
    g = ['<clipPath id="bodyClip"><path d="' + body_path + '"/></clipPath>']
    g.append('<g id="island">')
    # Govde: toprak, alti tas
    g.append(f'<path d="{body_path}" fill="url(#dirt)"/>')
    g.append('<g clip-path="url(#bodyClip)">')
    g.append('<path d="M60,452 L130,436 L200,446 L280,430 L360,444 L440,428 L540,440 L540,620 L60,620 Z" fill="url(#stone)"/>')
    g.append('<path d="M300,300 L560,300 L560,620 L300,620 Z" fill="#000000" opacity="0.13"/>')
    g.append('<path d="M60,300 L150,300 L150,620 L60,620 Z" fill="#ffffff" opacity="0.06"/>')
    if detail:
        for _ in range(70):
            x = rng.randrange(90, 510, 20)
            y = rng.randrange(330, 580, 20)
            in_stone = y > 450
            color = rng.choice(["#7c8590", "#a3aab3", "#6b737d"] if in_stone else ["#8a5a2c", "#b57d45", "#7a4c24"])
            g.append(f'<rect x="{x}" y="{y}" width="20" height="20" fill="{color}" opacity="0.75"/>')
        for x, y, c in ((300, 500, "#5ee6dc"), (246, 470, "#ffd84d"), (336, 458, "#5ee6dc"), (214, 452, "#ff6b6b")):
            g.append(f'<rect x="{x}" y="{y}" width="14" height="14" rx="3" fill="{c}"/>'
                     f'<rect x="{x + 3}" y="{y + 3}" width="5" height="5" fill="#ffffff" opacity="0.8"/>')
    g.append('</g>')
    # Cimen kenari (yan) ve sarkan cimenler
    g.append('<path d="M90,300 A210,70 0 0 0 510,300 L510,326 A210,70 0 0 1 90,326 Z" fill="url(#grassSide)"/>')
    for x, h in ((110, 16), (150, 26), (190, 14), (238, 30), (286, 18), (330, 28), (380, 14), (426, 24), (470, 16)):
        y = 300 + 70 * math.sqrt(max(0.0, 1 - ((x + 8 - 300) / 210) ** 2)) + 18
        g.append(f'<rect x="{x}" y="{y - 4:.1f}" width="18" height="{h}" rx="6" fill="#3a8526"/>')
    # Ust yuzey
    g.append('<ellipse cx="300" cy="300" rx="210" ry="70" fill="url(#grass)"/>')
    g.append('<ellipse cx="270" cy="284" rx="150" ry="38" fill="#c6f58a" opacity="0.45"/>')
    # Su birikintisi ve selale
    g.append('<ellipse cx="430" cy="312" rx="44" ry="13" fill="#3fa2ea"/><ellipse cx="426" cy="309" rx="32" ry="8" fill="#8fdcff"/>')
    g.append('<rect x="452" y="318" width="26" height="170" rx="10" fill="url(#water)"/>')
    g.append('<rect x="458" y="330" width="6" height="110" rx="3" fill="#ffffff" opacity="0.7"/>')
    # Cicekler
    if detail:
        for x, y, c in ((150, 300, "#ffffff"), (176, 322, "#ffd84d"), (330, 330, "#ff6b8a"), (360, 314, "#ffffff"),
                        (210, 336, "#ff6b8a"), (390, 336, "#ffd84d"), (130, 318, "#ff9fd0"), (300, 344, "#ffffff")):
            g.append(f'<circle cx="{x}" cy="{y}" r="6" fill="{c}"/><circle cx="{x}" cy="{y}" r="2.4" fill="#ffb000"/>')
    # Mese agaci (sag)
    g.append('<rect x="366" y="216" width="18" height="78" rx="6" fill="#6e4322"/><rect x="366" y="216" width="7" height="78" rx="3" fill="#8b5a2c"/>')
    g.append('<rect x="326" y="168" width="98" height="70" rx="22" fill="#3f9a2b"/>')
    g.append('<rect x="342" y="146" width="66" height="44" rx="18" fill="#5cbf2a"/>')
    g.append('<rect x="352" y="154" width="22" height="16" rx="6" fill="#8fe05a"/>')
    # Kiraz agaci (sol, buyuk)
    g.append('<rect x="226" y="196" width="26" height="110" rx="8" fill="#6e4322"/><rect x="226" y="196" width="10" height="110" rx="4" fill="#8b5a2c"/>')
    g.append('<rect x="150" y="128" width="178" height="104" rx="34" fill="#e85fa6"/>')
    g.append('<rect x="162" y="112" width="152" height="96" rx="32" fill="#ff8cc6"/>')
    g.append('<rect x="190" y="82" width="98" height="62" rx="26" fill="#ffa6d4"/>')
    g.append('<rect x="132" y="160" width="56" height="50" rx="18" fill="#f272b6"/>')
    g.append('<rect x="296" y="164" width="50" height="46" rx="16" fill="#f272b6"/>')
    if detail:
        for x, y, w in ((206, 96, 26), (176, 132, 20), (250, 124, 22), (284, 150, 16), (150, 176, 16), (222, 168, 18)):
            g.append(f'<rect x="{x}" y="{y}" width="{w}" height="{w * 0.7:.0f}" rx="6" fill="#ffd0e8"/>')
        for x, y, a in ((120, 250, 20), (330, 262, -25), (176, 274, 40), (300, 236, 10)):
            g.append(f'<rect x="{x}" y="{y}" width="10" height="7" rx="2" fill="#ff8cc6" transform="rotate({a} {x} {y})"/>')
    g.append('</g>')
    # Bulutlar (adanin onunde)
    g.append(cloud(70, 520, 1.0))
    g.append(cloud(410, 556, 0.82))
    return "\n".join(g)


def sun(x, y, r):
    return (f'<circle cx="{x}" cy="{y}" r="{r * 1.9}" fill="url(#sunGlow)"/>'
            f'<circle cx="{x}" cy="{y}" r="{r}" fill="url(#sun)"/>')


# ---------------------------------------------------------------------------
# Yazi: Ravun tarzi "cikartma" (beyaz dis cerceve + lacivert kontur + derinlik + gradyan)
# ---------------------------------------------------------------------------
def wordmark(cx, baseline, size, spacing=4, depth=None, outline=None, white=None):
    depth = size * 0.055 if depth is None else depth
    outline = size * 0.085 if outline is None else outline
    white = size * 0.22 if white is None else white
    attrs = (f'x="{cx}" text-anchor="middle" font-family="SkyNunito, Nunito, sans-serif" font-weight="1000" '
             f'font-size="{size}" letter-spacing="{spacing}" stroke-linejoin="round" stroke-linecap="round"')
    top = baseline - size * 0.72
    words = '<tspan fill="url(#skyTextW)">SKY</tspan> <tspan fill="url(#goldTextW)">SURVIVAL</tspan>'
    plain = "SKY SURVIVAL"
    return f"""
  <linearGradient id="skyTextW" gradientUnits="userSpaceOnUse" x1="0" y1="{top}" x2="0" y2="{baseline}">
    <stop offset="0" stop-color="#ffffff"/><stop offset="0.42" stop-color="#c4ecff"/><stop offset="1" stop-color="#3ea4f5"/>
  </linearGradient>
  <linearGradient id="goldTextW" gradientUnits="userSpaceOnUse" x1="0" y1="{top}" x2="0" y2="{baseline}">
    <stop offset="0" stop-color="#fff6c4"/><stop offset="0.42" stop-color="#ffd84d"/><stop offset="1" stop-color="#ff9a12"/>
  </linearGradient>
  <g filter="url(#softShadow)">
    <text {attrs} y="{baseline + depth}" fill="{WHITE}" stroke="{WHITE}" stroke-width="{white}">{plain}</text>
    <text {attrs} y="{baseline}" fill="{WHITE}" stroke="{WHITE}" stroke-width="{white}">{plain}</text>
  </g>
  <text {attrs} y="{baseline + depth}" fill="{NAVY}" stroke="{NAVY}" stroke-width="{outline}">{plain}</text>
  <text {attrs} y="{baseline}" fill="{NAVY}" stroke="{NAVY}" stroke-width="{outline}">{plain}</text>
  <text {attrs} y="{baseline}">{words}</text>
"""


def tagline(cx, cy, width, height, size, text=TAGLINE):
    return f"""
  <g filter="url(#softShadow)">
    <rect x="{cx - width / 2}" y="{cy - height / 2}" width="{width}" height="{height}" rx="{height / 2}"
          fill="{NAVY}" stroke="{WHITE}" stroke-width="{height * 0.16}"/>
  </g>
  <text x="{cx}" y="{cy + size * 0.36}" text-anchor="middle" font-family="SkyNunito, sans-serif" font-weight="900"
        font-size="{size}" letter-spacing="{size * 0.12}" fill="{GOLD}">{text}</text>
"""


def svg(width, height, body, background=""):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">'
            f"<defs>{defs()}</defs>{background}{body}</svg>")


def sky_background(width, height, rx=0, clouds=True, seed=3):
    rng = random.Random(seed)
    parts = [f'<rect width="{width}" height="{height}" rx="{rx}" fill="url(#skyBg)"/>']
    if clouds:
        parts.append(f'<clipPath id="bgClip"><rect width="{width}" height="{height}" rx="{rx}"/></clipPath><g clip-path="url(#bgClip)" opacity="0.55">')
        for _ in range(max(3, width // 260)):
            x = rng.uniform(-60, width - 60)
            y = rng.uniform(height * 0.15, height * 0.95)
            parts.append(cloud(x, y, rng.uniform(0.5, 1.1) * height / 500, shade=False))
        parts.append("</g>")
    return "\n".join(parts)


# ---------------------------------------------------------------------------
# Kompozisyonlar
# ---------------------------------------------------------------------------
def logo_full():
    """Seffaf ana logo: ustte amblem, altta yazi ve bant."""
    w, h = 1800, 1150
    body = (f'<g transform="translate({w / 2 - 300} 30)">{sun(470, 120, 56)}<g filter="url(#sticker)">{emblem()}</g></g>'
            + wordmark(w / 2, 840, 196)
            + tagline(w / 2, 975, 900, 78, 38))
    return svg(w, h, body)


def emblem_only(size=600, background=True, detail=True, zoom=False):
    s = size / 600
    bg = ""
    if background:
        bg = (f'<rect width="600" height="600" rx="120" fill="url(#skyBg)"/>'
              f'<g opacity="0.6">{cloud(60, 200, 0.6, False)}{cloud(430, 300, 0.5, False)}</g>')
    place = "translate(4 -6) scale(0.98)" if zoom else "translate(46 26) scale(0.84)"
    inner = f'{bg}{sun(468, 118, 50)}<g transform="{place}" filter="url(#sticker)">{emblem(detail)}</g>'
    if background:
        inner = f'<clipPath id="iconClip"><rect width="600" height="600" rx="120"/></clipPath><g clip-path="url(#iconClip)">{inner}</g>'
    return svg(size, size, f'<g transform="scale({s})">{inner}</g>')


def banner_wide():
    w, h = 1500, 500
    body = (f'<g transform="translate(40 52) scale(0.66)">{sun(470, 120, 56)}<g filter="url(#sticker)">{emblem()}</g></g>'
            + wordmark(990, 252, 112)
            + tagline(990, 340, 690, 60, 28)
            + f'<text x="990" y="430" text-anchor="middle" font-family="SkyNunito, sans-serif" font-weight="800" font-size="30" '
              f'fill="{NAVY}" letter-spacing="2">{SUBLINE_XML}</text>')
    return svg(w, h, body, sky_background(w, h))


def banner_small():
    w, h = 468, 60
    body = (f'<g transform="translate(4 1) scale(0.095)"><g filter="url(#sticker)">{emblem(False)}</g></g>'
            + wordmark(205, 43, 33, spacing=1, depth=2, outline=3.2, white=7.5)
            + f'<text x="410" y="27" text-anchor="middle" font-family="SkyNunito, sans-serif" font-weight="900" font-size="11" fill="{NAVY}">MESLEK • KLAN</text>'
            + f'<text x="410" y="43" text-anchor="middle" font-family="SkyNunito, sans-serif" font-weight="900" font-size="11" fill="{NAVY}">EKONOMİ • KOTH</text>')
    return svg(w, h, body, sky_background(w, h, clouds=False))


def promo_card():
    w, h = 1200, 630
    body = (f'<g transform="translate({w / 2 - 183} 12) scale(0.61)">{sun(470, 120, 56)}<g filter="url(#sticker)">{emblem()}</g></g>'
            + wordmark(w / 2, 500, 128)
            + tagline(w / 2, 575, 640, 56, 26))
    return svg(w, h, body, sky_background(w, h, seed=9))


# ---------------------------------------------------------------------------
# Cizim (Chromium) ve kirpma
# ---------------------------------------------------------------------------
def render(jobs):
    renderer = HERE / "render.js"
    env = dict(os.environ)
    node_path = env.get("NODE_PATH", "")
    for candidate in ("/opt/node22/lib/node_modules", "/usr/lib/node_modules", "/usr/local/lib/node_modules"):
        if Path(candidate, "playwright").exists():
            node_path = candidate + (os.pathsep + node_path if node_path else "")
    env["NODE_PATH"] = node_path
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as f:
        json.dump(jobs, f)
        job_file = f.name
    try:
        subprocess.run(["node", str(renderer), job_file], check=True, env=env)
    finally:
        os.unlink(job_file)


def crop(path, margin):
    image = Image.open(path)
    box = image.getchannel("A").getbbox()
    if box:
        image = image.crop((max(0, box[0] - margin), max(0, box[1] - margin),
                            min(image.width, box[2] + margin), min(image.height, box[3] + margin)))
    image.save(path, optimize=True)
    return image.size


def main():
    OUT.mkdir(exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix="skylogo-"))
    files = {
        "logo.svg": (logo_full(), 1800, 1150),
        "logo-ikon.svg": (emblem_only(600, True), 600, 600),
        "banner-1500x500.svg": (banner_wide(), 1500, 500),
        "banner-468x60.svg": (banner_small(), 468, 60),
        "tanitim-1200x630.svg": (promo_card(), 1200, 630),
    }
    jobs = []
    for name, (content, width, height) in files.items():
        (OUT / name).write_text(content, encoding="utf-8")
        scale = 2 if name == "logo.svg" else 1
        jobs.append({"svg": str(OUT / name), "png": str(work / name.replace(".svg", ".png")),
                     "width": width, "height": height, "scale": scale})
    # Kucuk ikonlar icin sadelestirilmis amblem
    small = emblem_only(600, True, detail=False, zoom=True)
    (work / "ikon-sade.svg").write_text(small, encoding="utf-8")
    jobs.append({"svg": str(work / "ikon-sade.svg"), "png": str(work / "ikon-sade.png"), "width": 600, "height": 600, "scale": 1})
    render(jobs)

    shutil.copy(work / "logo.png", OUT / "logo.png")
    print("  logo.png", crop(OUT / "logo.png", 24))
    icon = Image.open(work / "logo-ikon.png")
    icon.resize((512, 512), Image.LANCZOS).save(OUT / "logo-512.png", optimize=True)
    simple = Image.open(work / "ikon-sade.png")
    simple.resize((128, 128), Image.LANCZOS).save(OUT / "server-icon-buyuk.png", optimize=True)
    server_icon = simple.resize((64, 64), Image.LANCZOS)
    server_icon.save(OUT / "server-icon.png", optimize=True)
    server_icon.save(ROOT / "server" / "server-icon.png", optimize=True)
    for name in ("banner-1500x500", "banner-468x60", "tanitim-1200x630"):
        shutil.copy(work / f"{name}.png", OUT / f"{name}.png")
    shutil.rmtree(work)
    print("Bitti:", OUT)


if __name__ == "__main__":
    sys.exit(main())
