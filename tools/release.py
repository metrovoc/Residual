"""Mirrors a release of Residual from GitHub onto its other pages. The release workflow runs it after each release:

    python3 tools/release.py notes      # the release's notes as the stores show them
    python3 tools/release.py modrinth   # the Modrinth project: README, summary, links, license, icon and gallery
    python3 tools/release.py discord    # announces the release on the Discord server

notes needs TAG; modrinth needs MODRINTH_ID and MODRINTH_TOKEN; discord needs TAG, DISCORD_WEBHOOK, MODRINTH_ID and
CURSEFORGE_ID. All read the repository from GitHub, with GITHUB_TOKEN when it is set. CurseForge has no API for its
project page: `python3 tools/release.py description` prints the README as the stores show it, to paste there.
"""

import hashlib
import json
import mimetypes
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

REPO = "metrovoc/Residual"
ROOT = Path(__file__).resolve().parent.parent


def call(method, url, body=None, content_type="application/json", headers=None):
    if isinstance(body, dict):
        body = json.dumps(body).encode()
    request = urllib.request.Request(url, body, method=method, headers={
        "User-Agent": f"{REPO} release workflow", **({"Content-Type": content_type} if body else {}), **(headers or {})})
    try:
        with urllib.request.urlopen(request) as response:
            text = response.read()
    except urllib.error.HTTPError as e:
        sys.exit(f"{method} {url.split('?')[0]}: {e.code} {e.read().decode(errors='replace')}")
    return json.loads(text) if text else None


def github(path=""):
    token = os.environ.get("GITHUB_TOKEN")
    return call("GET", f"https://api.github.com/repos/{REPO}{path}", headers={"Authorization": f"Bearer {token}"} if token else None)


def modrinth(method, path="", body=None, content_type="application/json"):
    return call(method, f"https://api.modrinth.com/v2/project/{os.environ['MODRINTH_ID']}{path}", body, content_type,
                {"Authorization": os.environ["MODRINTH_TOKEN"]})


def store(markdown, ref):
    """Markdown from GitHub as the stores show it, what it links to and shows coming from GitHub at `ref`. The stores
    allow no downloads but their own, so a link to one keeps its words, and a row of links goes: they show their own."""
    markdown = re.sub(r"^\[[^\]]*\]\([^)]*\)(?: · \[[^\]]*\]\([^)]*\))+\n\n", "", markdown, flags=re.M)
    downloads = rf"https://(?:github\.com/{REPO}/releases|modrinth\.com|(?:www\.)?curseforge\.com)\b[^)]*"
    markdown = re.sub(rf"(?<!!)\[([^\]]*)\]\({downloads}\)", r"\1", markdown)
    raw = f"https://raw.githubusercontent.com/{REPO}/{ref}/"
    markdown = re.sub(r'(<img src=")(?!https?://)', rf"\g<1>{raw}", markdown)
    markdown = re.sub(r"(!\[[^\]]*\]\()(?!https?://)", rf"\g<1>{raw}", markdown)
    markdown = re.sub(r"((?<!!)\[[^\]]*\]\()(?!https?://|#)", rf"\g<1>https://github.com/{REPO}/blob/{ref}/", markdown)
    # GitHub's alerts are plain quotes elsewhere
    return re.sub(r"^> \[!(\w+)\]$", lambda m: f"> **{m[1].title()}:**", markdown, flags=re.M)


def head():
    return os.environ.get("GITHUB_SHA") or subprocess.run(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True, text=True, check=True).stdout.strip()


def pictures(readme):
    """The README's pictures from docs/images, in order, each with its alt text."""
    found = re.findall(r'!\[([^\]]*)\]\((docs/images/[^)]+)\)|<img src="(docs/images/[^"]+)" alt="([^"]*)"', readme)
    return [(path or path2, alt or alt2) for alt, path, path2, alt2 in found]


def gallery(project, readme):
    """The gallery holds the README's pictures, the first featured; Modrinth names each file by its SHA-1."""
    wanted = {}
    for order, (path, alt) in enumerate(pictures(readme)):
        data = (ROOT / path).read_bytes()
        title, _, caption = alt.partition(": ")
        meta = {"title": title, "featured": str(order == 0).lower(), "ordering": str(order)}
        if caption:
            meta["description"] = caption[:1].upper() + caption[1:]
        wanted[hashlib.sha1(data).hexdigest()] = (path, data, meta)
    for item in project["gallery"]:
        held = wanted.pop(item["raw_url"].rsplit("/", 1)[1].split(".")[0], None)
        shown = {"title": item["title"], "featured": str(item["featured"]).lower(), "ordering": str(item["ordering"])}
        if item["description"]:
            shown["description"] = item["description"]
        if held is None:
            modrinth("DELETE", "/gallery?" + urllib.parse.urlencode({"url": item["url"]}))
        elif shown != held[2]:
            modrinth("PATCH", "/gallery?" + urllib.parse.urlencode({"url": item["url"], **held[2]}))
    for path, data, meta in wanted.values():
        query = urllib.parse.urlencode({"ext": Path(path).suffix[1:], **meta})
        modrinth("POST", f"/gallery?{query}", data, mimetypes.guess_type(path)[0])


def page():
    readme = (ROOT / "README.md").read_text()
    repo = github()
    discord = re.search(r"https://discord\.gg/\w+", readme)
    modrinth("PATCH", body={
        "description": repo["description"],
        "body": store(readme, head()),
        "source_url": repo["html_url"],
        "issues_url": repo["html_url"] + "/issues",
        "wiki_url": repo["homepage"] or None,
        "discord_url": discord[0] if discord else None,
        "license_id": repo["license"]["spdx_id"],
    })
    project = modrinth("GET")
    icon = (ROOT / "docs/images/icon.png").read_bytes()
    if hashlib.sha1(icon).hexdigest() not in (project["icon_url"] or ""):
        modrinth("PATCH", "/icon?ext=png", icon, "image/png")
    gallery(project, readme)


def announce():
    tag = os.environ["TAG"]
    release = github(f"/releases/tags/{tag}")
    slug = call("GET", f"https://api.modrinth.com/v2/project/{os.environ['MODRINTH_ID']}")["slug"]
    links = " · ".join([
        f"[GitHub]({release['html_url']})",
        f"[Modrinth](https://modrinth.com/mod/{slug}/version/{tag.removeprefix('v')})",
        f"[CurseForge](https://www.curseforge.com/projects/{os.environ['CURSEFORGE_ID']})",
    ])
    summary = release["body"].strip().split("\n\n")[0]
    call("POST", os.environ["DISCORD_WEBHOOK"], {"embeds": [{
        "title": release["name"], "url": release["html_url"], "description": f"{summary}\n\n{links}", "color": 0x40E0D0}]})


def notes():
    tag = os.environ["TAG"]
    print(store(github(f"/releases/tags/{tag}")["body"], tag))


def description():
    print(store((ROOT / "README.md").read_text(), head()))


if __name__ == "__main__":
    {"notes": notes, "modrinth": page, "discord": announce, "description": description}[sys.argv[1]]()
