---
navigation:
  title: Gated Feed-Forward Block
  icon: swiglu
  parent: machines/index.md
  position: 20
item_ids:
  - swiglu
---

# Gated Feed-Forward Block

<BlockImage id="swiglu" scale="3" float="left" />

A feed-forward block whose hidden numbers have a gate each: one branch, through SiLU, decides how much of the other
gets through.

![the gated feed-forward block](/formulas/swiglu.png)

## Shapes

A sequence [B×T×C] in, the same shape out. It widens to 8/3 of C inside.

## Parameters

3 × C × 8/3 C = 8 × C², no biases: what a <ItemLink id="mlp" /> at Expand 4 has, less its biases.

## In PyTorch

> `self.w1, self.w2 = nn.Linear(C, H, False), nn.Linear(C, H, False)`\
> `self.w3 = nn.Linear(H, C, False)`\
> `y = self.w3(F.silu(self.w1(x)) * self.w2(x))`

## Making it

The <ItemLink id="fabricator" /> makes it from a photomask of **Gated Feed-Forward**: verify the spec on a
<ItemLink id="bench" />, or find a salvaged mask in the [ruins](../ruins.md), which makes it unfused. See
[The Bench and the Fabricator](../bench.md).
