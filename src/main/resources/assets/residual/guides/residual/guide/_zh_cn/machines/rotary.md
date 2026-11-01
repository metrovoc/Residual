---
navigation:
  title: 旋转位置
  icon: rotary
  parent: machines/index.md
  position: 21
item_ids:
  - rotary
---

# 旋转位置

<BlockImage id="rotary" scale="3" float="left" />

用旋转来表示词元在哪里。词元的每一对数都转过一个随序列位置增大的角度，第一对转得最快。在<ItemLink id="split_heads" />之后，查询上接一个，键上接一个：于是查询和键之间的分数只看它们相隔多远，不管它们站在序列的什么地方。

![旋转位置](/formulas/rotary.png)

## 形状

输入 […×T×C]，输出相同形状。C 必须是偶数（用在头上时指每个头的宽度）。

## 参数

没有。和<ItemLink id="position" />不同，它不用填表，也没有最长序列的限制。

## 在 PyTorch 中

> `z = torch.view_as_complex(x.reshape(*x.shape[:-1], -1, 2))`\
> `y = torch.view_as_real(z * freqs_cis[:T]).flatten(-2)`

## 制造

<ItemLink id="fabricator" />用**旋转位置**光掩模制造它：在<ItemLink id="bench" />上验证规格，或者在[遗迹](../ruins.md)里找到打捞的光掩模（造出的是未融合的）。见[试验台与制造机](../bench.md)。
