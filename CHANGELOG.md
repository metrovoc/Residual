# Changelog

## 0.6.0-alpha

Build a datacenter, keep a reactor in its band with a model you trained, and turn recorded fights into loot in an Arena. Models can now be tuned, tested on fresh cases and examined inside the stream; agents and Ports share a fuller language for running a base.

### Power and compute

- Boilers, Turbines and Solar Panels supply larger builds. Reactors use learned controllers and fuel rods; an unsafe run trips, and an empty core stops producing power.
- New cables, cells, process nodes, memory, racks, pods and halls set the capacity, speed and energy cost of training and serving. Cores distribute training across data, tensor and pipeline parallel layouts.
- Forecaster III spends stored energy on bursts up to ×256 across an area. Its compute and power must keep up for the whole burst.
- An Exosuit combines learned Pilot and Survey abilities with compute and power. Flight Frames and Exosuits have articulated pods and visible exhaust.
- Mob Logs record actual mobs. An Arena uses those records and a trained model to run fights, with loot depending on what the model learned.

### Models

- Build Modern Blocks from RMS norm, SwiGLU, rotary positions and attention with QK norm and online softmax. The Bench has specs for their parts.
- A Core's Recipe controls the learning-rate schedule, precision, optimizer and packing. Runs can stop at a chosen length. A Two-Token Head replaces the last Linear Layer to predict the token after next alongside the next token.
- Packing and document masks apply to Archive Drives, Relay Dumps and Token Cells, including short text fragments. Switching to a packing mode with no readable data stops the run.
- Quantize saved weights to eight or four bits. Model Servers batch requests, cache keys and values, serve packed weights and check a smaller model's proposals.
- The Tuner supports fine-tuning, adapters, merging, preference training, judged and verified rewards, and distillation. Servers can spend more inference on checking and choosing answers.
- The Stream Scope examines activations, ablations, patches, probes and the logit lens. Trial Rigs measure contamination, forgetting, collapse and changes in what a run has learned. The contamination view shows the loaded data's benchmark and restores the chosen benchmark when that data is removed. Fresh tests, multiple seeds and shared scoreboards help compare models.
- A Refinery cleans, filters, classifies, mixes and measures text before it becomes training data.

### Running a base

- Agents and Ports share variables, lists, procedures, events, priorities, filters and nine area shapes. A debugger steps through programs, and their current work shows above them.
- Agents delivering items between stores keep the tools and supplies they already carried.
- Orders are tested as complete programs. Longer orders are read back before running unless the model has passed Programs.
- Relay masts extend servers' reach, gates carry players, and links join distant storage. The Terminal has Map, Base, Agents and Writing views.
- Storage adds larger and fluid drives, a Drive Dock, wireless and portable access, Link Drivers, processing patterns, pinned requests and plan trees.
- Demand models predict requests; Watch Posts notice changes in a machine's work. Terminals support pinyin and learned search. JEI can synchronize searches and fill recipe and filter ghosts.

### Field notes and compatibility

- Core, Bench, storage and program screens have revised layouts and tooltips in both languages. Compute views distinguish the selected strategy from the work actually running. Watch Posts show the names of the targets they follow, including renamed agents.
- The new systems and screens have field notes in English and Simplified Chinese, with annotated views, live demonstrations and step-through scenes.
- Older tokenizers and checkpoints retain their learned merges and weights when loading the expanded program alphabet. A Forge shrinks a tokenizer only when a smaller size is selected. Retest older Orders models before relying on their saved scores for the complete-program test.
- A Normalizer saved in RMS mode returns to its default mode. Use the dedicated RMS operation for that design.

## 0.5.0-alpha

Residual 0.5.0-alpha puts models to work. A first model now runs your furnaces ahead of time, and the ones after it copy your builds, find your ore and fly you. Residual also keeps its own storage now, the old world has places you can visit, and the field notes move.

### Putting models to work

- A Forecaster runs the machines and crops around it ahead of time, up to sixteen times as fast. One Embedding Table is enough for furnaces, so forecasting is now the first test.
- An Hourglass spends the time it keeps on bursts for one machine or crop, anywhere in its dimension.
- An agent copies, moves and repeats whole builds.
- A Surveyor shows you the ores in the ground, and agents dig to them as a crew.
- A Smelter makes two ingots from every ore, and three when a model you trained keeps the melt at the right heat.
- A flight frame flies you, held steady by a pilot you trained.

### Storage and power

- Drives keep any number of kinds of item, a terminal finds anything, Ports move items between the network and every chest and machine, and Assemblers craft what you ask for.
- A Port with a filter model can filter by what things are.
- Combustion Generators, Power Cables and Energy Cells run on Forge Energy.

### The old world

- The old world left its places behind: sixteen kinds of ruin, each rare. A Wayfinder leads to the nearest of any kind the archive has told you of.
- The archive grew to about 11 MB of the old world's writing.

### How it looks

- Every texture was redrawn, and the screens are calmer.
- The field notes play clips of what they describe, in game and on the website.

### Breaking changes

- Farming no longer has a test of its own. The Reacting benchmark and the Field Dataset are gone, and the Growth Dataset is now the Forecast Dataset.

## 0.4.0-alpha

Residual 0.4.0-alpha speaks Simplified Chinese.

- Every screen, tooltip, advancement and message, and all of the field notes, are translated into Simplified Chinese, and shown when the game's language is 简体中文. GuideME's own labels are translated too.
- What the models read and write stays English: the archive, orders, programs, sorting labels and agent names. The models learn a fixed English alphabet.
- Shape, wiring and sampling messages from the server now arrive in each player's own language.

## 0.3.1-alpha

Residual 0.3.1-alpha gives the mod one mark everywhere and keeps the field notes inside their world.

- The Residual Junction is the mod's mark: drawn as a block for its logo in the mod list, and shown on the creative tab, the first advancement and the guide's front page.
- The field notes speak from inside their world: they no longer mention the game or your computer, and cite no papers by author and year.
- Making a Program Card no longer posts its advancement in chat, like the other small advancements.

## 0.3.0-alpha

Residual 0.3.0-alpha has you build the machines themselves. Each module is designed on a Bench from the smallest operations there are, verified on cases worked out exactly and fabricated from a photomask, and a transformer is laid out the way it is drawn, along a residual stream. Agents run programs, models read with vocabularies they learn, and training shows itself as waves of light.

### Machines from scratch

- The Bench holds specs: what a module must compute, checked on cases worked out exactly, among them the inputs that break the obvious design. Wire add and multiply, exp and log, sums and maxima along the last axis, transposes and modules already verified until every case passes, then record the design onto a photomask.
- Specs build on each other, from matrix multiply, mean, GELU and softmax up to the linear layer, the MLP, layer norm, attention and a whole transformer block.
- The Fabricator makes machines from casings, wafers and weight crystals, a smaller design costing fewer wafers. Photomasks found in the ruins make machines that give the same answers but run one operation at a time, using more memory and training more slowly.
- A Residual Stream block carries a model's running sum from the embedding to the unembedding: outputs touching it add into it and inputs read it, so a transformer can be laid out the way it is drawn.

### Models

- A BPE Forge learns a vocabulary from the text on an Archive Drive and writes it onto a Tokenizer. A Model Core reads with the tokenizer in its new slot, checkpoints keep the vocabulary they were trained with, and losses are counted per character, so models with different vocabularies compare fairly.
- Eight benchmarks: Forecasting and Sorting join Reacting, Recall, Reversal, Induction, Orders and Reading, practised on the new Growth and Sorting Datasets. Reading passes at a loss of 1.40.
- While a model trains, a teal wave runs through its machines from the tokens to the loss and an amber one runs back with the gradient, each machine lit by what it carries. Every machine shows its output as a hologram, and attention heads show where each token looks.
- A machine's screen shows the math it computes.

### Agents

- An agent runs a program, such as `repeat (harvest, plant wheat seeds, put wheat)`, typed in its screen or clicked together from a palette of pieces. The screen checks a program as it is typed and traces every step, check and failure. The old jobs are ready-made programs.
- `sort` files things into chests labelled Food, Plants, Wood, Stone, Ores, Tools or Other. The filter model in the agent's new filter slot tells each thing's kind from its name alone.
- An order said in chat becomes a program written by the agent's model. A single step or job runs at once; anything longer is read back and runs once you click Do it.
- Program Cards carry a program from agent to agent.

### Forecasting

- A Forecaster watches the crops around it and keeps their growth on a Field Log. When a model's expected growth comes within 11% of what the field did, the Forecaster runs the field four looks ahead for every one it takes; within 25%, two.

### Field notes

- A Milestones page lists every advancement in its frame, lit once made, with what it asks and how to go about it.
- New pages cover the Bench and the Fabricator, the Residual Stream, Tokens and the Forecaster.

### Fixes

- Operations that broadcast one tensor over another take their shape from the one with more dimensions, not the one with more numbers.
- Saving writes a checkpoint whole or not at all, and only into the crystal that was tested.
- A model that fails while answering no longer leaves its agent, Reader or Terminal waiting.
- An agent that cannot reach its waymark or chest waits before trying again.

### Upgrading from 0.2.0-alpha

- Minds are now models, apprentices agents, drills datasets, abilities benchmarks and the mind slot the model slot. Apprentices, drills, salvaged minds, the models saved on checkpoints and the advancements about minds, apprentices and abilities do not carry over.
- Machines no longer have crafting recipes: the Fabricator makes them. The attention, blocks, context and positions schematics are gone.
- The Core's Code tab and the PyTorch export are gone.
- Start a new world.

[`Residual-0.3.0-alpha.mrpack`](https://github.com/metrovoc/Residual/releases/download/v0.3.0-alpha/Residual-0.3.0-alpha.mrpack) sets up Residual with Applied Energistics 2, JEI, Jade, Sodium, ModernFix and FerriteCore. Import it in Prism Launcher or the Modrinth App.

## 0.2.0-alpha

Residual 0.2.0-alpha puts the minds you build to work in a world that someone left behind: ruins of a civilization that built thinking machines, an archive of their writing to recover, and apprentices that do what their minds decide.

### The archive

- Archive houses, relay masts and buried vaults generate across the overworld. Their chests and suspicious gravel hold archive shards, schematics and salvaged minds.
- Each shard is one record of the old world's writing, with some of its text worn away. Filed into an Archive Drive, the records you recover become text to train on.
- The Reader restores lost text with a mind of your own. A lost block comes back when the mind predicts at least half of it.

### Minds

- Every mind is tested on six abilities, always on examples that practice never shows: Reacting, Recall, Reversal, Induction, Orders and Reading.
- The Field, Storage, Errand, Pattern and Orders Drills and the Archive Drive replace the lessons. The Model Core trains on up to four of them at once.
- A checkpoint holds a saved mind. Saving tests the mind on all six abilities first.
- Salvaged minds are broken models from the ruins. Rebuild one in front of a Core and repair it within its step budget.

### Apprentices

- An apprentice farms, keeps a storeroom, hauls and builds. At every real decision it asks its mind in exactly the format of that mind's test and does what the mind answers.
- Give an order in chat, such as `Pip, bring me some bread`, or in the apprentice's screen. Its mind decides what you meant.
- The Survey Rod marks six waymarks for hauling and the two ends of a row for building.

### Serving minds

- An apprentice carries a mind of up to 40,000 parameters. A Model Server serves bigger minds over relays.
- A Relay Card puts a served mind in an apprentice or a Reader. A Terminal lets you write with the served mind and carries your orders as far as the relays reach.
- A Training Accelerator kept cool by water, ice or snow lends a full thread to a Core or a Model Server, and half a thread without it.

### Upgrading from 0.1.0-alpha

- Lessons and Insights are gone. Insight items disappear from 1.0 worlds, and the machine recipes that needed them now need schematics from the ruins.
- Ruins generate only in chunks explored after the update. Start a new world, or travel to land you have not visited.

`Residual-2.0.0.mrpack` sets up Residual with Applied Energistics 2, JEI, Jade, Sodium, ModernFix and FerriteCore. Import it in Prism Launcher or the Modrinth App.

This release was first tagged v2.0.0; its files keep that version number.

## 0.1.0-alpha

The first release of Residual.

- Six lessons, from a table of letter pairs to a small GPT trained on Shakespeare, and five broken models to diagnose.
- 18 machines, signal cables, the Model Core and Training Accelerators.
- An in-game guide with a page for every machine, lesson and case.
- Export any model you build as a PyTorch module with its trained weights.

Requires Minecraft 26.1.2, NeoForge 26.1.2.124 or newer and GuideME 26.1.16-beta or newer.
`Residual-1.0.0.mrpack` sets up a modpack with Applied Energistics 2, JEI, Jade, Sodium, ModernFix and FerriteCore;
import it in Prism Launcher or the Modrinth App.

This release was first tagged v1.0.0; its files keep that version number.
