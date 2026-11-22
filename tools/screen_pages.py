"""What tools/screens.py makes each screen's page from: for every screen, how a rehearsal opens it, the states worth showing and
what each named part is for, in both languages. K takes the words from the language files, so a tooltip and its page say the
same. A new screen goes here, and tools/screens.py check lists any that has not.
"""
from dataclasses import dataclass


@dataclass(frozen=True)
class T:
    en: str
    zh: str


@dataclass(frozen=True)
class K:
    key: str


def P(name, title, text=None):
    return {"name": name, "title": title, "text": text}


def state(id, caption, alt, parts, steps=()):
    return {"id": id, "caption": caption, "alt": alt, "parts": parts, "steps": list(steps)}


def screen(id, cls, icon, nav, title, intro, open, states, clip=None):
    return {"id": id, "class": cls, "icon": icon, "nav": nav, "title": title, "intro": intro, "open": open, "states": states, "clip": clip}


INDEX = {
    "nav": T("Screens", "界面"),
    "intro": T("Every screen, part by part. The numbers on a picture are the parts listed under it, and pointing at a number says what it is.",
               "每个界面，一部分一部分地看。图上的数字对应图下列出的部分，指向一个数字就能看到它是什么。"),
}


def shard_open(x, z):
    # a shard learns which record it is the first time it is held
    return ["cmd give @s residual:archive_shard", "slot residual:archive_shard", "wait 20", "useitem", "wait 10"]


SHARD = screen(
    "archive_shard", "ShardScreen", "archive_shard", T("Archive Shard", "档案残片"), T("An Archive Shard, read", "读档案残片"),
    T("Use an Archive Shard to read it. [The Archive](../archive.md) tells what a shard is a piece of.", "用一块档案残片来读取它。[档案](../archive.md)会说明一块残片是什么的一部分。"),
    shard_open,
    [state("read", T("A record of the old world, as far as it can still be read.", "旧世界的一份记录，能读多少算多少。"),
           T("A shard's record, some of it worn away", "残片上的记录，有些已经磨损"),
           [P("legible", T("How much can be read", "可辨认多少"), T("The share of the record still legible.", "这份记录还能看清的比例。")),
            P("text", T("Its text", "它的文字"), T("Lost blocks show worn. Scroll for the rest.", "失落的文字块显示为磨损。滚动可看后面的内容。"))])])


def wayfinder_open(x, z):
    return ["cmd give @s residual:wayfinder", "slot residual:wayfinder", "useitem", "wait 10"]


WAYFINDER = screen(
    "wayfinder", "WayfinderScreen", "wayfinder", T("Wayfinder", "寻路仪"), T("The Wayfinder's screen", "寻路仪的界面"),
    T("Use a Wayfinder to open it. [Ruins](../ruins.md) tells what it finds.", "使用寻路仪即可打开。它能找到什么，见[遗迹](../ruins.md)。"),
    wayfinder_open,
    [state("kinds", T("The kinds of place it knows of.", "它知道的几类地方。"),
           T("The kinds of place a Wayfinder can look for", "寻路仪能找的几类地方"),
           [P("window", T("The places", "地方"), T("Click one and it looks for the nearest. Shards tell of more places.", "点击一个，它就去找最近的。残片里还写着别的地方。"))])],
    clip="wayfinder")


SCREENS = [SHARD, WAYFINDER]
