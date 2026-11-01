"""Turns the places a rehearsal script clicks into the names of what is there, from the console log of a run of it.

    python3 tools/name_clicks.py LOG SCRIPT [OUT]

The harness logs what a step by place hit (`that is NAME at DX DY of W H`). A small part or a widget is clicked at its
centre as well as anywhere, so it goes by its name alone; a place on a large part, a canvas or a grid, keeps where on it
(`canvas+60+40`). A place with nothing there stays a place. Writes to OUT, or prints the script.
"""
import re
import sys
from pathlib import Path

STEP = re.compile(r"\[rehearsal\] (.*)$")
THAT = re.compile(r"that is (\S+) at (-?\d+) (-?\d+) of (\d+) (\d+)$")
BY_PLACE = ("click", "rclick", "altclick", "hover", "drag")


def named(said):
    """The word for a place, from what the log said of it, or None."""
    m = THAT.match(said)
    if not m:
        return None
    name, dx, dy, w, h = m.group(1), int(m.group(2)), int(m.group(3)), int(m.group(4)), int(m.group(5))
    # a button, a slot or a row of a list does the same wherever it is clicked; anything larger keeps where on it
    return name if h <= 20 or w * h <= 40 * 40 else f"{name}+{dx}+{dy}"


def renamed(step, said):
    words = step.split()
    if words[0] not in BY_PLACE or not re.fullmatch(r"-?\d+", words[1]):
        return step
    if words[0] == "drag":
        if len(said) < 2 or not all(named(s) for s in said[:2]):
            return step
        return " ".join(["drag", named(said[0]), named(said[1]), *words[5:]])
    if not said or not named(said[0]):
        return step
    return " ".join([words[0], named(said[0]), *words[3:]])


def main(log, script, out=None):
    # the log repeats nothing of the script but its steps in order, each followed by what it hit
    played = []
    for line in Path(log).read_text(encoding="utf-8", errors="replace").splitlines():
        m = STEP.search(line)
        if not m:
            continue
        if m.group(1).startswith("that is "):
            if played:
                played[-1][1].append(m.group(1))
        else:
            played.append((m.group(1), []))
    lines = Path(script).read_text(encoding="utf-8").splitlines()
    at = 0
    for i, line in enumerate(lines):
        step = line.strip()
        if not step or step.startswith("#"):
            continue
        while at < len(played) and played[at][0] != step:
            at += 1
        if at == len(played):
            sys.exit(f"the log stops before line {i + 1}: {step}")
        lines[i] = renamed(step, played[at][1])
        at += 1
    text = "\n".join(lines) + "\n"
    if out:
        Path(out).write_text(text, encoding="utf-8")
    else:
        print(text, end="")


if __name__ == "__main__":
    main(*sys.argv[1:])
