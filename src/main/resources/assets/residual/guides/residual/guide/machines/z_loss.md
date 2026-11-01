---
navigation:
  title: Z-Loss Regulator
  icon: z_loss
  parent: machines/index.md
  position: 23
item_ids:
  - z_loss
---

# Z-Loss Regulator

<BlockImage id="z_loss" scale="3" float="left" />

Goes between a model's last layer and the <ItemLink id="model_core" />. It hands the scores on unchanged, and while the
model trains it adds a small penalty to the loss for scores that have drifted from zero: scores that drift far are where
a softmax stops being accurate, and where a run begins to come apart.

![z-loss](/formulas/zloss.png)

## Shapes

The model's scores [B×T×V] in, the same out.

## Parameters

None. Its penalty weighs 0.0001.

## In PyTorch

> `loss = ce + 1e-4 * logits.logsumexp(-1).pow(2).mean()`

## Making it

The <ItemLink id="fabricator" /> makes it from a photomask of **Z-Loss**: verify the spec on a
<ItemLink id="bench" />, or find a salvaged mask in the [ruins](../ruins.md), which makes it unfused. See
[The Bench and the Fabricator](../bench.md).
