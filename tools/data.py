"""Writes the mod's JSON and structure templates: blockstates, models, item definitions, loot tables, tags,
recipes, advancements and the ruins.

    uv run python tools/data.py
"""
import json
import shutil
from pathlib import Path

import ruins

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
NS = "residual"

HORIZONTAL = {"north": {}, "south": {"y": 180}, "east": {"y": 90}, "west": {"y": 270}}
GENERATED = ["assets/residual/blockstates", "assets/residual/models", "assets/residual/items",
             "data/residual/loot_table", "data/residual/recipe", "data/residual/advancement", "data/residual/tags",
             "data/residual/worldgen", "data/residual/structure", "data/minecraft/tags"]


def rid(path):
    return f"{NS}:{path}"


def write(rel, obj):
    path = RES / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n")


write.root = RES


def item_definition(name, model):
    write(f"assets/{NS}/items/{name}.json", {"model": {"type": "minecraft:model", "model": model}})


def glowing(texture):
    """Whether tools/art.py split this face's lit pixels into a _lights texture."""
    return (RES / "assets" / NS / "textures" / (texture.split(":")[1] + "_lights.png")).exists()


def box(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


def cube(name, front, side):
    """A full block that stands upright, its front to the north. A face with lights gets them on a second, full-bright
    layer filling the holes art.py left in it, the way AE2 and Mekanism light theirs."""
    faces = {"north": front, "south": side, "east": side, "west": side, "up": side, "down": side}
    lit = [f for f, t in faces.items() if glowing(t)]
    if not lit:
        write(f"assets/{NS}/models/block/{name}.json", {"parent": "minecraft:block/cube", "textures": {"particle": side} | faces})
        return
    bright = {"block_light": 15, "sky_light": 15}
    write(f"assets/{NS}/models/block/{name}.json", {
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout",
        "textures": {"particle": side} | faces | {f"{f}_lights": faces[f] + "_lights" for f in lit},
        "elements": [box([0, 0, 0], [16, 16, 16], {f: {"texture": f"#{f}", "cullface": f} for f in faces}),
                     box([0, 0, 0], [16, 16, 16], {f: {"texture": f"#{f}_lights", "cullface": f, "neoforge_data": bright}
                                                   for f in lit}) | {"shade": False}]})


def core():
    """Dark while it is empty; holding a drive, its faces light."""
    variants = {}
    for drive, suffix in (("false", ""), ("true", "_on")):
        cube(f"model_core{suffix}", rid(f"block/model_core_front{suffix}"), rid(f"block/model_core_side{suffix}"))
        for facing, rot in HORIZONTAL.items():
            variants[f"drive={drive},facing={facing}"] = {"model": rid(f"block/model_core{suffix}"), **rot}
    write(f"assets/{NS}/blockstates/model_core.json", {"variants": variants})
    item_definition("model_core", rid("block/model_core"))


def console():
    """A console in a model's hall: its screen, toward the walkway (east), shows what it does."""
    roles = ["exit", "task", "run", "step", "pace", "rate", "restart", "example", "width", "positions", "heads", "norm",
             "add_attention", "add_feed_forward", "switch", "remove"]
    variants = {}
    for role in roles:
        cube(f"console_{role}", rid(f"block/console_{role}"), rid("block/console_side"))
        variants[f"role={role}"] = {"model": rid(f"block/console_{role}"), **HORIZONTAL["east"]}
    write(f"assets/{NS}/blockstates/console.json", {"variants": variants})


def items():
    for name in ("archive_drive", "archive_shard", "field_log", "model_drive", "model_drive_written"):
        write(f"assets/{NS}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": rid(f"item/{name}")}})
    for name in ("archive_drive", "archive_shard", "field_log"):
        item_definition(name, rid(f"item/{name}"))
    # a model drive's cells light once it holds a model
    write(f"assets/{NS}/items/model_drive.json", {"model": {
        "type": "minecraft:condition", "property": "minecraft:has_component", "component": rid("model"),
        "on_true": {"type": "minecraft:model", "model": rid("item/model_drive_written")},
        "on_false": {"type": "minecraft:model", "model": rid("item/model_drive")}}})
    # the Wayfinder's needle, frame by frame as vanilla's compass: toward its ruin when tuned, idly turning when not
    frames = []
    for k in range(32):
        write(f"assets/{NS}/models/item/wayfinder_{k:02d}.json",
              {"parent": "minecraft:item/generated", "textures": {"layer0": rid(f"item/wayfinder_{k:02d}")}})
    for i in range(33):
        frames.append({"threshold": 0.0 if i == 0 else i - 0.5,
                       "model": {"type": "minecraft:model", "model": rid(f"item/wayfinder_{(i + 16) % 32:02d}")}})
    write(f"assets/{NS}/items/wayfinder.json", {"model": {
        "type": "minecraft:condition", "property": "minecraft:has_component", "component": "minecraft:lodestone_tracker",
        "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:compass", "target": "lodestone", "scale": 32.0,
                    "entries": frames},
        "on_false": {"type": "minecraft:range_dispatch", "property": "minecraft:compass", "target": "none", "scale": 32.0,
                     "entries": frames}}})
    # the guide book is GuideME's item; its guide definition points at this model
    write(f"assets/{NS}/models/item/guide.json", {"parent": "minecraft:item/generated", "textures": {"layer0": rid("item/guide")}})
    item_definition("guide", rid("item/guide"))


# ---------------------------------------------------------------- data

# blocks a player can break and carry; a hall's console is neither
BLOCKS = ["model_core"]


def loot_tables():
    for name in BLOCKS:
        entry = {"type": "minecraft:item", "name": rid(name)}
        write(f"data/{NS}/loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [entry],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": rid(f"blocks/{name}")})


def tags():
    write("data/minecraft/tags/block/mineable/pickaxe.json", {"values": [rid(n) for n in BLOCKS]})


def shaped(name, pattern, key, count=1):
    result = {"id": rid(name)} | ({"count": count} if count > 1 else {})
    write(f"data/{NS}/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": "misc",
                                             "key": key, "pattern": pattern, "result": result})


def shapeless(name, ingredients, count=1, file=None):
    result = {"id": rid(name)} | ({"count": count} if count > 1 else {})
    write(f"data/{NS}/recipe/{file or name}.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                                     "ingredients": ingredients, "result": result})


def recipes():
    shaped("model_core", ["IGI", "CBC", "IGI"], {"I": "minecraft:iron_ingot", "G": "minecraft:gold_ingot", "C": "minecraft:copper_ingot",
                                                 "B": "minecraft:redstone_block"})
    shaped("model_drive", ["IRI", "IAI", "III"], {"I": "minecraft:iron_ingot", "R": "minecraft:redstone", "A": "minecraft:amethyst_shard"})
    shaped("archive_drive", ["IRI", "IBI", "III"], {"I": "minecraft:iron_ingot", "R": "minecraft:redstone", "B": "minecraft:book"})
    shapeless("field_log", ["minecraft:paper", "minecraft:paper", "minecraft:wheat", "#minecraft:planks"])
    shaped("wayfinder", [" A ", "CKC", " C "], {"A": "minecraft:amethyst_shard", "C": "minecraft:copper_ingot",
                                                 "K": "minecraft:compass"})


def advancement(path, parent, icon, criteria, frame="task", hidden=False, toast=True, components=None):
    icon_obj = {"id": icon if ":" in icon else rid(icon)} | ({"components": components} if components else {})
    write(f"data/{NS}/advancement/{path}.json", {
        "parent": rid(parent),
        "criteria": criteria,
        "display": {"icon": icon_obj, "title": {"translate": f"advancements.residual.{path.replace('/', '.')}.title"},
                    "description": {"translate": f"advancements.residual.{path.replace('/', '.')}.description"},
                    "frame": frame, "hidden": hidden, "show_toast": toast, "announce_to_chat": frame != "task"},
        "requirements": [[k] for k in criteria]})


def awarded():
    return {"done": {"trigger": "minecraft:impossible"}}


def has(item):
    return {"got": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": rid(item)}]}}}


def advancements():
    write(f"data/{NS}/advancement/root.json", {
        "criteria": {"joined": {"trigger": "minecraft:tick"}},
        "display": {"icon": {"id": rid("model_core")}, "title": {"translate": "advancements.residual.root.title"},
                    "description": {"translate": "advancements.residual.root.description"},
                    "background": "minecraft:block/tuff_bricks", "show_toast": False, "announce_to_chat": False},
        "requirements": [["joined"]]})
    advancement("model/core", "root", "model_core", has("model_core"))
    advancement("model/drive", "model/core", "model_drive", awarded())
    advancement("model/inside", "model/drive", "model_drive", awarded(), frame="goal",
                components={rid("model"): [0, 0, 0, 0]})
    advancement("ruins/found", "root", "minecraft:chiseled_tuff",
                {"found": {"trigger": "minecraft:location", "conditions": {"player": [{"condition": "minecraft:entity_properties",
                    "entity": "this", "predicate": {"location": {"structures": "#residual:ruins"}}}]}}})
    advancement("archive/shard", "ruins/found", "archive_shard", has("archive_shard"))
    advancement("ruins/wayfinder", "archive/shard", "wayfinder", awarded())
    advancement("archive/drive", "archive/shard", "archive_drive", has("archive_drive"))


if __name__ == "__main__":
    for rel in GENERATED:
        shutil.rmtree(RES / rel, ignore_errors=True)
    core()
    console()
    items()
    loot_tables()
    tags()
    recipes()
    advancements()
    ruins.write_all(write, rid)
