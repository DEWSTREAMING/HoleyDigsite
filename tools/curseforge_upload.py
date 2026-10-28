"""Upload a mod jar or a modpack zip to CurseForge through its upload API.

Reads the API token from the CURSEFORGE_TOKEN environment variable and never prints it.
Nothing is sent unless --upload is given; without it the script shows what it would upload.

  python tools/curseforge_upload.py check
      Checks the token and finds the game version ids (read only).
  python tools/curseforge_upload.py mod 1.26.0 [--release-type alpha] [--upload]
      Uploads build/libs/holeydigsite-1.26.0.jar with the "## 1.26.0" section of docs/CHANGELOG.md.
  python tools/curseforge_upload.py pack 1.0.1 --from <exported pack zip> --project <modpack project id>
         [--mod-file <file id>] [--author <name>] [--put <path in zip>=<local file>] [--changelog <file.md>] [--upload]
      Sets the pack version (and the Holey Digsite file id, if given) in the zip's manifest.json, saves the new zip
      next to the old one and uploads it.
"""
import argparse
import io
import json
import os
import re
import sys
import urllib.error
import urllib.request
import uuid
import zipfile

API = "https://minecraft.curseforge.com/api"
MOD_PROJECT = 1762674
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GAME_VERSIONS = ["1.21.1", "NeoForge", "Java 21", "Client", "Server"]
OPTIONAL = ["curios", "jei", "skyblock-builder", "ftb-teams-forge"]


def token():
    t = os.environ.get("CURSEFORGE_TOKEN", "").strip()
    if not t:
        sys.exit("CURSEFORGE_TOKEN is not set (restart the app or terminal after adding it)")
    return t


def request(path, data=None, content_type=None):
    req = urllib.request.Request(API + path, data=data, method="POST" if data is not None else "GET")
    req.add_header("X-Api-Token", token())
    req.add_header("User-Agent", "holeydigsite-upload")
    if content_type:
        req.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            return json.loads(r.read().decode("utf-8") or "null")
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "replace")[:500]
        sys.exit(f"CurseForge said {e.code}: {body}")


def version_ids():
    types = {t["id"]: t["slug"] for t in request("/game/version-types")}
    found = {}
    for v in request("/game/versions"):
        slug = types.get(v["gameVersionTypeID"], "")
        name = v["name"]
        if name == "1.21.1" and not slug.startswith("minecraft-1-21"):
            continue  # same name on other games' version lists
        if name in GAME_VERSIONS and name not in found:
            found[name] = v["id"]
    missing = [n for n in GAME_VERSIONS if n not in found]
    if missing:
        sys.exit(f"game versions not found on CurseForge: {', '.join(missing)}")
    return found


def changelog_section(version):
    text = open(os.path.join(ROOT, "docs", "CHANGELOG.md"), encoding="utf-8").read()
    m = re.search(rf"^## {re.escape(version)}\b.*?$(.*?)(?=^## |\Z)", text, re.M | re.S)
    if not m:
        sys.exit(f"no '## {version}' section in docs/CHANGELOG.md")
    title = m.group(0).splitlines()[0][3:].strip()
    return f"## {title}\n\n{m.group(1).strip()}\n"


def multipart(metadata, filename, payload):
    boundary = uuid.uuid4().hex
    out = io.BytesIO()
    out.write(f'--{boundary}\r\nContent-Disposition: form-data; name="metadata"\r\n'
              f'Content-Type: application/json\r\n\r\n'.encode())
    out.write(json.dumps(metadata).encode("utf-8"))
    out.write(f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
              f'Content-Type: application/octet-stream\r\n\r\n'.encode())
    out.write(payload)
    out.write(f"\r\n--{boundary}--\r\n".encode())
    return out.getvalue(), f"multipart/form-data; boundary={boundary}"


def send(project, path, metadata, upload):
    shown = dict(metadata, changelog=metadata["changelog"][:300] + ("..." if len(metadata["changelog"]) > 300 else ""))
    print(f"project {project}\nfile    {path} ({os.path.getsize(path) // 1024} KB)")
    print(json.dumps(shown, indent=2))
    if not upload:
        print("Dry run: nothing sent. Add --upload to send it.")
        return
    body, ctype = multipart(metadata, os.path.basename(path), open(path, "rb").read())
    result = request(f"/projects/{project}/upload-file", body, ctype)
    print(f"Uploaded: file id {result.get('id')}")


def cmd_check(_):
    ids = version_ids()
    print("Token works. Game version ids:", ", ".join(f"{k}={v}" for k, v in ids.items()))


def cmd_mod(a):
    jar = os.path.join(ROOT, "build", "libs", f"holeydigsite-{a.version}.jar")
    if not os.path.exists(jar):
        sys.exit(f"{jar} not found: build it first")
    ids = version_ids()
    meta = {
        "changelog": open(a.changelog, encoding="utf-8").read() if a.changelog else changelog_section(a.version),
        "changelogType": "markdown",
        "displayName": f"Holey Digsite {a.version}",
        "gameVersions": [ids[n] for n in GAME_VERSIONS],
        "releaseType": a.release_type,
        "relations": {"projects": [{"slug": s, "type": "optionalDependency"} for s in OPTIONAL]},
    }
    send(MOD_PROJECT, jar, meta, a.upload)


def cmd_pack(a):
    with zipfile.ZipFile(a.source) as z:
        entries = {n: z.read(n) for n in z.namelist()}
    if "manifest.json" not in entries:
        sys.exit("no manifest.json in that zip: is it a CurseForge export?")
    manifest = json.loads(entries["manifest.json"])
    manifest["version"] = a.version
    if a.mod_file:
        hits = [f for f in manifest.get("files", []) if f.get("projectID") == MOD_PROJECT]
        if not hits:
            sys.exit("Holey Digsite is not in the pack's manifest")
        for f in hits:
            f["fileID"] = a.mod_file
    if a.author:
        manifest["author"] = a.author
    for put in a.put or []:
        inner, _, local = put.partition("=")
        if inner not in entries:
            sys.exit(f"{inner} is not in the zip (check the path)")
        entries[inner] = open(local, "rb").read()
        print(f"replaced {inner}")
    entries["manifest.json"] = json.dumps(manifest, indent=2).encode("utf-8")
    name = manifest.get("name", "modpack")
    out = os.path.join(os.path.dirname(os.path.abspath(a.source)), f"{name}-{a.version}.zip")
    if os.path.abspath(out) == os.path.abspath(a.source):
        sys.exit("the new zip would overwrite the source zip: use a different version")
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for n, data in entries.items():
            z.writestr(n, data)
    print(f"wrote {out}")
    mc = manifest.get("minecraft", {}).get("version", "1.21.1")
    ids = version_ids()
    meta = {
        "changelog": open(a.changelog, encoding="utf-8").read() if a.changelog else f"{name} {a.version}",
        "changelogType": "markdown",
        "displayName": f"{name} {a.version}",
        "gameVersions": [ids[mc]] if mc in ids else [ids["1.21.1"]],
        "releaseType": a.release_type,
    }
    send(a.project, out, meta, a.upload)


def main():
    p = argparse.ArgumentParser(description="Upload to CurseForge (dry run unless --upload).")
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("check")
    m = sub.add_parser("mod")
    m.add_argument("version")
    p_pack = sub.add_parser("pack")
    p_pack.add_argument("version")
    p_pack.add_argument("--from", dest="source", required=True)
    p_pack.add_argument("--project", type=int, required=True)
    p_pack.add_argument("--mod-file", type=int)
    p_pack.add_argument("--author")
    p_pack.add_argument("--put", action="append", help="replace a file in the zip: <path in zip>=<local file>")
    for s in (m, p_pack):
        s.add_argument("--release-type", default="alpha", choices=["alpha", "beta", "release"])
        s.add_argument("--changelog")
        s.add_argument("--upload", action="store_true")
    a = p.parse_args()
    {"check": cmd_check, "mod": cmd_mod, "pack": cmd_pack}[a.cmd](a)


if __name__ == "__main__":
    main()
