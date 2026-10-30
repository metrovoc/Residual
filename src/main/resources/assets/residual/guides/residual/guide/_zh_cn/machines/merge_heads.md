---
navigation:
  title: 合头器
  icon: merge_heads
  parent: machines/index.md
  position: 18
item_ids:
  - merge_heads
---

# 合头器

<BlockImage id="merge_heads" scale="3" float="left" />

把各个头重新合并为每个位置一个向量，作用与<ItemLink id="split_heads" />相反。

## 形状

输入 [B×H×T×d]，输出 [B×T×(H·d)]。

## 参数

无。

## 在 PyTorch 中

> `def merge_heads(x):`\
> `    B, H, T, D = x.shape`\
> `    return x.transpose(1, 2).reshape(B, T, H * D)`

## 说明

之后接一个线性层，把各个头的发现混合起来：这就是输出投影 W_O。

## 制造

可在<ItemLink id="fabricator" />中直接制造，无需光掩模。见[试验台与制造机](../bench.md)。
