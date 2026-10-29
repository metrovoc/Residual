---
navigation:
  title: Position Table
  icon: position
  parent: machines/index.md
  position: 10
item_ids:
  - position
---

# Position Table

<BlockImage id="position" scale="3" float="left" />

A learned vector for every position in the context, from 0 to T − 1. It takes no input. Add its output to
the token vectors with a Residual Junction, and every position carries both what it is and where it is.

![token plus position](/formulas/position.png)

## Shapes

No input. [T×C] out, which a Residual Junction adds to every sequence in the batch.

## Settings

- **Width**: 4 to 256. 32 by default. Match it to the Embedding Table's width.

## Parameters

T × C, where T is the context length, 64.

## In PyTorch

> `self.pos = nn.Embedding(T, C)`\
> `x = self.embed(tokens) + self.pos(torch.arange(T))`

## Notes

Learned positions are what GPT-2 used. Most recent models instead rotate each query and key by an angle that
depends on its position (RoPE), so that attention scores depend on how far apart two tokens are.

## Recipe

Its plans are on <ItemLink id="schematic_positions" />, found in [ruins](../ruins.md).

<RecipeFor id="position" />
