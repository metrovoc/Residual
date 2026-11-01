---
navigation:
  title: Attention Head
  icon: attention
  parent: machines/index.md
  position: 13
item_ids:
  - attention
---

# Attention Head

<BlockImage id="attention" scale="3" float="left" />

Multi-head causal self-attention in one machine: the attention you can build from Linear Layers, a Matrix Multiplier,
a Causal Mask and a Softmax, with queries, keys and values all computed from its one input, a causal mask built in,
several heads and an output projection.

![attention](/formulas/attention.png)

![multi-head attention](/formulas/heads.png)

<residual:Attention tokens="the old mind wrote it" points="1.4 0.1 -0.5 1.3 1.1 0.9 -1.2 -0.4 1.2 0.7" />

One head with nothing learned yet: each token is a point, and that point is its own query, key and value. A token gives
weight to what it can see by how far their vectors run along its own, and reads the weighted mean of them, the hollow
square. Drag the points to see the weights follow, click a row to read another token, and take the mask away to see tokens
read ahead. Training is what gives each token a query and a key of their own, through W_Q and W_K.

## Shapes

A sequence [B×T×C] in, the same shape out.

## Settings

- **Heads**: 1, 2, 4 or 8. 4 by default. The width C must divide evenly by it. Each head works on C/H of the numbers and scales its scores by 1/√(C/H).

## Parameters

4 × C²: the matrices W_Q, W_K, W_V and W_O, without biases.

## In PyTorch

> `self.attn = CausalSelfAttention(C, heads=4)`\
> `x = x + self.attn(self.norm1(x))`

## Notes

After training, its picture shows where its first head looks: one row per position, one column for every
position it can see.

## Making it

The <ItemLink id="fabricator" /> makes it from a photomask of **Attention**: verify the spec on a
<ItemLink id="bench" />, or find a salvaged mask in the [ruins](../ruins.md), which makes it unfused. See
[The Bench and the Fabricator](../bench.md).
