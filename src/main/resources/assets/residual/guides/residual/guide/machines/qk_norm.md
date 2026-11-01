---
navigation:
  title: QK Normalizer
  icon: qk_norm
  parent: machines/index.md
  position: 22
item_ids:
  - qk_norm
---

# QK Normalizer

<BlockImage id="qk_norm" scale="3" float="left" />

Compares queries with keys after bringing both to one size. Queries go in at the back, keys at any side, and scores come
out of the front, to go on to a <ItemLink id="causal_mask" /> and a <ItemLink id="softmax" />: it does the work of a
<ItemLink id="matmul" /> set to Transpose and a <ItemLink id="scale" />.

![normalized scores](/formulas/qknorm.png)

A score is the temperature t times √C times the cosine of the angle between query and key. It can't pass t·√C,
however large queries and keys grow, and only t, which learns slowly, can raise it.

## Shapes

Two grids of one shape, [B×T×C] or [B×H×T×C], in; [B×T×T] or [B×H×T×T] out.

## Parameters

1: the temperature t, starting at 1.

## In PyTorch

> `q = F.normalize(q, dim=-1)`\
> `k = F.normalize(k, dim=-1)`\
> `scores = t * C ** 0.5 * q @ k.mT`

## Making it

The <ItemLink id="fabricator" /> makes it from a photomask of **QK Norm**: verify the spec on a
<ItemLink id="bench" />, or find a salvaged mask in the [ruins](../ruins.md), which makes it unfused. See
[The Bench and the Fabricator](../bench.md).
