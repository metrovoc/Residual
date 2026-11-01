---
navigation:
  title: Z 损失调节器
  icon: z_loss
  parent: machines/index.md
  position: 23
item_ids:
  - z_loss
---

# Z 损失调节器

<BlockImage id="z_loss" scale="3" float="left" />

放在模型的最后一层和<ItemLink id="model_core" />之间。它把分数原样交出去，并在模型训练时，为偏离零的分数在损失里加一点惩罚：分数漂得太远，softmax 就不再精确，训练也开始散架。

![z 损失](/formulas/zloss.png)

## 形状

输入模型的分数 [B×T×V]，输出相同形状。

## 参数

没有。惩罚的权重是 0.0001。

## 在 PyTorch 中

> `loss = ce + 1e-4 * logits.logsumexp(-1).pow(2).mean()`

## 制造

<ItemLink id="fabricator" />用**Z 损失**光掩模制造它：在<ItemLink id="bench" />上验证规格，或者在[遗迹](../ruins.md)里找到打捞的光掩模（造出的是未融合的）。见[试验台与制造机](../bench.md)。
