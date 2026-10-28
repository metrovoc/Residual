---
navigation:
  title: Head Splitter
  icon: split_heads
  parent: machines/index.md
  position: 17
item_ids:
  - split_heads
---

# Head Splitter

<BlockImage id="split_heads" scale="3" float="left" />

Cuts each vector into H equal parts and gives the heads a dimension of their own, so the machines after it
work on every head at once.

![multi-head attention](/formulas/heads.png)

## Shapes

[B×T×C] in, [B×H×T×(C/H)] out.

## Settings

- **Heads**: 1, 2, 4 or 8. 4 by default.

## Parameters

None.

## In PyTorch

> `def split_heads(x, heads):`\
> `    B, T, C = x.shape`\
> `    return x.view(B, T, heads, C // heads).transpose(1, 2)`

## Notes

With a Head Splitter and a <ItemLink id="merge_heads" /> you can build multi-head attention from parts: split
the queries, keys and values the same way, and the Matrix Multipliers, Scaler, Causal Mask and Softmax all work on
every head in parallel.

## Recipe

Unlocked by <ItemLink id="insight_position" />, from [Knowing Where](../lessons/reverse.md).

<RecipeFor id="split_heads" />
