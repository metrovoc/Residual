---
navigation:
  title: Softmax
  icon: softmax
  parent: machines/index.md
  position: 9
item_ids:
  - softmax
---

# Softmax

<BlockImage id="softmax" scale="3" float="left" />

沿最后一维，把每一行分数变成全为正数、总和为一的权重。

![softmax](/formulas/softmax.png)

## 形状

任意形状输入，输出形状相同。

## 参数

无。

## 在 PyTorch 中

> `weights = F.softmax(scores, dim=-1)`

## 说明

不要用它作为模型的结尾。模型核心会对收到的分数自行做一次 softmax，与 PyTorch 的交叉熵损失一样；再多一次会把分数压得太平，模型永远无法有把握。

在注意力结构中，Softmax 的输出图就是注意力模式：每个位置一行。

## 制造

<ItemLink id="fabricator" />用 **Softmax** 的光掩模制造它：在<ItemLink id="bench" />上验证规格，或者在[遗迹](../ruins.md)中找到打捞来的光掩模，后者造出的是未融合的版本。见[试验台与制造机](../bench.md)。
