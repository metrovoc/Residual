---
navigation:
  title: 因果掩码
  icon: causal_mask
  parent: machines/index.md
  position: 8
item_ids:
  - causal_mask
---

# 因果掩码

<BlockImage id="causal_mask" scale="3" float="left" />

把 T × T 网格中对角线以上的每个分数设为 −∞。经过 Softmax 后，这些分数变成恰好为零的权重，因此位置 *t* 不会给 *t* 之后的任何位置分配权重。

![因果掩码](/formulas/mask.png)

<residual:Attention tokens="the old mind wrote it" points="1.4 0.1 -0.5 1.3 1.1 0.9 -1.2 -0.4 1.2 0.7" />

每一行是一个词元所看的东西。开着掩码时，它只看到自己和前面的词元，对角线以上的暗格不算数。去掉掩码，每个词元也会读到它后面的词元。

## 形状

输入分数网格 […×T×T]，输出形状相同。

## 参数

无。

## 在 PyTorch 中

> `def causal_mask(scores):`\
> `    T = scores.shape[-1]`\
> `    future = torch.triu(torch.ones(T, T, dtype=torch.bool), diagonal=1)`\
> `    return scores.masked_fill(future, float("-inf"))`

## 说明

它应放在分数和 Softmax 之间。没有它，训练来预测下一个词元的模型可以直接读到答案，损失降到几乎为零，却什么也没学到。

## 制造

可在<ItemLink id="fabricator" />中直接制造，无需光掩模。见[试验台与制造机](../bench.md)。
