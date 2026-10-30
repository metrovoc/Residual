---
navigation:
  title: 线性层
  icon: linear
  parent: machines/index.md
  position: 2
item_ids:
  - linear
---

# 线性层

<BlockImage id="linear" scale="3" float="left" />

把每个向量乘以一个学到的矩阵，再加上学到的偏置。它只作用于最后一维：每个序列的每个位置都分别经过同一组权重。

![线性层](/formulas/linear.png)

## 形状

输入 […×C_in]，输出 […×C_out]，其中 C_out 即「输出数」设置。

## 设置

- **输出数**：V，或 4 到 256，默认 32。模型应以一个输出数为 V 的线性层结尾：模型核心要的正是这些分数。
- **偏置**：默认开启。

## 参数

C_in × C_out，偏置另有 C_out 个。初始化方式与 PyTorch 相同：在 ±1/√C_in 内均匀分布，偏置为零。

## 在 PyTorch 中

> `self.hidden = nn.Linear(C_in, C_out)`\
> `h = self.hidden(x)`

## 说明

PyTorch 存储矩阵的方向相反，形状为 [C_out × C_in]。

## 制造

<ItemLink id="fabricator" />用**线性层**的光掩模制造它：在<ItemLink id="bench" />上验证规格，或者在[遗迹](../ruins.md)中找到打捞来的光掩模，后者造出的是未融合的版本。见[试验台与制造机](../bench.md)。
