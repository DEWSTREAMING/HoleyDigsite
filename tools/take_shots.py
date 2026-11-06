#!/usr/bin/env python3
"""
Photo studio: runs the real client, builds scenes, takes every shot in tools/shots/shots.json and quits.

Usage:
  python tools/take_shots.py                  all shots
  python tools/take_shots.py --only a,b       just these ids
  python tools/take_shots.py --list           list shot ids and exit
  python tools/take_shots.py --guide          Field Guide clips: frame bursts, then the guide sheets in the mod's assets

Opens a game window and uses the GPU. Only run it when asked to.
Shots with a "guide" entry are left out of a normal run. --guide runs only those (plain look), writes their frames
to the guide folder next to the stills and turns each folder into its page's sheet with make_guide_clips.py.
Images go to E:\\Minecraft Mods\\Holey Digsite Release\\screenshots\\<mod version>\\ with a contact-sheet.png.
"""
import argparse
import json
import os
import shutil
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHOTS = os.path.join(ROOT, "tools", "shots", "shots.json")
DEVTESTS = os.path.join(ROOT, "devtests")
DEVTEST_OUT = os.path.join(ROOT, "src", "main", "java", "com", "holeydigsite", "devtest")
RUN_DIR = os.path.join(ROOT, "run-shots")
SHADER_RUN_DIR = os.path.join(ROOT, "run-shots-shaders")
TEST1 = "C:/Users/Dewey/curseforge/minecraft/Instances/Test1"
SHADER_FILES = [
    ("mods", "iris-neoforge-1.8.12+mc1.21.1.jar"),
    ("mods", "sodium-neoforge-0.6.13+mc1.21.1.jar"),
    ("shaderpacks", "ComplementaryUnbound_r5.9.3.zip"),
]
SHADER_PACK = "ComplementaryUnbound_r5.9.3.zip"
RELEASE = r"E:\Minecraft Mods\Holey Digsite Release\screenshots"
STUDIO_CLASSES = ["PhotoStudio.java"]

OPTIONS = """version:3955
fov:70.0
gamma:0.5
guiScale:3
renderDistance:12
simulationDistance:12
renderClouds:"false"
particles:2
bobView:false
entityShadows:false
chatVisibility:2
tutorialStep:"none"
onboardAccessibility:false
fullscreen:false
pauseOnLostFocus:false
skipMultiplayerWarning:true
joinedFirstServer:true
narrator:0
musicVolume:0.0
soundCategory_master:0.0
"""


def client_pids():
    """PIDs of game clients started from this folder (their command line names our build folder)."""
    key = os.path.join(ROOT, "build", "moddev").replace("'", "''")
    cmd = (
        "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | "
        "Where-Object { $_.CommandLine -like '*%s*' } | ForEach-Object { $_.ProcessId }" % key
    )
    out = subprocess.run(["powershell", "-NoProfile", "-Command", cmd], capture_output=True, text=True).stdout
    return {int(x) for x in out.split() if x.isdigit()}


def kill_tree(pid):
    subprocess.run(["taskkill", "/F", "/T", "/PID", str(pid)], capture_output=True)


def run_client(limit, shaders=False):
    """Runs the client through gradle with a hard time limit. Only stops processes this run started."""
    before = client_pids()
    proc = subprocess.Popen([os.path.join(ROOT, "gradlew.bat"), "runClient", "-PstudioShots=1"] + (["-PstudioShaders=1"] if shaders else []),
        cwd=ROOT,
    )
    deadline = time.time() + limit
    code = None
    try:
        while time.time() < deadline:
            code = proc.poll()
            if code is not None:
                break
            time.sleep(2)
        else:
            print("time limit of %d s reached, stopping the client" % limit, file=sys.stderr)
            code = -1
    finally:
        if proc.poll() is None:
            kill_tree(proc.pid)
        # the game runs under the gradle daemon, not under gradlew, so find it by its command line
        for pid in client_pids() - before:
            kill_tree(pid)
    return code


def prepare_shader_dir(run_dir):
    """Copies Iris, Sodium and the shader pack from Test1 into the shader run folder and turns the pack on."""
    for sub, name in SHADER_FILES:
        src = os.path.join(TEST1, sub, name)
        if not os.path.isfile(src):
            raise SystemExit("missing %s, cannot run with shaders" % src)
        dst_dir = os.path.join(run_dir, sub)
        os.makedirs(dst_dir, exist_ok=True)
        dst = os.path.join(dst_dir, name)
        if not os.path.isfile(dst) or os.path.getsize(dst) != os.path.getsize(src):
            shutil.copyfile(src, dst)
    cfg = os.path.join(run_dir, "config")
    os.makedirs(cfg, exist_ok=True)
    with open(os.path.join(cfg, "iris.properties"), "w", encoding="utf-8", newline=chr(10)) as f:
        f.write("enableShaders=true" + chr(10) + "shaderPack=%s" % SHADER_PACK + chr(10)
                + "allowUnknownShaders=false" + chr(10) + "disableUpdateMessage=true" + chr(10))
    # The pack tilts the sun's path by default, so trees throw shadows across the machines at any time of day
    with open(os.path.join(run_dir, "shaderpacks", SHADER_PACK + ".txt"), "w", encoding="utf-8", newline=chr(10)) as f:
        f.write("SUN_ANGLE=0" + chr(10))

def compare_sheet(plain_dir, shader_dir, ids, target):
    """Plain and shader picture of each shot side by side, one row per shot."""
    from PIL import Image, ImageDraw

    pairs = []
    for sid in ids:
        a = os.path.join(plain_dir, sid + ".png")
        b = os.path.join(shader_dir, sid + ".png")
        if os.path.isfile(a) and os.path.isfile(b):
            pairs.append((sid, a, b))
    if not pairs:
        return None
    tw, th, pad, label = 640, 360, 8, 22
    sheet = Image.new("RGB", (2 * tw + 3 * pad, len(pairs) * (th + label + pad) + pad), (24, 24, 24))
    draw = ImageDraw.Draw(sheet)
    for i, (sid, a, b) in enumerate(pairs):
        y = pad + i * (th + label + pad)
        for j, (path, tag) in enumerate(((a, "plain"), (b, "shaders"))):
            x = pad + j * (tw + pad)
            sheet.paste(Image.open(path).convert("RGB").resize((tw, th), Image.LANCZOS), (x, y + label))
            draw.text((x + 2, y + 5), "%s  %s" % (sid, tag), fill=(255, 255, 255))
    sheet.save(target)
    return target


def mod_version():
    with open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8") as f:
        for line in f:
            if line.startswith("mod_version="):
                return line.split("=", 1)[1].strip()
    return "unknown"


def load_shots():
    with open(SHOTS, encoding="utf-8") as f:
        return json.load(f)["shots"]


def contact_sheet(out_dir, ids):
    from PIL import Image, ImageDraw

    thumbs = []
    for sid in ids:
        path = os.path.join(out_dir, sid + ".png")
        if not os.path.isfile(path):
            burst = os.path.join(out_dir, sid, "frame_000.png")
            path = burst if os.path.isfile(burst) else None
        thumbs.append((sid, path))
    if not thumbs:
        return None
    tw, th, pad, label = 480, 270, 8, 22
    cols = 3 if len(thumbs) > 4 else 2
    rows = (len(thumbs) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * (tw + pad) + pad, rows * (th + label + pad) + pad), (24, 24, 24))
    draw = ImageDraw.Draw(sheet)
    for i, (sid, path) in enumerate(thumbs):
        x = pad + (i % cols) * (tw + pad)
        y = pad + (i // cols) * (th + label + pad)
        if path:
            im = Image.open(path).convert("RGB").resize((tw, th), Image.LANCZOS)
            sheet.paste(im, (x, y + label))
            draw.text((x + 2, y + 5), sid, fill=(255, 255, 255))
        else:
            draw.rectangle([x, y + label, x + tw, y + label + th], outline=(200, 60, 60))
            draw.text((x + 2, y + 5), sid + " (FAILED)", fill=(255, 100, 100))
    target = os.path.join(out_dir, "contact-sheet.png")
    sheet.save(target)
    return target


def build_guide_sheets(out_dir, shots):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import make_guide_clips

    for s in shots:
        frames = os.path.join(out_dir, s["id"])
        if not os.path.isdir(frames):
            continue
        g = s["guide"]
        every = s.get("burst", {}).get("every", 2)
        make_guide_clips.convert_clip(frames, g["page"], fps=g.get("fps", 10), src_fps=20.0 / every,
                                      fade=g.get("fade", 0), colors=g.get("colors", 0), loop=g.get("loop", "fade"),
                                      start_s=g.get("start", 0.0), end_s=g.get("end"))


def main():
    ap = argparse.ArgumentParser(description="Photo studio")
    ap.add_argument("--only", help="comma-separated shot ids")
    ap.add_argument("--list", action="store_true", help="list shot ids")
    ap.add_argument("--shaders", action="store_true", help="run with Iris, Sodium and the shader pack from Test1")
    ap.add_argument("--timeout", type=int, default=1500, help="hard time limit in seconds (default 1500)")
    ap.add_argument("--guide", action="store_true", help="take the Field Guide clips and build their sheets")
    ap.add_argument("--no-sheets", action="store_true", help="with --guide: only take the frames")
    args = ap.parse_args()

    all_shots = load_shots()
    if args.list:
        for s in all_shots:
            print(s["id"], "-", s.get("scene"), "(guide: %s)" % s["guide"]["page"] if "guide" in s else "")
        return 0
    if args.guide and args.shaders:
        print("guide clips use the plain look, leave out --shaders", file=sys.stderr)
        return 2
    shots = [s for s in all_shots if ("guide" in s) == args.guide]

    only = [s.strip() for s in args.only.split(",") if s.strip()] if args.only else []
    known = [s["id"] for s in shots]
    for o in only:
        if o not in known:
            print("unknown shot id:", o, file=sys.stderr)
            return 2
    wanted = only or known

    plain_dir = os.path.join(RELEASE, mod_version())
    out_dir = os.path.join(plain_dir, "shaders") if args.shaders else plain_dir
    if args.guide:
        out_dir = os.path.join(plain_dir, "guide")
    run_dir = SHADER_RUN_DIR if args.shaders else RUN_DIR
    os.makedirs(out_dir, exist_ok=True)
    os.makedirs(run_dir, exist_ok=True)
    if args.shaders:
        prepare_shader_dir(run_dir)
    result_file = os.path.join(out_dir, "studio_result.json")
    if os.path.isfile(result_file):
        os.remove(result_file)
    for sid in wanted:
        stale = os.path.join(out_dir, sid + ".png")
        if os.path.isfile(stale):
            os.remove(stale)
        stale_frames = os.path.join(out_dir, sid)
        if os.path.isdir(stale_frames):
            shutil.rmtree(stale_frames)

    with open(os.path.join(run_dir, "options.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(OPTIONS)
    cfg = {"shots": SHOTS, "out": out_dir}
    if only or args.guide:
        cfg["only"] = ",".join(wanted)
    with open(os.path.join(run_dir, "studio_run.json"), "w", encoding="utf-8") as f:
        json.dump(cfg, f)

    copied = []
    code = 1
    try:
        os.makedirs(DEVTEST_OUT, exist_ok=True)
        for name in STUDIO_CLASSES:
            dst = os.path.join(DEVTEST_OUT, name)
            shutil.copyfile(os.path.join(DEVTESTS, name), dst)
            copied.append(dst)
        code = run_client(args.timeout, args.shaders)
    finally:
        for p in copied:
            if os.path.isfile(p):
                os.remove(p)
        if os.path.isdir(DEVTEST_OUT) and not os.listdir(DEVTEST_OUT):
            os.rmdir(DEVTEST_OUT)

    failed = {}
    warnings = {}
    if os.path.isfile(result_file):
        with open(result_file, encoding="utf-8") as f:
            result = json.load(f)
        failed = result.get("failed", {})
        warnings = result.get("warnings", {})
    else:
        print("no studio_result.json, the run did not finish (gradle exit code %d)" % code, file=sys.stderr)
        failed = {sid: "run did not finish" for sid in wanted}

    sheet_ids = known if not only else wanted
    sheet = contact_sheet(out_dir, sheet_ids)
    print("output:", out_dir)
    if sheet:
        print("contact sheet:", sheet)
    if args.shaders:
        cmp_path = compare_sheet(plain_dir, out_dir, sheet_ids, os.path.join(out_dir, "compare.png"))
        if cmp_path:
            print("compare:", cmp_path)
    if args.guide and not args.no_sheets:
        build_guide_sheets(out_dir, [s for s in shots if s["id"] in wanted and s["id"] not in failed])
    if warnings:
        print("shots taken with warnings:")
        for k, v in warnings.items():
            print("  %s: %s" % (k, v))
    if failed:
        print("failed shots:")
        for k, v in failed.items():
            print("  %s: %s" % (k, v))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
