---
navigation:
  title: 门控前馈块
  icon: swiglu
  parent: machines/index.md
  position: 20
item_ids:
  - swiglu
---

# 门控前馈块

<BlockImage id="swiglu" scale="3" float="left" />

一种隐藏层的数各带一道门的前馈块：一路经过 SiLU，决定另一路能通过多少。

![门控前馈块](/formulas/swiglu.png)

## 形状

输入序列 [B×T×C]，输出相同形状。内部加宽到 C 的 8/3。

## 参数

3 × C × 8/3 C = 8 × C²，没有偏置：和扩展取 4 的<ItemLink id="mlp" />一样多，只是少了偏置。

## 在 PyTorch 中

> `self.w1, self.w2 = nn.Linear(C, H, False), nn.Linear(C, H, False)`\
> `self.w3 = nn.Linear(H, C, False)`\
> `y = self.w3(F.silu(self.w1(x)) * self.w2(x))`

## 制造

<ItemLink id="fabricator" />用**门控前馈**光掩模制造它：在<ItemLink id="bench" />上验证规格，或者在[遗迹](../ruins.md)里找到打捞的光掩模（造出的是未融合的）。见[试验台与制造机](../bench.md)。
