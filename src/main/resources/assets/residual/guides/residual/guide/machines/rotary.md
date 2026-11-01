---
navigation:
  title: Rotary Positions
  icon: rotary
  parent: machines/index.md
  position: 21
item_ids:
  - rotary
---

# Rotary Positions

<BlockImage id="rotary" scale="3" float="left" />

Says where a token is by turning it. Each pair of its numbers turns by an angle that grows with its place in the
sequence, the first pair fastest. Put one on queries and one on keys, after a <ItemLink id="split_heads" />: a query and
a key then score each other by how far apart they are, wherever in the sequence they stand.

![rotary positions](/formulas/rotary.png)

## Shapes

[…×T×C] in, the same shape out. C must be even: each head's width, on a head.

## Parameters

None: unlike a <ItemLink id="position" />, it has no table to fill and no longest sequence.

## In PyTorch

> `z = torch.view_as_complex(x.reshape(*x.shape[:-1], -1, 2))`\
> `y = torch.view_as_real(z * freqs_cis[:T]).flatten(-2)`

## Making it

The <ItemLink id="fabricator" /> makes it from a photomask of **Rotary Positions**: verify the spec on a
<ItemLink id="bench" />, or find a salvaged mask in the [ruins](../ruins.md), which makes it unfused. See
[The Bench and the Fabricator](../bench.md).
