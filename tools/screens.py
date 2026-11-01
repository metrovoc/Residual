"""The field notes' page for every screen, made from tools/screen_pages.py, which says what each part of a screen is for.

    python3 tools/screens.py pages               # writes guide/screens/ in both languages
    python3 tools/screens.py script en|zh > S    # a rehearsal script that opens every screen in every state and shoots it
    python3 tools/screens.py shots FOLDER        # from a run's screenshots: guide/shots/NAME.png and .json, and _zh_cn's
    python3 tools/screens.py check [--building]  # lists anything without a page, a page gone stale, or a page without its shots

A page's parts go by the names the harness knows them by (client/Parts.java, Named in src/rehearsal), so the script checks
that each one is on its screen before it shoots. `shots` crops a shot to the screen and keeps where the parts are, in the
screen's own pixels, for residual:Shot; it refuses shots older than the last change to the textures, models or UI kit, as
the clips' encoder does, and needs Pillow. With --building, a shot still to take or a screen with no entry yet is listed and
does not fail.
"""
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
from screen_pages import INDEX, SCREENS, K, T  # noqa: E402

RES = ROOT / "src/main/resources/assets/residual"
GUIDE = RES / "guides/residual/guide"
FOLDER = {"en": GUIDE, "zh": GUIDE / "_zh_cn"}
LANG = {code: json.loads((RES / f"lang/{file}.json").read_text(encoding="utf-8")) for code, file in (("en", "en_us"), ("zh", "zh_cn"))}
COLON = {"en": ": ", "zh": "："}
CLIENT = ROOT / "src/main/java/io/github/metrovoc/residual/client"
# what a shot depends on: an older one shows what the game no longer draws
LOOKS = ["src/main/resources/assets/residual/textures", "src/main/resources/assets/residual/models", "src/main/java/io/github/metrovoc/residual/client/Ui.java"]


def say(text, code):
    if isinstance(text, K):
        value = LANG[code][text.key]
        if "%" in value:
            raise SystemExit(f"{text.key} takes arguments, so it cannot stand on a page")
        # what a tooltip may say freely means something on a page
        return re.sub(r"([<{*_\\])", r"\\\1", value)
    return text.en if code == "en" else text.zh


def clip_alt(screen, code):
    """The words a clip already has on its own page, so the two never differ."""
    name = screen["clip"]
    for page in sorted(FOLDER[code].rglob("*.md")):
        if code == "en" and "_zh_cn" in page.parts:
            continue
        found = re.search(rf'<residual:Clip name="{name}" alt="([^"]*)"', page.read_text(encoding="utf-8"))
        if found and page.parent.name != "screens":
            return found.group(1)
    raise SystemExit(f"no page shows clip {name}")


def part(p, code):
    title, text = say(p["title"], code), p["text"] and say(p["text"], code)
    words = f"**{title}**" + (COLON[code] + text if text else "")
    return f'<residual:Part name="{p["name"]}">\n  {words}\n</residual:Part>'


def indent(text, by):
    return "\n".join(by + line if line else "" for line in text.split("\n"))


def shot(state, screen, code):
    parts = "\n".join(part(p, code) for p in state["parts"])
    name = f'{screen["id"]}_{state["id"]}'
    return f'<residual:Shot name="{name}" alt="{say(state["alt"], code)}">\n{indent(parts, "  ")}\n</residual:Shot>'


def state_text(state, screen, code):
    return f'{say(state["caption"], code)}\n\n{shot(state, screen, code)}'


def page(screen, position, code):
    lines = ["---", "navigation:", f'  title: {say(screen["nav"], code)}', f'  icon: {screen["icon"]}', "  parent: screens/index.md",
             f"  position: {position}", "---", "", f'# {say(screen["title"], code)}', "", say(screen["intro"], code), ""]
    if screen.get("clip"):
        lines += [f'<residual:Clip name="{screen["clip"]}" alt="{clip_alt(screen, code)}" />', ""]
    states = screen["states"]
    if len(states) == 1:
        lines += [state_text(states[0], screen, code), ""]
    else:
        steps = "\n\n".join(f'  <residual:Step>\n{indent(state_text(s, screen, code), "    ")}\n  </residual:Step>' for s in states)
        lines += ["<residual:Steps>", steps, "</residual:Steps>", ""]
    return "\n".join(lines)


def index(code):
    return "\n".join(["---", "navigation:", f'  title: {say(INDEX["nav"], code)}', "  icon: bench", "  position: 115", "---", "",
                      f'# {say(INDEX["nav"], code)}', "", say(INDEX["intro"], code), "", "<SubPages />", ""])


def written(code):
    """Every page in one language, by file."""
    return {"index.md": index(code), **{f'{screen["id"]}.md': page(screen, position, code) for position, screen in enumerate(SCREENS)}}


def pages():
    for code, folder in FOLDER.items():
        out = folder / "screens"
        out.mkdir(exist_ok=True)
        for file, text in written(code).items():
            (out / file).write_text(text, encoding="utf-8")
    print(f"{len(SCREENS)} screens, {sum(len(s['states']) for s in SCREENS)} states, in both languages")


def script(code):
    lines = ["# Every screen in every state, shot, for the field notes' pages: tools/screens.py wrote this, and takes the shots from here.",
             "# Each part a page names is checked before the shot, so a part renamed or gone fails the run.",
             "world", "gui 2", "cmd gamerule send_command_feedback false", "cmd gamerule show_advancement_messages false", "cmd gamerule advance_time false",
             "cmd gamerule spawn_mobs false", "fly on", "cmd time set noon", "wait 40", "cmd clear @s"]
    if code == "zh":
        lines += ["lang zh_cn", "wait 40"]
    prefix = "zh_" if code == "zh" else ""
    for at, screen in enumerate(SCREENS):
        x, z = at % 7 * 20, at // 7 * 20
        lines += [f'# ---- {screen["id"]}']
        lines += screen["open"](x, z)
        for state in screen["states"]:
            lines += [f"# {state['id']}"]
            # a state that reopens its screen says where: {sx} {sz} is the block beside the first, {ax} {az} the one before it, {x2} {x4} ... the column so far along
            lines += [line.format(sx=x + 2, sz=z, ax=x + 2, az=z - 1, **{f"x{d}": x + d for d in range(2, 20, 2)}) for line in state["steps"]]
            lines += ["wait 4", "hover 1 1", "wait 2"]
            lines += [f'expect part {p["name"]} 1' for p in state["parts"]]
            lines += [f'shot screen_{prefix}{screen["id"]}_{state["id"]}']
        lines += ["close", "wait 4", "cmd clear @s"]
    lines += ["quit"]
    print("\n".join(lines))


def newest_look():
    stamps = [subprocess.run(["git", "log", "-1", "--format=%ct", "--", path], cwd=ROOT, capture_output=True, text=True).stdout.strip() for path in LOOKS]
    return max(int(s) for s in stamps if s)


def shots(folder):
    from PIL import Image

    folder, oldest = Path(folder), newest_look()
    made, missing = 0, []
    for code, prefix in (("en", ""), ("zh", "zh_")):
        out = FOLDER[code] / "shots"
        out.mkdir(exist_ok=True)
        for screen in SCREENS:
            for state in screen["states"]:
                name = f'{screen["id"]}_{state["id"]}'
                png, sidecar = folder / f"screen_{prefix}{name}.png", folder / f"screen_{prefix}{name}.parts.json"
                if not png.exists() or not sidecar.exists():
                    missing.append(f"{code} {name}")
                    continue
                if png.stat().st_mtime < oldest:
                    raise SystemExit(f"{png}: older than the last change to the textures, models or UI kit, shoot it again")
                info = json.loads(sidecar.read_text(encoding="utf-8"))
                named = {p["name"]: info["parts"][p["name"]] for p in state["parts"] if p["name"] in info["parts"]}
                frame = info.get("window") or info["parts"]["window"]
                boxes = [frame, *named.values()]
                x0, y0 = min(b[0] for b in boxes) - 2, min(b[1] for b in boxes) - 2
                x1, y1 = max(b[0] + b[2] for b in boxes) + 2, max(b[1] + b[3] for b in boxes) + 2
                scale = info["scale"]
                image = Image.open(png).convert("RGB")
                image.crop((round(x0 * scale), round(y0 * scale), round(x1 * scale), round(y1 * scale))).save(out / f"{name}.png", optimize=True)
                placed = {key: [b[0] - x0, b[1] - y0, b[2], b[3]] for key, b in named.items()}
                (out / f"{name}.json").write_text(json.dumps({"width": x1 - x0, "height": y1 - y0, "parts": placed}, indent=1), encoding="utf-8")
                made += 1
    print(f"{made} shots placed" + (f"; not shot: {', '.join(missing)}" if missing else ""))


def check(building):
    """Every screen class has a page in both languages, saying what the language files now say, and every state of a page its shot."""
    classes = sorted(p.stem for p in CLIENT.glob("*Screen.java"))
    known = {s["class"]: s for s in SCREENS}
    problems, waiting = [], []
    for name in classes:
        if name not in known:
            (print if building else problems.append)(f"no page for {name}")
    for name in known:
        if name not in classes:
            problems.append(f"a page for {name}, which the game no longer has")
    for code, folder in FOLDER.items():
        made = written(code)
        for file, text in made.items():
            path = folder / "screens" / file
            if not path.exists():
                problems.append(f"{code}: no page {file}: run tools/screens.py pages")
            elif path.read_text(encoding="utf-8") != text:
                problems.append(f"{code}: {file} no longer says what the tooltips and tools/screen_pages.py do: run tools/screens.py pages")
        problems += [f"{code}: {path.name} is a page for no screen" for path in (folder / "screens").glob("*.md") if path.name not in made]
        for screen in SCREENS:
            for state in screen["states"]:
                name = f'{screen["id"]}_{state["id"]}'
                if not (folder / "shots" / f"{name}.png").exists():
                    waiting.append(f"{code} {name}")
    for line in problems:
        print(line)
    if waiting:
        print(f"{len(waiting)} shots still to take ({'left for the verifier' if building else 'a release needs them'}): {', '.join(waiting)}")
    print(f"{len(classes)} screens checked")
    return 1 if problems or (waiting and not building) else 0


def main():
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    if command == "pages":
        pages()
    elif command == "script":
        script(sys.argv[2])
    elif command == "shots":
        shots(sys.argv[2])
    elif command == "check":
        sys.exit(check("--building" in sys.argv))
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
