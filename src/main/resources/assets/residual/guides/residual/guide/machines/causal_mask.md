---
navigation:
  title: Causal Mask
  icon: causal_mask
  parent: machines/index.md
  position: 8
item_ids:
  - causal_mask
---

# Causal Mask

<BlockImage id="causal_mask" scale="3" float="left" />

Sets every score above the diagonal of a T × T grid to −∞. After a Softmax those become weights of exactly
zero, so position *t* gives no weight to anything after *t*.

![the causal mask](/formulas/mask.png)

## Shapes

A grid of scores […×T×T] in, the same shape out.

## Parameters

None.

## In PyTorch

> `def causal_mask(scores):`\
> `    T = scores.shape[-1]`\
> `    future = torch.triu(torch.ones(T, T, dtype=torch.bool), diagonal=1)`\
> `    return scores.masked_fill(future, float("-inf"))`

## Notes

It belongs between the scores and the Softmax. Without it, a model trained to predict the next token can
simply read it, and its loss falls to almost nothing while it learns nothing.

## Making it

The <ItemLink id="fabricator" /> makes it with no mask. See [The Bench and the Fabricator](../bench.md).
