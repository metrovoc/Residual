---
navigation:
  title: Scaler
  icon: scale
  parent: machines/index.md
  position: 7
item_ids:
  - scale
---

# Scaler

<BlockImage id="scale" scale="3" float="left" />

Multiplies everything by a constant.

![why divide by the square root of d](/formulas/scale.png)

## Shapes

Any shape in, the same shape out.

## Settings

- **Factor**: 1/√d by default, where d is the last size of the input. Also 0.5, 0.25, 0.125, 2 and 4.

## Parameters

None.

## In PyTorch

> `q = q / math.sqrt(d)`

## Notes

In attention it does not matter much where the 1/√d goes: scaling the queries or scaling the scores gives the
same result.

## Making it

The <ItemLink id="fabricator" /> makes it with no mask. See [The Bench and the Fabricator](../bench.md).
