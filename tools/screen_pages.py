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


def stand(x, z):
    return f"cmd execute anchored eyes run tp @s {x + 0.5} -58.6 {z - 2.5} facing {x + 0.5} -59.6 {z + 0.5}"


def block(id, x, z, facing="north"):
    return [f"cmd setblock {x} -60 {z} residual:{id}[facing={facing}]", stand(x, z), "wait 10", f"use {x} -60 {z} {facing}", "wait 10"]


INDEX = {
    "nav": T("Screens", "界面"),
    "intro": T("Every screen, part by part. The numbers on a picture are the parts listed under it, and pointing at a number says what it is.",
               "每个界面，一部分一部分地看。图上的数字对应图下列出的部分，指向一个数字就能看到它是什么。"),
}

STATUS = P("status", T("Status", "状态"), T("What the Core is doing, and how many steps the model has trained.", "核心正在做什么，以及模型已经训练了多少步。"))
TABS = T("The tabs", "标签页")
SAVE = P("core.save", K("residual.core.save"), T("Tests the model on every benchmark and keeps it, scores and all, in the checkpoint.",
                                                 "用每一项基准测试这个模型，并把它连同分数一起存进检查点。"))


def core_open(x, z):
    return ["cmd give @s residual:forecast_dataset", "cmd give @s residual:checkpoint", f"blueprint bigram {x} -60 {z} south", stand(x, z), "wait 20", f"use {x} -60 {z} north", "wait 10"]


CORE = screen(
    "core", "CoreScreen", "model_core", T("Model Core", "模型核心"), T("The Model Core's screen", "模型核心的界面"),
    T("Use a Model Core to open it. Its tabs take a model from its data to its tests; [The Model Core](../core.md) tells how they go together.",
      "对模型核心使用即可打开。各个标签页带着模型从数据走到测试；它们如何配合，见[模型核心](../core.md)。"),
    core_open,
    [state("data", T("**Data**: what the model practises on, and where it is kept.", "**数据**：模型用什么练习，又存到哪里。"),
           T("The Data tab: a dataset in the first slot, the tokenizer and checkpoint slots beside it", "数据页：第一个槽里有数据集，旁边是分词器槽和检查点槽"),
           [P("tab:data", TABS, T("The screen's views. It opens on the one you left it on.", "这个界面的几个视图。它会停在你上次离开时的那一页。")),
            P("data:0", K("residual.core.data"), K("residual.core.data_hint")),
            P("tokenizer", K("residual.core.tokenizer"), K("residual.core.tokenizer_hint")),
            P("checkpoint", K("residual.core.crystal"), K("residual.core.crystal_hint")),
            SAVE,
            P("practising", T("What it practises", "练习的内容"), T("Each benchmark what is loaded lets it practise, and the job that benchmark is used for.", "已放入的东西能让它练习的每一项基准，以及这项基准的用途。")),
            STATUS],
           ["click has:residual:forecast_dataset", "wait 2", "click data:0", "wait 4"]),
     state("train", T("**Train**: the model learning, step by step.", "**训练**：模型一步一步地学习。"),
           T("The Train tab while a model trains: the loss on practice and on the test falling", "模型训练时的训练页：练习损失和测试损失正在下降"),
           [P("chart", T("The curve", "曲线"), T("Train loss in teal, test loss in amber, accuracy in green, against the steps trained. Point at it to read any step.",
                                               "青色是训练损失，琥珀色是测试损失，绿色是准确率，横轴是训练的步数。指向曲线可读出任意一步。")),
            P("legend", T("Where it stands", "现在的数字"), T("The numbers the curves end at. Point at them for its parameters, gradient norm and threads.", "曲线末端的数字。指向它们可看到参数量、梯度范数和线程数。")),
            P("train", K("residual.core.train"), T("Starts training, and stops it.", "开始训练，也用来停止。")),
            P("core.reset", K("residual.core.reset"), K("residual.core.reset_hint")),
            P("core.check", K("residual.core.check"), K("residual.core.check_hint")),
            P("core.speed", K("residual.core.speed"), K("residual.core.speed_hint")),
            P("core.learning_rate", K("residual.core.learning_rate"), K("residual.core.learning_rate_hint")),
            P("core.batch", K("residual.core.batch"), K("residual.core.batch_hint")),
            P("core.clip", K("residual.core.clip"), K("residual.core.clip_hint"))],
           ["click tab:train", "wait 2", "click train", "wait 200", "click train"]),
     state("tests", T("**Tests**: how the model does on what it has not practised.", "**测试**：模型在没练习过的东西上表现如何。"),
           T("The Tests tab: a line for each benchmark, with its score", "测试页：每项基准一行，带着分数"),
           [P("row:forecasting", T("A benchmark", "一项基准"), T("One line for each: its name, a bar toward what passes, and its score, green once it passes. Point at a line for its job, when it was tested and how its test goes.",
                                                               "每项基准一行：名称、一条通向及格线的进度条和分数，及格后变绿。指向一行可看到它的用途、测试时间和测试方式。")),
            P("core.test_all", K("residual.core.test_all"), T("Runs every test now.", "立即运行所有测试。")),
            SAVE],
           ["click tab:tests", "wait 20", "click core.test_all", "wait 60"]),
     state("try", T("**Try**: the model answering.", "**试用**：模型作答。"),
           T("The Try tab: an example drawn from the test and the model's answer", "试用页：从测试中抽出的一个样例和模型的回答"),
           [P("try.benchmark", K("residual.try.benchmark"), T("Which benchmark to try it on.", "用哪项基准来试它。")),
            P("try.example", K("residual.try.example"), T("Draws a fresh example from the test and has the model answer it. Where you can type, this asks or writes instead.",
                                                         "从测试中抽一个新样例，让模型作答。能打字的基准上，这个按钮变成提问或续写。")),
            P("output", T("Its answer", "它的回答"), K("residual.sample.legend"))],
           ["click tab:try", "wait 2", "click try.example", "wait 20"]),
     state("inspect", T("**Inspect**: the model's machines, a line each.", "**检查**：模型的机器，每台一行。"),
           T("The Inspect tab: every machine of the model with its shape, parameters and gradient", "检查页：模型的每台机器，以及它的形状、参数和梯度"),
           [P("table", T("Its machines", "它的机器"), T("Each machine with its name, the shape of what it puts out, its parameters and how much gradient reaches it. Anything wrong comes first, in red.",
                                                      "每台机器的名称、输出的形状、参数量和传到它那里的梯度大小。有问题的排在最前面，显示为红色。"))],
           ["click tab:inspect", "wait 2"]),
     state("recipe", T("**Recipe**: how a run goes beside its learning rate.", "**配方**：一次运行除了学习率之外怎么走。"),
           T("The Recipe tab: the settings, what the run is doing, the rate over the run and the units at the Core", "配方页：各项设置、运行正在做的事、整次运行的学习率，以及核心身边的单元"),
           [P("settings", T("The settings", "设置"), T("Each is dim until the unit for it touches the Core. [The Recipe](../recipe.md) says what each does.",
                                                     "相应的单元接触核心之前，它是暗的。每一项的作用见[配方](../recipe.md)。")),
            P("readings", T("What the run is doing", "运行正在做的事"), T("The rate now, what the weights take, the text read, the spikes, the largest scores and how often a second head is right. Point at one for what it means.",
                                                                     "当前的学习率、权重占用的大小、读到的文字、尖峰、最大的分数，以及第二个头猜对的比例。指向某一项可看到它的含义。")),
            P("rate", K("residual.recipe.plot"), T("What the learning rate is multiplied by from the first step to the last, with a line where the run has reached.",
                                                  "学习率从第一步到最后一步乘上的比例，并用一条线标出运行走到了哪里。")),
            P("units", T("The units", "单元"), T("Lit if one touches the Core, directly or through accelerators and other units. Point at one for what it opens.",
                                                "有单元接触核心（直接接触，或经由训练加速器和其他单元相连）时亮起。指向一个可看到它开放什么。")),
            P("recipe.schedule", K("residual.recipe.schedule"), K("residual.recipe.schedule.hint")),
            P("recipe.warmup", K("residual.recipe.warmup"), K("residual.recipe.warmup.hint")),
            P("recipe.length", K("residual.recipe.length"), K("residual.recipe.length.hint")),
            P("recipe.precision", K("residual.recipe.precision"), K("residual.recipe.precision.hint")),
            P("recipe.moves", K("residual.recipe.moves"), K("residual.recipe.moves.hint")),
            P("recipe.packing", K("residual.recipe.packing"), K("residual.recipe.packing.hint"))],
           ["click tab:recipe", "wait 2"]),
     state("compute", T("**Compute**: what the run really costs, and how it shares out its work.", "**算力**：这次训练实际花了多少，以及它怎样分配工作。"),
           T("The Compute tab: the run's devices, memory, flops and power, and what each device did through its last step", "算力页：这次训练的设备、内存、运算次数和电力，以及每个设备在上一步做了什么"),
           [P("reading", T("The run", "这次训练"), T("How it shares out and on how many devices, its memory against what has to hold it, its flops a second, its power and PUE, and what it has spent so far.",
                                                 "它怎样分配工作、用多少设备，内存和要放下它的容量，每秒运算次数、电力和 PUE，以及到目前为止花了多少。")),
            P("timeline", T("The last step", "上一步"), K("residual.compute.gantt")),
            P("compute.split", K("residual.compute.split"), K("residual.compute.split_hint"))],
           ["click tab:compute", "wait 20"])],
    clip="core")


def agent_open(x, z):
    return ['cmd give @s residual:agent[custom_name="Pip"]', "slot residual:agent", f"cmd tp @s {x + 0.5} -59 {z - 2.5} 0 40", "wait 10", f"use {x} -61 {z} up", "wait 20", "entity residual:agent", "wait 10"]


AGENT = screen(
    "agent", "AgentScreen", "agent", T("Agent", "智能体"), T("An agent's screen", "智能体的界面"),
    T("Use an agent to open it. [Agents](../agents.md) tells what it can do.", "对智能体使用即可打开。它能做什么，见[智能体](../agents.md)。"),
    agent_open,
    [state("work", T("**Work**: its job, what it is doing, and telling it something.", "**工作**：它的工作、它在做什么，以及对它说话。"),
           T("The Work tab of an agent's screen", "智能体界面的工作页"),
           [P("tab:work", TABS, T("What it does, the program it runs, and what it has been doing.", "它做什么、运行的程序，以及它做过什么。")),
            P("job.store", T("Jobs", "工作"), T("A button for each job; the one it is doing is greyed out. Point at one for what it does.", "每项工作一个按钮；它正在做的那项是灰的。指向按钮可看到这项工作做什么。")),
            P("display", T("What it is doing", "它在做什么"), T("Its status, the model that made its last decision, what it asked and answered, and how often it was right.", "它的状态、做出上一个决定的模型、它问了什么答了什么，以及它答对的次数。")),
            P("agent.order", K("residual.agent.order"), T("An order in your own words, for its model.", "用你自己的话写一条指令，交给它的模型。")),
            P("agent.tell", K("residual.agent.tell"), K("residual.agent.tell.help")),
            P("filter", T("Filter", "过滤"), K("residual.agent.filter_slot.help")),
            P("model", K("residual.agent.model_slot"), K("residual.agent.model_slot.help")),
            P("agent.set_home", K("residual.agent.set_home"), K("residual.agent.set_home.help")),
            P("carry", T("What it carries", "它带着的东西"), T("Twelve slots of its own.", "它自己的十二个槽。"))]),
     state("program", T("**Program**: the program it runs, to read, rewrite or click together.", "**程序**：它运行的程序，可以读、可以改，也可以点选拼出来。"),
           T("The Program tab with the palette open beside it", "程序页，旁边打开着指令板"),
           [P("program", T("The program", "程序"), T("Checked as it is typed. Clicking it with a thing in hand names that thing.", "输入时就会检查。手里拿着东西点击，就会写下这件东西的名字。")),
            P("check", T("The check", "检查结果"), T("Says whether it reads as a program, and where it slips if not.", "告诉你它能不能读成程序，读不成时错在哪里。")),
            P("program.run", K("residual.program.run"), K("residual.program.run.help")),
            P("program.stop", K("residual.program.stop"), K("residual.program.stop.help")),
            P("palette", K("residual.palette"), T("Shows the pieces beside the window, or hides them.", "在窗口旁显示这些拼块，或把它们收起来。")),
            P("program.wide", K("residual.program.wide"), T("Gives the program the whole window, with the slots out of the way.", "让程序占满整个窗口，把槽位挪开。")),
            P("palette_panel", T("The pieces", "拼块"), T("Everything a program can say, page by page, to click together. Each piece goes on the end of the program. Its last page is what you hold and the agent carries.",
                                                         "程序能说的一切，分页列出，点选即可拼合。每一块都接在程序末尾。最后一页是你手里拿的和智能体带着的东西。"))],
           ["click tab:program", "wait 2", "click palette", "wait 2", "click program", "click palette_page:1", "wait 2", "click palette.repeat", "click palette_page:0", "wait 2", "click palette.harvest", "click palette_page:1", "wait 2", "click palette.close", "wait 2", "click program.run", "wait 40"]),
     state("trace", T("**Trace**: what it has been doing, step by step, with a debugger beside it.", "**追踪**：它一步一步做了什么，旁边是调试器。"),
           T("The Trace tab: each step the agent took and how it went, and the debugger", "追踪页：智能体走过的每一步和结果，以及调试器"),
           [P("trace", T("The trace", "追踪"), T("Each step with how it went: what it did, what it could not, and what it asked its model.", "每一步及其结果：它做了什么、哪里做不到，以及它问了模型什么。")),
            P("debug_lines", K("residual.debug.title"), K("residual.debug.pause.help")),
            P("debug.step", K("residual.debug.step"), K("residual.debug.step.help")),
            P("debug_values", K("residual.debug.values"), T("What the program holds now, and what you asked to watch.", "程序现在保存的值，以及你要观察的东西。"))],
           ["click tab:trace", "wait 4"])],
    clip="program")

BENCH = screen(
    "bench", "BenchScreen", "bench", T("Bench", "试验台"), T("The Bench's screen", "试验台的界面"),
    T("Use a Bench to open it. [The Bench and the Fabricator](../bench.md) tells how a design is wired.", "对试验台使用即可打开。设计怎样接线，见[试验台与制造机](../bench.md)。"),
    lambda x, z: block("bench", x, z),
    [state("board", T("A design, wired from the parts to the result.", "一个设计，从零件一路接到结果。"),
           T("The mean wired on the Bench: sum over size, every case passing", "试验台上接好的均值：求和除以大小，每个用例都通过"),
           [P("spec", T("The spec", "规格"), T("What you are wiring. Click it to choose another.", "你正在接的规格。点击可换一个。")),
            P("palette", T("The parts", "零件"), T("What a design is wired from: the smallest operations, then the modules you have verified. Drag one onto the board.", "设计用来接线的东西：最小的运算，以及你验证过的模块。把它拖到板上。")),
            P("canvas", T("The board", "接线板"), K("residual.bench.help")),
            P("inspector", T("Its numbers", "它的数字"), T("What the node you clicked computes on the chosen case, or what the result should be.", "你点击的节点在所选用例上算出了什么，或结果应该是什么。")),
            P("goal", T("The goal", "目标"), T("What the spec must compute, and its formula.", "这个规格必须算出什么，以及它的公式。")),
            P("cases", T("The cases", "用例"), T("A mark for each test case, passing or not. Click one to run the design on it.", "每个测试用例一个标记，表示通过与否。点击一个，就用它来运行这个设计。")),
            P("result", K("residual.bench.result"), T("Wire the last node here. Green means the chosen case passes.", "把最后一个节点接到这里。绿色表示所选用例通过。")),
            P("record", K("residual.bench.record"), T("Once every case passes, verifies the design, and records it to a photomask where the spec makes a machine.", "每个用例都通过后，验证这个设计；规格能造出机器时，把它录到光掩模上。"))],
           ["drag chip:sum canvas+60+10", "drag out:x in:last:0", "drag chip:size canvas+60+50", "drag out:x in:last:0", "drag chip:div canvas+130+30", "drag out:1 in:last:0", "drag out:2 in:last:1", "drag out:last result:in"]),
     state("chooser", T("Every spec: those verified ticked, those not yet open locked.", "所有规格：验证过的打勾，还没开放的上锁。"),
           T("The list of specs open over the board", "接线板上方打开的规格列表"),
           [P("spec:softmax", T("A spec", "一个规格"), T("Click one to open it. A spec opens once those it builds on are verified.", "点击就能打开。它所依赖的规格都验证过后，它才会开放。"))],
           ["click spec", "wait 2"])],
    clip="bench")


def fabricator_open(x, z):
    return ["cmd give @s residual:tensor_casing 16", "cmd give @s residual:wafer 16", "cmd give @s residual:weight_crystal 16"] + block("fabricator", x, z)


FABRICATOR = screen(
    "fabricator", "FabricatorScreen", "fabricator", T("Fabricator", "制造机"), T("The Fabricator's screen", "制造机的界面"),
    T("Use a Fabricator to open it. [The Bench and the Fabricator](../bench.md) tells what it makes.", "对制造机使用即可打开。它能造什么，见[试验台与制造机](../bench.md)。"),
    fabricator_open,
    [state("choosing", T("With no photomask, what it can make are the machines that need none.", "没有光掩模时，它能造的是不需要光掩模的机器。"),
           T("The Fabricator with no mask: the plain machines to choose from", "没有光掩模的制造机：可选的普通机器"),
           [P("mask", T("Photomask", "光掩模"), T("A mask offers what its design makes.", "光掩模提供它的设计能造出的东西。")),
            P("choices", T("What it can make", "能造的东西"), T("Click one; the teal one is what it makes.", "点击选择；青色的就是它要造的。")),
            P("materials", T("Materials", "材料"), T("What the machine takes, with how many under each, red while short.", "这台机器需要的材料，下方写着数量，不够时显示为红色。")),
            P("arrow", T("The work", "工序"), T("Fills as it works. Point at it for the machine's name, what it does and how long it takes.", "随着制造填满。指向它可看到机器的名称、用途和所需时间。")),
            P("output", T("What it made", "造好的东西"), T("Faint until it is made.", "造好之前是淡淡的。"))]),
     state("making", T("With its materials in, it goes to work.", "放进材料，它就开始工作。"),
           T("The Fabricator at work, its arrow filling", "工作中的制造机，箭头正在填满"),
           [P("materials", T("Materials", "材料"), T("Taken as the work begins.", "工作开始时取走。")),
            P("arrow", T("The work", "工序"))],
           ["click choice:0", "wait 2", "shift residual:tensor_casing", "shift residual:wafer", "shift residual:weight_crystal", "wait 12"])])


def forge_open(x, z):
    return ["drive 400", "cmd give @s residual:tokenizer"] + block("bpe_forge", x, z)


FORGE = screen(
    "forge", "ForgeScreen", "bpe_forge", T("BPE Forge", "BPE 锻炉"), T("The BPE Forge's screen", "BPE 锻炉的界面"),
    T("Use a BPE Forge to open it. [Tokens](../tokenizers.md) tells what a vocabulary is for.", "对 BPE 锻炉使用即可打开。词表有什么用，见[词元](../tokenizers.md)。"),
    forge_open,
    [state("learning", T("A vocabulary being learned from a drive, merge by merge.", "从一个驱动器学习词表，一次合并一次合并地进行。"),
           T("The BPE Forge at work: a passage cut into tokens and its latest merges", "工作中的 BPE 锻炉：切成词元的一段文字和最近的合并"),
           [P("text", K("item.residual.archive_drive"), K("residual.forge.text_hint")),
            P("card", K("item.residual.tokenizer"), K("residual.forge.card_hint")),
            P("sizes", K("residual.forge.vocabulary"), T("How many tokens to learn. Bigger tokens carry more text each, but the embedding and the last layer grow with the vocabulary.", "要学多少个词元。词元越大，每个带的文本越多，但嵌入层和最后一层也随词表变大。")),
            P("progress", T("How far it has come", "进度"), T("Point at it for what it is doing.", "指向它可看到它在做什么。")),
            P("sample", K("residual.forge.sample"), T("A passage of the drive, cut as the vocabulary now cuts it. Point at a token for what it is made of.", "驱动器里的一段文字，按当前词表切开。指向一个词元可看到它由什么组成。")),
            P("merges", K("residual.forge.merges"), T("The pairs it joined last, newest first.", "它最后合并的几对词元，最新的在最前面。"))],
           ["shift residual:archive_drive", "wait 4", "shift residual:tokenizer", "wait 60"])],
    clip="forge")

OPERATOR = screen(
    "operator", "OperatorScreen", "linear", T("Machines", "机器"), T("A machine's screen", "机器的界面"),
    T("Use any machine of a model to open it; each has its own settings and math. [Machines](../machines/index.md) has a page for each.", "对模型的任意一台机器使用即可打开；每台都有自己的设置和数学形式。[机器](../machines/index.md)里每台各有一页。"),
    lambda x, z: block("linear", x, z, "east"),
    [state("machine", T("A Linear Layer before its first check.", "第一次校验之前的线性层。"),
           T("A Linear Layer's screen: its name, settings, pictures and math", "线性层的界面：名称、设置、图像和数学形式"),
           [P("name", K("residual.machine.name"), T("A name of your own, shown wherever the machine is listed.", "你给它起的名字，凡是列出这台机器的地方都会显示。")),
            P("field.out", T("Settings", "设置"), T("What this machine can be set to; they differ from machine to machine. Click for the next value, right-click for the one before.", "这台机器可以调的东西，每台都不一样。点击换下一个值，右键换上一个。")),
            P("check", T("What the last check found", "上次校验的结果"), T("The shape it puts out, its parameters and gradient, or what is wrong with it.", "它输出的形状、参数和梯度，或者它出了什么问题。")),
            P("picture", K("residual.machine.picture"), T("What comes out of its front, amber above zero and blue below. A machine with weights shows its main matrix beside it.", "从它正面出来的东西，大于零为琥珀色，小于零为蓝色。有权重的机器会在旁边显示主要的权重矩阵。")),
            P("math", K("residual.machine.math"), T("What it computes, in a line or two.", "用一两行写出它算的是什么。"))])],
    clip="operator")


def reader_open(x, z):
    return ["cmd give @s residual:archive_shard"] + block("reader", x, z)


READER = screen(
    "reader", "ReaderScreen", "reader", T("Reader", "阅读器"), T("The Reader's screen", "阅读器的界面"),
    T("Use a Reader to open it. [The Archive](../archive.md) tells how a model restores what was lost.", "对阅读器使用即可打开。模型怎样复原失落的文字，见[档案](../archive.md)。"),
    reader_open,
    [state("reading", T("A shard in its tray, its lost text worn away.", "托盘里放着一块残片，失落的文字已经磨损。"),
           T("The Reader with a shard: its text, some of it worn", "放着残片的阅读器：它的文字，有些已经磨损"),
           [P("shard", K("item.residual.archive_shard"), T("The shard to restore.", "要复原的残片。")),
            P("model", K("residual.agent.model_slot"), T("What restores it: a checkpoint, or a Relay Card tuned to a Model Server.", "用来复原它的东西：一个检查点，或调谐到模型服务器的中继卡。")),
            P("text", T("Its text", "它的文字"), T("What can be read, and the blocks still worn, lighting up as they come back.", "能读的部分和仍然磨损的文字块，复原时会亮起来。")),
            P("state", T("What it is doing", "它在做什么"), T("Which block it is on and how the last try went, or what it needs.", "它正在读哪一段、上一次尝试结果如何，或者它还缺什么。"))],
           ["shift residual:archive_shard", "wait 4"])],
    clip="reader")


def forecaster_open(x, z):
    return [f"cmd setblock {x - 1} -60 {z} minecraft:furnace[facing=north]", f"cmd item replace block {x - 1} -60 {z} container.0 with minecraft:raw_iron 64",
            f"cmd item replace block {x - 1} -60 {z} container.1 with minecraft:coal_block 8"] + block("forecaster", x, z) + ["wait 100"]


FORECASTER = screen(
    "forecaster", "ForecasterScreen", "forecaster", T("Forecaster", "预报机"), T("A Forecaster's screen", "预报机的界面"),
    T("Use a Forecaster to open it. [The Forecaster](../forecaster.md) tells how it comes to trust a model.", "对预报机使用即可打开。它怎样开始信任一个模型，见[预报机](../forecaster.md)。"),
    forecaster_open,
    [state("watching", T("What it sees around it, and how close its model comes to it.", "它看到的周围情况，以及模型离这些情况有多近。"),
           T("A Forecaster watching a furnace", "正在观察熔炉的预报机"),
           [P("field", T("The blocks around it", "周围的方块"), K("residual.forecaster.field_hint")),
            P("tab:machines", K("residual.forecaster.machines"), T("The kind of block the bars show: crops or machines.", "柱状图显示哪一类方块：作物或机器。")),
            P("bars", T("The bars", "柱状图"), T("How far they moved from each place, in amber, against what its model expects, in teal. Point at a group to read it.", "它们从每个位置走了多远（琥珀色），对照模型的预期（青色）。指向一组可读出数字。")),
            P("state", T("What it is doing", "它在做什么"), T("Whether it is watching, checking or running them ahead.", "它是在观察、校验，还是在让它们超前运行。")),
            P("row:machines", T("Each kind", "每一类"), T("How many it watches, then the progress seen so far or how close the model came. Within 11% it runs them four looks for every one; within 25%, two.",
                                                        "它观察多少个，然后是已看到的进度或模型的接近程度。误差在 11% 以内，每查看一次就让它们跑四次；在 25% 以内，跑两次。")),
            P("log", K("item.residual.field_log"), K("residual.forecaster.log_hint")),
            P("model", K("residual.agent.model_slot"), K("residual.forecaster.model_hint"))])],
    clip="forecaster_screen")


def surveyor_open(x, z):
    return block("surveyor", x, z)


SURVEYOR = screen(
    "surveyor", "SurveyorScreen", "surveyor", T("Surveyor", "勘测仪"), T("The Surveyor's screen", "勘测仪的界面"),
    T("Use a Surveyor to open it. [The Surveyor](../surveyor.md) tells what its model reads.", "对勘测仪使用即可打开。它的模型读什么，见[勘测仪](../surveyor.md)。"),
    surveyor_open,
    [state("waiting", T("With no model, it says what it needs; with one, the finds are counted here.", "没有模型时，它会说明缺什么；有了模型，这里会数着找到的东西。"),
           T("A Surveyor with no model yet", "还没有模型的勘测仪"),
           [P("model", K("residual.agent.model_slot"), K("residual.surveyor.model_hint")),
            P("finds", T("What it found", "它找到的东西"), T("Each ore and the caches, with how many its model has found this survey. Point at one for more.", "每种矿石和遗藏，以及模型这一轮找到了多少。指向一项可看到更多。")),
            P("progress", T("How far it has gone", "进度"), T("Along the display's foot, how far this survey has gone. Point at it for how far it reaches.", "显示屏底部表示这一轮勘测走了多远。指向它可看到它能勘测多远。"))])],
    clip="survey_screen")


def watch_open(x, z):
    return [f"cmd setblock {x + 2} -60 {z} minecraft:furnace[facing=north]", f"cmd item replace block {x + 2} -60 {z} container.0 with minecraft:raw_iron 64",
            f"cmd item replace block {x + 2} -60 {z} container.1 with minecraft:coal 8"] + block("watch_post", x, z) + ["wait 200"]


WATCH = screen(
    "watch_post", "WatchPostScreen", "watch_post", T("Watch Post", "哨所"), T("The Watch Post's screen", "哨所的界面"),
    T("Use a Watch Post to open it. [The Watch Post](../watch.md) tells what it watches for.", "对哨所使用即可打开。它在看什么，见[哨所](../watch.md)。"),
    watch_open,
    [state("watching", T("A row for each thing it watches.", "它观察的每样东西一行。"),
           T("A Watch Post watching a furnace", "正在观察熔炉的哨所"),
           [P("rows", T("What it watches", "它观察的东西"), T("Each thing's line of looks as bars, newest on the right, red while something is wrong with it. Point at a row to hear what it thinks.", "每样东西的查看记录画成柱状，最新的在右边，出问题时变红。指向一行可看到它的判断。")),
            P("model", K("residual.agent.model_slot"), K("residual.watch.model_hint"))])])


def power_open(x, z):
    return [f"cmd setblock {x + 2} -60 {z} residual:smelter[facing=north]", f"cmd setblock {x + 2} -60 {z - 2} residual:reactor_core[facing=north]",
            "cmd give @s minecraft:coal 16"] + block("combustion_generator", x, z)


POWER = screen(
    "power", "PowerScreen", "combustion_generator", T("Generator, Smelter and Reactor", "发电机、冶炼炉与反应堆"), T("The screens of power", "电力的界面"),
    T("Use a Combustion Generator, a Boiler, a Smelter or a Reactor Core to open them. [Power](../power.md) tells what they make and use.", "对燃烧发电机或冶炼炉使用即可打开。它们产生和使用什么，见[电力](../power.md)。"),
    power_open,
    [state("generator", T("A **Combustion Generator**: fuel under its flame.", "**燃烧发电机**：火焰下面放燃料。"),
           T("A Combustion Generator burning coal", "正在烧煤的燃烧发电机"),
           [P("fuel", T("Fuel", "燃料"), T("What a furnace burns.", "熔炉能烧的东西。")),
            P("flame", T("The flame", "火焰"), T("Burns down with the fuel.", "随着燃料燃尽而变小。")),
            P("power", T("Stored power", "储存的电力"), T("Point at it for how much, and what it makes.", "指向它可看到有多少，以及它产生多少。"))],
           ["shift minecraft:coal", "wait 10"]),
     state("smelter", T("A **Smelter**: ore on the left, ingots on the right.", "**冶炼炉**：左边放矿石，右边出锭。"),
           T("A Smelter's screen", "冶炼炉的界面"),
           [P("ore", T("Ore", "矿石")), P("work", T("The work", "工序"), T("Fills as it smelts. With a controller, it shows the melt: a column a look, the band shaded across.", "随着冶炼填满。有控制器时，这里显示熔融状态：每次查看一列，带状区域以阴影标出。")),
            P("ingots", T("Ingots", "锭")),
            P("power", T("Stored power", "储存的电力"), T("Point at it for how much, and what it draws.", "指向它可看到有多少，以及它消耗多少。")),
            P("controller", K("residual.power.controller"), K("residual.power.controller_hint"))],
           ["close", "wait 4", "use {sx} -60 {sz} north", "wait 10"]),
     state("reactor", T("A **Reactor Core**: fuel under its flame, and its cycle look by look.", "**反应堆核心**：火焰下面放燃料，周期一次次查看地画出来。"),
           T("A Reactor Core's screen", "反应堆核心的界面"),
           [P("fuel", T("Fuel", "燃料"), T("Reactor Fuel.", "反应堆燃料。")),
            P("flame", T("The flame", "火焰"), T("Burns down with the fuel.", "随着燃料燃尽而变小。")),
            P("cycle", T("The cycle", "周期"), T("A column a look, the band shaded across. Point at it for why it is not running, or how far through the cycle it is and how much of the last one was held.",
                                                "每次查看一列，带内的部分标了底色。指向它可看到它为什么没有运行，或者这个周期走到哪里、上个周期保持了多少。")),
            P("power", T("Stored power", "储存的电力"), T("Point at it for how much, and what it makes.", "指向它可看到有多少，以及它产生多少。")),
            P("controller", K("residual.power.controller"), K("residual.reactor.controller_hint"))],
           ["close", "wait 4", "use {ax} -60 {bz} north", "wait 10"])],
    clip="smelter")


def network(x, z):
    """A rack with a drive, links, and what a terminal needs to make torches from planks and coal, with no coal yet."""
    return [f"cmd setblock {x} -60 {z} residual:drive_rack[facing=north]", f"cmd fill {x + 1} -60 {z} {x + 6} -60 {z} residual:storage_link",
            "cmd give @s residual:storage_drive", stand(x, z), "wait 10", f"use {x} -60 {z} north", "wait 10", "shift residual:storage_drive", "wait 4", "close", "wait 4"]


def terminal_open(x, z):
    return network(x, z) + [
        f"cmd setblock {x + 2} -60 {z - 1} residual:storage_terminal[facing=north]",
        f"cmd setblock {x + 3} -60 {z - 1} residual:assembler[facing=north]", f"cmd setblock {x + 4} -60 {z - 1} residual:assembler[facing=north]",
        f'cmd data merge block {x + 3} -60 {z - 1} {{keep:0,grid:{{Items:[{{Slot:0b,id:"minecraft:oak_planks",count:1}},{{Slot:3b,id:"minecraft:oak_planks",count:1}}]}}}}',
        f'cmd data merge block {x + 4} -60 {z - 1} {{keep:0,grid:{{Items:[{{Slot:0b,id:"minecraft:coal",count:1}},{{Slot:3b,id:"minecraft:stick",count:1}}]}}}}',
        f"stock {x} -60 {z} minecraft:oak_planks 64", f"stock {x} -60 {z} minecraft:cobblestone 256", f"stock {x} -60 {z} minecraft:iron_ingot 32",
        f"cmd execute anchored eyes run tp @s {x + 2.5} -58.6 {z - 3.5} facing {x + 2.5} -59.6 {z - 0.5}", "wait 10", f"use {x + 2} -60 {z - 1} north", "wait 20"]


TERMINAL = screen(
    "storage_terminal", "StorageTerminalScreen", "storage_terminal", T("Storage Terminal", "存储终端"), T("A Storage Terminal's screen", "存储终端的界面"),
    T("Use a Storage Terminal to open it. A Wireless Terminal and a Portable Drive open the same. [Storage](../storage.md) tells what the network keeps and makes.", "对存储终端使用即可打开，无线终端和便携驱动器打开的也是它。网络存什么、造什么，见[存储](../storage.md)。"),
    terminal_open,
    [state("grid", T("All the network keeps, with what it can make marked.", "网络存着的一切，能做出来的会有标记。"),
           T("A Storage Terminal's grid", "存储终端的网格"),
           [P("search", K("residual.storage.search"), K("residual.storage.search_help")),
            P("jobs", K("residual.craft.jobs"), K("residual.craft.jobs_help")),
            P("sort", T("Sort", "排序"), K("residual.storage.sort_help")),
            P("grid", T("The kinds", "各种东西"), T("Click to take a stack, right-click for half, shift-click to send a stack to your inventory; click holding something to put it in, right-click for one. Alt-click pins a kind to the top.",
                                                  "点击取一组，右键取一半，按住 Shift 点击把一组送进物品栏；手里有东西时点击放入，右键放一个。按住 Alt 点击把这种东西钉在最前面。")),
            P("scrollbar", T("The scrollbar", "滚动条"), T("Drag it, or scroll over the grid.", "拖动它，或在网格上滚动。")),
            P("usage", T("How full it is", "用了多少"), T("What is kept, what there is room for, and how many kinds.", "存了多少、还能存多少，以及有多少种。")),
            P("model", K("residual.agent.model_slot"), K("residual.search.slot_hint")),
            P("craft", T("A crafting grid", "合成网格"), T("Works as at a crafting table, and draws what it needs from the network.", "和工作台一样用，所需的东西从网络里取。"))]),
     state("ask", T("Asking for what the network can make: the plan, and what is missing.", "向网络要它能做的东西：规划，以及缺什么。"),
           T("The plan for sixteen torches, with coal missing", "十六支火把的规划，缺煤"),
           [P("amount", K("residual.craft.amount"), T("How many to make, typed or stepped.", "要做多少，可以输入，也可以按按钮增减。")),
            P("+16", T("Steps", "增减"), K("residual.craft.step_help")),
            P("plan", T("The plan", "规划"), K("residual.craft.legend")),
            P("start", K("residual.craft.start"), K("residual.craft.start_help")),
            P("back", T("Back", "返回"), K("residual.craft.back"))],
           ["click search", "type *torch", "wait 4", "click kind:minecraft:torch", "wait 4", "click +16", "wait 20"]),
     state("jobs", T("The jobs running on the network: what each makes, how far it has come, what it waits for.", "网络上运行的任务：每个做什么、做到哪儿了、在等什么。"),
           T("A job waiting for coal", "等煤的任务"),
           [P("job:0", T("A job", "一个任务"), K("residual.craft.job_help")),
            P("job:0:cancel", T("Cancel", "取消"), T("Stops the job.", "停止这个任务。"))],
           ["click start", "wait 30"]),
     state("tree", T("A job's plan as a tree: each row under the step that needs it.", "任务的规划画成树：每一行都在需要它的那一步下面。"),
           T("A job's plan drawn as a tree", "画成树的任务规划"),
           [P("tree", T("The plan", "规划"), T("How many of each came from storage, how far its crafts have come, and what is still missing.", "每样东西有多少来自存储、合成进行到哪儿，还缺什么。"))],
           ["click job:0", "wait 20"])],
    clip="storage")


def port_open(x, z):
    return network(x, z) + [
        f"cmd setblock {x + 2} -60 {z - 1} residual:port[facing=north,mode=import]", f"cmd setblock {x + 2} -60 {z - 2} minecraft:chest", "cmd give @s minecraft:raw_iron 8",
        f"cmd execute anchored eyes run tp @s {x + 2.5} -58.6 {z - 3.5} facing {x + 2.5} -59.6 {z - 0.5}", "wait 10", f"use {x + 2} -60 {z - 1} north", "wait 10"]


PORT = screen(
    "port", "PortScreen", "port", T("Port", "端口"), T("A Port's screen", "端口的界面"),
    T("Use a Port to open it. [Storage](../storage.md) tells how its modes differ.", "对端口使用即可打开。各种模式有什么不同，见[存储](../storage.md)。"),
    port_open,
    [state("import", T("Its mode, its filter, and what it moved.", "它的模式、过滤和搬动的东西。"),
           T("A Port importing", "正在导入的端口"),
           [P("port.mode.import", K("residual.port.mode.import"), K("residual.port.mode.import.help")),
            P("port.mode.export", K("residual.port.mode.export"), K("residual.port.mode.export.help")),
            P("filter", T("Filter", "过滤"), K("residual.port.filter_hint")),
            P("status", T("What it moved", "搬动的东西"), T("How much in the last second, or that a full link holds it back.", "上一秒搬了多少，或者链路满了挡住了它。")),
            P("model", K("residual.port.model"), K("residual.port.model_hint")),
            P("target", K("residual.port.target"), K("residual.port.target_hint")),
            P("log", K("residual.port.log"), K("residual.port.log_hint"))]),
     state("stock", T("Stocking: a number to keep, and asking for what it lacks.", "存量：要保持的数量，以及请求缺的东西。"),
           T("A Port stocking with a filter and a number", "设好过滤和数量、正在维持存量的端口"),
           [P("port.mode.stock", K("residual.port.mode.stock"), K("residual.port.mode.stock.help")),
            P("port.mode.window", K("residual.port.mode.window"), K("residual.port.mode.window.help")),
            P("more:16", T("The number", "数量"), T("How many of each kind to keep there. With shift, sixty-four times as many.", "每种东西要在那里保持多少。按住 Shift，乘以六十四。")),
            P("port.craft", K("residual.port.craft"), K("residual.port.craft.off")),
            P("port.programs", K("residual.port.programs"), K("residual.port.programs.help"))],
           ["click port.mode.stock", "wait 4", "click has:minecraft:raw_iron", "wait 2", "click filter:0", "wait 2", "click more:16", "wait 4"])])


def rack_open(x, z):
    return ["cmd give @s residual:storage_drive"] + block("drive_rack", x, z)


RACK = screen(
    "drive_rack", "RackScreen", "drive_rack", T("Drive Rack", "驱动器机架"), T("A Drive Rack's screen", "驱动器机架的界面"),
    T("Use a Drive Rack to open it. [Storage](../storage.md) tells what the drives hold.", "对驱动器机架使用即可打开。驱动器能存多少，见[存储](../storage.md)。"),
    rack_open,
    [state("bays", T("Eight bays, a drive in the first.", "八个槽位，第一个放着驱动器。"),
           T("A Drive Rack with one drive", "放着一个驱动器的驱动器机架"),
           [P("bays", T("The bays", "槽位"), K("residual.rack.bay_hint"))],
           ["shift residual:storage_drive", "wait 4"])])


def assembler_open(x, z):
    return network(x, z) + [f"cmd setblock {x + 2} -60 {z - 1} residual:assembler[facing=north]", "cmd give @s minecraft:oak_planks 4",
                            f"cmd execute anchored eyes run tp @s {x + 2.5} -58.6 {z - 3.5} facing {x + 2.5} -59.6 {z - 0.5}", "wait 10", f"use {x + 2} -60 {z - 1} north", "wait 10"]


ASSEMBLER = screen(
    "assembler", "AssemblerScreen", "assembler", T("Assembler", "装配机"), T("An Assembler's screen", "装配机的界面"),
    T("Use an Assembler to open it. [Storage](../storage.md) tells how a network crafts.", "对装配机使用即可打开。网络怎样合成，见[存储](../storage.md)。"),
    assembler_open,
    [state("recipe", T("A recipe drawn in its grid.", "画在网格里的配方。"),
           T("An Assembler with a recipe for sticks", "画着木棍配方的装配机"),
           [P("grid", T("The recipe", "配方"), K("residual.assembler.grid_hint")),
            P("result", T("What it makes", "它做的东西"), T("Worked out from the grid.", "由网格算出。")),
            P("keep", T("Keep", "保持"), T("How many to keep in the network, made whenever there are fewer. With nothing to keep, it makes only what is asked of it. With shift, sixty-four times as many.", "要在网络里保持多少，少于这个数就做。不设保持时，它只做别人要的东西。按住 Shift，乘以六十四。")),
            P("status", T("What it is doing", "它在做什么"), T("Crafting, with enough, missing something, or with no recipe yet.", "正在合成、已经够了、缺东西，或者还没有配方。"))],
           ["click has:minecraft:oak_planks", "wait 2", "click grid:0", "wait 2", "click grid:3", "wait 2", "close", "wait 4", "use {ax} -60 {az} north", "wait 10"])])


def processor_open(x, z):
    return [f"cmd setblock {x} -60 {z} residual:processor[facing=north]", f"cmd setblock {x} -60 {z - 1} minecraft:furnace[facing=south]", "cmd give @s minecraft:raw_iron 1", "cmd give @s minecraft:iron_ingot 1",
            f"cmd execute anchored eyes run tp @s {x + 0.5} -58.6 {z + 2.5} facing {x + 0.5} -59.6 {z + 0.5}", "wait 10", f"use {x} -60 {z} south", "wait 10"]


PROCESSOR = screen(
    "processor", "ProcessorScreen", "processor", T("Processor", "处理器"), T("A Processor's screen", "处理器的界面"),
    T("Use a Processor to open it. [Storage](../storage.md) tells how a network crafts.", "对处理器使用即可打开。网络怎样合成，见[存储](../storage.md)。"),
    processor_open,
    [state("pattern", T("A pattern: what goes in, the machine in front, what comes out.", "一个样式：放进去什么、前面的机器、出来什么。"),
           T("A Processor's pattern: raw iron in, an ingot out, with a furnace in front", "处理器的样式：放进粗铁，出来铁锭，前面是熔炉"),
           [P("in", T("What goes in", "放进去的"), K("residual.processor.in_hint")),
            P("out", T("What comes out", "出来的"), K("residual.processor.out_hint")),
            P("machine", T("The machine", "机器"), T("What stands in front of the Processor, which it runs the pattern on.", "处理器前面的东西，样式就在它上面运行。")),
            P("in:on", T("Pages", "页"), T("Nine pages of nine cells each way.", "两边各有九页，每页九格。")),
            P("keep", T("Keep", "保持"), T("How many to keep in the network, made whenever there are fewer.", "要在网络里保持多少，少于这个数就做。")),
            P("status", T("What it is doing", "它在做什么"), T("Rounds a second, or what it lacks.", "每秒做多少轮，或者缺什么。"))],
           ["click hotbar:0", "wait 2", "click in:0", "wait 2", "click hotbar:0", "wait 2", "click hotbar:1", "wait 2", "click out:0", "wait 2", "click hotbar:1", "wait 4"])])


def dock_open(x, z):
    return network(x, z) + [f"cmd setblock {x + 2} -60 {z - 1} residual:drive_dock[facing=north]", "cmd give @s residual:storage_drive",
                            f"cmd execute anchored eyes run tp @s {x + 2.5} -58.6 {z - 3.5} facing {x + 2.5} -59.6 {z - 0.5}", "wait 10", f"use {x + 2} -60 {z - 1} north", "wait 10"]


DOCK = screen(
    "drive_dock", "DockScreen", "drive_dock", T("Drive Dock", "驱动器坞"), T("A Drive Dock's screen", "驱动器坞的界面"),
    T("Use a Drive Dock to open it. [Storage](../storage.md) tells what it is for.", "对驱动器坞使用即可打开。它有什么用，见[存储](../storage.md)。"),
    dock_open,
    [state("bays", T("The drives waiting on the left, the drives done on the right.", "左边是等待的驱动器，右边是处理完的。"),
           T("A Drive Dock with a drive waiting", "有一个驱动器在等待的驱动器坞"),
           [P("waiting", T("Waiting", "等待"), K("residual.dock.mode_help")),
            P("arrow", T("What it moves", "搬动的东西"), T("Fills while it moves anything. Point at it for how much in the last second.", "搬东西时填满。指向它可看到上一秒搬了多少。")),
            P("done", T("Done", "完成"), T("Where each drive comes out.", "每个驱动器从这里出来。")),
            P("dock.mode", K("residual.dock.mode"), T("Empties drives into the network, or fills them from it.", "把驱动器倒进网络，或者从网络装满驱动器。"))],
           ["shift residual:storage_drive", "wait 4"])])


def access_open(x, z):
    return network(x, z) + [f"cmd setblock {x + 2} -60 {z - 1} residual:access_point", f"cmd execute anchored eyes run tp @s {x + 2.5} -58.6 {z - 3.5} facing {x + 2.5} -59.6 {z - 0.5}", "wait 10", f"use {x + 2} -60 {z - 1} north", "wait 10"]


ACCESS = screen(
    "access_point", "AccessPointScreen", "access_point", T("Access Point", "接入点"), T("An Access Point's screen", "接入点的界面"),
    T("Use an Access Point to open it. [Storage](../storage.md) tells how far terminals reach.", "对接入点使用即可打开。终端能覆盖多远，见[存储](../storage.md)。"),
    access_open,
    [state("card", T("A slot for a Relay Card, and a mark for how far it reaches.", "一个放中继卡的槽，和一个表示覆盖范围的标记。"),
           T("An Access Point with no card", "没有中继卡的接入点"),
           [P("card", K("item.residual.relay_card"), K("residual.access_point.card_hint")),
            P("light", T("Its reach", "它的覆盖"), T("Teal with power in a Link Driver on its network, amber with a Relay Card that reaches. Point at it for how many blocks.", "网络上的链路增幅器有电时为青色，中继卡接通时为琥珀色。指向它可看到覆盖多少格。"))])])


def terminal_item_open(x, z):
    return ["cmd give @s residual:terminal", "slot residual:terminal", "useitem", "wait 10"]


MODEL_TERMINAL = screen(
    "terminal", "TerminalScreen", "terminal", T("Terminal", "终端"), T("A Terminal's screen", "终端的界面"),
    T("Use a Terminal to open it, from anywhere the relays of its Model Server reach. [Serving Models](../deployment.md) tells how one is tuned.", "使用终端即可打开，只要它的模型服务器的中继覆盖得到。怎样调谐，见[模型服务](../deployment.md)。"),
    terminal_item_open,
    [state("map", T("**Map**: the relay network as it stands.", "**地图**：中继网络现在的样子。"),
           T("A Terminal's map, not yet tuned", "还没有调谐的终端的地图"),
           [P("terminal.tab.map", K("residual.terminal.tab.map"), K("residual.terminal.tab.map.help")),
            P("reach", T("Its reach", "它的覆盖"), T("Whether it reaches its server from here, and how fast that server answers.", "从这里能否连到它的服务器，以及服务器回答得多快。")),
            P("map", T("The map", "地图"), K("residual.map.fit.help")),
            P("gates", T("The gates", "门"), T("Every gate on the network; click one to go there.", "网络上的每一道门；点击就去那里。")),
            P("map.fit", K("residual.map.fit"), K("residual.map.fit.help"))]),
     state("base", T("**Base**: what the base holds, and what its machines are doing.", "**基地**：基地存着什么，机器在做什么。"),
           T("A Terminal's Base tab", "终端的基地页"),
           [P("terminal.tab.base", K("residual.terminal.tab.base"), K("residual.terminal.tab.base.help")),
            P("base", T("The base", "基地"), T("Each storage network, to open as if standing at it, then each machine with what it is doing.", "每个存储网络，可以像站在它跟前一样打开；然后是每台机器和它在做什么。"))],
           ["click terminal.tab.base", "wait 10"]),
     state("agents", T("**Agents**: what each is doing, to stop, start, hand a program or give an order.", "**智能体**：每个智能体在做什么，可以让它停下、开始、交给它程序或下指令。"),
           T("A Terminal's Agents tab", "终端的智能体页"),
           [P("terminal.tab.agents", K("residual.terminal.tab.agents"), K("residual.terminal.tab.agents.help")),
            P("agents", T("The agents", "智能体"), T("Each agent and what it is doing. Click one to choose it.", "每个智能体和它在做什么。点击可选中。")),
            P("doing", T("The one chosen", "选中的那个"), T("The program it runs, or the one it wants a yes or no to.", "它运行的程序，或者它等你点头的程序。")),
            P("program_cards", K("item.residual.program_card"), T("The written cards you carry, to hand it one.", "你带着的写好的程序卡，可以交给它一张。")),
            P("agent.order", K("residual.agent.order"), T("An order in your own words, for the served model to write as a program.", "用你自己的话写一条指令，由服务器上的模型写成程序。"))],
           ["click terminal.tab.agents", "wait 10"]),
     state("write", T("**Writing**: the served model carrying on whatever you start.", "**续写**：服务器上的模型接着你的开头写下去。"),
           T("A Terminal's Writing tab", "终端的续写页"),
           [P("terminal.tab.write", K("residual.terminal.tab.write"), K("residual.terminal.tab.write.help")),
            P("terminal.prompt", K("residual.terminal.prompt"), T("The start of something, for the model to write on from.", "写一段开头，让模型接着写。")),
            P("terminal.write", K("residual.terminal.write"), T("Sends it to the server.", "把它发给服务器。")),
            P("terminal.clear", K("residual.terminal.clear"), T("Clears the prompt and the answer.", "清空提示和回答。")),
            P("answer", T("The answer", "回答"), T("Grey is what you wrote, white what the model wrote.", "灰色是你写的，白色是模型写的。"))],
           ["click terminal.tab.write", "wait 4"])],
    clip="terminal")


def shard_open(x, z):
    # a shard learns which record it is the first time it is held
    return ["cmd give @s residual:archive_shard", "slot residual:archive_shard", "wait 20", "useitem", "wait 10"]


SHARD = screen(
    "archive_shard", "ShardScreen", "archive_shard", T("Archive Shard", "档案残片"), T("An Archive Shard, read", "读档案残片"),
    T("Use an Archive Shard to read it. [The Archive](../archive.md) tells where its lost text goes.", "使用档案残片即可阅读。失落的文字怎样回来，见[档案](../archive.md)。"),
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

def port_program_open(x, z):
    return block("port", x, z) + ["click port.programs", "wait 10"]


PORT_PROGRAM = screen(
    "port_program", "PortProgramScreen", "port", T("A Port's programs", "端口的程序"), T("A Port's programs", "端口的程序"),
    T("Press the programs button on a Port's screen to open them. With a written card in it, a Port does what the card says instead of what its mode says. [Programs](../programs.md) tells the language they speak.", "在端口的界面上按程序按钮即可打开。放进写好的程序卡后，端口按卡上写的做，不再按模式做。它们说的语言，见[程序](../programs.md)。"),
    port_program_open,
    [state("program", T("**Program**: its cards, its upgrades and the chosen card's program.", "**程序**：它的卡、升级，以及选中的卡上的程序。"),
           T("A Port's program tab", "端口的程序页"),
           [P("tab:program", TABS, T("The program on the chosen card, and how it has been running.", "选中的卡上的程序，以及它运行得如何。")),
            P("cards", K("item.residual.program_card"), T("Click a slot to choose it; the program below is what that card says.", "点击一个槽来选中；下面的程序就是那张卡上写的。")),
            P("program", T("The program", "程序"), T("Checked as it is typed.", "输入时就会检查。")),
            P("check", T("The check", "检查结果"), T("Says whether it reads as a program, and where it slips if not.", "告诉你它能不能读成程序，读不成时错在哪里。")),
            P("program.run", K("residual.program.run"), K("residual.port.program.run.help")),
            P("program.stop", K("residual.program.stop"), K("residual.program.stop.help")),
            P("hands", T("What it holds", "手里的东西"), T("What its programs work with.", "它的程序用来干活的东西。")),
            P("upgrades", T("Upgrades", "升级"), T("What makes it faster, stronger and further-reaching.", "让它更快、更强、够得更远的东西。")),
            P("port.to_filter", K("residual.port.to_filter"), K("residual.port.to_filter.help")),
            P("owned.access", K("residual.owned.access"), K("residual.owned.help"))]),
     state("trace", T("**Trace**: how it has been running, with a debugger beside it.", "**追踪**：它运行得如何，旁边是调试器。"),
           T("A Port's trace and debugger", "端口的追踪和调试器"),
           [P("trace", T("The trace", "追踪"), T("Each step with how it went.", "每一步及其结果。")),
            P("debug_lines", K("residual.debug.title"), K("residual.debug.pause.help")),
            P("debug.step", K("residual.debug.step"), K("residual.debug.step.help")),
            P("debug_values", K("residual.debug.values"), T("What the program holds now, and what you asked to watch.", "程序现在保存的值，以及你要观察的东西。"))],
           ["click tab:trace", "wait 4"])])


def exosuit_open(x, z):
    return ["cmd item replace entity @s armor.chest with residual:exosuit", "wait 4", "bound key.residual.exosuit", "wait 10"]


EXOSUIT = screen(
    "exosuit", "ExosuitScreen", "exosuit", T("Exosuit", "外骨骼"), T("An exosuit's screen", "外骨骼的界面"),
    T("Sneak and use an exosuit, or press its key while you wear it, to open it. [The Exosuit](../exosuit.md) tells what it runs on.", "潜行时使用外骨骼，或穿着它时按键，即可打开。它靠什么运转，见[外骨骼](../exosuit.md)。"),
    exosuit_open,
    [state("board", T("What its board found, and each ability, turned on and off beside the window.", "它的板子发现了什么，以及每种能力，在窗口旁打开或关闭。"),
           T("An exosuit with no model on board", "板上没有模型的外骨骼"),
           [P("charge", T("Its charge", "电量"), T("Point at it for how much it holds.", "指向它可看到存着多少。")),
            P("display", T("Its board", "它的板子"), T("The model's weights, what they take as plain floats and in each form it can run in, how each scores, its compute, and what each ability running draws from it.", "模型的权重、用普通浮点和每种能运行的形式各占多少、各得多少分、它的算力，以及每种运行中的能力要从中取走多少。")),
            P("row:flight", T("An ability", "一种能力"), T("A line for each, with the compute it draws, teal while it runs, amber if it is on and there is not enough for it. Point at one for what it does.", "每种能力一行，带着它要的算力，运行时为青色，打开但算力不够时为琥珀色。指向一行可看到它做什么。")),
            P("ability.flight", T("Its switch", "开关"), T("Turns the ability on or off. Those left on run cheapest first, as far as the compute goes.", "打开或关闭这种能力。打开的能力从最便宜的开始运行，算力用完为止。"))])])


def gate_open(x, z):
    return [f"cmd setblock {x} -60 {z} residual:relay_gate", stand(x, z), "wait 10", f"use {x} -60 {z} north", "wait 10"]


GATE = screen(
    "relay_gate", "GateScreen", "relay_gate", T("Relay Gate", "中继门"), T("A Relay Gate's screen", "中继门的界面"),
    T("Use a Relay Gate to open it. [Serving Models](../deployment.md) tells how the network is built and what opens a gate.", "对中继门使用即可打开。网络怎样搭起来、门怎样开启，见[模型服务](../deployment.md)。"),
    gate_open,
    [state("map", T("Its name, the network, and every gate on it to go to.", "它的名字、网络，以及网络上可以去的每一道门。"),
           T("A Relay Gate's screen", "中继门的界面"),
           [P("relay_gate.name", T("Its name", "它的名字"), T("Shown on the map and in every list of gates.", "显示在地图和每个门的列表里。")),
            P("status", T("Open or shut", "开启或关闭"), T("Open with how many answers a second its model gives, or why it is shut.", "开启时显示模型每秒能回答多少次，关闭时说明原因。")),
            P("map", T("The map", "地图"), K("residual.map.fit.help")),
            P("gates", T("The gates", "门"), T("Every other gate on the network; click one to go there.", "网络上的其他每一道门；点击就去那里。")),
            P("map.fit", K("residual.map.fit"), K("residual.map.fit.help"))])])

LINE = ["refinery_intake", "duplicate_sieve", "filter_bed", "quality_classifier", "program_press", "domain_mixer", "contamination_check", "token_tank"]


def refinery_open(x, z):
    """A line on power that a dump has been through, so every machine has something to say, with its Intake open."""
    return [f"cmd setblock {x + 2 * i} -60 {z} residual:{id}[facing=east]" for i, id in enumerate(LINE)] + [
        f"cmd fill {x} -61 {z} {x + 16} -61 {z} residual:power_cable", f"cmd setblock {x + 16} -60 {z} residual:combustion_generator[facing=north]",
        f'cmd data merge block {x + 16} -60 {z} {{items:{{Items:[{{Slot:0b,id:"minecraft:coal_block",count:64}}]}}}}',
        "cmd give @s residual:relay_dump", stand(x, z), "wait 10", f"use {x} -60 {z} north", "wait 10", "shift residual:relay_dump", "wait 4", "click refinery.start", "wait 4", "close",
        f"await 6000 refined {x + 14} -60 {z} 1", "wait 200", f"use {x} -60 {z} north", "wait 10"]


def next_machine(i):
    return ["close", "wait 4", "cmd tp @s ~2 ~ ~", "wait 4", f"use {{x{2 * i}}} -60 {{sz}} north", "wait 10"]


def refinery_setting(id):
    return P(f"refinery.setting.{id}", K(f"residual.refinery.setting.{id}"), K(f"residual.refinery.setting.{id}.hint"))


READ_ROWS = T("Point at a row for what it means.", "指向一行可看到它的意思。")
REFINERY = screen(
    "refinery", "RefineryScreen", "refinery_intake", T("Refinery machines", "精炼厂的机器"), T("The screens of a Refinery", "精炼厂的界面"),
    T("Use any machine of a line to open its screen. [The Refinery](../refinery.md) tells how a line goes together and what each machine is worth.",
      "对线上的任意一台机器使用即可打开它的界面。一条线怎样搭起来、每台机器值多少，见[精炼厂](../refinery.md)。"),
    refinery_open,
    [state("intake", T("A **Refinery Intake**: what goes in, and the two things it can be asked.", "**精炼进料口**：放进去的东西，以及可以让它做的两件事。"),
           T("A Refinery Intake after a pass", "一次精炼之后的精炼进料口"),
           [P("slots", T("What goes in", "放进去的东西"), K("residual.refinery.refinery_intake.slot0")),
            P("rows", T("What it read", "它读到的"), T("What the slots hold and how much text that came to; once measured, the held-out loss of the raw text.", "槽里有什么、一共多少文本；测量之后，还有原始文本的留出损失。")),
            P("refinery.start", K("residual.refinery.start"), K("residual.refinery.start_hint")),
            P("refinery.measure", K("residual.refinery.measure"), K("residual.refinery.measure_hint"))]),
     state("sieve", T("A **Duplicate Sieve**: what it took out, and the curve its hashes make.", "**去重筛**：它去掉了什么，以及它的哈希画出的曲线。"),
           T("A Duplicate Sieve after a pass", "一次精炼之后的去重筛"),
           [P("rows", T("What it took out", "它去掉的"), READ_ROWS),
            P("curve", T("The curve", "曲线"), K("residual.refinery.duplicate_sieve.curve")),
            refinery_setting("near")],
           next_machine(1)),
     state("filters", T("A **Filter Bed**: a row and a switch for each rule.", "**过滤床**：每条规则一行、一个开关。"),
           T("A Filter Bed after a pass", "一次精炼之后的过滤床"),
           [P("rows", T("What each rule threw out", "每条规则丢掉的"), READ_ROWS)] + [refinery_setting(id) for id in ("short", "symbols", "words", "repeats", "endings")],
           next_machine(2)),
     state("classifier", T("A **Quality Classifier**: its model and its rule, and what each decided.", "**质量分类器**：它的模型和规则，以及各自的决定。"),
           T("A Quality Classifier that had no model", "没有模型的质量分类器"),
           [P("slots", T("Its model", "它的模型"), K("residual.refinery.quality_classifier.slot0")),
            P("rows", T("What it decided", "它的决定"), READ_ROWS),
            refinery_setting("sure")],
           next_machine(3)),
     state("press", T("A **Program Press**: what it wrote into the stream.", "**程序印刷机**：它往文本流里写了什么。"),
           T("A Program Press after a pass", "一次精炼之后的程序印刷机"),
           [P("slots", T("Its book", "它的书"), K("residual.refinery.program_press.slot0")),
            P("rows", T("What it wrote", "它写的"), READ_ROWS),
            refinery_setting("program"), refinery_setting("share")],
           next_machine(4)),
     state("mixer", T("A **Domain Mixer**: a row for each kind of writing, and what the last of a run reads.", "**领域混合器**：每类文字一行，以及一次训练的最后阶段读什么。"),
           T("A Domain Mixer after a pass", "一次精炼之后的领域混合器"),
           [P("rows", T("The kinds of writing", "各类文字"), T("Each one's share of what a model reads. Click a row for more or less of it.", "每类文字在模型所读内容里的份额。点击一行可以增减。")),
            refinery_setting("anneal"), refinery_setting("after")],
           next_machine(5)),
     state("check", T("A **Contamination Check**: what it found of each benchmark's test.", "**污染检查站**：它在每个基准的测试里发现了什么。"),
           T("A Contamination Check after a pass", "一次精炼之后的污染检查站"),
           [P("rows", T("What it found", "它发现的"), T("Lines it found first, then a row for each benchmark. Scroll for the rest.", "先是发现的行，然后每个基准一行。滚动可看到其余。")),
            refinery_setting("remove")],
           next_machine(6)),
     state("tank", T("A **Token Tank**: what the line holds, counted.", "**词元罐**：这条线存下的内容，已经数好。"),
           T("A Token Tank after a pass", "一次精炼之后的词元罐"),
           [P("tokenizer", K("item.residual.tokenizer"), K("residual.refinery.token_tank.slot0")),
            P("cell", K("item.residual.token_cell"), K("residual.refinery.token_tank.slot1")),
            P("rows", T("What it holds", "它存下的"), READ_ROWS)],
           next_machine(7))])


def rack_module_open(x, z):
    return ["cmd give @s residual:accelerator 8"] + block("rack_module", x, z)


RACK_MODULE = screen(
    "rack_module", "RackModuleScreen", "rack_module", T("Rack Module", "机架模块"), T("A Rack Module's screen", "机架模块的界面"),
    T("Use a Rack Module to open it. [Compute](../compute.md) tells how racks are built.", "对机架模块使用即可打开。机架怎样搭起来，见[算力](../compute.md)。"),
    rack_module_open,
    [state("bays", T("Eight bays, an Accelerator in the first.", "八个槽位，第一个放着加速器。"),
           T("A Rack Module with one device", "放着一个设备的机架模块"),
           [P("bay:0", T("The bays", "槽位"), K("residual.rack_module.bay_hint"))],
           ["shift residual:accelerator", "wait 4"])])


HALL_CONSOLE = screen(
    "hall_console", "HallConsoleScreen", "hall_console", T("Hall Console", "机房控制台"), T("A Hall Console's screen", "机房控制台的界面"),
    T("Use a Hall Console to open it. [Halls](../datacenter.md) tells what makes a hall.", "对机房控制台使用即可打开。怎样才算机房，见[机房](../datacenter.md)。"),
    lambda x, z: block("hall_console", x, z),
    [state("reading", T("One display: what the hall is, what it is doing, and what the largest run it could hold would cost.", "一块显示屏：机房是什么样、正在做什么，以及它放得下的最大一次训练要花多少。"),
           T("A Hall Console's screen", "机房控制台的界面"),
           [P("reading", T("The hall", "机房"), T("Its racks, pods and devices, what it is rated for, what it is running, and what it draws. Point at the first line for what it still lacks, at the power for what PUE is.",
                                                "它的机架、机组和设备，额定能力，正在运行多少，以及耗电多少。指向第一行可看到还缺什么，指向电力那行可看到 PUE 是什么。")),
            P("largest", T("The largest run", "最大的一次训练"), K("residual.hall.prediction")),
            P("racks", T("Its racks", "它的机架"), T("A column for each rack, as tall as that rack is busy.", "每个机架一列，机架越忙，这一列越高。"))])])

def server_open(x, z):
    return block("model_server", x, z)


SERVER = screen(
    "model_server", "ServerScreen", "model_server", T("Model Server", "模型服务器"), T("A Model Server's screen", "模型服务器的界面"),
    T("Use a Model Server with an empty hand to open it. [Serving Models](../deployment.md) tells what each thing in its panel earns.",
      "空手对模型服务器使用即可打开。面板里每样东西能换来什么，见[模型服务](../deployment.md)。"),
    server_open,
    [state("empty", T("What it serves and what that costs, its panel beside it, and its settings under that.", "它在服务什么、代价如何，旁边是面板，面板下面是设置。"),
           T("A Model Server with nothing in it", "空的模型服务器"),
           [P("model", K("residual.server.model_slot"), T("A saved checkpoint. Sneak-use the server with an empty hand to take it back.", "一枚已保存的检查点。潜行空手对服务器使用可以取回。")),
            P("rate", T("Answers a second", "每秒回答数"), T("Answers that read 16 tokens and write 8, as its model and what is in its panel were measured to cost. The bar is how much of its operations is in use.",
                                                             "读 16 个词元、写 8 个的回答，按这个模型和面板里的东西实测的代价算。条形表示它的运算用掉了多少。")),
            P("memory", K("residual.server.memory"), T("Its weights, its draft and what it keeps, of all it holds.", "权重、草稿模型和保留的序列，占它全部内存的多少。")),
            P("drawn", K("residual.server.drawn"), K("residual.server.drawn.title")),
            P("cache", K("item.residual.kv_cache"), K("residual.server.cache_slot")),
            P("packer", K("item.residual.weight_packer"), K("residual.server.packer_slot")),
            P("draft", K("residual.server.draft"), K("residual.server.draft_slot")),
            P("judge", K("residual.server.judge"), K("residual.server.judge_slot")),
            P("server.form", K("residual.server.form"), K("residual.server.form.hint")),
            P("server.batch", K("residual.server.batch"), K("residual.server.batch.hint")),
            P("server.effort", K("residual.server.effort"), K("residual.server.effort.hint"))])])


def tuner_open(x, z):
    return block("tuner", x, z)


TUNER = screen(
    "tuner", "TunerScreen", "tuner", T("Tuner", "调校台"), T("A Tuner's screen", "调校台的界面"),
    T("Use a Tuner to open it. [Tuning](../tuning.md) tells what each way of tuning does and what it earns.", "对调校台使用即可打开。每种调校方式做什么、能换来什么，见[调校](../tuning.md)。"),
    tuner_open,
    [state("idle", T("A saved model on the left, what it needs beside it under that, and a blank checkpoint on the right; the toolbar sets how.", "左边是已保存的模型，下面放它需要的东西，右边是空白检查点；工具栏决定怎么调校。"),
           T("A Tuner with nothing in it", "空的调校台"),
           [P("base", T("The base", "基础模型"), K("residual.tuner.base_slot")),
            P("aid", T("Beside it", "放在旁边的"), K("residual.tuner.method.fine_tune.needs")),
            P("out", T("What comes out", "产出"), K("residual.tuner.out_slot")),
            P("show", T("The display", "显示屏"), T("Before it starts, what the way it is set to does. While it runs, the reward in teal against what was really right in amber, each try's mark, and what the model last wrote.",
                                                    "开始之前，显示当前方式做什么。运行时，青色是奖励，琥珀色是真正对了多少，还有每次尝试的标记，以及模型最后写的东西。")),
            P("tuner.run", K("residual.tuner.run"), K("residual.tuner.run.hint")),
            P("tuner.method", K("residual.tuner.method"), K("residual.tuner.method.hint")),
            P("tuner.budget", K("residual.tuner.budget"), K("residual.tuner.budget.hint")),
            P("tuner.auto", K("residual.tuner.auto"), K("residual.tuner.auto.hint"))])])


SCREENS = [CORE, AGENT, BENCH, FABRICATOR, FORGE, OPERATOR, READER, FORECASTER, SURVEYOR, WATCH, POWER, TERMINAL, PORT, PORT_PROGRAM, RACK, ASSEMBLER, PROCESSOR, DOCK, ACCESS,
           MODEL_TERMINAL, GATE, EXOSUIT, SHARD, WAYFINDER, REFINERY, RACK_MODULE, HALL_CONSOLE, SERVER, TUNER]
