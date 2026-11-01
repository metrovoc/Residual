---
navigation:
  title: Normalizer
  icon: norm
  parent: machines/index.md
  position: 12
item_ids:
  - norm
---

# Normalizer

<BlockImage id="norm" scale="3" float="left" />

Rescales each vector to a mean of 0 and a variance of 1, then applies a learned gain and a learned offset.

![layer normalization](/formulas/layernorm.png)

## Shapes

[…×C] in, the same shape out. Each vector is normalized over its last size.

## Parameters

2 × C. ε is 1e-5.

## In PyTorch

> `self.norm1 = nn.LayerNorm(C)`\
> `h = self.norm1(x)`

## Notes

Put one at the start of each branch, before attention or the feed-forward block, and one before the final
Linear Layer. This pre-norm layout is GPT-2's, and nearly every model since uses it. The
<ItemLink id="rms_norm" /> does the same job with less.

## Making it

The <ItemLink id="fabricator" /> makes it from a photomask of **Layer Norm**: verify the spec on a
<ItemLink id="bench" />, or find a salvaged mask in the [ruins](../ruins.md), which makes it unfused. See
[The Bench and the Fabricator](../bench.md).
