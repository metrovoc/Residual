---
navigation:
  title: Feed-Forward Block
  icon: mlp
  parent: machines/index.md
  position: 14
item_ids:
  - mlp
---

# Feed-Forward Block

<BlockImage id="mlp" scale="3" float="left" />

Two Linear Layers with an Activation between them: widen each vector, bend it, project it back. It works on
every position separately. Attention moves information between positions; this block processes it in place.

![the feed-forward block](/formulas/mlp.png)

## Shapes

A sequence [B×T×C] in, the same shape out.

## Settings

- **Expand**: 1, 2 or 4. 4 by default. The hidden width is Expand × C.
- **Function**: ReLU, GELU or SiLU. GELU by default.

## Parameters

2 × E × C² + E × C + C, where E is Expand.

## In PyTorch

> `self.ffn = FeedForward(C, 4 * C, gelu_tanh)`\
> `x = x + self.ffn(self.norm2(x))`

## Notes

With Expand at 4, it holds two thirds of a transformer layer's weights. Interpretability research suggests
these layers store much of what a model knows about the world (Geva et al., 2021; Meng et al., 2022).

## Making it

The <ItemLink id="fabricator" /> makes it from a photomask of **Feed-Forward Block**: verify the spec on a
<ItemLink id="bench" />, or find a salvaged mask in the [ruins](../ruins.md), which makes it unfused. See
[The Bench and the Fabricator](../bench.md).
