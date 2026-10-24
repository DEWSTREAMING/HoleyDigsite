# tools/overrides

Files here (same layout as `src/main/resources/`, e.g. `assets/holeydigsite/models/block/x.json`) are copied over the
generated result LAST by `python tools/make_tiers.py`, so a hand-made model, texture or lang line always wins over the
generator. Put the special case here, re-run the generator, done. Empty by default (nothing needed an override in Phase 15).
