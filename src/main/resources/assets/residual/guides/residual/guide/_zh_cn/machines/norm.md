---
navigation:
  title: 归一化器
  icon: norm
  parent: machines/index.md
  position: 12
item_ids:
  - norm
---

# 归一化器

<BlockImage id="norm" scale="3" float="left" />

把每个向量缩放到均值 0、方差 1，再乘以一个学出来的增益，加上一个学出来的偏移。

![层归一化](/formulas/layernorm.png)

## 形状

输入 […×C]，输出相同形状。每个向量在最后一维上归一化。

## 参数

2 × C。ε 为 1e-5。

## 在 PyTorch 中

> `self.norm1 = nn.LayerNorm(C)`\
> `h = self.norm1(x)`

## 说明

在每条分支的开头，也就是注意力或前馈块之前，各放一个；在最后的线性层之前再放一个。这种前置归一化的布局来自 GPT-2，此后几乎所有模型都沿用它。<ItemLink id="rms_norm" />用更少的步骤干同样的活。

## 制造

<ItemLink id="fabricator" />用**层归一化**的光掩模制造它：在<ItemLink id="bench" />上验证规格，或者在[遗迹](../ruins.md)中找到打捞来的光掩模，后者造出的是未融合的版本。见[试验台与制造机](../bench.md)。
