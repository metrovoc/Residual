---
navigation:
  title: Two-Token Head
  icon: two_token_head
  parent: machines/index.md
  position: 26
item_ids:
  - two_token_head
---

# Two-Token Head

<BlockImage id="two_token_head" scale="3" float="left" />

Goes where a model's last <ItemLink id="linear" /> would. It gives two sets of scores from the same vectors: one for the
next token, which is all anyone sees, and one for the token after next, which only training reads. Learning both makes the
layers beneath it look further ahead: see [The Recipe](../recipe.md).

![the loss with a second head](/formulas/second_head.png)

## Shapes

A sequence [B×T×C] in, scores [B×T×V] out.

## Parameters

Two matrices C×V and two offsets of V: twice a Linear Layer's. The second set is only trained, never asked, and is still
counted in what a model weighs.

## In PyTorch

> `loss = F.cross_entropy(self.head1(x)[:, :-1], t[:, 1:]) + 0.3 * F.cross_entropy(self.head2(x)[:, :-2], t[:, 2:])`

## Making it

The <ItemLink id="fabricator" /> makes it from a photomask of **Multi-Token Prediction**: verify the spec on a
<ItemLink id="bench" />, or find a salvaged mask in the [ruins](../ruins.md), which makes it unfused. See
[The Bench and the Fabricator](../bench.md).
