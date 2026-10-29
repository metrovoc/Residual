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

# Op ids in registry order, with their arity where it isn't 1 (see graph/Op.java)
OPS = ["embedding", "position", "linear", "activation", "add", "multiply", "norm", "matmul", "scale", "causal_mask",
       "softmax", "split_heads", "merge_heads", "shift", "concat", "attention", "mlp", "transformer"]
ARITY = {"position": 0, "add": 2, "multiply": 2, "matmul": 2, "concat": 2}
DYES = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple",
        "blue", "brown", "green", "red", "black"]
CABLES = ["cable"] + [f"{d}_cable" for d in DYES]
# see learn/Benchmark.java and registry/ModItems.java
BENCHMARKS = ["reacting", "recall", "reversal", "induction", "orders", "reading"]
DATASETS = {"field_dataset": "minecraft:wheat_seeds", "storage_dataset": "minecraft:chest", "errand_dataset": "minecraft:compass",
          "pattern_dataset": "minecraft:bricks", "orders_dataset": "minecraft:writable_book"}
SCHEMATICS = ["schematic_relays"]
# see bench/Specs.java, in the order the Bench lists them, each with the spec whose module it builds on first
SPECS = {"mean": None, "softmax": None, "matmul": None, "linear": "matmul", "gelu": None, "layer_norm": "mean",
         "attention": "softmax", "mlp": "linear", "transformer": "attention"}
WRECKS = ruins.WRECKS

FACING = {"north": {}, "south": {"y": 180}, "east": {"y": 90}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
HORIZONTAL = {k: FACING[k] for k in ("north", "south", "east", "west")}
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


def facing_variants(model, extra="", facings=FACING):
    return {f"facing={f}{extra}": {"model": model, **rot} for f, rot in facings.items()}


def cube(name, north, south, side, up=None, down=None):
    write(f"assets/{NS}/models/block/{name}.json", {
        "parent": "minecraft:block/cube",
        "textures": {"particle": side, "north": north, "south": south, "east": side, "west": side,
                     "up": up or side, "down": down or side}})


# ---------------------------------------------------------------- blocks

def operators():
    for op in OPS:
        side = rid(f"block/{op}_side")
        back = rid(f"block/{op}_back") if ARITY.get(op, 1) == 0 else rid("block/machine_back")
        cube(op, rid(f"block/{op}_front"), back, side)
        write(f"assets/{NS}/blockstates/{op}.json", {"variants": facing_variants(rid(f"block/{op}"))})
        item_definition(op, rid(f"block/{op}"))


def core():
    for suffix in ("", "_on"):
        side = rid(f"block/model_core_side{suffix}")
        cube(f"model_core{suffix}", rid(f"block/model_core_front{suffix}"), side, side)
    variants = {}
    for running, suffix in (("false", ""), ("true", "_on")):
        for key, value in facing_variants(rid(f"block/model_core{suffix}")).items():
            variants[f"{key},running={running}"] = value
    write(f"assets/{NS}/blockstates/model_core.json", {"variants": variants})
    item_definition("model_core", rid("block/model_core"))


def simple(name):
    write(f"assets/{NS}/models/block/{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": rid(f"block/{name}")}})
    write(f"assets/{NS}/blockstates/{name}.json", {"variants": {"": {"model": rid(f"block/{name}")}}})
    item_definition(name, rid(f"block/{name}"))


def accelerator():
    for name in ("accelerator", "accelerator_hot"):
        write(f"assets/{NS}/models/block/{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": rid(f"block/{name}")}})
    write(f"assets/{NS}/blockstates/accelerator.json", {"variants": {
        "hot=false": {"model": rid("block/accelerator")}, "hot=true": {"model": rid("block/accelerator_hot")}}})
    item_definition("accelerator", rid("block/accelerator"))


def server():
    """Front shows the crystal bay: empty, loaded, or serving with its lights running."""
    side, top = rid("block/model_server_side"), rid("block/model_server_top")
    fronts = {"": "model_server_front", "_loaded": "model_server_front_loaded", "_on": "model_server_front_on"}
    for suffix, front in fronts.items():
        cube(f"model_server{suffix}", rid(f"block/{front}"), side, side, top, top)
    variants = {}
    for loaded in ("false", "true"):
        for serving in ("false", "true"):
            suffix = "_on" if serving == "true" and loaded == "true" else "_loaded" if loaded == "true" else ""
            for key, value in facing_variants(rid(f"block/model_server{suffix}"), facings=HORIZONTAL).items():
                variants[f"{key},loaded={loaded},serving={serving}"] = value
    write(f"assets/{NS}/blockstates/model_server.json", {"variants": variants})
    item_definition("model_server", rid("block/model_server"))


def reader():
    side, top = rid("block/reader_side"), rid("block/reader_top")
    for suffix in ("", "_on"):
        cube(f"reader{suffix}", rid(f"block/reader_front{suffix}"), side, side, top, rid("block/machine_back"))
    variants = {}
    for reading, suffix in (("false", ""), ("true", "_on")):
        for key, value in facing_variants(rid(f"block/reader{suffix}"), facings=HORIZONTAL).items():
            variants[f"{key},reading={reading}"] = value
    write(f"assets/{NS}/blockstates/reader.json", {"variants": variants})
    item_definition("reader", rid("block/reader"))


def bench():
    """A table: a breadboard top, four legs and a shelf."""
    side = {"uv": [0, 0, 16, 4], "texture": "#side"}
    top = {"up": {"uv": [0, 0, 16, 16], "texture": "#top", "cullface": "up"},
           "down": {"uv": [0, 4, 16, 16], "texture": "#side"}}
    top |= {f: side | {"cullface": f} for f in ("north", "south", "east", "west")}
    leg = {f: {"uv": [0, 4, 2, 16], "texture": "#side"} for f in ("north", "south", "east", "west")}
    leg["down"] = {"uv": [0, 4, 2, 6], "texture": "#side", "cullface": "down"}
    shelf = {f: {"uv": [2, 4, 14, 5], "texture": "#side"} for f in ("north", "south", "east", "west")}
    shelf |= {f: {"uv": [2, 4, 14, 16], "texture": "#side"} for f in ("up", "down")}
    write(f"assets/{NS}/models/block/bench.json", {
        "parent": "minecraft:block/block",
        "textures": {"top": rid("block/bench_top"), "side": rid("block/bench_side"), "particle": rid("block/bench_side")},
        "elements": [box([0, 12, 0], [16, 16, 16], top)]
                    + [box([x, 0, z], [x + 2, 12, z + 2], leg) for x in (1, 13) for z in (1, 13)]
                    + [box([2, 3, 2], [14, 4, 14], shelf)]})
    write(f"assets/{NS}/blockstates/bench.json", {"variants": facing_variants(rid("block/bench"), facings=HORIZONTAL)})
    item_definition("bench", rid("block/bench"))


def fabricator():
    side, top = rid("block/fabricator_side"), rid("block/fabricator_top")
    for suffix in ("", "_on"):
        cube(f"fabricator{suffix}", rid(f"block/fabricator_front{suffix}"), side, side, top, rid("block/machine_back"))
    variants = {}
    for working, suffix in (("false", ""), ("true", "_on")):
        for key, value in facing_variants(rid(f"block/fabricator{suffix}"), facings=HORIZONTAL).items():
            variants[f"{key},working={working}"] = value
    write(f"assets/{NS}/blockstates/fabricator.json", {"variants": variants})
    item_definition("fabricator", rid("block/fabricator"))


def stream():
    """The channel seen end on at both ends; on the four sides, chevrons pointing the way it flows. The side texture
    points up, so each side face turns it toward north, the model's flow; the blockstate turns the whole block."""
    side = {"uv": [0, 0, 16, 16], "texture": "#side"}
    faces = {"north": {"uv": [0, 0, 16, 16], "texture": "#end"}, "south": {"uv": [0, 0, 16, 16], "texture": "#end"},
             "up": side, "down": side | {"rotation": 180}, "east": side | {"rotation": 90}, "west": side | {"rotation": 270}}
    write(f"assets/{NS}/models/block/stream.json", {
        "parent": "minecraft:block/block",
        "textures": {"side": rid("block/stream_side"), "end": rid("block/stream_end"), "particle": rid("block/stream_side")},
        "elements": [box([0, 0, 0], [16, 16, 16], {f: v | {"cullface": f} for f, v in faces.items()})]})
    write(f"assets/{NS}/blockstates/stream.json", {"variants": facing_variants(rid("block/stream"))})
    item_definition("stream", rid("block/stream"))


def box(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


def all_faces(uv_side, uv_end, texture="#relay", cull_down=False):
    faces = {f: {"uv": uv_side, "texture": texture} for f in ("north", "south", "east", "west")}
    faces["up"] = {"uv": uv_end, "texture": texture}
    faces["down"] = {"uv": uv_end, "texture": texture} | ({"cullface": "down"} if cull_down else {})
    return faces


def relay():
    """A footing, a mast and a lamp head; the texture keeps each part in its own band."""
    write(f"assets/{NS}/models/block/relay.json", {
        "parent": "minecraft:block/block", "textures": {"relay": rid("block/relay"), "particle": rid("block/relay")},
        "elements": [
            box([4, 0, 4], [12, 2, 12], all_faces([0, 14, 8, 16], [8, 8, 16, 16], cull_down=True)),
            box([6.5, 2, 6.5], [9.5, 11, 9.5], all_faces([0, 5, 3, 14], [3, 5, 6, 8])),
            box([5, 11, 5], [11, 16, 11], all_faces([0, 0, 6, 5], [10, 0, 16, 6])),
        ]})
    write(f"assets/{NS}/blockstates/relay.json", {"variants": {"": {"model": rid("block/relay")}}})
    item_definition("relay", rid("block/relay"))


def cables():
    """A 4 px joint plus one arm per connected side. Every face samples the plus-shaped cable texture."""
    joint = {f: {"uv": [6, 6, 10, 10], "texture": "#cable"} for f in ("north", "south", "east", "west", "up", "down")}
    write(f"assets/{NS}/models/block/template_cable_joint.json", {
        "parent": "minecraft:block/block", "textures": {"particle": "#cable"},
        "elements": [box([6, 6, 6], [10, 10, 10], joint)]})
    arm = {"north": {"uv": [6, 6, 10, 10], "texture": "#cable", "cullface": "north"},
           "east": {"uv": [10, 6, 16, 10], "texture": "#cable"}, "west": {"uv": [0, 6, 6, 10], "texture": "#cable"},
           "up": {"uv": [6, 0, 10, 6], "texture": "#cable"}, "down": {"uv": [6, 10, 10, 16], "texture": "#cable"}}
    write(f"assets/{NS}/models/block/template_cable_arm.json", {
        "parent": "minecraft:block/block", "textures": {"particle": "#cable"},
        "elements": [box([6, 6, 0], [10, 10, 6], arm)]})
    straight = {"north": {"uv": [0, 6, 16, 10], "texture": "#cable"}, "south": {"uv": [0, 6, 16, 10], "texture": "#cable"},
                "up": {"uv": [0, 6, 16, 10], "texture": "#cable"}, "down": {"uv": [0, 6, 16, 10], "texture": "#cable"},
                "east": {"uv": [6, 6, 10, 10], "texture": "#cable"}, "west": {"uv": [6, 6, 10, 10], "texture": "#cable"}}
    write(f"assets/{NS}/models/item/template_cable.json", {
        "parent": "minecraft:block/block", "textures": {"particle": "#cable"},
        "elements": [box([0, 6, 6], [16, 10, 10], straight)]})

    for name in CABLES:
        textures = {"cable": rid(f"block/{name}")}
        write(f"assets/{NS}/models/block/{name}_joint.json", {"parent": rid("block/template_cable_joint"), "textures": textures})
        write(f"assets/{NS}/models/block/{name}_arm.json", {"parent": rid("block/template_cable_arm"), "textures": textures})
        write(f"assets/{NS}/models/item/{name}.json", {"parent": rid("item/template_cable"), "textures": textures})
        parts = [{"apply": {"model": rid(f"block/{name}_joint")}}]
        for side, rot in FACING.items():
            parts.append({"when": {side: "true"}, "apply": {"model": rid(f"block/{name}_arm"), **rot}})
        write(f"assets/{NS}/blockstates/{name}.json", {"multipart": parts})
        item_definition(name, rid(f"item/{name}"))


def items():
    flat = (["weight_crystal", "wafer", "checkpoint", "archive_drive", "archive_shard", "salvaged_model",
             "agent", "relay_card", "terminal"] + list(DATASETS) + SCHEMATICS)
    for name in flat:
        write(f"assets/{NS}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": rid(f"item/{name}")}})
        item_definition(name, rid(f"item/{name}"))
    for name in ("tensor_probe", "survey_rod"):
        write(f"assets/{NS}/models/item/{name}.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": rid(f"item/{name}")}})
        item_definition(name, rid(f"item/{name}"))
    # a photomask looks etched once a design is recorded on it
    for name in ("photomask", "photomask_recorded"):
        write(f"assets/{NS}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": rid(f"item/{name}")}})
    write(f"assets/{NS}/items/photomask.json", {"model": {
        "type": "minecraft:condition", "property": "minecraft:has_component", "component": rid("mask"),
        "on_true": {"type": "minecraft:model", "model": rid("item/photomask_recorded")},
        "on_false": {"type": "minecraft:model", "model": rid("item/photomask")}}})
    # the guide book is GuideME's item; its guide definition points at this model
    write(f"assets/{NS}/models/item/guide.json", {"parent": "minecraft:item/generated", "textures": {"layer0": rid("item/guide")}})
    item_definition("guide", rid("item/guide"))


# ---------------------------------------------------------------- data

MACHINE_BLOCKS = ["model_core", "accelerator", "tensor_casing", "model_server", "reader", "relay", "bench", "fabricator", "stream"]


def blocks():
    return OPS + MACHINE_BLOCKS + CABLES


def loot_tables():
    for name in blocks():
        entry = {"type": "minecraft:item", "name": rid(name)}
        if name in OPS:
            # an unfused machine stays unfused when it is picked up
            entry["functions"] = [{"function": "minecraft:copy_components", "source": "block_entity", "include": [rid("fused")]}]
        write(f"data/{NS}/loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [entry],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": rid(f"blocks/{name}")})


def tags():
    write("data/minecraft/tags/block/mineable/pickaxe.json", {"values": [rid(n) for n in OPS + MACHINE_BLOCKS]})
    write(f"data/{NS}/tags/item/cables.json", {"values": [rid(n) for n in CABLES]})


def shaped(name, pattern, key, count=1):
    result = {"id": rid(name)} | ({"count": count} if count > 1 else {})
    write(f"data/{NS}/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": "misc",
                                             "key": key, "pattern": pattern, "result": result})


def shapeless(name, ingredients, count=1, file=None):
    result = {"id": rid(name)} | ({"count": count} if count > 1 else {})
    write(f"data/{NS}/recipe/{file or name}.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                                     "ingredients": ingredients, "result": result})


def recipes():
    shaped("tensor_casing", ["ICI", "CRC", "ICI"], {"I": "minecraft:iron_ingot", "C": "minecraft:copper_ingot", "R": "minecraft:redstone"}, 4)
    shaped("cable", [" G ", "CRC", " G "], {"G": "minecraft:glass_pane", "C": "minecraft:copper_ingot", "R": "minecraft:redstone"}, 8)
    write(f"data/{NS}/recipe/cable_from_colored.json", {
        "type": "minecraft:crafting_shaped", "category": "misc", "key": {"C": "#residual:cables", "W": "minecraft:water_bucket"},
        "pattern": ["CCC", "CWC", "CCC"], "result": {"id": rid("cable"), "count": 8}})
    for dye in DYES:
        shaped(f"{dye}_cable", ["CCC", "CDC", "CCC"], {"C": "#residual:cables", "D": f"minecraft:{dye}_dye"}, 8)
    shaped("weight_crystal", [" L ", "RGR", " L "], {"L": "minecraft:lapis_lazuli", "R": "minecraft:redstone", "G": "minecraft:glass"}, 2)
    shaped("model_core", ["KGK", "WBW", "KGK"], {"K": rid("tensor_casing"), "G": "minecraft:gold_ingot",
                                                 "W": rid("weight_crystal"), "B": "minecraft:redstone_block"})
    shaped("tensor_probe", ["  G", " R ", "I  "], {"G": "minecraft:glass_pane", "R": "minecraft:redstone", "I": "minecraft:iron_ingot"})
    # every machine comes out of the Fabricator
    shaped("bench", ["GRG", "KTK", "I I"], {"G": "minecraft:glass_pane", "R": "minecraft:repeater", "K": rid("tensor_casing"),
                                            "T": "minecraft:crafting_table", "I": "minecraft:iron_ingot"})
    shaped("fabricator", ["KSK", "GPG", "KFK"], {"K": rid("tensor_casing"), "S": "minecraft:lantern", "G": "minecraft:glass",
                                                 "P": "minecraft:piston", "F": "minecraft:furnace"})
    shapeless("wafer", ["minecraft:sand", "minecraft:sand", "minecraft:redstone", "minecraft:glass_pane"], 2)
    shapeless("photomask", ["minecraft:glass", "minecraft:iron_nugget", "minecraft:iron_nugget"])
    shaped("stream", ["KGK", "RPR", "KGK"], {"K": rid("tensor_casing"), "G": "minecraft:glass", "R": "minecraft:redstone",
                                             "P": "minecraft:prismarine_crystals"}, 4)

    shaped("checkpoint", [" A ", "AWA", " A "], {"A": "minecraft:amethyst_shard", "W": rid("weight_crystal")})
    shaped("archive_drive", ["IRI", "IBI", "III"], {"I": "minecraft:iron_ingot", "R": "minecraft:redstone", "B": "minecraft:book"})
    for dataset, part in DATASETS.items():
        shapeless(dataset, ["minecraft:paper", "minecraft:paper", "minecraft:redstone", part])
    shaped("agent", [" L ", "CRC", "C C"], {"L": "minecraft:lantern", "C": "minecraft:copper_ingot", "R": "minecraft:redstone"})
    shaped("survey_rod", ["  A", " S ", "S  "], {"A": "minecraft:amethyst_shard", "S": "minecraft:stick"})
    shaped("reader", ["GBG", "KWK", "KKK"], {"G": "minecraft:glass_pane", "B": "minecraft:book", "K": rid("tensor_casing"),
                                             "W": rid("weight_crystal")})
    shapeless("model_server", [rid("tensor_casing"), rid("tensor_casing"), rid("weight_crystal"), rid("weight_crystal"),
                               "minecraft:gold_ingot", "minecraft:redstone_block", rid("schematic_relays")])
    shapeless("relay", ["minecraft:lightning_rod", "minecraft:lantern", "minecraft:copper_ingot", "minecraft:redstone",
                        rid("schematic_relays")], 2)
    shapeless("relay_card", ["minecraft:paper", "minecraft:copper_ingot", "minecraft:redstone", rid("schematic_relays")], 2)
    shapeless("terminal", [rid("relay_card"), "minecraft:glass_pane", "minecraft:copper_ingot", "minecraft:redstone"])


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


def placed(block):
    return {"placed": {"trigger": "minecraft:placed_block", "conditions": {"location": [
        {"condition": "minecraft:location_check", "predicate": {"block": {"blocks": rid(block)}}}]}}}


def advancements():
    write(f"data/{NS}/advancement/root.json", {
        "criteria": {"joined": {"trigger": "minecraft:tick"}},
        "display": {"icon": {"id": rid("model_core")}, "title": {"translate": "advancements.residual.root.title"},
                    "description": {"translate": "advancements.residual.root.description"},
                    "background": "minecraft:block/tuff_bricks", "show_toast": False, "announce_to_chat": False},
        "requirements": [["joined"]]})
    advancement("ruins/found", "root", "minecraft:chiseled_tuff",
                {"found": {"trigger": "minecraft:location", "conditions": {"player": [{"condition": "minecraft:entity_properties",
                    "entity": "this", "predicate": {"location": {"structures": "#residual:ruins"}}}]}}})
    advancement("archive/shard", "ruins/found", "archive_shard", has("archive_shard"))
    advancement("archive/drive", "archive/shard", "archive_drive", has("archive_drive"))
    advancement("model/saved", "root", "checkpoint", awarded())
    for a in BENCHMARKS:
        parent = "archive/drive" if a == "reading" else "model/saved"
        advancement(f"benchmark/{a}", parent, "checkpoint", awarded(), frame="goal")
    advancement("model/complete", "benchmark/reading", "checkpoint", awarded(), frame="challenge")
    advancement("agent/made", "root", "agent", has("agent"))
    advancement("agent/order", "agent/made", "minecraft:writable_book", awarded(), frame="goal")
    advancement("deploy/server", "agent/made", "model_server", placed("model_server"))
    advancement("deploy/relay", "deploy/server", "relay", placed("relay"))
    advancement("deploy/reader", "archive/drive", "reader", placed("reader"))
    advancement("bench/made", "root", "bench", placed("bench"))
    advancement("model/stream", "bench/made", "stream", placed("stream"))
    advancement("bench/fabricator", "bench/made", "fabricator", placed("fabricator"))
    for spec, after in SPECS.items():
        frame = "goal" if spec == "transformer" else "task"
        advancement(f"bench/{spec}", f"bench/{after}" if after else "bench/made", "photomask", awarded(), frame=frame,
                    components={rid("mask"): {"spec": spec}})
    advancement("bench/all", "bench/transformer", "photomask", awarded(), frame="challenge", components={rid("mask"): {"spec": "transformer"}})
    advancement("wreck/found", "ruins/found", "salvaged_model", has("salvaged_model"))
    for w in WRECKS:
        advancement(f"wreck/{w}", "wreck/found", "salvaged_model", awarded(), frame="goal",
                    components={"residual:wreck": w})


if __name__ == "__main__":
    for rel in GENERATED:
        shutil.rmtree(RES / rel, ignore_errors=True)
    operators()
    core()
    accelerator()
    simple("tensor_casing")
    server()
    reader()
    relay()
    bench()
    fabricator()
    stream()
    cables()
    items()
    loot_tables()
    tags()
    recipes()
    advancements()
    ruins.write_all(write, rid)
