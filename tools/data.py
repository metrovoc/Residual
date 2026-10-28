"""Writes the mod's JSON: blockstates, models, item definitions, loot tables, tags, recipes and advancements.

    uv run python tools/data.py
"""
import json
import shutil
from pathlib import Path

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
# lessons and cases in unlock order (see learn/Curriculum.java), with the insight a lesson awards
LESSONS = [("counting", "insight_counting"), ("case_shape", None), ("context", "insight_context"),
           ("case_diverge", None), ("recall", "insight_attention"), ("case_leak", None),
           ("reverse", "insight_position"), ("case_order", None), ("case_depth", None),
           ("induction", "insight_circuits"), ("language", "insight_language")]

FACING = {"north": {}, "south": {"y": 180}, "east": {"y": 90}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
GENERATED = ["assets/residual/blockstates", "assets/residual/models", "assets/residual/items",
             "data/residual/loot_table", "data/residual/recipe", "data/residual/advancement", "data/residual/tags"]


def rid(path):
    return f"{NS}:{path}"


def write(rel, obj):
    path = RES / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n")


def item_definition(name, model):
    write(f"assets/{NS}/items/{name}.json", {"model": {"type": "minecraft:model", "model": model}})


def facing_variants(model, extra=""):
    return {f"facing={f}{extra}": {"model": model, **rot} for f, rot in FACING.items()}


# ---------------------------------------------------------------- blocks

def operators():
    for op in OPS:
        side = rid(f"block/{op}_side")
        back = rid(f"block/{op}_back") if ARITY.get(op, 1) == 0 else rid("block/machine_back")
        write(f"assets/{NS}/models/block/{op}.json", {
            "parent": "minecraft:block/cube",
            "textures": {"particle": side, "north": rid(f"block/{op}_front"), "south": back,
                         "east": side, "west": side, "up": side, "down": side}})
        write(f"assets/{NS}/blockstates/{op}.json", {"variants": facing_variants(rid(f"block/{op}"))})
        item_definition(op, rid(f"block/{op}"))


def core():
    for suffix in ("", "_on"):
        side = rid(f"block/model_core_side{suffix}")
        write(f"assets/{NS}/models/block/model_core{suffix}.json", {
            "parent": "minecraft:block/cube",
            "textures": {"particle": side, "north": rid(f"block/model_core_front{suffix}"), "south": side,
                         "east": side, "west": side, "up": side, "down": side}})
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
    flat = ["weight_crystal"] + [insight for _, insight in LESSONS if insight]
    for name in flat:
        write(f"assets/{NS}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": rid(f"item/{name}")}})
        item_definition(name, rid(f"item/{name}"))
    write(f"assets/{NS}/models/item/tensor_probe.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": rid("item/tensor_probe")}})
    item_definition("tensor_probe", rid("item/tensor_probe"))
    # the guide book is GuideME's item; its guide definition points at this model
    write(f"assets/{NS}/models/item/guide.json", {"parent": "minecraft:item/generated", "textures": {"layer0": rid("item/guide")}})
    item_definition("guide", rid("item/guide"))


# ---------------------------------------------------------------- data

def blocks():
    return OPS + ["model_core", "accelerator", "tensor_casing"] + CABLES


def loot_tables():
    for name in blocks():
        write(f"data/{NS}/loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": rid(name)}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": rid(f"blocks/{name}")})


def tags():
    machines = [rid(n) for n in OPS + ["model_core", "accelerator", "tensor_casing"]]
    write("data/minecraft/tags/block/mineable/pickaxe.json", {"values": machines})
    write(f"data/{NS}/tags/item/cables.json", {"values": [rid(n) for n in CABLES]})


def shaped(name, pattern, key, count=1):
    result = {"id": rid(name)} | ({"count": count} if count > 1 else {})
    write(f"data/{NS}/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": "misc",
                                             "key": key, "pattern": pattern, "result": result})


def shapeless(name, ingredients, count=1, file=None):
    result = {"id": rid(name)} | ({"count": count} if count > 1 else {})
    write(f"data/{NS}/recipe/{file or name}.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                                     "ingredients": ingredients, "result": result})


# Each machine is a casing, a part that hints at what it does, and, past the first lesson, the Insight that unlocks
# it. Insights stay in the grid.
MACHINES = {
    "embedding": (None, ["residual:weight_crystal", "residual:weight_crystal", "minecraft:book"]),
    "linear": (None, ["residual:weight_crystal", "residual:weight_crystal", "minecraft:iron_ingot"]),
    "shift": ("insight_counting", ["minecraft:repeater"]),
    "concat": ("insight_counting", ["minecraft:iron_chain"]),
    "activation": ("insight_counting", ["minecraft:redstone_torch", "minecraft:redstone"]),
    "matmul": ("insight_context", ["minecraft:crafting_table", "minecraft:redstone"]),
    "scale": ("insight_context", ["minecraft:heavy_weighted_pressure_plate"]),
    "causal_mask": ("insight_context", ["minecraft:carved_pumpkin"]),
    "softmax": ("insight_context", ["#minecraft:wool", "minecraft:redstone"]),
    "position": ("insight_attention", ["residual:weight_crystal", "residual:weight_crystal", "minecraft:clock"]),
    "add": ("insight_attention", ["#residual:cables", "#residual:cables", "minecraft:copper_ingot"]),
    "norm": ("insight_attention", ["residual:weight_crystal", "minecraft:compass"]),
    "attention": ("insight_attention", ["residual:weight_crystal", "residual:weight_crystal", "minecraft:spyglass"]),
    "mlp": ("insight_attention", ["residual:weight_crystal", "residual:weight_crystal", "minecraft:hopper"]),
    "multiply": ("insight_position", ["#minecraft:fence_gates", "minecraft:redstone"]),
    "split_heads": ("insight_position", ["minecraft:shears"]),
    "merge_heads": ("insight_position", ["minecraft:string", "minecraft:string"]),
}


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
    shaped("accelerator", ["KGK", "RDR", "KGK"], {"K": rid("tensor_casing"), "G": "minecraft:gold_ingot",
                                                  "R": "minecraft:redstone", "D": "minecraft:diamond"})
    shaped("tensor_probe", ["  G", " R ", "I  "], {"G": "minecraft:glass_pane", "R": "minecraft:redstone", "I": "minecraft:iron_ingot"})
    for op, (insight, parts) in MACHINES.items():
        shapeless(op, [rid("tensor_casing")] + parts + ([rid(insight)] if insight else []))
    shapeless("transformer", [rid("attention"), rid("mlp"), rid("norm"), rid("insight_circuits")])


def advancements():
    write(f"data/{NS}/advancement/root.json", {
        "criteria": {"joined": {"trigger": "minecraft:tick"}},
        "display": {"icon": {"id": rid("model_core")}, "title": {"translate": "advancements.residual.root.title"},
                    "description": {"translate": "advancements.residual.root.description"},
                    "background": rid("block/tensor_casing"), "show_toast": False, "announce_to_chat": False},
        "requirements": [["joined"]]})
    parent = rid("root")
    for lesson, insight in LESSONS:
        last = lesson == LESSONS[-1][0]
        write(f"data/{NS}/advancement/lesson/{lesson}.json", {
            "parent": parent,
            "criteria": {"done": {"trigger": "minecraft:impossible"}},
            "display": {"icon": {"id": rid(insight or "tensor_probe")},
                        "title": {"translate": f"residual.lesson.{lesson}.title"},
                        "description": {"translate": f"advancements.residual.{lesson}.description"},
                        "frame": "challenge" if last else "task" if insight else "goal",
                        "announce_to_chat": False},
            "requirements": [["done"]]})
        parent = rid(f"lesson/{lesson}")


if __name__ == "__main__":
    for rel in GENERATED:
        shutil.rmtree(RES / rel, ignore_errors=True)
    operators()
    core()
    simple("accelerator")
    simple("tensor_casing")
    cables()
    items()
    loot_tables()
    tags()
    recipes()
    advancements()
