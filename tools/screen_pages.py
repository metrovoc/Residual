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
           [P("row:forecasting", T("A benchmark", "一项基准"), T("One line for each: its name, a bar toward what passes, and its score, green once it passes. Point at a line for its job, when it was tested and how its test goes. Click it for its runs from fresh seeds, a look-alike of its test and the server's leaderboard: [Comparing Models](../evaluation.md) tells how they go.",
                                                               "每项基准一行：名称、一条通向及格线的进度条和分数，及格后变绿。指向一行可看到它的用途、测试时间和测试方式。点击它可看到从新种子训练的结果、它的相似题，以及服务器的排行榜：见[比较模型](../evaluation.md)。")),
            P("core.test_all", K("residual.core.test_all"), T("Runs every test now.", "立即运行所有测试。")),
            SAVE],
           ["click tab:tests", "wait 20", "click core.test_all", "wait 60"]),
     state("evaluation", T("Click a benchmark to compare its test, fresh runs and the server's designs.", "点击一项基准，比较它的测试、新运行和服务器上的设计。"),
           T("One benchmark's comparison, with seed runs and the server's leaderboard", "一项基准的比较视图，包含种子运行和服务器排行榜"),
           [P("evaluation", T("The comparison", "比较"), T("The score on its test and on a look-alike with other items. A warning means its test flatters it beyond chance.", "原测试与另一套相似题的分数。出现警告，表示原测试带来的优势超出了偶然。")),
            P("seeds", T("Fresh seeds", "新种子"), T("The spread of runs from fresh weights and batches, with the previous design below when there is one.", "从新权重和批次开始的多次运行有多分散；有上一个设计时，它显示在下面。")),
            P("leaderboard", T("The server's board", "服务器榜单"), T("The leading designs, their makers, scores and sizes. Point at one for its machines.", "领先的设计，以及它们的作者、分数和大小。指向一个可看到它的机器。")),
            P("histograms", T("The distributions", "分布"), T("How the server's designs spread by score and size, with this model marked in amber.", "服务器的设计按分数和大小怎样分布，当前模型以琥珀色标出。")),
            P("evaluation.back", K("residual.evaluation.back"), T("Returns to the benchmark list.", "返回基准列表。"))],
           ["click row:forecasting", "until 1200 fresh 1", "wait 4"]),
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
           T("The Recipe tab: the settings, what the run is doing and the rate over the run", "配方页：各项设置、运行正在做的事和整次运行的学习率"),
           [P("settings", T("The settings", "设置"), T("The value each is set to. [The Recipe](../recipe.md) says what each does.", "每一项现在的值。每一项的作用见[配方](../recipe.md)。")),
            P("readings", T("What the run is doing", "运行正在做的事"), T("The rate now, what the weights take, the text read, the spikes, the largest scores and how often a second head is right. Point at one for what it means.",
                                                                     "当前的学习率、权重占用的大小、读到的文字、尖峰、最大的分数，以及第二个头猜对的比例。指向某一项可看到它的含义。")),
            P("rate", K("residual.recipe.plot"), T("What the learning rate is multiplied by from the first step to the last, with a line where the run has reached.",
                                                  "学习率从第一步到最后一步乘上的比例，并用一条线标出运行走到了哪里。")),
            P("recipe.schedule", K("residual.recipe.schedule"), K("residual.recipe.schedule.hint")),
            P("recipe.warmup", K("residual.recipe.warmup"), K("residual.recipe.warmup.hint")),
            P("recipe.length", K("residual.recipe.length"), K("residual.recipe.length.hint")),
            P("recipe.precision", K("residual.recipe.precision"), K("residual.recipe.precision.hint")),
            P("recipe.moves", K("residual.recipe.moves"), K("residual.recipe.moves.hint")),
            P("recipe.packing", K("residual.recipe.packing"), K("residual.recipe.packing.hint"))],
           ["click tab:recipe", "wait 2"]),
     state("compute", T("**Compute**: what the run really costs.", "**算力**：这次训练实际花了多少。"),
           T("The Compute tab: the run's threads, memory and flops", "算力页：这次训练的线程、内存和运算次数"),
           [P("reading", T("The run", "这次训练"), T("The threads it trains on, the memory it takes to train, its flops a second, the flops and tokens counted so far, and how many flops that comes to for each parameter and token.",
                                                 "它训练用的线程、训练占用的内存、每秒运算次数、到目前为止数到的运算次数和词元，以及平均每个参数、每个词元用了多少次运算。"))],
           ["click tab:compute", "wait 20"])])


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
            P("model", K("residual.reader.model_slot"), T("The checkpoint whose model restores it.", "一个检查点，用里面的模型来复原它。")),
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
            P("model", K("residual.reader.model_slot"), K("residual.forecaster.model_hint"))])],
    clip="forecaster_screen")


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


def instruments_open(x, z):
    return ["cmd rehearsal model pattern", "cmd give @s residual:checkpoint", f"cmd setblock {x + 2} -60 {z} residual:trial_rig[facing=north]"] + block("stream_scope", x, z) + [
        "shift residual:checkpoint", "wait 10"]


INSTRUMENTS = screen(
    "instruments", "InstrumentScreen", "stream_scope", T("Stream Scope and Trial Rig", "流示波器与故障试验架"), T("The screens of the Scope and the Rig", "流示波器与故障试验架的界面"),
    T("Use a Stream Scope or a Trial Rig to open them. [The Stream Scope](../scope.md) and [The Trial Rig](../trials.md) tell what each tab does.",
      "对流示波器或故障试验架使用即可打开。每个标签页做什么，见[流示波器](../scope.md)和[故障试验架](../trials.md)。"),
    instruments_open,
    [state("model", T("**Model**: the model it works on, a copy of it each time.", "**模型**：它处理的模型，每次都用一个副本。"),
           T("A Stream Scope with a model in it", "放着模型的流示波器"),
           [P("instrument.model", T("The model", "模型"), T("A checkpoint. With none, the model standing on a Model Core it touches.", "一个检查点。没有的话，就用它接触的模型核心上的模型。")),
            P("status", T("Status", "状态"), T("How big the model is and how many points its stream has, or how the last work came out.", "模型有多大、它的流有几个点，或者上一次工作的结果。"))]),
     state("instrument", T("Every other tab is one instrument: its choices in a row, then one display.", "其他每个标签页都是一件仪器：一排选项，下面一个显示屏。"),
           T("A Stream Scope's logit lens", "流示波器的 logit 透镜"),
           [P("tab:lens", T("The instruments", "仪器"), T("Each reads the model its own way.", "每件仪器用自己的方式读模型。")),
            P("instrument.run", T("Start or stop", "开始或停止"), T("Starts the work, and while it runs, stops it.", "开始工作；工作进行时，点它就停止。")),
            P("display", T("The display", "显示屏"), T("What the work comes to, drawn as it goes. Point at anything for its numbers.", "工作的结果，边做边画。指向任何东西可看到它的数字。"))],
           ["click tab:lens", "wait 4", "click instrument.run", "wait 200"]),
     state("rig_model", T("A Trial Rig's **Model** tab also holds what the trial works on and a checkpoint for the model it leaves behind.", "故障试验架的**模型**页还放着试验处理的内容，以及用来存下试验所得模型的检查点。"),
           T("A Trial Rig's model, data and output slots", "故障试验架的模型、数据和输出槽"),
           [P("instrument.model", T("The model", "模型"), T("A checkpoint. With none, the model standing on a Model Core it touches.", "一个检查点。没有的话，就用它接触的模型核心上的模型。")),
            P("instrument.data", T("What it works on", "处理的内容"), T("A dataset, a drive or a Field Log, for the trials that need one.", "数据集、驱动器或田间日志，供需要的试验使用。")),
            P("instrument.output", T("What it keeps", "留下的东西"), T("A checkpoint that takes the model a trial leaves behind.", "一个检查点，存下试验留下的模型。")),
            P("status", T("Status", "状态"), T("How big the model is and how many points its stream has, or how the last work came out.", "模型有多大、它的流有几个点，或者上一次工作的结果。"))],
           ["close", "wait 4", "use {sx} -60 {sz} north", "wait 10", "click tab:model", "wait 4"]),
     state("trial", T("A Trial Rig's tabs are trials: each trains a copy of the model and charts what comes of it.", "故障试验架的标签页都是试验：每个都训练模型的一个副本，并画出结果。"),
           T("A Trial Rig's contamination trial", "故障试验架的污染试验"),
           [P("tab:contaminate", T("The trials", "试验"), T("Memory, Backdoor, Forgetting, Collapse and Contamination.", "记忆、后门、遗忘、崩塌和污染。")),
            P("instrument.run", T("Start or stop", "开始或停止"), T("Starts the trial, and while it runs, stops it.", "开始试验；试验进行时，点它就停止。")),
            P("display", T("The display", "显示屏"), T("The trial's curves as it goes.", "试验进行时的曲线。"))],
           ["click tab:contaminate", "wait 4"])])

SCREENS = [CORE, FORGE, OPERATOR, READER, FORECASTER, SHARD, WAYFINDER, INSTRUMENTS]
