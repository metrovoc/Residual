---
navigation:
  title: RMS 归一化器
  icon: rms_norm
  parent: machines/index.md
  position: 19
item_ids:
  - rms_norm
---

# RMS 归一化器

<BlockImage id="rms_norm" scale="3" float="left" />

把每个向量缩放到均方根为 1，再乘上学到的增益。它事先不减什么，事后也不加什么，所以用更少的步骤和更少的权重，干了<ItemLink id="norm" />的活。

![RMS 归一化](/formulas/rmsnorm.png)

## 形状

输入 […×C]，输出相同形状。每个向量沿最后一维归一化。

## 参数

C 个。ε 为 1e-5。

## 在 PyTorch 中

> `self.norm1 = nn.RMSNorm(C)`\
> `h = self.norm1(x)`

## 说明

数的符号和大小顺序都不变，离零很远的一行仍然离零很远：变的只有它的大小。见[现代块](../modern.md)。

## 制造

<ItemLink id="fabricator" />用**RMS 归一化**光掩模制造它：在<ItemLink id="bench" />上验证规格，或者在[遗迹](../ruins.md)里找到打捞的光掩模（造出的是未融合的）。见[试验台与制造机](../bench.md)。
