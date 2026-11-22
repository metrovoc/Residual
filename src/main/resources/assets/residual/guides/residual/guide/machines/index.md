---
navigation:
  title: Machines
  icon: linear
  position: 120
---

# Machines

Each machine performs one operation of a model, with the same arithmetic as PyTorch. A machine takes its main
input on its back and, if it has one, its side input on any other face, and sends its result out of its front: see
[Machines and Cables](../wiring.md). Right-click a machine to change its settings and see what it computes.

<residual:Clip name="operator" alt="A machine's screen while it trains: its output and weights as they change" />

Every machine is made at a crafting table, and its page has the recipe.

[A machine's screen, part by part](../screens/operator.md).

## The parts

- <ItemLink id="embedding" />: token ids to vectors.
- <ItemLink id="position" />: a learned vector for every position.
- <ItemLink id="add" />: addition, and with it the residual stream.
- <ItemLink id="multiply" />: one signal opening or closing another.
- <ItemLink id="scale" />: multiplied by a constant, usually 1/√d.
- <ItemLink id="causal_mask" />: the future hidden.
- <ItemLink id="split_heads" /> and <ItemLink id="merge_heads" />: multi-head attention from parts.
- <ItemLink id="shift" />: a fixed look back in time.
- <ItemLink id="concat" />: two vectors joined end to end.

## Built from them

- <ItemLink id="matmul" />: grids multiplied, keys compared with queries.
- <ItemLink id="softmax" />: scores turned into weights.
- <ItemLink id="linear" />: a learned matrix, the workhorse of every model.
- <ItemLink id="activation" />: a nonlinear function on every number.
- <ItemLink id="norm" />: vectors kept at a steady size.
- <ItemLink id="attention" />: multi-head attention in one machine.
- <ItemLink id="mlp" />: widen, bend, project back.
- <ItemLink id="transformer" />: a whole transformer layer.
- <ItemLink id="two_token_head" />: scores for the next token and the one after it.

## The modern block

See [The Modern Block](../modern.md).

- <ItemLink id="rms_norm" />: vectors kept at a steady size, with less work.
- <ItemLink id="swiglu" />: a feed-forward block whose numbers have gates.
- <ItemLink id="rotary" />: a token's place, told by turning it.
- <ItemLink id="qk_norm" />: queries and keys compared at one size.
- <ItemLink id="z_loss" />: a small penalty that holds a model's scores near zero.
- <ItemLink id="modern_attention" />: all of that in one attention machine.
- <ItemLink id="modern_block" />: a whole modern layer.
