---
navigation:
  title: The Modern Block
  icon: modern_block
  position: 65
---

# The Modern Block

The <ItemLink id="transformer" /> is how the first models that talked seem to have been built. The old world's last ones
kept its shape, a running sum that every layer reads and adds to, and changed what is inside it, a part at a time. Each
part is a spec on the <ItemLink id="bench" /> and a machine of its own, and each can be tried and taken out again.

## The parts

**RMS norm.** A <ItemLink id="norm" /> takes each vector's mean off, rescales it and adds an offset back. An
<ItemLink id="rms_norm" /> only rescales, with no mean and no offset, and a layer needs nothing more.

**A gate.** A <ItemLink id="mlp" /> widens each token, bends it and projects it back. A <ItemLink id="swiglu" /> makes two
wide copies and lets one, through SiLU, open or shut each number of the other. Widened to 8/3 rather than 4 times, it costs
the same weights.

**Positions by turning.** A <ItemLink id="position" /> is a table of learned vectors, one for every place, added to the
tokens, and it has an end. <ItemLink id="rotary" /> has no weights and no end: it turns each pair of numbers in a query
or a key by an angle that grows with the place, each pair at its own rate.

![rotary positions](/formulas/rotary.png)

Turn a query and a key that way and the score between them depends only on how far apart they are, wherever they stand.

**QK norm.** Left alone, queries and keys can grow during training until one score swamps all the others, and a softmax
that sharp gives gradients that throw the weights about. A <ItemLink id="qk_norm" /> compares them at one size, with one
learned temperature, so no score can pass t·√C.

![normalized scores](/formulas/qknorm.png)

**The online softmax.** An <ItemLink id="attention" /> writes out every score of every token against every other, T×T of
them for each head, in several copies with their gradients. A <ItemLink id="modern_attention" /> made from a verified design
never holds them. It works each token's scores out a tile at a time, keeping only the largest so far and the running sum:

![the online softmax](/formulas/online.png)

The answer is the same to within rounding, gradients included. At a batch of 32, four heads of width 8 and 64 tokens, the
plain way holds 4.3 million numbers and the tiles 82 thousand. The backward pass works the scores out again from the
queries and keys it kept.

**Z-loss.** A <ItemLink id="z_loss" /> sits before the <ItemLink id="model_core" /> and adds a small penalty for scores far
from zero, where a softmax stops being accurate.

![z-loss](/formulas/zloss.png)

## Building one

A <ItemLink id="modern_block" /> holds a whole modern layer, with the online softmax inside. A line of them needs nothing
for positions: an <ItemLink id="embedding" />, the blocks, an <ItemLink id="rms_norm" /> and a <ItemLink id="linear" /> set
to V. Its screen turns **Rotary** and **QK norm** off, to see what each was doing. With Rotary off it needs a Position
Table before it, as a Transformer Block does.
