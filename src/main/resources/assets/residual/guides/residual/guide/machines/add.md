---
navigation:
  title: Residual Junction
  icon: add
  parent: machines/index.md
  position: 11
item_ids:
  - add
---

# Residual Junction

<BlockImage id="add" scale="3" float="left" />

Adds its two inputs, number by number. It has two jobs. It combines token and position vectors, and it makes
residual connections: adding a branch's output back onto the stream that went into it, so the branch refines the
stream instead of replacing it.

![a residual connection](/formulas/residual.png)

![a residual connection's gradient](/formulas/residual_grad.png)

## Shapes

Two equal shapes, or a smaller one that matches the end of the larger and repeats across it: [B×T×C] and [T×C] make [B×T×C].

## Parameters

None.

## In PyTorch

> `x = x + self.attn(self.norm1(x))`

## Notes

The identity term in its gradient gives every layer a direct path back to the loss. That is why stacks of
residual layers keep learning when plain stacks stall.

## Recipe

Its plans are on <ItemLink id="schematic_attention" />, found in [ruins](../ruins.md).

<RecipeFor id="add" />
