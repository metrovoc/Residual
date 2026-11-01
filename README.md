<div align="center">

<img src="src/main/resources/residual_logo.png" alt="Residual" width="372">

**Rediscover the lost art of building intelligence from scratch.**

[Website](https://metrovoc.github.io/Residual/) · [Download](https://github.com/metrovoc/Residual/releases) · [Discord](https://discord.gg/UuV4hEYVFy)

</div>

> [!WARNING]
> Residual is an experimental project and currently in alpha. Expect breaking changes and major bugs. Your
> [feedback](https://github.com/metrovoc/Residual/issues) is much appreciated.

![A model training by a river at noon: a wave of light runs along its cables and each machine's output floats above it, while far behind it agents build small houses near a relay tower](docs/images/hero.webp)

Someone lived in this world before you. They are no longer here, but traces of them still remain in the ruins. Most
mysteriously, they seemed to have built machines that thought.

Residual is a Minecraft mod about building those machines again, and every model you make with them is a real language
model. Each machine performs one real operation: a table lookup, a matrix multiplication, a softmax, a whole transformer
layer. Design the machines from the smallest operations there are, cable them into a network, train it, test it on what
it has never seen, and put it to work. A model that knows what the blocks around it will do next runs them ahead of time,
and agents run the programs you click together, with models where learning beats a rule. The arithmetic is what PyTorch
does, down to the optimizer.

## Why this project

Real systems are a lot of fun, but they often come with overwhelming complexity, some more accidental. This project aims to abstract that
complexity away at the right levels, to make a mod that is enjoyable and satisfying to play while staying faithful to
the essence of real systems.

## Machines from scratch

![Softmax on the Bench: wired straight from its formula it overflows on scores near 1000; with each row's maximum taken off first, every case passes](docs/images/bench.webp)

The machines that do the real computing, from the matrix multiplier to the transformer block, are made from designs
you wire yourself. On a Bench, a module is built from the smallest operations there are: add and multiply, exp and
log, a sum or a maximum along the last axis, a transpose. A spec says what the module must compute and checks it on
cases worked out exactly, among them the inputs that break the obvious design. Softmax wired straight from its formula
passes the ordinary cases and turns red on scores near 1000, where the exponential overflows; take each row's maximum
off first and it passes them all. Specs build on each other, from matrix multiply and softmax up to attention and a
whole transformer block.

A verified design is recorded onto a photomask, and a Fabricator makes machines from it. The ruins hold old photomasks
too, for anyone who would rather not wire everything themselves. Their designs are correct but written out one
operation at a time, so their machines give the same answers, use more memory and train more slowly.

## Models

<p>
<img src="docs/images/train.webp" alt="The Train tab as a small GPT learns to sort: the loss on practice and on the test falling together" width="49%">
<img src="docs/images/tests.jpg" alt="The Tests tab: a model's score on each benchmark" width="49%">
</p>

Cable the machines together, and a Model Core trains them as one model, on up to four kinds of practice at once. It
shows the learning curves as they fall, every machine's output shape, its weights and its gradient.

While a model trains, waves run through its blocks: a teal pulse from the tokens to the loss as the batch goes forward,
then an amber one back as the gradient returns, each block lighting as brightly as what it carries. A block that stays
dark on the way back is hardly learning. Above every machine floats its output as a hologram, and an attention head
shows where each token looks.

Every model is tested on what practice never shows it, and a job asks its model in exactly the test's format, so a test
score is how often the model will get that job right. A single embedding table can forecast a furnace. Copying a build
takes two layers of attention, and understanding what you say takes a model too big to carry.

## Running ahead

<p>
<img src="docs/images/forecaster.webp" alt="A Forecaster among furnaces, a smoker and a blast furnace, running them ahead while a furnace out of its reach keeps its own pace" width="49%">
<img src="docs/images/forecaster_screen.jpg" alt="The Forecaster's screen: four machines, how far they moved against what its model expected, and the model trusted at four times" width="49%">
</p>

A Forecaster watches the furnaces, smokers, brewing stands and crops around it and writes down what each one does. A
model trained on lines like these is a small model of the world, and knows what those blocks will do next. The
Forecaster checks it against everything the blocks did by themselves, and if its forecasts hold, the Forecaster runs
them ahead, drawing each block's next moments from the model and making them so. A model that is wrong on average is
refused. Over one minute at four times the speed, a furnace beside a Forecaster made 24 iron ingots, and one out of its
reach made 6.

A machine keeps its pace, so a model of machines in general forecasts yours, and the first model you build, a single
embedding table, is enough. Crops are harder: the way to forecast your own field is to train on what the Forecaster saw
it do. Better models go further. Forecaster II runs blocks up to sixteen times as fast, and an Hourglass spends the time
it keeps on bursts for one block, wherever it is.

## Agents

<p>
<img src="docs/images/program.webp" alt="A program clicked together from an agent's palette of pieces, then run" width="49%">
<img src="docs/images/sort.webp" alt="An agent's trace as it sorts a barrel by kind, filing each thing where its filter model says" width="49%">
</p>

An agent is a frame that runs a program, such as `repeat (mine 4 stone, put)` or `take bread, come, give bread`, typed in
its screen or clicked together from a palette of pieces, and carried from agent to agent on Program Cards. Walking,
carrying, mining and laying blocks are its own. Wherever there is a real decision to make (which chest the iron went
into; what kind of thing this is; what you meant), it asks a model and does what the model answers. Its trace shows every
step it took, what it checked, and why a step failed. A model that passes its test at 98% decides right 98 times in 100.

Say an order in chat, such as `Pip, bring me some bread`, and every agent named Pip within earshot hears it. Its model
writes the order as a program; anything longer than one step it reads back, and runs only once you click Do it.

With a model that can carry on a pattern, an agent copies a build, moves it with everything inside, or keeps building
it. A Surveyor reads the ground around it with a model, and holding a Survey Rod near it shows you through the ground the
ores it found, and the chests and barrels the old world filled that nobody has opened. Tell an agent `mine 8 iron` and it
digs its way to them. Agents mining near each other each take a different block, so they work as a crew.

## Storage

<p>
<img src="docs/images/storage.jpg" alt="A Storage Terminal showing what the network keeps, with how many of each" width="49%">
<img src="docs/images/port.jpg" alt="A Port's screen: its four modes, its filter, and the items moved in the last second" width="49%">
</p>

Residual keeps its own storage, with no channels and nothing to power. Storage Drives in a Drive Rack keep any number of
kinds of item, and Storage Links join them to Storage Terminals and Ports. A terminal shows everything the network keeps
and finds anything by name, mod, tag, tooltip or id: `@minecraft ingot -iron` finds every ingot but iron. A Port holds
its front against a chest or a machine and imports, exports, keeps a set stock, or opens the inventory to the network,
moving 1,280 items a second.

An Assembler crafts a recipe drawn in its grid from what the network keeps, and a Processor runs a furnace or any other
machine the same way. Ask a terminal for something they can make, and it plans the whole tree before anything starts:
what comes from storage, how many crafts make the rest, what is missing. A job can start with something missing and wait
for it, any number of jobs run at once, and Ports and agents can ask too.

## Power

A Combustion Generator burns furnace fuel, and Power Cables carry the power to Energy Cells and to the machines that run
on it. It is all Forge Energy, so it works with other mods' cables and machines. The Smelter makes two ingots from every
ore, and three when a model you trained keeps the melt at the right heat, something it learned by playing melts of its
own and learning from its choices.

A flight frame, worn on the back and charged from an Energy Cell, flies you as freely as creative play while its pilot
holds it steady: a model that learned to fly the frame on its own flights, reading its speed from the heights before and
meeting the wind before it pushes.

## The archive

<p>
<img src="docs/images/ruin.jpg" alt="An archive house of calcite and tuff, one corner fallen in" width="49%">
<img src="docs/images/reader.webp" alt="The Reader restoring a record's lost text, block by block" width="49%">
</p>

The old world left its places behind: relay towers along the old roads, windmill farms on the ridges, stilt houses on
the marshes, slipways on the shores, inns and watermills, a market town's square, halls where minds were taught, a great
mine and an archive dug into a hill, sixteen kinds in all. Each is rare and stands only in the land that suits it, and a
Wayfinder leads to the nearest of any kind the archive has told you of. They hold archive shards, drives already full of
the old world's writing, the schematics of machines whose plans were lost, and salvaged models that no longer work.

A shard is one record of the old world's archive, a letter or a journal, a story, a song, a manual or a notice, with
some of its text worn away. Filed into an Archive Drive, what you recover becomes something to learn from: a model that
practises on it learns to read the way the old world wrote. A Reader restores lost text with a model of your own, and a
lost block comes back only if your model can predict it. Each restored block is more text to learn from.

Models start out reading one character at a time. A BPE Forge learns a vocabulary from the text you have recovered,
merging the most frequent pair of tokens over and over, and a model that reads with it sees words where it used to see
letters.

## Serving models

<p align="center">
<img src="docs/images/terminal.jpg" alt="A Terminal: writing with a model served from a Model Server" width="60%">
</p>

An agent carries a small model. A bigger one stays in a Model Server and answers over relays, anywhere they reach. A
Terminal lets you write with the served model and carries your orders as far as the relays go. Training Accelerators give
the Core more threads and the server more answers a second, and kept cool by water, ice or snow, they work twice as hard.

## Real models

Residual trains with its own reverse-mode autograd engine, written in Java to match PyTorch operation for operation,
and with the same optimizer: AdamW with gradient clipping and a short warmup. Every operation's output and gradients,
and the optimizer's steps, are tested against reference values computed once with PyTorch (`TorchParityTest`), and
every gradient against numerical differentiation (`OpsGradientTest`). Nothing of PyTorch ships with the mod.

A two-layer GPT with four attention heads and a 64-wide residual stream has 113,742 parameters. Trained for 4,000 steps
on the text shards can carry, it reaches a loss of 1.34 on records it never saw, and the Reader restores 88% of the lost
blocks it is given.

## Install

Residual needs Minecraft 26.1.2, NeoForge 26.1.2.124 or newer, and [GuideME](https://modrinth.com/mod/guideme)
26.1.16-beta or newer. Put the jar from [Releases](https://github.com/metrovoc/Residual/releases) in your `mods` folder
next to GuideME.

You get the field notes the first time you join a world, and they are also
[online](https://metrovoc.github.io/Residual/). Hold **G** over any Residual item to open its page.

### Modpack

`Residual-0.5.0-alpha.mrpack`, also on the Releases page, sets up Residual with Applied Energistics 2, JEI, Jade, Sodium,
ModernFix and FerriteCore. Import it in Prism Launcher or the Modrinth App.

## Building

Building needs Java 25.

```sh
./gradlew build       # build/libs/residual-26.1.2-0.5.0-alpha.jar
./gradlew runClient
```

The textures, the JSON data, the ruins and the guide's formula images are generated by the scripts in `tools`, and
their output is checked in. After changing a script, run it with [uv](https://docs.astral.sh/uv/):

```sh
uv run --with pillow python tools/art.py
uv run python tools/data.py
uv run --with matplotlib python tools/formulas.py
```

`uv run python tools/pack.py` builds the modpack around the jar in `build/libs`, and `./gradlew guideWebsite` builds
the field notes as a website in `build/guideWebsite`.

## Credits

The guide runs on [GuideME](https://github.com/AppliedEnergistics/GuideME).

## License

[MIT](LICENSE)
