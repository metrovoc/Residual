---
navigation:
  title: 残差汇合点
  icon: add
  parent: machines/index.md
  position: 11
item_ids:
  - add
---

# 残差汇合点

<BlockImage id="add" scale="3" float="left" />

把两个输入逐个数相加。它有两项用途：把词元向量和位置向量合在一起；构成残差连接，即把一个分支的输出加回进入该分支的流上，让分支修正这条流，而不是取代它。

![残差连接](/formulas/residual.png)

![残差连接的梯度](/formulas/residual_grad.png)

## 形状

两个相同的形状，或者一个较小的形状，与较大形状的末尾一致，并沿其余部分重复：[B×T×C] 和 [T×C] 得到 [B×T×C]。

## 参数

无。

## 在 PyTorch 中

> `x = x + self.attn(self.norm1(x))`

## 说明

它的梯度里有一个恒等项，让每一层都有一条直通损失的路径。正因如此，残差层堆叠起来仍能继续学习，而普通的堆叠会停滞。

## 制造

可在<ItemLink id="fabricator" />中直接制造，无需光掩模。见[试验台与制造机](../bench.md)。
