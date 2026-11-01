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

Every machine comes out of the [Fabricator](../bench.md). The simple ones need no design; the rest need a photomask
of the spec that describes them, verified on a Bench or salvaged from the ruins.

[A machine's screen, part by part](../screens/operator.md).

## No design needed

- <ItemLink id="embedding" />: token ids to vectors.
- <ItemLink id="position" />: a learned vector for every position.
- <ItemLink id="add" />: addition, and with it the residual stream.
- <ItemLink id="multiply" />: one signal opening or closing another.
- <ItemLink id="scale" />: multiplied by a constant, usually 1/√d.
- <ItemLink id="causal_mask" />: the future hidden.
- <ItemLink id="split_heads" /> and <ItemLink id="merge_heads" />: multi-head attention from parts.
- <ItemLink id="shift" />: a fixed look back in time.
- <ItemLink id="concat" />: two vectors joined end to end.

## From a design

- <ItemLink id="matmul" />, from Matrix Multiply: grids multiplied, keys compared with queries.
- <ItemLink id="softmax" />, from Softmax: scores turned into weights.
- <ItemLink id="linear" />, from Linear Layer: a learned matrix, the workhorse of every model.
- <ItemLink id="activation" />, from GELU: a nonlinear function on every number.
- <ItemLink id="norm" />, from Layer Norm: vectors kept at a steady size.
- <ItemLink id="attention" />, from Attention: multi-head attention in one machine.
- <ItemLink id="mlp" />, from Feed-Forward Block: widen, bend, project back.
- <ItemLink id="transformer" />, from Transformer Block: a whole transformer layer.
- <ItemLink id="two_token_head" />, from Multi-Token Prediction: scores for the next token and the one after it.

## The modern block

Every one of these from a design too: see [The Modern Block](../modern.md).

- <ItemLink id="rms_norm" />, from RMS Norm: vectors kept at a steady size, with less work.
- <ItemLink id="swiglu" />, from Gated Feed-Forward: a feed-forward block whose numbers have gates.
- <ItemLink id="rotary" />, from Rotary Positions: a token's place, told by turning it.
- <ItemLink id="qk_norm" />, from QK Norm: queries and keys compared at one size.
- <ItemLink id="z_loss" />, from Z-Loss: a small penalty that holds a model's scores near zero.
- <ItemLink id="modern_attention" />, from Modern Attention: all of that in one attention machine.
- <ItemLink id="modern_block" />, from Modern Block: a whole modern layer.
