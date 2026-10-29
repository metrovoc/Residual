---
navigation:
  title: Softmax
  icon: softmax
  parent: machines/index.md
  position: 9
item_ids:
  - softmax
---

# Softmax

<BlockImage id="softmax" scale="3" float="left" />

Turns each row of scores into weights that are positive and sum to one, along the last size.

![softmax](/formulas/softmax.png)

## Shapes

Any shape in, the same shape out.

## Parameters

None.

## In PyTorch

> `weights = F.softmax(scores, dim=-1)`

## Notes

Don't end a model with one. The Core applies its own softmax to the scores it receives, as PyTorch's
cross-entropy loss does, and a second one squashes them so flat that the model can never be confident.

In an attention build, the Softmax's output picture is the attention pattern: one row per position.

## Recipe

Its plans are on <ItemLink id="schematic_context" />, found in [ruins](../ruins.md).

<RecipeFor id="softmax" />
