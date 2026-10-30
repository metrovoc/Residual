---
navigation:
  title: 前馈块
  icon: mlp
  parent: machines/index.md
  position: 14
item_ids:
  - mlp
---

# 前馈块

<BlockImage id="mlp" scale="3" float="left" />

两个线性层，中间夹一个激活层：把每个向量加宽、弯折、再投影回去。它在每个位置上分别工作。注意力在位置之间搬运信息；这个块就地处理信息。

![前馈块](/formulas/mlp.png)

## 形状

输入序列 [B×T×C]，输出相同形状。

## 设置

- **扩展**：1、2 或 4，默认为 4。隐藏层宽度为扩展倍数 × C。
- **函数**：ReLU、GELU 或 SiLU，默认为 GELU。

## 参数

2 × E × C² + E × C + C，其中 E 为扩展倍数。

## 在 PyTorch 中

> `self.ffn = FeedForward(C, 4 * C, gelu_tanh)`\
> `x = x + self.ffn(self.norm2(x))`

## 说明

扩展倍数为 4 时，它占一个 Transformer 层三分之二的权重。可解释性研究表明，模型关于世界的知识有很大一部分存在这些层里。

## 制造

<ItemLink id="fabricator" />用**前馈块**的光掩模制造它：在<ItemLink id="bench" />上验证规格，或者在[遗迹](../ruins.md)中找到打捞来的光掩模，后者造出的是未融合的版本。见[试验台与制造机](../bench.md)。
