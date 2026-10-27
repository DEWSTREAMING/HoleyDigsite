"""Dump the mod's real data into docs/guide/_facts.md – the ONLY source the guide writer may use.
Run: python tools/guide_facts.py"""
import glob
import json
import os

R = "src/main/resources/"
D = R + "data/holeydigsite/"
lang = json.load(open(R + "assets/holeydigsite/lang/en_us.json", encoding="utf-8"))


def name(item_id):
    p = item_id.split(":")[-1]
    return lang.get(f"item.holeydigsite.{p}") or lang.get(f"block.holeydigsite.{p}") or p


def money(c):
    g, rest = divmod(int(c), 64)
    s, cu = divmod(rest, 8)
    parts = [f"{g} gold" if g else "", f"{s} silver" if s else "", f"{cu} copper" if cu else ""]
    return " ".join(p for p in parts if p) or "0 copper"


out = ["# Holey Digsite – facts for the guide (generated from the data, do not edit)\n",
       "Money: 8 copper = 1 silver, 8 silver = 1 gold (so 1 gold = 64 copper).\n"]
out.append("## Shop catalogue (item – price – where sold)")
for f in sorted(glob.glob(D + "holeydigsite/shop_items/*.json")):
    e = json.load(open(f, encoding="utf-8"))
    where = e.get("site", "every store") + (" (Ledger blueprint)" if e.get("ledger") else "")
    out.append(f"- {name(e['item'])} x{e.get('count', 1)} – {money(e['price'])} – {where}")
out.append("\n## Sell values (copper per gram at the Sell Pad)")
for f in sorted(glob.glob(D + "holeydigsite/sell_values/*.json")):
    e = json.load(open(f, encoding="utf-8"))
    out.append(f"- {os.path.basename(f)[:-5]}: {e}")
out.append("\n## Item tooltips and descriptions (from the game text)")
for k, v in lang.items():
    if k.startswith("tooltip.holeydigsite."):
        out.append(f"- {k[len('tooltip.holeydigsite.'):]}: {v}")
out.append("\n## Tier families (machines and tools by tier)")
for f in sorted(glob.glob(D + "tiers/*.json")):
    if f.endswith("index.json"):
        continue
    e = json.load(open(f, encoding="utf-8"))
    tiers = e if isinstance(e, list) else e.get("tiers", e)
    out.append(f"- {os.path.basename(f)[:-5]}: " + json.dumps(tiers)[:1500])
out.append("\n## Ledger milestones (chapter – name – hint – Renown)")
for f in sorted(glob.glob(D + "ledger/milestones/*.json"), key=lambda p: json.load(open(p))["order"]):
    m = json.load(open(f, encoding="utf-8"))
    out.append(f"- {m['chapter']} – {lang.get(m['name'], m['name'])} – {lang.get(m['hint'], m['hint'])} – {m['renown']}")
out.append("\n## Ledger levels (level – rewards – coins – title)")
for f in sorted(glob.glob(D + "ledger/levels/*.json")):
    l = json.load(open(f, encoding="utf-8"))
    def reward(r):
        if "item" in r:
            return name(r["item"]) + f" x{r.get('count', 1)}"
        if "skin" in r:
            return lang.get(f"skin.holeydigsite.{r['skin']}", r["skin"]) + " Drill Skin Kit (Fresh)"
        return "Blueprint: " + name(r["blueprint"])
    rw = ", ".join(reward(r) for r in l["rewards"])
    t = lang.get(f"ledger.holeydigsite.title.{l['title']}", "") if "title" in l else ""
    out.append(f"- L{l['level']} – {rw or '-'} – {l.get('coins', '')} – {t}")
out.append("\n## Recipes (crafting / cooking / forge)")
for f in sorted(glob.glob(D + "recipe/*.json") + glob.glob(D + "holeydigsite/forge_recipes/*.json")):
    out.append(f"- {os.path.basename(f)[:-5]}: " + json.dumps(json.load(open(f, encoding="utf-8")))[:400])
os.makedirs("docs/guide", exist_ok=True)
open("docs/guide/_facts.md", "w", encoding="utf-8").write("\n".join(out) + "\n")
print(len(out), "lines")
