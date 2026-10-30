---
navigation:
  title: Activation
  icon: activation
  parent: machines/index.md
  position: 3
item_ids:
  - activation
---

# Activation

<BlockImage id="activation" scale="3" float="left" />

Bends every number through the same nonlinear function. Without one, two Linear Layers in a row multiply
out into a single Linear Layer, however many you stack: the activation is what lets depth add anything.

![ReLU, GELU, SiLU, tanh and sigmoid](/formulas/activations.png)

## Shapes

Any shape in, the same shape out.

## Settings

- **Function**: ReLU by default.
- **ReLU** keeps positive numbers and zeroes the rest. Its slope is 0 or 1, so it never shrinks a gradient that passes.
- **GELU** is a smooth ReLU, used by GPT-2 and BERT.
- **SiLU** is z·σ(z), used inside the feed-forward layers of LLaMA and its relatives.
- **tanh** squashes into (−1, 1), as in the first neural language models.
- **sigmoid** squashes into (0, 1). Its slope is at most a quarter, so every sigmoid layer shrinks the gradient that passes back through it.

## Parameters

None.

## In PyTorch

> `F.relu(x)`\
> `F.gelu(x, approximate="tanh")`\
> `F.silu(x)`\
> `torch.tanh(x)`\
> `torch.sigmoid(x)`

## Making it

The <ItemLink id="fabricator" /> makes it from a photomask of **GELU**: verify the spec on a
<ItemLink id="bench" />, or find a salvaged mask in the [ruins](../ruins.md), which makes it unfused. See
[The Bench and the Fabricator](../bench.md).
