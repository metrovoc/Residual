---
navigation:
  title: 分头器
  icon: split_heads
  parent: machines/index.md
  position: 17
item_ids:
  - split_heads
---

# 分头器

<BlockImage id="split_heads" scale="3" float="left" />

把每个向量切成 H 等份，并给各个头单独一维，让后面的机器同时处理所有头。

![多头注意力](/formulas/heads.png)

## 形状

输入 [B×T×C]，输出 [B×H×T×(C/H)]。

## 设置

- **头数**：1、2、4 或 8，默认 4。

## 参数

无。

## 在 PyTorch 中

> `def split_heads(x, heads):`\
> `    B, T, C = x.shape`\
> `    return x.view(B, T, heads, C // heads).transpose(1, 2)`

## 说明

有了分头器和<ItemLink id="merge_heads" />，就能用零件搭出多头注意力：用同样的方式切分查询、键和值，矩阵乘法器、缩放器、因果掩码和 Softmax 便都会并行处理每个头。

## 制造

可在<ItemLink id="fabricator" />中直接制造，无需光掩模。见[试验台与制造机](../bench.md)。
