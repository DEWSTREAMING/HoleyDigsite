#!/usr/bin/env python3
"""Phase 15 tier generator: ONE data entry in src/main/resources/data/holeydigsite/tiers/<family>.json ->
every file a new tier of an existing family needs.

    python tools/make_tiers.py            # generate / refresh everything for all tiers
    python tools/make_tiers.py --check    # verify (no writes): data files, lang, tags and asset references all agree
    python tools/make_tiers.py --prune    # delete generated files of tiers that are no longer defined (uses tools/tiers_manifest.json)

What is written, per tier (needs Pillow):
  * data/holeydigsite/holeydigsite/shop_items/<id>.json            from  obtain.shop            (if the file is missing / differs)
  * data/holeydigsite/holeydigsite/forge_recipes/<file>.json       from  obtain.recipes
  * data/minecraft/tags/block/<tag>.json                           ids added to the tags listed in  tags
  * assets/holeydigsite/lang/en_us.json                            block/item name (+ tooltip) of generated tiers
  * for tiers with  "assets": {"mode": "clone", "template": "<tier id>"}  (new art):
      blockstate, block models, item model, loot table (copied from the template with ids renamed) and the textures the
      template's models use, palette-swapped to this tier's  colors  (+ border / rails / Roman-numeral mark per family rule).
Tiers with  "assets": {"mode": "legacy"}  (the hand-made ones) are never touched; --check still verifies their data files.

Files in  tools/overrides/  (same layout as src/main/resources/) are copied over the generated result LAST, so a hand-made
model or texture always wins - put the special case there and re-run.
Deterministic: same input -> same files.
"""
import colorsys  # noqa: F401  (kept for hand-written rules in overrides scripts)
import json
import os
import re
import shutil
import sys

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    Image = None

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', 'src', 'main', 'resources'))
if os.environ.get('HD_RESOURCES'):
    ROOT = os.path.abspath(os.environ['HD_RESOURCES'])
TIERS = os.path.join(ROOT, 'data', 'holeydigsite', 'tiers')
DATA = os.path.join(ROOT, 'data', 'holeydigsite')
ASSETS = os.path.join(ROOT, 'assets', 'holeydigsite')
MC_TAGS = os.path.join(ROOT, 'data', 'minecraft', 'tags', 'block')
OVERRIDES = os.environ.get('HD_OVERRIDES') or os.path.join(HERE, 'overrides')
MANIFEST = os.environ.get('HD_MANIFEST') or os.path.join(HERE, 'tiers_manifest.json')
NS = 'holeydigsite'

CHECK = '--check' in sys.argv
PRUNE = '--prune' in sys.argv
problems = []
written = []        # files this run produced (relative to ROOT)


# ------------------------------------------------------------------------------------------------ io helpers
def load_json(path):
    with open(path, encoding='utf-8') as f:
        return json.load(f)


def dump_text(obj):
    return json.dumps(obj, indent=2, ensure_ascii=False) + '\n'


def write_text(path, text):
    rel = os.path.relpath(path, ROOT)
    old = None
    if os.path.exists(path):
        with open(path, encoding='utf-8') as f:
            old = f.read()
    if old == text:
        written.append(rel)
        return
    if CHECK:
        problems.append('%s %s' % ('differs:' if old is not None else 'missing:', rel))
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(text)
    written.append(rel)


def write_bytes_from(img_or_path, path):
    rel = os.path.relpath(path, ROOT)
    if CHECK:
        if not os.path.exists(path):
            problems.append('missing: ' + rel)
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if isinstance(img_or_path, str):
        shutil.copyfile(img_or_path, path)
    else:
        img_or_path.save(path)
    written.append(rel)


# ------------------------------------------------------------------------------------------------ tier files
def load_families():
    families = {}
    for name in load_json(os.path.join(TIERS, 'index.json'))['families']:
        fam = load_json(os.path.join(TIERS, name + '.json'))
        fam.setdefault('family', name)
        families[name] = fam
    return families


def all_tiers(families):
    out = {}
    for fam in families.values():
        for t in fam['tiers']:
            out[t['id']] = (fam, t)
    return out


def tag_of(tier):
    """'t2' for ids ending in _t2 etc., else the explicit  tag  field (None for the base tier)."""
    if tier.get('tag'):
        return tier['tag']
    m = re.search(r'_(t\d+)$', tier['id'])
    return m.group(1) if m else None


def is_clone(tier):
    return tier.get('assets', {}).get('mode') == 'clone'


# ------------------------------------------------------------------------------------------------ data outputs
def gen_shop(tier):
    shop = tier.get('obtain', {}).get('shop')
    if not shop:
        return
    obj = {'item': '%s:%s' % (NS, tier['id']), 'count': shop.get('count', 1), 'price': shop['price']}
    write_json_semantic(os.path.join(DATA, 'holeydigsite', 'shop_items', tier['id'] + '.json'), obj)


def gen_recipes(tier):
    for r in tier.get('obtain', {}).get('recipes', []):
        obj = {
            'order': r['order'],
        }
        if r.get('category'):
            obj['category'] = r['category']
        if r.get('variant'):
            obj['variant'] = r['variant']
        obj['metals'] = dict(r['metals'])
        obj['parts'] = [{'item': p['item'], 'count': p.get('count', 1)} for p in r['parts']]
        obj['result'] = {'item': '%s:%s' % (NS, tier['id']), 'count': r.get('count', 1)}
        write_json_semantic(os.path.join(DATA, 'holeydigsite', 'forge_recipes', r.get('file', tier['id']) + '.json'), obj)


def write_json_semantic(path, obj):
    """Writes only when the parsed content differs, so hand-formatted legacy files stay byte-identical."""
    if os.path.exists(path):
        try:
            if load_json(path) == obj:
                written.append(os.path.relpath(path, ROOT))
                return
        except ValueError:
            pass
    write_text(path, dump_text(obj))


def gen_tags(families):
    """Adds every tier id to the block tags in its  tags  list (and removes ids of tiers that are gone from managed tags)."""
    managed = {}
    for fam in families.values():
        for t in fam['tiers']:
            for tag in t.get('tags', []):
                managed.setdefault(tag, []).append('%s:%s' % (NS, t['id']))
    for tag, ids in managed.items():
        ns, _, path = tag.partition(':')
        p = os.path.join(ROOT, 'data', ns, 'tags', 'block', path + '.json')
        obj = load_json(p) if os.path.exists(p) else {'values': []}
        values = list(obj['values'])
        want = set(ids)
        missing = [i for i in ids if i not in values]
        if not missing:
            continue
        if CHECK:
            problems.append('tag %s lacks %s' % (tag, missing))
            continue
        obj['values'] = values + missing
        write_text(p, dump_text(obj))


def set_lang(entries):
    """Adds / updates keys in en_us.json textually (keeps the hand-written order and layout)."""
    if not entries:
        return
    p = os.path.join(ASSETS, 'lang', 'en_us.json')
    with open(p, encoding='utf-8') as f:
        text = f.read()
    cur = json.loads(text)
    changed = False
    for key, val in entries.items():
        line = '  %s: %s' % (json.dumps(key, ensure_ascii=False), json.dumps(val, ensure_ascii=False))
        if key in cur:
            if cur[key] != val:
                if CHECK:
                    problems.append('lang %s is %r, tiers say %r' % (key, cur[key], val))
                    continue
                text = re.sub(r'^  %s: .*?(,?)$' % re.escape(json.dumps(key)), lambda m: line + m.group(1), text, count=1, flags=re.M)
                changed = True
        else:
            if CHECK:
                problems.append('lang key missing: ' + key)
                continue
            idx = text.rstrip().rfind('}')
            body = text[:idx].rstrip()
            text = body + ',\n' + line + '\n}\n'
            changed = True
    if changed:
        with open(p, 'w', encoding='utf-8', newline='\n') as f:
            f.write(text)
        written.append(os.path.relpath(p, ROOT))


def lang_entries(tier, has_block):
    out = {}
    kind = 'block' if has_block else 'item'
    out['%s.%s.%s' % (kind, NS, tier['id'])] = tier['name']
    if tier.get('tooltip'):
        out['tooltip.%s.%s' % (NS, tier['id'])] = tier['tooltip']
    return out


# ------------------------------------------------------------------------------------------------ asset cloning
def refs_of(obj, ctx=None, out=None):
    """Collects ('model'|'texture', 'block'|'item', name) from a blockstate / model JSON."""
    if out is None:
        out = set()
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k in ('model', 'parent') and isinstance(v, str):
                add_ref(v, 'model', out)
            elif k == 'textures' and isinstance(v, dict):
                for tv in v.values():
                    if isinstance(tv, str):
                        add_ref(tv, 'texture', out)
            else:
                refs_of(v, k, out)
    elif isinstance(obj, list):
        for v in obj:
            refs_of(v, ctx, out)
    return out


def add_ref(value, what, out):
    m = re.fullmatch(r'%s:(block|item)/([a-z0-9_/]+)' % NS, value)
    if m:
        out.add((what, m.group(1), m.group(2)))


def model_path(folder, name):
    return os.path.join(ASSETS, 'models', folder, name + '.json')


def tex_path(folder, name):
    return os.path.join(ASSETS, 'textures', folder, name + '.png')


def hexcolor(c):
    return tuple(int(v) for v in c)


def palette_swap(img, mapping):
    img = img.copy()
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a and (r, g, b) in mapping:
                nr, ng, nb = mapping[(r, g, b)]
                px[x, y] = (nr, ng, nb, a)
    return img


def draw_border(img, ramp):
    """1-px frame around every 16x16 frame: ramp[2] / ramp[1] alternating, corners ramp[0] (like rivets)."""
    px = img.load()
    for f in range(img.height // 16):
        o = f * 16
        for i in range(16):
            for (x, y) in ((i, o), (i, o + 15), (0, o + i), (15, o + i)):
                if px[x, y][3]:
                    px[x, y] = ramp[2] + (255,) if (i % 4) else ramp[1] + (255,)
        for (x, y) in ((0, o), (15, o), (0, o + 15), (15, o + 15)):
            px[x, y] = ramp[0] + (255,)


def draw_rails(img, xs, ramp):
    px = img.load()
    for y in range(img.height):
        for x in xs:
            if px[x, y][3]:
                px[x, y] = (ramp[2] if (y % 16) % 4 else ramp[1]) + (255,)


GLYPHS = {
    'I': ['#', '#', '#', '#', '#'],
    'V': ['#...#', '#...#', '.#.#.', '.#.#.', '..#..'],
    'X': ['#...#', '.#.#.', '..#..', '.#.#.', '#...#'],
}


def mark_cells(text):
    """Pixels (dx, dy) of a Roman numeral, glyphs 1 px apart; plus its width."""
    cells, x = [], 0
    for ch in text:
        rows = GLYPHS[ch]
        for dy, row in enumerate(rows):
            for dx, c in enumerate(row):
                if c == '#':
                    cells.append((x + dx, dy))
        x += len(rows[0]) + 1
    return cells, x - 1


def draw_mark(img, text, pos, ramp, erase_text=None):
    """Draws the numeral (top row ramp[-1], others ramp[1]); an old numeral is first painted over with its left-hand neighbour."""
    px = img.load()
    cells, width = mark_cells(text)
    x0 = pos[0] if pos[0] is not None else 8 - width // 2
    y0 = pos[1]
    if erase_text:
        ocells, ow = mark_cells(erase_text)
        ox0 = pos[0] if pos[0] is not None else 8 - ow // 2
        old = {(ox0 + dx, y0 + dy) for dx, dy in ocells}
        for (x, y) in sorted(old):
            nx = x
            while (nx, y) in old and nx > 0:
                nx -= 1
            if (nx, y) in old:
                nx = x
                while (nx, y) in old and nx < 15:
                    nx += 1
            px[x, y] = px[nx, y]
    light, mid, dark = ramp[-1], ramp[-2], ramp[0]
    # drop shadow first, then the glyph on top
    for dx, dy in cells:
        sx, sy = x0 + dx + 1, y0 + dy + 1
        if 0 <= sx < 16 and 0 <= sy < img.height:
            px[sx, sy] = dark + (255,)
    top = min(dy for _, dy in cells)
    for dx, dy in cells:
        sx, sy = x0 + dx, y0 + dy
        if 0 <= sx < 16 and 0 <= sy < img.height:
            px[sx, sy] = (light if dy == top else mid) + (255,)


def rename_model(name, tpl, new):
    if name == tpl:
        return new
    if name.startswith(tpl + '_'):
        return new + name[len(tpl):]
    return None


def clone_tier(fam, tier, tpl_tier, spec_all):
    """Writes the cloned blockstate / models / item model / loot table / textures of `tier` from `tpl_tier`."""
    tid, tpl = tier['id'], tpl_tier['id']
    spec = fam.get('assets', {})
    has_block = 'block' in tier
    stem = spec.get('texture_stem', tpl)
    tag_mode = spec.get('texture_naming', 'id') == 'tag'
    tpl_tag, new_tag = tag_of(tpl_tier), tag_of(tier)
    owned = stem + ('_' + tpl_tag if tag_mode and tpl_tag else '') if tag_mode else tpl_tier.get('texture_stem', tpl)
    if tag_mode:
        if not new_tag:
            problems.append('%s: needs a "tag" (e.g. "t4") - it names its textures %s_<tag>_...' % (tid, stem))
            return
        new_stem = stem + '_' + new_tag
    else:
        new_stem = tid
    if new_stem == owned:
        problems.append('%s: texture names would collide with the template %s' % (tid, tpl))
        return
    for key in spec.get('same_stats', []):
        if tier.get('stats', {}).get(key) != tpl_tier.get('stats', {}).get(key):
            problems.append('%s: stat "%s" must equal the template\'s (%s) because the blockstate depends on it - clone a template with the same value' % (tid, key, tpl))
            return
    shared_tex = [re.compile(p) for p in spec.get('shared_textures', [])]
    link_from = tpl_tier.get('textures_from')
    link_to = tier.get('textures_from')
    if link_from and not link_to:
        problems.append('%s: needs "textures_from" (the tier whose textures it uses, like %s)' % (tid, link_from))
        return

    def tex_new(name):
        """-> (new name, copy?) or None when the texture stays shared."""
        if link_from and (name == link_from or name.startswith(link_from + '_')):
            return (link_to + name[len(link_from):], False)
        if any(p.search(name) for p in shared_tex):
            return None
        if name == owned:
            return (new_stem, True)
        if name.startswith(owned + '_'):
            return (new_stem + name[len(owned):], True)
        return None

    # --- closure of models and textures reachable from the template's blockstate and item model
    models = {}          # (folder, name) -> parsed json
    textures = set()     # (folder, name)
    todo = []
    if has_block:
        bs_path = os.path.join(ASSETS, 'blockstates', tpl + '.json')
        if not os.path.exists(bs_path):
            problems.append('%s: template blockstate %s missing' % (tid, bs_path))
            return
        bs = load_json(bs_path)
        todo += sorted(refs_of(bs))
    item_model = model_path('item', tpl)
    if os.path.exists(item_model):
        todo.append(('model', 'item', tpl))
    seen = set()
    while todo:
        what, folder, name = todo.pop()
        if (what, folder, name) in seen:
            continue
        seen.add((what, folder, name))
        if what == 'texture':
            textures.add((folder, name))
            continue
        p = model_path(folder, name)
        if not os.path.exists(p):
            continue            # vanilla / not ours
        data = load_json(p)
        models[(folder, name)] = data
        todo += sorted(refs_of(data))

    # --- renames
    model_map = {}
    for (folder, name) in models:
        n = rename_model(name, tpl, tid)
        if n and not (folder == 'item' and name == tpl and False):
            model_map[(folder, name)] = n
    tex_map = {}
    copy_tex = set()
    for (folder, name) in textures:
        n = tex_new(name)
        if n:
            tex_map[(folder, name)] = n[0]
            if n[1]:
                copy_tex.add((folder, name))

    def apply_names(text):
        # longest names first so  a_b_c  is replaced before  a_b
        for (folder, name), new in sorted(model_map.items(), key=lambda kv: -len(kv[0][1])):
            text = re.sub(r'%s:%s/%s(?![a-z0-9_])' % (NS, folder, re.escape(name)), '%s:%s/%s' % (NS, folder, new), text)
        for (folder, name), new in sorted(tex_map.items(), key=lambda kv: -len(kv[0][1])):
            text = re.sub(r'%s:%s/%s(?![a-z0-9_])' % (NS, folder, re.escape(name)), '%s:%s/%s' % (NS, folder, new), text)
        return text

    # models
    for (folder, name), new in model_map.items():
        src = model_path(folder, name)
        with open(src, encoding='utf-8') as f:
            text = apply_names(f.read())
        write_text(model_path(folder, new), text)
    # a blockstate with no model of its own name still needs renaming of its references
    if has_block:
        with open(bs_path, encoding='utf-8') as f:
            write_text(os.path.join(ASSETS, 'blockstates', tid + '.json'), apply_names(f.read()))
        loot = os.path.join(DATA, 'loot_table', 'blocks', tpl + '.json')
        if os.path.exists(loot):
            with open(loot, encoding='utf-8') as f:
                t = f.read()
            t = re.sub(r'%s:%s(?![a-z0-9_])' % (NS, re.escape(tpl)), '%s:%s' % (NS, tid), t)
            t = re.sub(r'blocks/%s(?![a-z0-9_])' % re.escape(tpl), 'blocks/' + tid, t)
            write_text(os.path.join(DATA, 'loot_table', 'blocks', tid + '.json'), t)
        else:
            problems.append('%s: template loot table %s missing' % (tid, loot))

    # textures
    colors_t, colors_n = tpl_tier.get('colors', {}), tier.get('colors', {})
    rules = spec.get('rules', [])
    default_rule = spec.get('default', {})
    for (folder, name), new in sorted(tex_map.items()):
        if (folder, name) not in copy_tex:
            continue
        src = tex_path(folder, name)
        if not os.path.exists(src):
            problems.append('%s: template texture %s missing' % (tid, os.path.relpath(src, ROOT)))
            continue
        dst = tex_path(folder, new)
        rest = name[len(owned) + 1:] if name.startswith(owned + '_') else name
        rule = default_rule
        for r in rules:
            if re.search(r['match'], rest):
                rule = r
                break
        meta = src + '.mcmeta'
        if CHECK:
            if not os.path.exists(dst):
                problems.append('missing: ' + os.path.relpath(dst, ROOT))
            continue
        if Image is None:
            problems.append('Pillow is needed to make textures (pip install pillow)')
            return
        img = Image.open(src).convert('RGBA')
        mapping = {}
        for key in rule.get('swap', []):
            a, b = colors_t.get(key), colors_n.get(key)
            if a is None or b is None or len(a) != len(b):
                problems.append('%s: colors.%s must exist with equal length in %s and %s' % (tid, key, tpl, tid))
                continue
            for ca, cb in zip(a, b):
                mapping[hexcolor(ca)] = hexcolor(cb)
        if mapping:
            img = palette_swap(img, mapping)
        if rule.get('border'):
            ramp = colors_n.get(rule['border'])
            if ramp:
                draw_border(img, [hexcolor(c) for c in ramp])
        if rule.get('rails'):
            ramp = colors_n.get(rule['rails']['ramp'])
            if ramp:
                draw_rails(img, rule['rails']['xs'], [hexcolor(c) for c in ramp])
        if rule.get('mark') and tier.get('mark'):
            ramp = colors_n.get(rule['mark'].get('ramp', 'trim'))
            pos = rule['mark'].get('pos', [None, 5])
            erase = tpl_tier.get('mark') if tpl_tier.get('mark', 'I') != 'I' else None
            if ramp and tier['mark'] != 'I':
                draw_mark(img, tier['mark'], pos, [hexcolor(c) for c in ramp], erase)
        write_bytes_from(img, dst)
        if os.path.exists(meta):
            m = load_json(meta)
            if tier.get('frametime') and 'animation' in m:
                m['animation']['frametime'] = tier['frametime']
            with open(dst + '.mcmeta', 'w', encoding='utf-8', newline='\n') as f:
                f.write(dump_text(m))
            written.append(os.path.relpath(dst + '.mcmeta', ROOT))


# ------------------------------------------------------------------------------------------------ validation
def validate_assets(families):
    """Every model / texture a tier's blockstate or item model reaches must exist (catches missing art early)."""
    for fam in families.values():
        for t in fam['tiers']:
            if is_clone(t) and CHECK is False:
                pass
            todo, seen = [], set()
            if 'block' in t:
                bs = os.path.join(ASSETS, 'blockstates', t['id'] + '.json')
                if not os.path.exists(bs):
                    problems.append('no blockstate for ' + t['id'])
                else:
                    todo += sorted(refs_of(load_json(bs)))
            im = model_path('item', t['id'])
            if not os.path.exists(im):
                problems.append('no item model for ' + t['id'])
            else:
                todo.append(('model', 'item', t['id']))
            if 'block' in t and not os.path.exists(os.path.join(DATA, 'loot_table', 'blocks', t['id'] + '.json')):
                problems.append('no loot table for ' + t['id'])
            while todo:
                what, folder, name = todo.pop()
                if (what, folder, name) in seen:
                    continue
                seen.add((what, folder, name))
                if what == 'texture':
                    if not os.path.exists(tex_path(folder, name)):
                        problems.append('%s: texture %s/%s is missing' % (t['id'], folder, name))
                else:
                    p = model_path(folder, name)
                    if os.path.exists(p):
                        todo += sorted(refs_of(load_json(p)))
                    elif folder == 'block' or folder == 'item':
                        problems.append('%s: model %s/%s is missing' % (t['id'], folder, name))


# ------------------------------------------------------------------------------------------------ overrides / manifest / prune
def apply_overrides():
    if not os.path.isdir(OVERRIDES):
        return
    for dp, _, files in os.walk(OVERRIDES):
        for fn in files:
            if fn.startswith('.') or fn == 'README.md':
                continue
            src = os.path.join(dp, fn)
            rel = os.path.relpath(src, OVERRIDES)
            dst = os.path.join(ROOT, rel)
            if CHECK:
                continue
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(src, dst)
            written.append(rel)


def main():
    families = load_families()
    tiers = all_tiers(families)
    manifest = load_json(MANIFEST) if os.path.exists(MANIFEST) else {}

    if PRUNE:
        gone = [tid for tid in manifest if tid not in tiers]
        for tid in gone:
            for rel in manifest[tid]:
                p = os.path.join(ROOT, rel)
                if os.path.exists(p):
                    os.remove(p)
                    print('removed', rel)
            # and its tag / lang entries
            for tag_file in manifest[tid]:
                pass
            del manifest[tid]
        prune_refs(set(gone))
        with open(MANIFEST, 'w', encoding='utf-8') as f:
            f.write(dump_text(manifest))
        return

    lang = {}
    for fam in families.values():
        for t in fam['tiers']:
            start = len(written)
            gen_shop(t)
            gen_recipes(t)
            if is_clone(t):
                tpl_id = t['assets']['template']
                if tpl_id not in tiers:
                    problems.append('%s: unknown template %s' % (t['id'], tpl_id))
                    continue
                clone_tier(fam, t, tiers[tpl_id][1], tiers)
                lang.update(lang_entries(t, 'block' in t))
            elif CHECK:
                lang.update(lang_entries(t, 'block' in t))
            if is_clone(t):
                manifest[t['id']] = sorted(set(written[start:]))
    gen_tags(families)
    set_lang(lang)
    apply_overrides()
    validate_assets(families)
    if not CHECK:
        with open(MANIFEST, 'w', encoding='utf-8') as f:
            f.write(dump_text(manifest))
    if problems:
        print('PROBLEMS:')
        for p in problems:
            print('  ' + p)
        sys.exit(1)
    print('make_tiers: %d tiers in %d families, %s' % (len(tiers), len(families), 'check passed' if CHECK else '%d files written/verified' % len(written)))


def prune_refs(gone):
    """Removes lang keys and tag entries of pruned tiers."""
    p = os.path.join(ASSETS, 'lang', 'en_us.json')
    with open(p, encoding='utf-8') as f:
        text = f.read()
    for tid in gone:
        for key in ('block.%s.%s' % (NS, tid), 'item.%s.%s' % (NS, tid), 'tooltip.%s.%s' % (NS, tid)):
            text = re.sub(r'^  %s: .*\n' % re.escape(json.dumps(key)), '', text, flags=re.M)
    text = re.sub(r',(\s*\n\})', r'\1', text)
    with open(p, 'w', encoding='utf-8', newline='\n') as f:
        f.write(text)
    for tag in ('mineable/pickaxe', 'mineable/axe', 'mineable/shovel'):
        tp = os.path.join(MC_TAGS, tag + '.json')
        if os.path.exists(tp):
            obj = load_json(tp)
            vals = [v for v in obj['values'] if v.split(':')[-1] not in gone]
            if vals != obj['values']:
                obj['values'] = vals
                with open(tp, 'w', encoding='utf-8', newline='\n') as f:
                    f.write(dump_text(obj))
    for tid in gone:
        for rel in (os.path.join('data', 'holeydigsite', 'holeydigsite', 'shop_items', tid + '.json'),):
            q = os.path.join(ROOT, rel)
            if os.path.exists(q):
                os.remove(q)


if __name__ == '__main__':
    main()
