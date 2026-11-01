---
navigation:
  title: Modern Attention Head
  icon: modern_attention
  parent: machines/index.md
  position: 24
item_ids:
  - modern_attention
---

# Modern Attention Head

<BlockImage id="modern_attention" scale="3" float="left" />

Multi-head causal self-attention as the late designs do it: <ItemLink id="rotary" /> on queries and keys, a
<ItemLink id="qk_norm" /> between them, in one machine. A machine made from a verified design takes each row's scores
a tile at a time with an online softmax and never holds them: see [The Modern Block](../modern.md).

## Shapes

A sequence [B×T×C] in, the same shape out.

## Settings

- **Heads**: 1, 2, 4 or 8. 4 by default. C must divide evenly by it, and with Rotary on, each head's width must be even.
- **Rotary**: on by default. Off, it tells nothing about places: it needs a <ItemLink id="position" /> before it.
- **QK norm**: on by default. Off, its scores are q·kᵀ/√d, with no bound.

## Parameters

4 × C², and 1 for the temperature with QK norm on.

## In PyTorch

> `q, k = rotate(q), rotate(k)`\
> `q, k = F.normalize(q, dim=-1) * t * dh ** 0.5, F.normalize(k, dim=-1)`\
> `y = F.scaled_dot_product_attention(q, k, v, is_causal=True)`

## Notes

Its picture shows the attention pattern of its first head, like the <ItemLink id="attention" />'s. Each picture comes
from a separate pass the plain way, which holds the scores; the steps of training never do.

## Making it

The <ItemLink id="fabricator" /> makes it from a photomask of **Modern Attention**: verify the spec on a
<ItemLink id="bench" />, or find a salvaged mask in the [ruins](../ruins.md), which makes it unfused. See
[The Bench and the Fabricator](../bench.md).
