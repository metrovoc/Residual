---
navigation:
  title: Gate
  icon: multiply
  parent: machines/index.md
  position: 16
item_ids:
  - multiply
---

# Gate

<BlockImage id="multiply" scale="3" float="left" />

Multiplies its two inputs number by number, so one signal can scale, open or close another.

![elementwise multiplication](/formulas/multiply.png)

## Shapes

The same shapes as the Residual Junction takes.

## Parameters

None.

## In PyTorch

> `y = F.silu(self.gate(x)) * self.up(x)`

## Notes

Gates sit inside the feed-forward layers of most recent language models. SwiGLU multiplies SiLU(xW₁) by a
second projection xW₃ before projecting back down (Shazeer, 2020). To build one: two Linear Layers reading the same
input, an Activation set to SiLU after one of them, a Gate joining them, and a Linear Layer back to the original
width. LSTMs, the recurrent networks that came before transformers, controlled their memory with gates like this.

## Recipe

Unlocked by <ItemLink id="insight_position" />, from [Knowing Where](../lessons/reverse.md).

<RecipeFor id="multiply" />
