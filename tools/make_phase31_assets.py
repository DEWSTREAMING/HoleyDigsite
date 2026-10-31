"""Outback collection data and assets: the seven skin files, lang lines, the Fossicker title, the Outback Crate (red dirt box with an
opal in the lid, padlock variant) and its crate file.

Usage: python tools/make_phase31_assets.py    (run from the project folder; textures of the skins come from make_skins.py)
Then:  python tools/make_pbr.py
"""
import json
import os
import random

from PIL import Image

import make_phase30_assets as p30

RES = p30.RES
ASSETS = p30.ASSETS
DATA = p30.DATA
TEX = p30.TEX

# id, name, rarity (data first: change a name or rarity here, then also the crate file)
SKINS = [("outback", "Outback", "common"), ("outback_dusty", "Dusty Outback", "common"), ("outback_ochre", "Ochre", "rare"),
         ("opal_calm", "Calm Opal", "rare"), ("opal", "Opal", "epic"), ("opal_boulder", "Boulder Opal", "epic"),
         ("black_opal", "Black Opal", "legendary")]


def skins_and_lang():
    for sid, name, rarity in SKINS:
        p30.write_json(os.path.join(DATA, 'skins', sid + '.json'),
                       {"rarity": rarity, "families": ["mining_drill", "sluice_box"], "name": "skin.holeydigsite." + sid})
    lang_path = os.path.join(ASSETS, 'lang/en_us.json')
    with open(lang_path, encoding='utf-8') as f:
        lang = json.load(f)
    new = {"skin.holeydigsite." + sid: name for sid, name, _ in SKINS}
    new["ledger.holeydigsite.title.fossicker"] = "Fossicker"
    out = {}
    for k, v in lang.items():
        out[k] = new.pop(k, v)
        if k == "skin.holeydigsite.frosted":
            for nk in [n for n in list(new) if n.startswith("skin.holeydigsite.")]:
                out[nk] = new.pop(nk)
        if k == "ledger.holeydigsite.title.jackpot":
            out["ledger.holeydigsite.title.fossicker"] = new.pop("ledger.holeydigsite.title.fossicker")
    out.update(new)
    with open(lang_path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(out, f, indent=2, ensure_ascii=False)
        f.write('\n')

    titles_path = os.path.join(DATA, 'ledger/titles.json')
    with open(titles_path, encoding='utf-8') as f:
        titles = json.load(f)
    if "fossicker" not in titles["crate_titles"]:
        titles["crate_titles"].append("fossicker")
    p30.write_json(titles_path, titles)


def opal_lid(img):
    """A small opal set in the lid: dark body, flecks of green, blue, violet and orange, a pale rim."""
    rng = random.Random('outback_opal')
    body = [p30.hexc(c) for c in ('#1B2033', '#222A44', '#161B2C')]
    flecks = [p30.hexc(c) for c in ('#3CCD78', '#468CF5', '#A05FF0', '#F59632')]
    rim = p30.hexc('#9DA5B8')
    for y in range(5, 11):
        for x in range(4, 12):
            edge = x in (4, 11) or y in (5, 10)
            corner = (x in (4, 11)) and (y in (5, 10))
            if corner:
                continue
            img.putpixel((x, y), rim if edge else rng.choice(body))
    for _ in range(9):
        x, y = rng.randrange(5, 11), rng.randrange(6, 10)
        img.putpixel((x, y), rng.choice(flecks))


def textures():
    p30.WOOD = ['#A8512E', '#9A4828', '#B65C36', '#843E22']
    p30.SEAM = '#4A2212'
    rng = random.Random('outback_crate')
    for name, vertical in (('side', False), ('front', False), ('top', True)):
        img = p30.planks(rng, vertical)
        p30.band(img, 2, 3)
        p30.band(img, 12, 13)
        if name == 'front':
            p30.hasp(img)
        p30.rust(img, rng)
        if name == 'top':
            opal_lid(img)
        p30.save(img, os.path.join(TEX, f'outback_crate_{name}.png'))
    bottom = p30.planks(rng)
    for y in range(16):
        for x in range(16):
            bottom.putpixel((x, y), p30.shade(bottom.getpixel((x, y)), 0.8))
    p30.save(bottom, os.path.join(TEX, 'outback_crate_bottom.png'))
    pad = Image.open(os.path.join(TEX, 'prospector_padlock.png'))
    p30.save(pad, os.path.join(TEX, 'outback_padlock.png'))


def models():
    locked = json.load(open(os.path.join(ASSETS, 'models/block/prospector_crate_locked.json'), encoding='utf-8'))
    text = json.dumps(locked).replace('prospector_', 'outback_')
    p30.write_json(os.path.join(ASSETS, 'models/block/outback_crate_locked.json'), json.loads(text))
    p30.write_json(os.path.join(ASSETS, 'models/block/outback_crate_open.json'), {
        'parent': 'holeydigsite:block/crate_open', 'textures': {
            "side": "holeydigsite:block/outback_crate_side", "front": "holeydigsite:block/outback_crate_front",
            "top": "holeydigsite:block/outback_crate_top", "bottom": "holeydigsite:block/outback_crate_bottom",
            "inside": "holeydigsite:block/crate_inside", "particle": "holeydigsite:block/outback_crate_side"}})
    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for open_ in ('false', 'true'):
            for lk in ('false', 'true'):
                model = 'outback_crate_open' if open_ == 'true' else ('outback_crate_locked' if lk == 'true' else 'outback_crate')
                v = {"model": "holeydigsite:block/" + model}
                if y:
                    v["y"] = y
                variants[f"facing={facing},open={open_},locked={lk}"] = v
    p30.write_json(os.path.join(ASSETS, 'blockstates/outback_crate.json'), {"variants": variants})
    p30.write_json(os.path.join(ASSETS, 'models/item/outback_crate.json'), {
        "parent": "holeydigsite:block/outback_crate",
        "overrides": [{"predicate": {"holeydigsite:locked": 1}, "model": "holeydigsite:block/outback_crate_locked"}]})


def crate_file():
    p30.write_json(os.path.join(DATA, 'crates/outback_crate.json'), {})  # replaced below, keeps key order readable
    text = '''{
  "name": "crate.holeydigsite.collection.outback_crate",
  "look": "outback",
  "odds": {"common": 80, "rare": 16, "epic": 3, "legendary": 1},
  "finds": {"pan": 2000, "sluice": 1440, "drill": 1440, "shovel": 3000},
  "site": "event_island",
  "pools": {
    "common": [
      {"skin": "outback"},
      {"skin": "outback_dusty"},
      {"item": "holeydigsite:corrugated_iron_sheet", "count": 4},
      {"item": "holeydigsite:southern_cross_banner"}
    ],
    "rare": [
      {"skin": "outback_ochre"},
      {"skin": "opal_calm"},
      {"title": "fossicker"},
      {"item": "holeydigsite:little_windmill"},
      {"item": "holeydigsite:ute_ore_cart"}
    ],
    "epic": [
      {"skin": "opal"},
      {"skin": "opal_boulder"},
      {"item": "holeydigsite:welcome_stranger_stand"}
    ],
    "legendary": [
      {"skin": "black_opal"}
    ]
  }
}
'''
    with open(os.path.join(DATA, 'crates/outback_crate.json'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(text)


if __name__ == '__main__':
    skins_and_lang()
    textures()
    models()
    crate_file()
    print('ok')
