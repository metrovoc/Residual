---
navigation:
  title: Transformer 块
  icon: transformer
  parent: machines/index.md
  position: 15
item_ids:
  - transformer
---

# Transformer 块

<BlockImage id="transformer" scale="3" float="left" />

一台机器装下一整个 Transformer 层：归一化、注意力、加回去；归一化、前馈、加回去。把它们堆起来就是一个 GPT。

![Transformer 层](/formulas/block.png)

## 形状

输入序列 [B×T×C]，输出相同形状。

## 设置

- **头数**：1、2、4 或 8，默认为 4。
- **扩展**：1、2 或 4，默认为 4。
- **函数**：ReLU、GELU 或 SiLU，默认为 GELU。

## 参数

扩展倍数为 4 时是 12 × C² + 9 × C：宽度 64 时约 50,000 个。

## 在 PyTorch 中

> `self.block1 = Block(C, heads=4, hidden=4 * C, act=gelu_tanh)`\
> `x = self.block1(x)`

## 说明

它的图像显示第一个头的注意力模式，和注意力头的一样。

## 制造

<ItemLink id="fabricator" />用 **Transformer 块**的光掩模制造它：在<ItemLink id="bench" />上验证规格，或者在[遗迹](../ruins.md)中找到打捞来的光掩模，后者造出的是未融合的版本。见[试验台与制造机](../bench.md)。
