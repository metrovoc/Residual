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
BENCHMARKS = ["forecasting", "recall", "reversal", "induction", "sorting", "orders", "reading", "control", "survey", "pilot", "demand", "watch", "retrieval"]
DATASETS = {"forecast_dataset": "minecraft:clock", "storage_dataset": "minecraft:chest",
            "errand_dataset": "minecraft:compass", "pattern_dataset": "minecraft:bricks", "sorting_dataset": "#minecraft:signs",
            "orders_dataset": "minecraft:writable_book", "process_dataset": "minecraft:raw_iron", "survey_dataset": "minecraft:flint",
            "flight_dataset": "minecraft:feather", "demand_dataset": "minecraft:hopper", "watch_dataset": "minecraft:spyglass", "retrieval_dataset": "minecraft:map"}
SCHEMATICS = ["schematic_relays"]
# see storage/PortMode.java
PORT_MODES = ["import", "export", "stock", "window"]
# see bench/Specs.java, in the order the Bench lists them, each with the spec whose module it builds on first
SPECS = {"mean": None, "softmax": None, "matmul": None, "linear": "matmul", "gelu": None, "layer_norm": "mean",
         "attention": "softmax", "mlp": "linear", "transformer": "attention"}
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


def accelerator():
    for name in ("accelerator", "accelerator_hot"):
        cube(name, *[rid(f"block/{name}")] * 3)
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


# a Drive Rack's bays in face coordinates (u, v), as tools/art.py draws them: two columns of four, 6x2 px, 2 px apart
RACK_BAY_U = (1, 9)
RACK_BAY_V = (1, 5, 9, 13)


def north_uv(x0, y0, x1, y1):
    """The face-coordinate rectangle a north face of these model bounds shows."""
    return [16 - x1, 16 - y1, 16 - x0, 16 - y0]


def rack():
    """A 3D Drive Rack, built the way AE2 builds its drive: full top and bottom panels and two side panels make the
    shell, its front edge a 1 px rim; behind the rim, a divider and shelves set 1 px back part eight bays that run 6 px
    deep to a back panel. A cartridge model per bay shows when a drive is in it."""
    t = {"front": rid("block/drive_rack_front"), "inside": rid("block/drive_rack_inside"),
         "panel": rid("block/drive_rack_back_panel"), "side": rid("block/drive_rack_side"), "top": rid("block/drive_rack_top"),
         "particle": rid("block/drive_rack_front")}
    holes_x = [(16 - u - 6, 16 - u) for u in RACK_BAY_U]
    holes_y = [(16 - v - 2, 16 - v) for v in RACK_BAY_V]
    parts = []

    def element(frm, to, faces):
        parts.append(box(frm, to, faces))

    def face(texture, uv, cull=None):
        return {"texture": texture, "uv": list(uv)} | ({"cullface": cull} if cull else {})

    inside = face("#inside", [0, 0, 16, 16])
    # the shell: top and bottom panels the full width, side panels between them
    element([0, 15, 0], [16, 16, 16], {"up": face("#top", [0, 0, 16, 16], "up"), "down": inside,
                                       "north": face("#front", north_uv(0, 15, 16, 16), "north"),
                                       **{f: face("#side", [0, 0, 16, 1], f) for f in ("east", "west", "south")}})
    element([0, 0, 0], [16, 1, 16], {"down": face("#top", [0, 0, 16, 16], "down"), "up": inside,
                                     "north": face("#front", north_uv(0, 0, 16, 1), "north"),
                                     **{f: face("#side", [0, 15, 16, 16], f) for f in ("east", "west", "south")}})
    for x0, outer, inner in ((0, "west", "east"), (15, "east", "west")):
        element([x0, 1, 0], [x0 + 1, 15, 16], {outer: face("#side", [0, 1, 16, 15], outer), inner: inside,
                                               "north": face("#front", north_uv(x0, 1, x0 + 1, 15), "north"),
                                               "south": face("#side", [15 - x0, 1, 16 - x0, 15], "south")})
    # the divider between the columns and the shelves between the rows, 1 px behind the rim
    element([7, 1, 1], [9, 15, 7], {"north": face("#front", north_uv(7, 1, 9, 15)), "east": inside, "west": inside})
    for (y0, y1), (y2, _) in zip(holes_y[1:], holes_y[:-1]):
        element([1, y1, 1], [15, y2, 7], {"north": face("#front", north_uv(1, y1, 15, y2)), "up": inside, "down": inside})
    # the back panel the bays end at, and the back of the block
    element([1, 1, 7], [15, 15, 16], {"north": face("#panel", north_uv(1, 1, 15, 15)),
                                      "south": face("#side", [1, 1, 15, 15], "south")})
    write(f"assets/{NS}/models/block/drive_rack.json", {"parent": "minecraft:block/block", "textures": t, "elements": parts})

    # a drive in a bay: a cartridge 2 px behind the face
    multipart = [{"when": {"facing": f}, "apply": {"model": rid("block/drive_rack"), **rot}} for f, rot in HORIZONTAL.items()]
    for i in range(8):
        col, row = i % 2, i // 2
        x0, x1 = holes_x[col]
        y0, y1 = holes_y[row]
        # the lens at the drive's outer end: bays on the left show the face mirrored
        face = [6, 0, 0, 2] if col == 0 else [0, 0, 6, 2]
        cart = {"north": {"uv": face, "texture": "#cartridge"}, "up": {"uv": [0, 2, 6, 7], "texture": "#cartridge"},
                "down": {"uv": [0, 7, 6, 12], "texture": "#cartridge"}, "east": {"uv": [6, 2, 11, 4], "texture": "#cartridge"},
                "west": {"uv": [6, 4, 11, 6], "texture": "#cartridge"}}
        write(f"assets/{NS}/models/block/drive_rack_bay{i}.json", {
            "parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"cartridge": rid("block/drive_cartridge"), "particle": rid("block/drive_cartridge")},
            "elements": [box([x0, y0, 2], [x1, y1, 7], cart)]})
        for f, rot in HORIZONTAL.items():
            multipart.append({"when": {"facing": f, f"bay{i}": "true"}, "apply": {"model": rid(f"block/drive_rack_bay{i}"), **rot}})
    write(f"assets/{NS}/blockstates/drive_rack.json", {"multipart": multipart})
    item_definition("drive_rack", rid("block/drive_rack"))


def storage():
    """Drive Rack, Storage Terminal, Port and Storage Link."""
    rack()

    side = rid("block/storage_terminal_side")
    variants = {}
    for lit, suffix in (("false", ""), ("true", "_on")):
        cube(f"storage_terminal{suffix}", rid(f"block/storage_terminal_front{suffix}"), side, side, side, side)
        for key, value in facing_variants(rid(f"block/storage_terminal{suffix}"), facings=HORIZONTAL).items():
            variants[f"{key},lit={lit}"] = value
    write(f"assets/{NS}/blockstates/storage_terminal.json", {"variants": variants})
    item_definition("storage_terminal", rid("block/storage_terminal"))

    # a Port's front holds against its inventory and its back usually against a link, so its four sides, the faces
    # players see, show its mode: importing, an arrow toward the back; exporting, toward the front
    back, front = rid("block/port_back"), rid("block/port_front")
    variants = {}
    for mode in PORT_MODES:
        for active, suffix in (("false", ""), ("true", "_on")):
            cube(f"port_{mode}{suffix}", front, back, rid(f"block/port_{mode}{suffix}"), flow=True)
            for key, value in facing_variants(rid(f"block/port_{mode}{suffix}")).items():
                variants[f"{key},mode={mode},active={active}"] = value
    write(f"assets/{NS}/blockstates/port.json", {"variants": variants})
    item_definition("port", rid("block/port_import"))

    side = rid("block/assembler_side")
    variants = {}
    for working, suffix in (("false", ""), ("true", "_on")):
        cube(f"assembler{suffix}", rid(f"block/assembler_front{suffix}"), side, side, rid("block/drive_rack_top"), side)
        for key, value in facing_variants(rid(f"block/assembler{suffix}"), facings=HORIZONTAL).items():
            variants[f"{key},working={working}"] = value
    write(f"assets/{NS}/blockstates/assembler.json", {"variants": variants})
    item_definition("assembler", rid("block/assembler"))

    # a Processor holds its front against its machine and its back usually against a link, like a Port
    variants = {}
    for working, suffix in (("false", ""), ("true", "_on")):
        cube(f"processor{suffix}", front, back, rid(f"block/processor_side{suffix}"), flow=True)
        for key, value in facing_variants(rid(f"block/processor{suffix}")).items():
            variants[f"{key},working={working}"] = value
    write(f"assets/{NS}/blockstates/processor.json", {"variants": variants})
    item_definition("processor", rid("block/processor"))

    # three blocks of the network that stand upright, each with a face that lights
    for name, prop, top in (("drive_dock", "working", "block/drive_rack_top"), ("access_point", "lit", "block/drive_rack_top"),
                            ("link_driver", "working", "block/combustion_generator_top")):
        side = rid(f"block/{name}_side")
        variants = {}
        for on, suffix in (("false", ""), ("true", "_on")):
            cube(f"{name}{suffix}", rid(f"block/{name}_front{suffix}"), side, side, rid(top), side)
            for key, value in facing_variants(rid(f"block/{name}{suffix}"), facings=HORIZONTAL).items():
                variants[f"{key},{prop}={on}"] = value
        write(f"assets/{NS}/blockstates/{name}.json", {"variants": variants})
        item_definition(name, rid(f"block/{name}"))

    # a 6 px link, built like the signal cables
    joint = {f: {"uv": [5, 5, 11, 11], "texture": "#link"} for f in ("north", "south", "east", "west", "up", "down")}
    write(f"assets/{NS}/models/block/storage_link_joint.json", {
        "parent": "minecraft:block/block", "textures": {"particle": rid("block/storage_link"), "link": rid("block/storage_link")},
        "elements": [box([5, 5, 5], [11, 11, 11], joint)]})
    arm = {"north": {"uv": [5, 5, 11, 11], "texture": "#link", "cullface": "north"},
           "east": {"uv": [11, 5, 16, 11], "texture": "#link"}, "west": {"uv": [0, 5, 5, 11], "texture": "#link"},
           "up": {"uv": [5, 0, 11, 5], "texture": "#link"}, "down": {"uv": [5, 11, 11, 16], "texture": "#link"}}
    write(f"assets/{NS}/models/block/storage_link_arm.json", {
        "parent": "minecraft:block/block", "textures": {"particle": rid("block/storage_link"), "link": rid("block/storage_link")},
        "elements": [box([5, 5, 0], [11, 11, 5], arm)]})
    straight = {"north": {"uv": [0, 5, 16, 11], "texture": "#link"}, "south": {"uv": [0, 5, 16, 11], "texture": "#link"},
                "up": {"uv": [0, 5, 16, 11], "texture": "#link"}, "down": {"uv": [0, 5, 16, 11], "texture": "#link"},
                "east": {"uv": [5, 5, 11, 11], "texture": "#link"}, "west": {"uv": [5, 5, 11, 11], "texture": "#link"}}
    write(f"assets/{NS}/models/item/storage_link.json", {
        "parent": "minecraft:block/block", "textures": {"particle": rid("block/storage_link"), "link": rid("block/storage_link")},
        "elements": [box([0, 5, 5], [16, 11, 11], straight)]})
    parts = [{"apply": {"model": rid("block/storage_link_joint")}}]
    for face, rot in FACING.items():
        parts.append({"when": {face: "true"}, "apply": {"model": rid("block/storage_link_arm"), **rot}})
    write(f"assets/{NS}/blockstates/storage_link.json", {"multipart": parts})
    item_definition("storage_link", rid("item/storage_link"))


def items():
    flat = (["weight_crystal", "wafer", "checkpoint", "archive_drive", "archive_shard", "salvaged_model",
             "agent", "relay_card", "terminal", "field_log", "storage_drive", "dense_drive", "stacked_drive", "deep_drive",
             "portable_drive", "wireless_terminal", "hourglass", "flight_frame", "exosuit"] + list(DATASETS) + SCHEMATICS)
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
    # a tokenizer shows tokens once a vocabulary is written on it
    for name in ("tokenizer", "tokenizer_written"):
        write(f"assets/{NS}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": rid(f"item/{name}")}})
    write(f"assets/{NS}/items/tokenizer.json", {"model": {
        "type": "minecraft:condition", "property": "minecraft:has_component", "component": rid("vocab"),
        "on_true": {"type": "minecraft:model", "model": rid("item/tokenizer_written")},
        "on_false": {"type": "minecraft:model", "model": rid("item/tokenizer")}}})
    # a program card shows writing once a program is written on it
    for name in ("program_card", "program_card_written"):
        write(f"assets/{NS}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": rid(f"item/{name}")}})
    write(f"assets/{NS}/items/program_card.json", {"model": {
        "type": "minecraft:condition", "property": "minecraft:has_component", "component": rid("program"),
        "on_true": {"type": "minecraft:model", "model": rid("item/program_card_written")},
        "on_false": {"type": "minecraft:model", "model": rid("item/program_card")}}})
    # the guide book is GuideME's item; its guide definition points at this model
    write(f"assets/{NS}/models/item/guide.json", {"parent": "minecraft:item/generated", "textures": {"layer0": rid("item/guide")}})
    item_definition("guide", rid("item/guide"))


def power():
    """The power blocks: a cable built like the storage link but thinner and copper-cored, two machines that face the
    player and light while they work, and a cell whose faces fill in quarters."""
    for name in ("combustion_generator", "smelter"):
        side, top = rid(f"block/{name}_side"), rid(f"block/{name}_top")
        variants = {}
        for working, suffix in (("false", ""), ("true", "_on")):
            cube(f"{name}{suffix}", rid(f"block/{name}_front{suffix}"), rid("block/machine_back"), side, top, rid("block/machine_back"))
            for key, value in facing_variants(rid(f"block/{name}{suffix}"), facings=HORIZONTAL).items():
                variants[f"{key},working={working}"] = value
        write(f"assets/{NS}/blockstates/{name}.json", {"variants": variants})
        item_definition(name, rid(f"block/{name}"))
    variants = {}
    for charge in range(5):
        face, top = rid(f"block/energy_cell_{charge}"), rid("block/energy_cell_top")
        cube(f"energy_cell_{charge}", face, face, face, top, top)
        variants[f"charge={charge}"] = {"model": rid(f"block/energy_cell_{charge}")}
    write(f"assets/{NS}/blockstates/energy_cell.json", {"variants": variants})
    item_definition("energy_cell", rid("block/energy_cell_0"))

    # a 4 px cable
    joint = {f: {"uv": [6, 6, 10, 10], "texture": "#cable"} for f in ("north", "south", "east", "west", "up", "down")}
    textures = {"particle": rid("block/power_cable"), "cable": rid("block/power_cable")}
    write(f"assets/{NS}/models/block/power_cable_joint.json", {
        "parent": "minecraft:block/block", "textures": textures, "elements": [box([6, 6, 6], [10, 10, 10], joint)]})
    arm = {"north": {"uv": [6, 6, 10, 10], "texture": "#cable", "cullface": "north"},
           "east": {"uv": [10, 6, 16, 10], "texture": "#cable"}, "west": {"uv": [0, 6, 6, 10], "texture": "#cable"},
           "up": {"uv": [6, 0, 10, 6], "texture": "#cable"}, "down": {"uv": [6, 10, 10, 16], "texture": "#cable"}}
    write(f"assets/{NS}/models/block/power_cable_arm.json", {
        "parent": "minecraft:block/block", "textures": textures, "elements": [box([6, 6, 0], [10, 10, 6], arm)]})
    straight = {"north": {"uv": [0, 6, 16, 10], "texture": "#cable"}, "south": {"uv": [0, 6, 16, 10], "texture": "#cable"},
                "up": {"uv": [0, 6, 16, 10], "texture": "#cable"}, "down": {"uv": [0, 6, 16, 10], "texture": "#cable"},
                "east": {"uv": [6, 6, 10, 10], "texture": "#cable"}, "west": {"uv": [6, 6, 10, 10], "texture": "#cable"}}
    write(f"assets/{NS}/models/item/power_cable.json", {
        "parent": "minecraft:block/block", "textures": textures, "elements": [box([0, 6, 6], [16, 10, 10], straight)]})
    parts = [{"apply": {"model": rid("block/power_cable_joint")}}]
    for face, rot in FACING.items():
        parts.append({"when": {face: "true"}, "apply": {"model": rid("block/power_cable_arm"), **rot}})
    write(f"assets/{NS}/blockstates/power_cable.json", {"multipart": parts})
    item_definition("power_cable", rid("item/power_cable"))


# ---------------------------------------------------------------- data

MACHINE_BLOCKS = ["model_core", "accelerator", "tensor_casing", "model_server", "reader", "relay", "bench", "fabricator", "stream",
                  "bpe_forge", "forecaster", "forecaster_ii", "forecaster_iii", "surveyor", "watch_post", "combustion_generator", "energy_cell", "smelter", "power_cable",
                  "drive_rack", "storage_terminal", "port", "storage_link", "assembler", "processor", "drive_dock", "access_point",
                  "link_driver"]


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
    shaped("bpe_forge", ["KBK", "GWG", "KFK"], {"K": rid("tensor_casing"), "B": "minecraft:book", "G": "minecraft:glass",
                                                "W": rid("weight_crystal"), "F": "minecraft:blast_furnace"})
    shapeless("tokenizer", ["minecraft:paper", "minecraft:paper", "minecraft:ink_sac", "minecraft:amethyst_shard"], 2)
    for dataset, part in DATASETS.items():
        shapeless(dataset, ["minecraft:paper", "minecraft:paper", "minecraft:redstone", part])
    shaped("watch_post", ["GSG", "KWK", "KCK"], {"G": "minecraft:glass_pane", "S": "minecraft:spyglass", "K": rid("tensor_casing"),
                                                 "W": rid("weight_crystal"), "C": "minecraft:comparator"})
    shaped("forecaster", ["GLG", "KWK", "KCK"], {"G": "minecraft:glass_pane", "L": "minecraft:clock", "K": rid("tensor_casing"),
                                                 "W": rid("weight_crystal"), "C": "minecraft:composter"})
    shaped("combustion_generator", ["KCK", "IFI", "KRK"], {"K": rid("tensor_casing"), "C": "minecraft:copper_block",
                                                         "I": "minecraft:iron_ingot", "F": "minecraft:blast_furnace",
                                                         "R": "minecraft:redstone_block"})
    shaped("power_cable", ["CCC", "RRR", "CCC"], {"C": "minecraft:copper_ingot", "R": "minecraft:redstone"}, 12)
    shaped("energy_cell", ["KRK", "RCR", "KRK"], {"K": rid("tensor_casing"), "R": "minecraft:redstone_block",
                                                "C": "minecraft:copper_block"})
    shapeless("field_log", ["minecraft:paper", "minecraft:paper", "minecraft:wheat", "#minecraft:planks"])
    shaped("agent", [" L ", "CRC", "C C"], {"L": "minecraft:lantern", "C": "minecraft:copper_ingot", "R": "minecraft:redstone"})
    shapeless("program_card", ["minecraft:paper", "minecraft:redstone"], 2)
    shaped("survey_rod", ["  A", " S ", "S  "], {"A": "minecraft:amethyst_shard", "S": "minecraft:stick"})
    shaped("wayfinder", [" A ", "CKC", " C "], {"A": "minecraft:amethyst_shard", "C": "minecraft:copper_ingot",
                                                 "K": "minecraft:compass"})
    shaped("reader", ["GBG", "KWK", "KKK"], {"G": "minecraft:glass_pane", "B": "minecraft:book", "K": rid("tensor_casing"),
                                             "W": rid("weight_crystal")})
    shapeless("model_server", [rid("tensor_casing"), rid("tensor_casing"), rid("weight_crystal"), rid("weight_crystal"),
                               "minecraft:gold_ingot", "minecraft:redstone_block", rid("schematic_relays")])
    shapeless("relay", ["minecraft:lightning_rod", "minecraft:lantern", "minecraft:copper_ingot", "minecraft:redstone",
                        rid("schematic_relays")], 2)
    shapeless("relay_card", ["minecraft:paper", "minecraft:copper_ingot", "minecraft:redstone", rid("schematic_relays")], 2)
    shapeless("terminal", [rid("relay_card"), "minecraft:glass_pane", "minecraft:copper_ingot", "minecraft:redstone"])
    # a flight frame taken further, in netherite, with a board of its own
    shaped("exosuit", ["NWN", "AFA", "NSN"], {"N": "minecraft:netherite_ingot", "W": rid("wafer"), "A": rid("accelerator"),
                                              "F": rid("flight_frame"), "S": rid("model_server")})
    # storage, from what the crafting table and the Fabricator already make
    shaped("storage_drive", ["IWI", "WRW", "IWI"], {"I": "minecraft:iron_ingot", "W": rid("wafer"), "R": "minecraft:redstone"})
    # the largest drive is built from stacked ones to the plans of the relay houses, whose stores it keeps; the sizes between come from the Fabricator
    shaped("deep_drive", ["DDD", "DSD", "DDD"], {"D": rid("stacked_drive"), "S": rid("schematic_relays")})
    shaped("wireless_terminal", [" A ", "CTC", " R "], {"A": "minecraft:amethyst_shard", "C": "minecraft:copper_ingot",
                                                        "T": rid("storage_terminal"), "R": "minecraft:redstone"})
    shaped("link_driver", ["KLK", "CAC", "KLK"], {"K": rid("tensor_casing"), "L": rid("storage_link"), "C": "minecraft:copper_block",
                                                  "A": rid("accelerator")})
    shaped("drive_rack", ["KCK", "C C", "KCK"], {"K": rid("tensor_casing"), "C": "minecraft:copper_ingot"})
    shaped("storage_link", ["CCC", "RGR", "CCC"], {"C": "minecraft:copper_ingot", "R": "minecraft:redstone", "G": "minecraft:glass"}, 16)
    shaped("storage_terminal", ["KGK", "GWG", "KRK"], {"K": rid("tensor_casing"), "G": "minecraft:glass_pane", "W": rid("wafer"),
                                                       "R": "minecraft:redstone"})
    shaped("assembler", ["KTK", "WCW", "KRK"], {"K": rid("tensor_casing"), "T": "minecraft:crafting_table", "W": rid("wafer"),
                                                "C": rid("weight_crystal"), "R": "minecraft:redstone"})
    shaped("processor", ["KFK", "WCW", "KHK"], {"K": rid("tensor_casing"), "F": "minecraft:furnace", "W": rid("wafer"),
                                                "C": rid("weight_crystal"), "H": "minecraft:hopper"})
    shaped("port", ["KHK", "RWR", "KPK"], {"K": rid("tensor_casing"), "H": "minecraft:hopper", "R": "minecraft:redstone",
                                           "W": rid("wafer"), "P": "minecraft:piston"})


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
    advancement("agent/made", "root", "agent", has("agent"))
    advancement("agent/program", "agent/made", "program_card", awarded())
    advancement("agent/order", "agent/made", "minecraft:writable_book", awarded(), frame="goal")
    advancement("agent/copy", "agent/program", "survey_rod", awarded(), frame="goal")
    advancement("agent/move", "agent/copy", "minecraft:chest", awarded(), frame="goal")
    advancement("deploy/server", "agent/made", "model_server", placed("model_server"))
    advancement("deploy/relay", "deploy/server", "relay", placed("relay"))
    advancement("deploy/reader", "archive/drive", "reader", placed("reader"))
    advancement("forecast/watching", "root", "forecaster", placed("forecaster"))
    advancement("forecast/ahead", "forecast/watching", "minecraft:furnace", awarded(), frame="goal")
    advancement("forecast/hourglass", "forecast/ahead", "hourglass", awarded(), frame="goal")
    advancement("forecast/deep", "forecast/ahead", "forecaster_ii", awarded(), frame="goal")
    advancement("forecast/burst", "forecast/deep", "forecaster_iii", awarded(), frame="challenge")
    advancement("survey/found", "benchmark/survey", "surveyor", awarded(), frame="goal")
    advancement("watch/noticed", "benchmark/watch", "watch_post", awarded(), frame="goal")
    advancement("flight/frame", "benchmark/pilot", "flight_frame", awarded(), frame="goal")
    advancement("exosuit/board", "flight/frame", "exosuit", awarded(), frame="goal")
    advancement("exosuit/vein", "exosuit/board", "minecraft:raw_iron", awarded(), frame="goal")
    advancement("exosuit/all", "exosuit/board", "exosuit", awarded(), frame="challenge")
    advancement("power/generator", "root", "combustion_generator", placed("combustion_generator"))
    advancement("power/smelter", "power/generator", "smelter", awarded(), frame="goal")
    advancement("storage/drive", "root", "storage_drive", has("storage_drive"))
    advancement("storage/terminal", "storage/drive", "storage_terminal", placed("storage_terminal"))
    advancement("storage/port", "storage/terminal", "port", placed("port"), frame="goal")
    advancement("storage/assembler", "storage/terminal", "assembler", awarded(), frame="goal")
    advancement("storage/request", "storage/assembler", "storage_terminal", awarded(), frame="goal")
    advancement("storage/processor", "storage/assembler", "processor", awarded(), frame="goal")
    advancement("storage/fluid", "storage/terminal", "minecraft:water_bucket", awarded())
    advancement("storage/dense", "storage/drive", "dense_drive", has("dense_drive"))
    advancement("storage/deep", "storage/dense", "deep_drive", has("deep_drive"), frame="challenge")
    advancement("storage/dock", "storage/dense", "drive_dock", placed("drive_dock"), frame="goal")
    advancement("storage/driver", "storage/terminal", "link_driver", placed("link_driver"), frame="goal")
    advancement("storage/wireless", "storage/terminal", "wireless_terminal", awarded(), frame="goal")
    advancement("storage/relay", "storage/wireless", "relay", awarded(), frame="challenge")
    advancement("storage/portable", "storage/terminal", "portable_drive", has("portable_drive"), frame="goal")
    advancement("storage/search", "benchmark/retrieval", "storage_terminal", awarded(), frame="goal")
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
    forge()
    forecaster()
    forecaster("forecaster_ii")
    forecaster("forecaster_iii")
    forecaster("surveyor")
    forecaster("watch_post")
    power()
    storage()
    stream()
    cables()
    items()
    loot_tables()
    tags()
    recipes()
    advancements()
    ruins.write_all(write, rid)
