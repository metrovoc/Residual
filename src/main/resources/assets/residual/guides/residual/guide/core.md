---
navigation:
  title: The Model Core
  icon: model_core
  position: 50
item_ids:
  - model_core
  - accelerator
---

# The Model Core

<BlockImage id="model_core" scale="3" float="left" />

The Model Core feeds text into your machines and judges what comes back. Its front sends out tokens; every other face
takes in the model's scores. It holds the lesson you are working on, trains the model, and keeps the learning curve.
Its trained weights stay in the machines themselves, so a model keeps what it learned when you leave and come back.

<br clear="all" />

<RecipeFor id="model_core" />

## Lessons

The whole course, in order. Each entry unlocks once everything above it is done; in creative mode, everything is
open. Select one to read its brief, then **Load** it. A case also has **Build case**, which builds its broken model in
front of the Core: it needs a clear space there, and its machines drop nothing when broken. The **?** button opens
the entry's page in this guide.

## Train

The learning curve, the current numbers and the training settings. See [Training](training.md) for what each one
means. **Params** counts the model's weights; **Grad norm** is the size of the last gradient; **Threads** is how many
of your computer's threads the Core trains with.

## Sample

Runs the trained model. For text, type the start of something and **Generate**: the model continues it one token at a
time, drawing each token from its predicted probabilities. **Temp** (temperature) sharpens or flattens those
probabilities before drawing:

![temperature](/formulas/temperature.png)

Low temperatures repeat the model's favourite continuations; high ones take more risks. For puzzles, **New puzzle**
shows the model answering a fresh one. The bars show how likely the model found each candidate for the first token it
wrote.

## Inspect

Every machine of the model in the order data reaches it, with its output shape, its number of weights and the size of
its last gradient. Problems found by Check are listed here too. A bar that shrinks to nothing as you go down the list
is a gradient that never arrives: see [Too Deep to Learn](cases/case_depth.md).

## Code

The model as a PyTorch module, generated from your machines. **Copy** puts it on the clipboard; **Export model +
weights** saves it, with the trained weights and a script that samples from them, next to your game. See
[Beyond Residual](beyond.md).

## More threads

<ItemImage id="accelerator" float="left" />

Each <ItemLink id="accelerator" /> touching the Core, directly or through other Accelerators, lends it one more thread,
up to the limit the server allows.

<br clear="all" />
