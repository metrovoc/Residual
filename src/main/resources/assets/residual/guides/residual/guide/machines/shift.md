---
navigation:
  title: Delay Line
  icon: shift
  parent: machines/index.md
  position: 4
item_ids:
  - shift
---

# Delay Line

<BlockImage id="shift" scale="3" float="left" />

Shifts a sequence later in time: position *t* receives what position *t* − *k* had, and the first *k*
positions receive zeros. It lets a position see a fixed number of steps back.

![a delay](/formulas/shift.png)

## Shapes

A sequence […×T×C] in, the same shape out.

## Settings

- **Steps**: 1 to 4. 1 by default.

## Parameters

None.

## In PyTorch

> `def delay(x, steps):`\
> `    return F.pad(x, (0, 0, steps, 0))[..., : x.shape[-2], :]`\
> ` `\
> `prev = delay(e, 1)`

## Notes

Attention can look back any distance, so transformers rarely need a fixed delay. Some architectures keep one
anyway: RWKV mixes every token with the one before it, a learned blend called token shift.
[Induction Heads](../lessons/induction.md) forbids Delay Lines.

## Recipe

Unlocked by <ItemLink id="insight_counting" />, from [Counting Pairs](../lessons/counting.md).

<RecipeFor id="shift" />
