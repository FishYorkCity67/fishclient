#!/usr/bin/env python3
"""Compares the translation keys used in the Java code with en_us.json and de_de.json.

Run from the project root: python tools/check_lang.py
Keys that are put together at runtime are listed by hand in DYNAMIC. Exits non-zero when something is missing.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "src/main/java"
LANG = ROOT / "src/main/resources/assets/fishclient/lang"

TABS = ["auction", "recent", "transactions", "theme", "skins", "cape", "cosmetics", "music", "crosshair", "utilities",
        "scoreboard", "sky", "portals", "hud", "waypoints", "minimap", "fishing", "screenshots", "profiles", "settings"]
HUDS = ["profit", "top_payer", "fps_ping", "keystrokes", "cps", "armor", "fishing", "music", "minimap", "scoreboard", "live_auction"]
SKIES = ["northern_lights", "galaxy", "blood_moon", "nebula", "deep_ocean", "vaporwave", "sunset_dream", "emerald_veil"]
DYNAMIC = (
    [f"fishclient.tab.{t}" for t in TABS]
    + [f"fishclient.hud.{h}" for h in HUDS]
    + [f"fishclient.hud.{h}.desc" for h in HUDS]
    + [f"fishclient.sky.{s}" for s in SKIES]
    + [f"fishclient.crosshair.shape.{s}" for s in ("cross", "t", "circle", "dot")]
    + [f"fishclient.cosmetics.hat.{h}" for h in ("none", "top_hat", "fisher_hat", "crown", "fish_hat")]
    + [f"fishclient.settings.kind.{k}" for k in ("received", "sent", "sold", "bought")]
    + [f"key.fishclient.{k}" for k in ("menu", "freelook", "zoom", "waypoint")]
    + ["key.category.fishclient.main"]
)


def used_keys():
    keys = set(DYNAMIC)
    pattern = re.compile(r'\bL\.[tf]\("([^"]+)"\s*[,)]')
    for path in JAVA.rglob("*.java"):
        for key in pattern.findall(path.read_text(encoding="utf-8")):
            keys.add("fishclient." + key)
    return keys


def main():
    keys = used_keys()
    failed = False
    tables = {}
    for name in ("en_us", "de_de"):
        path = LANG / f"{name}.json"
        table = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
        tables[name] = table
        missing = sorted(keys - set(table))
        unused = sorted(set(table) - keys)
        print(f"{name}: {len(table)} entries, {len(missing)} missing, {len(unused)} unused")
        for key in missing:
            print("  missing:", key)
            failed = True
        for key in unused:
            print("  unused: ", key)
    # %s placeholders have to line up, or String.format would throw at runtime.
    for key in sorted(set(tables["en_us"]) & set(tables["de_de"])):
        if tables["en_us"][key].count("%") != tables["de_de"][key].count("%"):
            print("  placeholder mismatch:", key)
            failed = True
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
