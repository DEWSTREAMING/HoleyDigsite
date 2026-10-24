#!/usr/bin/env bash
# Regenerate the whole starting world and convert it to NBT. Works in the Linux sandbox and in Git Bash on Windows (JDK 21 from
# ~/.gradle/jdks, ';' classpath, cygpath).
#   tools/make_world.sh [pack root]
# 1. make_world.py  -> tools/world_snbt/*.snbt, data/holeydigsite/site|store_layout/*.json, <pack>/_source/island/holey_digsite.snbt
# 2. check_world.py -> safety checks incl. the floating-block connectivity check (stops on failure)
# 3. NbtTool batch  -> data/holeydigsite/structure/{store,plot}/*.nbt and <pack>/config/skyblockbuilder/templates/islands/holey_digsite.nbt
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"; MOD="$(dirname "$HERE")"
PACK="${1:-$MOD/../Holey Digsite Pack}"
PY="$(command -v python3 || command -v python)"
"$PY" "$HERE/make_world.py" "$PACK"
"$PY" "$HERE/check_world.py"
OUT="$HERE/world_snbt/classes"; rm -rf "$OUT"; mkdir -p "$OUT"
GUAVA=$(find ~/.gradle/caches/modules-2 -name guava-32.1.2-jre.jar | head -1)
if command -v cygpath >/dev/null 2>&1; then       # Windows (Git Bash)
  JDK="$(ls -d ~/.gradle/jdks/eclipse_adoptium-21-*/ | head -1)bin/"; EXE=.exe; SEP=';'; P() { cygpath -m "$1"; }
else
  JDK=""; EXE=""; SEP=':'; P() { echo "$1"; }
fi
LIBS=$(find ~/.gradle/caches/modules-2 -name '*.jar' ! -name '*sources*' ! -name 'guava-*' | while read -r j; do P "$j"; done | tr '\n' "$SEP")
CP="$(P "$GUAVA")$SEP$(P "$MOD/build/moddev/artifacts/neoforge-21.1.252-merged.jar")$SEP$LIBS"
"${JDK}javac$EXE" -proc:none -nowarn -implicit:none -sourcepath "" -d "$(P "$OUT")" -cp "$CP" "$(P "$PACK/_source/island/NbtTool.java")" 2>&1 | grep -v "^Note:" || true
ARGS=()
for f in $(cd "$HERE/world_snbt" && find store plot -name '*.snbt'); do
  ARGS+=("$(P "$HERE/world_snbt/$f")" "$(P "$MOD/src/main/resources/data/holeydigsite/structure/${f%.snbt}.nbt")")
done
ARGS+=("$(P "$PACK/_source/island/holey_digsite.snbt")" "$(P "$PACK/config/skyblockbuilder/templates/islands/holey_digsite.nbt")")
"${JDK}java$EXE" -cp "$(P "$OUT")$SEP$CP" NbtTool batch "${ARGS[@]}"
