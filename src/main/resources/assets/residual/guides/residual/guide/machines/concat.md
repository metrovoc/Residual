---
navigation:
  title: Concatenator
  icon: concat
  parent: machines/index.md
  position: 5
item_ids:
  - concat
---

# Concatenator

<BlockImage id="concat" scale="3" float="left" />

Joins two vectors end to end: the main input's numbers first, then the side input's.

![concatenation](/formulas/concat.png)

## Shapes

[…×a] and […×b] in, […×(a + b)] out. Every size but the last must match.

## Parameters

None.

## In PyTorch

> `x = torch.cat([a, b], dim=-1)`

## Notes

A Linear Layer after a Concatenator computes the same thing as two Linear Layers, one on each input, added
together.

## Making it

The <ItemLink id="fabricator" /> makes it with no mask. See [The Bench and the Fabricator](../bench.md).
