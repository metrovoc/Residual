---
navigation:
  title: Machines
  icon: linear
  position: 80
---

# Machines

Each machine performs one operation of a real model, with the same arithmetic as PyTorch. A machine takes its main
input on its back and, if it has one, its side input on any other face, and sends its result out of its front: see
[Machines and Cables](../wiring.md). Right-click a machine to change its settings and see what it computes.

Most machines need an Insight to craft. An Insight stays in the crafting grid, so one is enough for every machine it
unlocks: see [Insights](../insights.md).

## From the start

- <ItemLink id="embedding" />: token ids to vectors.
- <ItemLink id="linear" />: a learned matrix, the workhorse of every model.

## Counting

- <ItemLink id="activation" />: a nonlinear function on every number.
- <ItemLink id="shift" />: a fixed look back in time.
- <ItemLink id="concat" />: two vectors joined end to end.

## Context

- <ItemLink id="matmul" />: grids multiplied, keys compared with queries.
- <ItemLink id="scale" />: multiplied by a constant, usually 1/√d.
- <ItemLink id="causal_mask" />: the future hidden.
- <ItemLink id="softmax" />: scores turned into weights.

## Attention

- <ItemLink id="position" />: a learned vector for every position.
- <ItemLink id="add" />: addition, and with it the residual stream.
- <ItemLink id="norm" />: vectors kept at a steady size.
- <ItemLink id="attention" />: multi-head attention in one machine.
- <ItemLink id="mlp" />: widen, bend, project back.

## Position

- <ItemLink id="multiply" />: one signal opening or closing another.
- <ItemLink id="split_heads" /> and <ItemLink id="merge_heads" />: multi-head attention from parts.

## Induction

- <ItemLink id="transformer" />: a whole transformer layer.
