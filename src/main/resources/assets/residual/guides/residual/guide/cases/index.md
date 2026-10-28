---
navigation:
  title: Cases
  icon: tensor_probe
  position: 70
---

# Cases

A case is a model that is broken the way real models break. Load it on the Lessons tab of a
<ItemLink id="model_core" /> and press **Build case**: the Core builds the broken machine next to itself, with its
weights. Machines from a case drop nothing when you break them.

Your job is the diagnosis. Each case passes once the model reaches the goal of the lesson it borrows, but a case gives
you only 1,500 training steps per run, so training longer is never the fix. Press **Reset** for fresh weights and a
fresh budget.

The pages below describe the symptoms and give hints, from gentle to blunt. Read one, go back to the model, and only
read the next if you are still stuck. None of them tells you the whole answer.

<SubPages />

## Tools for a diagnosis

- **Check** on the Train tab compiles the model. Any problems it finds head the Inspect tab, each with the position of
  the machine at fault.
- The **Inspect** tab lists every machine with its output shape, its parameter count and the size of its gradient. The
  gradient bars span six powers of ten: a bar half as long as the longest stands for a gradient a thousand times
  weaker, and red bars are weaker still.
- A machine's own screen shows its output and, after training, pictures of what it computes and its weights.
- A <ItemLink id="tensor_probe" /> traces a cable: what drives it and what reads it.
- The **Sample** tab lets you hear what the model actually learned.
