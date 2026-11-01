---
navigation:
  title: Modern Block
  icon: modern_block
  parent: machines/index.md
  position: 25
item_ids:
  - modern_block
---

# Modern Block

<BlockImage id="modern_block" scale="3" float="left" />

One whole modern layer in one machine: RMS norm, modern attention, add back; RMS norm, gated feed-forward, add back.
With Rotary on, a stack of them needs no <ItemLink id="position" />: see [The Modern Block](../modern.md).

![the modern layer](/formulas/modern.png)

## Shapes

A sequence [B×T×C] in, the same shape out.

## Settings

- **Heads**: 1, 2, 4 or 8. 4 by default. C must divide evenly by it, and with Rotary on, each head's width must be even.
- **Rotary**: on by default.
- **QK norm**: on by default.

## Parameters

12 × C² + 2 × C + 1: about 49,000 at width 64, as a <ItemLink id="transformer" /> has.

## In PyTorch

> `x = x + self.attn(self.rms1(x))`\
> `x = x + self.ffn(self.rms2(x))`

## Notes

Its picture shows the attention pattern of its first head.

## Making it

The <ItemLink id="fabricator" /> makes it from a photomask of **Modern Block**: verify the spec on a
<ItemLink id="bench" />, or find a salvaged mask in the [ruins](../ruins.md), which makes it unfused. See
[The Bench and the Fabricator](../bench.md).
