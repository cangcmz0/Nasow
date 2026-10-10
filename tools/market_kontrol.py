#!/usr/bin/env python3
"""Sunucu marketinde (market.yml) "al -> uret -> sat" para basma acigi var mi kontrol eder.

Minecraft'in tum uretim (crafting) tariflerini PrismarineJS/minecraft-data'dan indirir ve
her tarif icin: malzemeleri marketten almak, urunu markete satmaktan ucuz mu diye bakar.
Ayrica blok <-> esya bolme (demir blogu -> 9 kulce gibi), firin ve koylu zumrudu kontrolu yapar.

Kullanim:  python3 tools/market_kontrol.py [market.yml] [--surum 26.1]
Cikis kodu 0: sorun yok, 1: acik bulundu.
"""
import argparse
import collections
import json
import os
import re
import sys
import urllib.request

KOK = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VARSAYILAN_MARKET = os.path.join(KOK, 'custom-plugins', 'SkyCore', 'src', 'main', 'resources', 'market.yml')
VERI = 'https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/{}/{}.json'

# Firin / ocak donusumleri (girdi -> cikti). Tarif verisinde olmadiklari icin elle.
FIRIN = {
    'cobblestone': 'stone', 'stone': 'smooth_stone', 'cobbled_deepslate': 'deepslate', 'sand': 'glass',
    'red_sand': 'glass', 'clay_ball': 'brick', 'netherrack': 'nether_brick', 'cactus': 'green_dye',
    'kelp': 'dried_kelp', 'raw_iron': 'iron_ingot', 'raw_gold': 'gold_ingot', 'raw_copper': 'copper_ingot',
    'potato': 'baked_potato', 'beef': 'cooked_beef', 'porkchop': 'cooked_porkchop', 'chicken': 'cooked_chicken',
    'mutton': 'cooked_mutton', 'cod': 'cooked_cod', 'salmon': 'cooked_salmon', 'sandstone': 'smooth_sandstone',
    'quartz_block': 'smooth_quartz', 'stone_bricks': 'cracked_stone_bricks', 'basalt': 'smooth_basalt',
    'wet_sponge': 'sponge', 'ancient_debris': 'netherite_scrap', 'chorus_fruit': 'popped_chorus_fruit',
}
LOGS = ('_log', '_wood', '_stem', '_hyphae')


def indir(surum, ad, onbellek):
    yol = os.path.join(onbellek, '{}-{}.json'.format(surum, ad))
    if not os.path.exists(yol):
        with urllib.request.urlopen(VERI.format(surum, ad), timeout=60) as cevap:
            veri = cevap.read()
        with open(yol, 'wb') as f:
            f.write(veri)
    with open(yol, encoding='utf-8') as f:
        return json.load(f)


def market_oku(yol):
    alis, satis = {}, {}
    with open(yol, encoding='utf-8') as f:
        for satir in f:
            m = re.match(r'\s*-\s*"([a-z0-9_]+)\s+([0-9.]+)\s+([0-9.]+)"', satir)
            if m:
                ad, a, s = m.group(1), float(m.group(2)), float(m.group(3))
                if a > 0:
                    alis[ad] = a
                if s > 0:
                    satis[ad] = s
    return alis, satis


def malzemeler(tarif, adlar):
    def secenekler(x):
        if x is None:
            return []
        if isinstance(x, dict):
            x = x['id']
        return [adlar[i] for i in (x if isinstance(x, list) else [x])]
    sonuc = []
    for sira in tarif.get('inShape', []):
        sonuc += [secenekler(h) for h in sira if h is not None]
    sonuc += [secenekler(h) for h in tarif.get('ingredients', [])]
    return [s for s in sonuc if s]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('market', nargs='?', default=VARSAYILAN_MARKET)
    ap.add_argument('--surum', default='26.1', help='minecraft-data surumu (varsayilan 26.1)')
    args = ap.parse_args()
    onbellek = os.path.join(os.path.expanduser('~'), '.cache', 'skysurvival-market')
    os.makedirs(onbellek, exist_ok=True)
    adlar = {i['id']: i['name'] for i in indir(args.surum, 'items', onbellek)}
    tarifler = indir(args.surum, 'recipes', onbellek)
    alis, satis = market_oku(args.market)
    sorunlar = []

    for kimlik, liste in tarifler.items():
        urun = adlar[int(kimlik)]
        for tarif in liste:
            adet = tarif['result']['count']
            parcalar = malzemeler(tarif, adlar)
            # 1) Malzemeleri marketten al, urunu sat.
            if urun in satis:
                maliyet, eksik = 0.0, False
                for secenek in parcalar:
                    fiyatlar = [alis[s] for s in secenek if s in alis]
                    if not fiyatlar:
                        eksik = True
                        break
                    maliyet += min(fiyatlar)
                if not eksik and satis[urun] * adet > maliyet:
                    sorunlar.append('URET: {} malzemesi {:.2f}, {} adet {} satisi {:.2f}'.format(
                        urun, maliyet, adet, urun, satis[urun] * adet))
            # 2) Tek malzemeli bolme: urunu al, parcalayip sat (orn. blok -> 9 kulce).
            if len(parcalar) == 1 and len(parcalar[0]) == 1:
                kaynak = parcalar[0][0]
                if kaynak in alis and urun in satis and satis[urun] * adet > alis[kaynak]:
                    sorunlar.append('BOL: {} alis {:.2f}, {} x {} satisi {:.2f}'.format(
                        kaynak, alis[kaynak], adet, urun, satis[urun] * adet))

    for girdi, cikti in FIRIN.items():
        if girdi in alis and cikti in satis and satis[cikti] > alis[girdi]:
            sorunlar.append('FIRIN: {} alis {:.2f} < {} satisi {:.2f}'.format(girdi, alis[girdi], cikti, satis[cikti]))
    for ad in alis:
        if ad.endswith(LOGS) and 'charcoal' in satis and satis['charcoal'] > alis[ad]:
            sorunlar.append('FIRIN: {} -> charcoal'.format(ad))
    if 'emerald' in satis:
        sorunlar.append('KOYLU: zumrut satilabiliyor ({}). Koyluler 32 cubuga (indirimle 1 cubuga) zumrut verir; '
                        'odun al -> cubuk -> zumrut -> sat dongusu para basar. Satisi 0 yapin.'.format(satis['emerald']))
    for ad in satis:
        if ad in alis and satis[ad] > alis[ad]:
            sorunlar.append('AL-SAT: {} alis {:.2f} < satis {:.2f}'.format(ad, alis[ad], satis[ad]))

    print('{} tarif, {} alinabilir, {} satilabilir esya kontrol edildi.'.format(
        sum(len(v) for v in tarifler.values()), len(alis), len(satis)))
    if sorunlar:
        print('ACIK BULUNDU:')
        for s in sorunlar:
            print('  - ' + s)
        return 1
    print('Sorun yok: marketten alip uretip satarak para kazanilamiyor.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
