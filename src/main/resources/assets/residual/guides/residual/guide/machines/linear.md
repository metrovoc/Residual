---
navigation:
  title: Linear Layer
  icon: linear
  parent: machines/index.md
  position: 2
item_ids:
  - linear
---

# Linear Layer

<BlockImage id="linear" scale="3" float="left" />

Multiplies every vector by a learned matrix and adds a learned bias. It works on the last size only: every
position of every sequence goes through the same weights, separately.

![a linear layer](/formulas/linear.png)

## Shapes

[…×C_in] in, […×C_out] out, where C_out is the Outputs setting.

## Settings

- **Outputs**: V, or 4 to 256. 32 by default. End a model with a Linear Layer whose Outputs is V: those are the scores the Core wants.
- **Bias**: on by default.

## Parameters

C_in × C_out, plus C_out for the bias. They start as PyTorch's do: uniform within ±1/√C_in, with the bias at zero.

## In PyTorch

> `self.hidden = nn.Linear(C_in, C_out)`\
> `h = self.hidden(x)`

## Notes

PyTorch stores the matrix the other way round, as [C_out × C_in]. The export takes care of that.

## Recipe

Needs no schematic.

<RecipeFor id="linear" />
