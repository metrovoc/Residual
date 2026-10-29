---
navigation:
  title: Head Merger
  icon: merge_heads
  parent: machines/index.md
  position: 18
item_ids:
  - merge_heads
---

# Head Merger

<BlockImage id="merge_heads" scale="3" float="left" />

Joins the heads back into one vector per position: the reverse of the <ItemLink id="split_heads" />.

## Shapes

[B×H×T×d] in, [B×T×(H·d)] out.

## Parameters

None.

## In PyTorch

> `def merge_heads(x):`\
> `    B, H, T, D = x.shape`\
> `    return x.transpose(1, 2).reshape(B, T, H * D)`

## Notes

Follow it with a Linear Layer to mix what the heads found: that is the output projection, W_O.

## Recipe

Its plans are on <ItemLink id="schematic_positions" />, found in [ruins](../ruins.md).

<RecipeFor id="merge_heads" />
