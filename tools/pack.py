"""Builds the Residual modpack, build/pack/Residual-<version>.mrpack, for Prism Launcher or the Modrinth App.

    ./gradlew build && uv run python tools/pack.py

The companion mods are pinned to exact Modrinth versions and the launcher downloads them; Residual's own jar
travels inside the pack.
"""
import json
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
API = "https://api.modrinth.com/v2"
AGENT = "metrovoc/Residual (github.com/metrovoc/Residual)"

# Modrinth version ids, all for Minecraft 26.1.2 on NeoForge
MODS = {
    "guideme": "YNGUsefV",  # 26.1.16-beta: the guide, which Residual and AE2 both need
    "ae2": "nVSxZcom",  # 26.1.13-beta
    "jei": "7qmEzno8",  # 29.47.0.110
    "mezzconfig": "gpcHFixw",  # 0.6.9, which JEI needs
    "jade": "Pr1h4azo",  # 26.1.12
    "sodium": "zg4YQ9EL",  # 0.9.2
    "modernfix": "Ys9Tnajc",  # 5.27.25
    "ferrite-core": "LtVvw4uS",  # 9.0.0
}


def get(path):
    with urllib.request.urlopen(urllib.request.Request(API + path, headers={"User-Agent": AGENT})) as r:
        return json.loads(r.read(), strict=False)


def main():
    props = dict(line.split("=", 1) for line in (ROOT / "gradle.properties").read_text().splitlines()
                 if "=" in line and not line.startswith("#"))
    jar = ROOT / "build/libs" / f"residual-{props['minecraft_version']}-{props['mod_version']}.jar"
    if not jar.exists():
        raise SystemExit(f"{jar.relative_to(ROOT)} is missing: run ./gradlew build first")

    files = []
    for slug, version_id in MODS.items():
        version = get(f"/version/{version_id}")
        project = get(f"/project/{version['project_id']}")
        file = next(f for f in version["files"] if f["primary"]) if len(version["files"]) > 1 else version["files"][0]
        files.append({
            "path": f"mods/{file['filename']}",
            "hashes": {"sha1": file["hashes"]["sha1"], "sha512": file["hashes"]["sha512"]},
            "env": {"client": project["client_side"], "server": project["server_side"]},
            "downloads": [file["url"]],
            "fileSize": file["size"],
        })
        print(f"{slug:14} {version['version_number']}")

    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": props["mod_version"],
        "name": "Residual",
        "summary": "Build models from the ground up, one block at a time.",
        "files": files,
        "dependencies": {"minecraft": props["minecraft_version"], "neoforge": props["neo_version"]},
    }
    out = ROOT / "build/pack" / f"Residual-{props['mod_version']}.mrpack"
    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(index, indent=2))
        z.write(jar, f"overrides/mods/{jar.name}")
    print(f"wrote {out.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
