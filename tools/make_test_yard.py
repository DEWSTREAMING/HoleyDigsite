#!/usr/bin/env python3
"""
Test Yard builder: creates a headless dedicated server run, builds stations in TestYard.java,
verifies main blocks, writes docs/test-yard/<version>.md, and installs the save into Test1.

Usage:
  python tools/make_test_yard.py              build all stations
  python tools/make_test_yard.py --only 3,7   build only specific stations
  python tools/make_test_yard.py --list       list station ids and exit
  python tools/make_test_yard.py --timeout 180
"""
import argparse
import json
import os
import shutil
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STATIONS_JSON = os.path.join(ROOT, "tools", "yard", "stations.json")
DEVTESTS = os.path.join(ROOT, "devtests")
DEVTEST_OUT = os.path.join(ROOT, "src", "main", "java", "com", "holeydigsite", "devtest")
RUN_DIR = os.path.join(ROOT, "run-yard")
TEST1_SAVES = r"C:\Users\Dewey\curseforge\minecraft\Instances\Test1\saves"
YARD_CLASSES = ["TestYard.java"]

SERVER_PROPERTIES = """# Minecraft server properties
level-name=world
level-type=minecraft\\:flat
generator-settings={"layers"\\:[{"block"\\:"minecraft\\:bedrock","height"\\:1},{"block"\\:"minecraft\\:stone","height"\\:126},{"block"\\:"minecraft\\:smooth_stone","height"\\:1}],"biome"\\:"minecraft\\:plains"}
gamemode=creative
difficulty=peaceful
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
pvp=false
white-list=false
online-mode=false
allow-flight=true
motd=Test Yard
enforce-whitelist=false
enable-command-block=true
pause-when-empty-seconds=-1
server-port=0
"""


def mod_version():
    with open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8") as f:
        for line in f:
            if line.startswith("mod_version="):
                return line.split("=", 1)[1].strip()
    return "unknown"


def load_stations():
    with open(STATIONS_JSON, encoding="utf-8") as f:
        return json.load(f)["stations"]


def server_pids():
    """PIDs of game servers started from this folder."""
    key = os.path.join(ROOT, "build", "moddev").replace("'", "''")
    cmd = (
        'Get-CimInstance Win32_Process -Filter "Name=\'java.exe\'" | '
        "Where-Object { $_.CommandLine -like '*%s*' } | ForEach-Object { $_.ProcessId }" % key
    )
    out = subprocess.run(["powershell", "-NoProfile", "-Command", cmd], capture_output=True, text=True).stdout
    return {int(x) for x in out.split() if x.isdigit()}


def kill_tree(pid):
    subprocess.run(["taskkill", "/F", "/T", "/PID", str(pid)], capture_output=True)


def run_server(timeout=180):
    before = server_pids()
    proc = subprocess.Popen(
        [os.path.join(ROOT, "gradlew.bat"), "runServer", "-PtestYard=1"],
        cwd=ROOT,
    )
    deadline = time.time() + timeout
    code = None
    try:
        while time.time() < deadline:
            code = proc.poll()
            if code is not None:
                break
            time.sleep(2)
        else:
            print("Time limit of %d s reached, stopping the server" % timeout, file=sys.stderr)
            code = -1
    finally:
        if proc.poll() is None:
            kill_tree(proc.pid)
        for pid in server_pids() - before:
            kill_tree(pid)
    return code


def write_doc_checklist(version, stations, wanted_ids):
    doc_dir = os.path.join(ROOT, "docs", "test-yard")
    os.makedirs(doc_dir, exist_ok=True)
    doc_path = os.path.join(doc_dir, f"{version}.md")

    lines = [
        f"# Test Yard {version}",
        "",
        "Walk the stations in order along the path, check each line, then report by number (e.g. `1-12 fine` or `1-9 fine, 10 wrong`).",
        "",
        "## Stations",
        "",
    ]

    for st in stations:
        if wanted_ids and st["id"] not in wanted_ids and str(st["number"]) not in wanted_ids:
            continue
        stX = (st["number"] - 1) * 12
        lines.append(f"### Station {st['number']}: {st['title']}")
        lines.append(f"Location: x = {stX}, z = 0 (`/tp @p {stX} 64 0`)")
        for check in st.get("checks", st.get("sign_lines", [])):
            lines.append(f"- [ ] {check}")
        lines.append("Dewey: [   ]")
        lines.append("")

    with open(doc_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines))
    return doc_path


def main():
    ap = argparse.ArgumentParser(description="Build Test Yard world for in-game checks")
    ap.add_argument("--only", help="comma-separated station numbers or ids")
    ap.add_argument("--list", action="store_true", help="list station ids and exit")
    ap.add_argument("--timeout", type=int, default=240, help="time limit in seconds (default 240)")
    args = ap.parse_args()

    all_stations = load_stations()
    if args.list:
        print("Available stations:")
        for s in all_stations:
            print(f"  {s['number']:2d}. {s['id']:<20} - {s['title']}")
        return 0

    wanted = [s.strip() for s in args.only.split(",") if s.strip()] if args.only else []
    version = mod_version()
    print(f"Building Test Yard for Holey Digsite v{version}...")

    # Clean old run-yard world and result
    os.makedirs(RUN_DIR, exist_ok=True)
    world_dir = os.path.join(RUN_DIR, "world")
    if os.path.isdir(world_dir):
        shutil.rmtree(world_dir)
    result_file = os.path.join(RUN_DIR, "yard_result.json")
    if os.path.isfile(result_file):
        os.remove(result_file)

    # Write eula and server.properties
    with open(os.path.join(RUN_DIR, "eula.txt"), "w", encoding="utf-8") as f:
        f.write("eula=true\n")
    with open(os.path.join(RUN_DIR, "server.properties"), "w", encoding="utf-8", newline="\n") as f:
        f.write(SERVER_PROPERTIES)

    # Write run configuration
    cfg = {
        "stations": STATIONS_JSON,
        "only": ",".join(wanted) if wanted else "",
    }
    with open(os.path.join(RUN_DIR, "yard_run.json"), "w", encoding="utf-8") as f:
        json.dump(cfg, f, indent=2)

    # Copy TestYard.java into src/main/java/com/holeydigsite/devtest/
    copied = []
    code = 1
    try:
        os.makedirs(DEVTEST_OUT, exist_ok=True)
        for name in YARD_CLASSES:
            src = os.path.join(DEVTESTS, name)
            dst = os.path.join(DEVTEST_OUT, name)
            shutil.copyfile(src, dst)
            copied.append(dst)

        code = run_server(args.timeout)
    finally:
        for p in copied:
            if os.path.isfile(p):
                os.remove(p)
        if os.path.isdir(DEVTEST_OUT) and not os.listdir(DEVTEST_OUT):
            os.rmdir(DEVTEST_OUT)

    # Inspect results
    if not os.path.isfile(result_file):
        print(f"FATAL: No yard_result.json generated (server exit code {code})", file=sys.stderr)
        return 1

    with open(result_file, encoding="utf-8") as f:
        result = json.load(f)

    failures = result.get("failures", {})
    built = result.get("built", [])

    if failures:
        print("Station building failures:", file=sys.stderr)
        for k, v in failures.items():
            print(f"  {k}: {v}", file=sys.stderr)
        return 1

    if not os.path.isfile(os.path.join(world_dir, "level.dat")):
        print(f"FATAL: World was not saved to {world_dir}", file=sys.stderr)
        return 1

    # Copy saved world to Test1
    target_save = os.path.join(TEST1_SAVES, f"Test Yard {version}")
    if os.path.isdir(target_save):
        shutil.rmtree(target_save)
    shutil.copytree(world_dir, target_save)
    print(f"Installed save: {target_save}")

    # Generate checklist document
    doc_path = write_doc_checklist(version, all_stations, set(wanted))
    print(f"Checklist written: {doc_path}")

    checks = result.get("checks", {})
    print(f"\nSUCCESS: Test Yard {version} built ({len(built)} stations verified):")
    for b in built:
        chk = checks.get(b)
        if chk:
            print(f"  [OK] {b}: {chk}")
        else:
            print(f"  [OK] {b}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
