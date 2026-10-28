---
navigation:
  title: Matrix Multiplier
  icon: matmul
  parent: machines/index.md
  position: 6
item_ids:
  - matmul
---

# Matrix Multiplier

<BlockImage id="matmul" scale="3" float="left" />

Multiplies two grids of numbers, the main input on the left and the side input on the right. In attention
it does two jobs: comparing every query with every key, with Transpose on, and mixing the values by the weights that
come out of the softmax.

![matrix multiplication](/formulas/matmul.png)

## Shapes

[…×n×k] and […×k×m] in, […×n×m] out. With Transpose on, the side input is […×m×k]. The sizes in front must match, or the side input can be a single [k×m] grid shared by all.

## Settings

- **Transpose**: off by default. Flips the side input's last two sizes before multiplying.

## Parameters

None: both inputs come from other machines.

## In PyTorch

> `scores = q @ k.transpose(-2, -1)`\
> `mixed = weights @ v`

## Recipe

Unlocked by <ItemLink id="insight_context" />, from [More Context](../lessons/context.md).

<RecipeFor id="matmul" />
