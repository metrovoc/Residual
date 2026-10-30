---
navigation:
  title: 注意力头
  icon: attention
  parent: machines/index.md
  position: 13
item_ids:
  - attention
---

# 注意力头

<BlockImage id="attention" scale="3" float="left" />

一台机器实现多头因果自注意力：也就是用线性层、矩阵乘法器、因果掩码和 Softmax 能搭出的那种注意力。查询、键和值都由它唯一的输入算出，内置因果掩码，带有多个头和输出投影。

![注意力](/formulas/attention.png)

![多头注意力](/formulas/heads.png)

## 形状

输入序列 [B×T×C]，输出相同形状。

## 设置

- **头数**：1、2、4 或 8，默认为 4。宽度 C 必须能被它整除。每个头处理 C/H 个数字，并把分数乘以 1/√(C/H)。

## 参数

4 × C²：矩阵 W_Q、W_K、W_V 和 W_O，不含偏置。

## 在 PyTorch 中

> `self.attn = CausalSelfAttention(C, heads=4)`\
> `x = x + self.attn(self.norm1(x))`

## 说明

训练之后，它的图像显示第一个头看向哪里：每个位置一行，它能看到的每个位置一列。

## 制造

<ItemLink id="fabricator" />用**注意力**的光掩模制造它：在<ItemLink id="bench" />上验证规格，或者在[遗迹](../ruins.md)中找到打捞来的光掩模，后者造出的是未融合的版本。见[试验台与制造机](../bench.md)。
