---
navigation:
  title: 拼接器
  icon: concat
  parent: machines/index.md
  position: 5
item_ids:
  - concat
---

# 拼接器

<BlockImage id="concat" scale="3" float="left" />

把两个向量首尾相接：先是主输入的数，再是侧输入的数。

![拼接](/formulas/concat.png)

## 形状

输入 […×a] 和 […×b]，输出 […×(a + b)]。除最后一维外，其余大小都必须一致。

## 参数

无。

## 在 PyTorch 中

> `x = torch.cat([a, b], dim=-1)`

## 说明

拼接器后面接一个线性层，算出的结果等同于用两个线性层分别处理两个输入，再把结果相加。

## 制造

可在<ItemLink id="fabricator" />中直接制造，无需光掩模。见[试验台与制造机](../bench.md)。
