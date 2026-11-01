---
navigation:
  title: 现代块
  icon: modern_block
  parent: machines/index.md
  position: 25
item_ids:
  - modern_block
---

# 现代块

<BlockImage id="modern_block" scale="3" float="left" />

一台机器装下一整个现代层：RMS 归一化、现代注意力、加回去；RMS 归一化、门控前馈、加回去。开启旋转位置时，把它们堆起来不需要<ItemLink id="position" />：见[现代块](../modern.md)。

![现代层](/formulas/modern.png)

## 形状

输入序列 [B×T×C]，输出相同形状。

## 设置

- **头数**：1、2、4 或 8，默认为 4。C 必须能被它整除；开启旋转位置时，每个头的宽度还必须是偶数。
- **旋转位置**：默认开启。
- **QK 归一化**：默认开启。

## 参数

12 × C² + 2 × C + 1：宽度 64 时约 49,000 个，和<ItemLink id="transformer" />差不多。

## 在 PyTorch 中

> `x = x + self.attn(self.rms1(x))`\
> `x = x + self.ffn(self.rms2(x))`

## 说明

它的图片显示第一个头的注意力模式。

## 制造

<ItemLink id="fabricator" />用**现代块**光掩模制造它：在<ItemLink id="bench" />上验证规格，或者在[遗迹](../ruins.md)里找到打捞的光掩模（造出的是未融合的）。见[试验台与制造机](../bench.md)。
