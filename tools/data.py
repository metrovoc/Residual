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
       "softmax", "split_heads", "merge_heads", "shift", "concat", "attention", "mlp", "transformer", "rms_norm", "swiglu",
       "rotary", "qk_norm", "z_loss", "modern_attention", "modern_block", "two_token_head"]
ARITY = {"position": 0, "add": 2, "multiply": 2, "matmul": 2, "concat": 2, "qk_norm": 2}
DYES = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple",
        "blue", "brown", "green", "red", "black"]
CABLES = ["cable"] + [f"{d}_cable" for d in DYES]
# see learn/Benchmark.java and registry/ModItems.java
BENCHMARKS = ["forecasting", "recall", "reversal", "induction", "sorting", "reading", "modular"]
DATASETS = {"forecast_dataset": "minecraft:clock", "storage_dataset": "minecraft:chest",
            "errand_dataset": "minecraft:compass", "pattern_dataset": "minecraft:bricks", "sorting_dataset": "#minecraft:signs",
            "modular_dataset": "minecraft:repeater"}
# what an operator takes at the crafting table, beside a Tensor Casing and redstone
OP_PARTS = {"embedding": "book", "position": "compass", "linear": "iron_ingot", "activation": "redstone_torch", "add": "copper_ingot",
            "multiply": "gold_ingot", "norm": "quartz", "matmul": "lapis_lazuli", "scale": "stick", "causal_mask": "glass_pane",
            "softmax": "sugar", "split_heads": "shears", "merge_heads": "string", "shift": "repeater", "concat": "lever",
            "attention": "amethyst_shard", "mlp": "comparator", "transformer": "diamond", "rms_norm": "flint", "swiglu": "glowstone_dust",
            "rotary": "clock", "qk_norm": "feather", "z_loss": "coal", "modern_attention": "emerald", "modern_block": "gold_block",
            "two_token_head": "paper"}
WRECKS = ruins.WRECKS

FACING = {"north": {}, "south": {"y": 180}, "east": {"y": 90}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
HORIZONTAL = {k: FACING[k] for k in ("north", "south", "east", "west")}
# as block/cube_directional turns its sides, clockwise from outside
FLOW_TURNS = {"east": 90, "west": 270, "down": 180}
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


def glowing(texture):
    """Whether tools/art.py split this face's lit pixels into a _lights texture."""
    return (RES / "assets" / NS / "textures" / (texture.split(":")[1] + "_lights.png")).exists()


def cube(name, north, south, side, up=None, down=None, flow=False):
    """A full block. A face with lights gets them on a second, full-bright layer filling the holes art.py left in it,
    the way AE2 and Mekanism light theirs. With flow, a block that can face any way and carries something from its back
    to its front turns its sides as vanilla's cube_directional does: the top of each side's texture points to the front,
    so what is drawn on them runs the same way all round the block."""
    faces = {"north": north, "south": south, "east": side, "west": side, "up": up or side, "down": down or side}
    lit = [f for f, t in faces.items() if glowing(t)]
    if not lit:
        write(f"assets/{NS}/models/block/{name}.json", {"parent": f"minecraft:block/{'cube_directional' if flow else 'cube'}",
                                                        "textures": {"particle": side} | faces})
        return
    turns = FLOW_TURNS if flow else {}
    turned = lambda f: {"rotation": turns[f]} if f in turns else {}
    bright = {"block_light": 15, "sky_light": 15}
    write(f"assets/{NS}/models/block/{name}.json", {
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout",
        "textures": {"particle": side} | faces | {f"{f}_lights": faces[f] + "_lights" for f in lit},
        "elements": [box([0, 0, 0], [16, 16, 16], {f: {"texture": f"#{f}", "cullface": f} | turned(f) for f in faces}),
                     box([0, 0, 0], [16, 16, 16], {f: {"texture": f"#{f}_lights", "cullface": f, "neoforge_data": bright} | turned(f)
                                                   for f in lit}) | {"shade": False}]})


def operators():
    for op in OPS:
        side = rid(f"block/{op}_side")
        back = rid(f"block/{op}_back") if ARITY.get(op, 1) == 0 else rid("block/machine_back")
        cube(op, rid(f"block/{op}_front"), back, side, flow=True)
        write(f"assets/{NS}/blockstates/{op}.json", {"variants": facing_variants(rid(f"block/{op}"))})
        item_definition(op, rid(f"block/{op}"))


def core():
    for suffix in ("", "_on"):
        side = rid(f"block/model_core_side{suffix}")
        cube(f"model_core{suffix}", rid(f"block/model_core_front{suffix}"), side, side, flow=True)
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


def forge():
    side, top = rid("block/bpe_forge_side"), rid("block/bpe_forge_top")
    for suffix in ("", "_on"):
        cube(f"bpe_forge{suffix}", rid(f"block/bpe_forge_front{suffix}"), side, side, top, rid("block/machine_back"))
    variants = {}
    for working, suffix in (("false", ""), ("true", "_on")):
        for key, value in facing_variants(rid(f"block/bpe_forge{suffix}"), facings=HORIZONTAL).items():
            variants[f"{key},working={working}"] = value
    write(f"assets/{NS}/blockstates/bpe_forge.json", {"variants": variants})
    item_definition("bpe_forge", rid("block/bpe_forge"))


def forecaster(name="forecaster"):
    """A machine that faces the player and lights at work: the Forecaster and the two instruments."""
    side, top = rid(f"block/{name}_side"), rid(f"block/{name}_top")
    for suffix in ("", "_on"):
        cube(f"{name}{suffix}", rid(f"block/{name}_front{suffix}"), side, side, top, rid("block/machine_back"))
    variants = {}
    for working, suffix in (("false", ""), ("true", "_on")):
        for key, value in facing_variants(rid(f"block/{name}{suffix}"), facings=HORIZONTAL).items():
            variants[f"{key},working={working}"] = value
    write(f"assets/{NS}/blockstates/{name}.json", {"variants": variants})
    item_definition(name, rid(f"block/{name}"))


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
    flat = ["weight_crystal", "checkpoint", "archive_drive", "archive_shard", "salvaged_model", "field_log"] + list(DATASETS)
    for name in flat:
        write(f"assets/{NS}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": rid(f"item/{name}")}})
        item_definition(name, rid(f"item/{name}"))
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
    write(f"assets/{NS}/models/item/tensor_probe.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": rid("item/tensor_probe")}})
    item_definition("tensor_probe", rid("item/tensor_probe"))
    # a tokenizer shows tokens once a vocabulary is written on it
    for name in ("tokenizer", "tokenizer_written"):
        write(f"assets/{NS}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": rid(f"item/{name}")}})
    write(f"assets/{NS}/items/tokenizer.json", {"model": {
        "type": "minecraft:condition", "property": "minecraft:has_component", "component": rid("vocab"),
        "on_true": {"type": "minecraft:model", "model": rid("item/tokenizer_written")},
        "on_false": {"type": "minecraft:model", "model": rid("item/tokenizer")}}})
    # the guide book is GuideME's item; its guide definition points at this model
    write(f"assets/{NS}/models/item/guide.json", {"parent": "minecraft:item/generated", "textures": {"layer0": rid("item/guide")}})
    item_definition("guide", rid("item/guide"))


# ---------------------------------------------------------------- data

MACHINE_BLOCKS = ["model_core", "tensor_casing", "reader", "stream", "bpe_forge", "forecaster", "stream_scope", "trial_rig"]


def blocks():
    return OPS + MACHINE_BLOCKS + CABLES


def loot_tables():
    for name in blocks():
        entry = {"type": "minecraft:item", "name": rid(name)}
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
    for op, part in OP_PARTS.items():
        shapeless(op, [rid("tensor_casing"), "minecraft:redstone", f"minecraft:{part}"])
    shaped("stream", ["KGK", "RPR", "KGK"], {"K": rid("tensor_casing"), "G": "minecraft:glass", "R": "minecraft:redstone",
                                             "P": "minecraft:prismarine_crystals"}, 4)
    shaped("checkpoint", [" A ", "AWA", " A "], {"A": "minecraft:amethyst_shard", "W": rid("weight_crystal")})
    shaped("archive_drive", ["IRI", "IBI", "III"], {"I": "minecraft:iron_ingot", "R": "minecraft:redstone", "B": "minecraft:book"})
    shaped("bpe_forge", ["KBK", "GWG", "KFK"], {"K": rid("tensor_casing"), "B": "minecraft:book", "G": "minecraft:glass",
                                                "W": rid("weight_crystal"), "F": "minecraft:blast_furnace"})
    shapeless("tokenizer", ["minecraft:paper", "minecraft:paper", "minecraft:ink_sac", "minecraft:amethyst_shard"], 2)
    for dataset, part in DATASETS.items():
        shapeless(dataset, ["minecraft:paper", "minecraft:paper", "minecraft:redstone", part])
    shaped("forecaster", ["GLG", "KWK", "KCK"], {"G": "minecraft:glass_pane", "L": "minecraft:clock", "K": rid("tensor_casing"),
                                                 "W": rid("weight_crystal"), "C": "minecraft:composter"})
    shapeless("field_log", ["minecraft:paper", "minecraft:paper", "minecraft:wheat", "#minecraft:planks"])
    shaped("wayfinder", [" A ", "CKC", " C "], {"A": "minecraft:amethyst_shard", "C": "minecraft:copper_ingot",
                                                 "K": "minecraft:compass"})
    shaped("reader", ["GBG", "KWK", "KKK"], {"G": "minecraft:glass_pane", "B": "minecraft:book", "K": rid("tensor_casing"),
                                             "W": rid("weight_crystal")})
    shaped("stream_scope", ["GSG", "KWK", "KRK"], {"G": "minecraft:glass_pane", "S": "minecraft:spyglass", "K": rid("tensor_casing"),
                                                   "W": rid("weight_crystal"), "R": "minecraft:redstone"})
    shaped("trial_rig", ["GTG", "KWK", "KRK"], {"G": "minecraft:glass_pane", "T": "minecraft:target", "K": rid("tensor_casing"),
                                                "W": rid("weight_crystal"), "R": "minecraft:redstone"})


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
        "display": {"icon": {"id": rid("add")}, "title": {"translate": "advancements.residual.root.title"},
                    "description": {"translate": "advancements.residual.root.description"},
                    "background": "minecraft:block/tuff_bricks", "show_toast": False, "announce_to_chat": False},
        "requirements": [["joined"]]})
    advancement("ruins/found", "root", "minecraft:chiseled_tuff",
                {"found": {"trigger": "minecraft:location", "conditions": {"player": [{"condition": "minecraft:entity_properties",
                    "entity": "this", "predicate": {"location": {"structures": "#residual:ruins"}}}]}}})
    advancement("archive/shard", "ruins/found", "archive_shard", has("archive_shard"))
    advancement("ruins/wayfinder", "archive/shard", "wayfinder", awarded())
    advancement("archive/drive", "archive/shard", "archive_drive", has("archive_drive"))
    advancement("archive/tokenizer", "archive/drive", "tokenizer", awarded(), components={rid("vocab"): [70, 58]})
    advancement("model/saved", "root", "checkpoint", awarded())
    for a in BENCHMARKS:
        parent = "archive/drive" if a == "reading" else "model/saved"
        advancement(f"benchmark/{a}", parent, "checkpoint", awarded(), frame="goal")
    advancement("model/complete", "benchmark/reading", "checkpoint", awarded(), frame="challenge")
    advancement("model/modern", "model/saved", "modern_block", awarded(), frame="goal")
    advancement("model/stream", "root", "stream", placed("stream"))
    advancement("recipe/planned", "model/saved", "minecraft:clock", awarded())
    advancement("recipe/narrow", "model/saved", "weight_crystal", awarded(), frame="goal")
    advancement("recipe/muon", "model/saved", "minecraft:compass", awarded(), frame="goal")
    advancement("recipe/packed", "model/saved", "minecraft:bundle", awarded(), frame="goal")
    advancement("recipe/second_head", "model/saved", "two_token_head", awarded())
    advancement("deploy/reader", "archive/drive", "reader", placed("reader"))
    advancement("forecast/watching", "root", "forecaster", placed("forecaster"))
    advancement("forecast/ahead", "forecast/watching", "minecraft:furnace", awarded(), frame="goal")
    advancement("discovery/phase_change", "root", "pattern_dataset", awarded(), frame="challenge")
    advancement("discovery/grokked", "root", "modular_dataset", awarded(), frame="challenge")
    advancement("scope/lens", "model/saved", "stream_scope", awarded())
    advancement("scope/superposed", "scope/lens", "stream_scope", awarded(), frame="goal")
    advancement("scope/patched", "scope/lens", "stream_scope", awarded(), frame="goal")
    advancement("scope/steered", "scope/patched", "stream_scope", awarded(), frame="goal")
    advancement("scope/features", "scope/lens", "stream_scope", awarded(), frame="goal")
    advancement("scope/graph", "scope/features", "stream_scope", awarded(), frame="goal")
    advancement("failure/rig", "model/saved", "trial_rig", placed("trial_rig"))
    advancement("failure/extracted", "failure/rig", "trial_rig", awarded(), frame="goal")
    advancement("failure/backdoor", "failure/rig", "trial_rig", awarded(), frame="goal")
    advancement("failure/forgot", "failure/rig", "trial_rig", awarded(), frame="goal")
    advancement("failure/collapse", "failure/rig", "trial_rig", awarded(), frame="goal")
    advancement("evaluation/contaminated", "failure/rig", "trial_rig", awarded(), frame="goal")
    advancement("evaluation/seeds", "model/saved", "checkpoint", awarded())
    advancement("evaluation/first", "model/saved", "checkpoint", awarded(), frame="goal")
    advancement("wreck/found", "ruins/found", "salvaged_model", has("salvaged_model"))
    for w in WRECKS:
        advancement(f"wreck/{w}", "wreck/found", "salvaged_model", awarded(), frame="goal",
                    components={"residual:wreck": w})


if __name__ == "__main__":
    for rel in GENERATED:
        shutil.rmtree(RES / rel, ignore_errors=True)
    operators()
    core()
    simple("tensor_casing")
    reader()
    forge()
    forecaster()
    forecaster("stream_scope")
    forecaster("trial_rig")
    stream()
    cables()
    items()
    loot_tables()
    tags()
    recipes()
    advancements()
    ruins.write_all(write, rid)
