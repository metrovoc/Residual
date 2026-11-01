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

<residual:Softmax scores="2 1 0 -1" labels="bread stone iron rain" />

Drag a score and every chance moves, since together they must stay one. Only the differences between the scores count:
raise them all alike and nothing changes. A low temperature stretches those differences until the largest takes
everything, and a high one flattens them, which is how a model is made to write boldly or wander.

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

## Making it

The <ItemLink id="fabricator" /> makes it from a photomask of **Softmax**: verify the spec on a
<ItemLink id="bench" />, or find a salvaged mask in the [ruins](../ruins.md), which makes it unfused. See
[The Bench and the Fabricator](../bench.md).
