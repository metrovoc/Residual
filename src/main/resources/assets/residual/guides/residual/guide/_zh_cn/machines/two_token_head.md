---
navigation:
  title: 双词元头
  icon: two_token_head
  parent: machines/index.md
  position: 26
item_ids:
  - two_token_head
---

# 双词元头

<BlockImage id="two_token_head" scale="3" float="left" />

放在模型最后那个<ItemLink id="linear" />的位置。它从同样的向量给出两组分数：一组给下一个词元，也是外面唯一能看到的那组；另一组给再下一个词元，只有训练才读它。两组一起学，下面的层就会往前看得更远：见[配方](../recipe.md)。

![带第二个头的损失](/formulas/second_head.png)

## 形状

输入序列 [B×T×C]，输出分数 [B×T×V]。

## 参数

两个 C×V 的矩阵和两个长度为 V 的偏移：是线性层的两倍。第二组只训练、从不被询问，但仍然算在模型的重量里。

## 在 PyTorch 中

> `loss = F.cross_entropy(self.head1(x)[:, :-1], t[:, 1:]) + 0.3 * F.cross_entropy(self.head2(x)[:, :-2], t[:, 2:])`

## 制造

<ItemLink id="fabricator" />用**多词元预测**光掩模制造它：在<ItemLink id="bench" />上验证规格，或者在[遗迹](../ruins.md)里找到打捞的光掩模（造出的是未融合的）。见[试验台与制造机](../bench.md)。
