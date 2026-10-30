---
navigation:
  title: 缩放器
  icon: scale
  parent: machines/index.md
  position: 7
item_ids:
  - scale
---

# 缩放器

<BlockImage id="scale" scale="3" float="left" />

把所有数乘以一个常数。

![为什么要除以 d 的平方根](/formulas/scale.png)

## 形状

任意形状输入，输出形状相同。

## 设置

- **系数**：默认为 1/√d，其中 d 是输入最后一维的大小。也可选 0.5、0.25、0.125、2 和 4。

## 参数

无。

## 在 PyTorch 中

> `q = q / math.sqrt(d)`

## 说明

在注意力中，1/√d 放在哪里关系不大：缩放查询和缩放分数结果相同。

## 制造

可在<ItemLink id="fabricator" />中直接制造，无需光掩模。见[试验台与制造机](../bench.md)。
