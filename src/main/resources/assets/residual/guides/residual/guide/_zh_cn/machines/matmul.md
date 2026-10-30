---
navigation:
  title: 矩阵乘法器
  icon: matmul
  parent: machines/index.md
  position: 6
item_ids:
  - matmul
---

# 矩阵乘法器

<BlockImage id="matmul" scale="3" float="left" />

把两个数字网格相乘，主输入在左，侧输入在右。在注意力中它有两项工作：开启转置，把每个查询与每个键比较；以及按 softmax 给出的权重混合各个值。

![矩阵乘法](/formulas/matmul.png)

## 形状

输入 […×n×k] 和 […×k×m]，输出 […×n×m]。开启转置时，侧输入为 […×m×k]。前面各维的大小必须一致，或者侧输入也可以是所有部分共用的单个 [k×m] 网格。

## 设置

- **转置**：默认关闭。相乘前交换侧输入的最后两维。

## 参数

无：两个输入都来自其他机器。

## 在 PyTorch 中

> `scores = q @ k.transpose(-2, -1)`\
> `mixed = weights @ v`

## 制造

<ItemLink id="fabricator" />用**矩阵乘法**的光掩模制造它：在<ItemLink id="bench" />上验证规格，或者在[遗迹](../ruins.md)中找到打捞来的光掩模，后者造出的是未融合的版本。见[试验台与制造机](../bench.md)。
