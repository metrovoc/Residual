---
navigation:
  title: Transformer Block
  icon: transformer
  parent: machines/index.md
  position: 15
item_ids:
  - transformer
---

# Transformer Block

<BlockImage id="transformer" scale="3" float="left" />

One whole transformer layer in one machine: normalize, attend, add back; normalize, feed forward, add
back. Stack them to make a GPT.

![the transformer layer](/formulas/block.png)

## Shapes

A sequence [B×T×C] in, the same shape out.

## Settings

- **Heads**: 1, 2, 4 or 8. 4 by default.
- **Expand**: 1, 2 or 4. 4 by default.
- **Function**: ReLU, GELU or SiLU. GELU by default.

## Parameters

12 × C² + 9 × C with Expand at 4: about 50,000 at width 64.

## In PyTorch

> `self.block1 = Block(C, heads=4, hidden=4 * C, act=gelu_tanh)`\
> `x = self.block1(x)`

## Notes

Its picture shows the attention pattern of its first head, like the Attention Head's.

## Making it

The <ItemLink id="fabricator" /> makes it from a photomask of **Transformer Block**: verify the spec on a
<ItemLink id="bench" />, or find a salvaged mask in the [ruins](../ruins.md), which makes it unfused. See
[The Bench and the Fabricator](../bench.md).
