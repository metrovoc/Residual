---
navigation:
  title: QK 归一化器
  icon: qk_norm
  parent: machines/index.md
  position: 22
item_ids:
  - qk_norm
---

# QK 归一化器

<BlockImage id="qk_norm" scale="3" float="left" />

先把查询和键缩放到同一大小，再比较它们。查询从背面进，键从任意侧面进，分数从正面出，接着进<ItemLink id="causal_mask" />和<ItemLink id="softmax" />：它干的是设为转置的<ItemLink id="matmul" />加<ItemLink id="scale" />的活。

![归一化后的分数](/formulas/qknorm.png)

一个分数等于温度 t 乘 √C，再乘查询与键夹角的余弦。无论查询和键变得多大，分数都不会超过 t·√C；能抬高它的只有学得很慢的 t。

## 形状

输入两个形状相同的网格 [B×T×C] 或 [B×H×T×C]；输出 [B×T×T] 或 [B×H×T×T]。

## 参数

1 个：温度 t，从 1 开始。

## 在 PyTorch 中

> `q = F.normalize(q, dim=-1)`\
> `k = F.normalize(k, dim=-1)`\
> `scores = t * C ** 0.5 * q @ k.mT`

## 制造

<ItemLink id="fabricator" />用**QK 归一化**光掩模制造它：在<ItemLink id="bench" />上验证规格，或者在[遗迹](../ruins.md)里找到打捞的光掩模（造出的是未融合的）。见[试验台与制造机](../bench.md)。
