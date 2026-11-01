---
navigation:
  title: RMS Normalizer
  icon: rms_norm
  parent: machines/index.md
  position: 19
item_ids:
  - rms_norm
---

# RMS Normalizer

<BlockImage id="rms_norm" scale="3" float="left" />

Rescales each vector to a root mean square of 1, then applies a learned gain. It takes nothing off first and adds
nothing after, so it does the job of a <ItemLink id="norm" /> with fewer steps and fewer weights.

![RMS normalization](/formulas/rmsnorm.png)

## Shapes

[…×C] in, the same shape out. Each vector is normalized over its last size.

## Parameters

C. ε is 1e-5.

## In PyTorch

> `self.norm1 = nn.RMSNorm(C)`\
> `h = self.norm1(x)`

## Notes

The numbers keep their sign and their order, and a row far from zero stays far from zero: only its size changes. See
[The Modern Block](../modern.md).

## Making it

The <ItemLink id="fabricator" /> makes it from a photomask of **RMS Norm**: verify the spec on a
<ItemLink id="bench" />, or find a salvaged mask in the [ruins](../ruins.md), which makes it unfused. See
[The Bench and the Fabricator](../bench.md).
