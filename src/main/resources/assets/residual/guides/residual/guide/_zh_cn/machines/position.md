---
navigation:
  title: 位置表
  icon: position
  parent: machines/index.md
  position: 10
item_ids:
  - position
---

# 位置表

<BlockImage id="position" scale="3" float="left" />

为上下文中从 0 到 T − 1 的每个位置各存一个学到的向量。它不接收输入。用残差汇合点把它的输出加到词元向量上，每个位置就同时带有「它是什么」和「它在哪里」。

![词元加位置](/formulas/position.png)

## 形状

无输入。输出 [T×C]，由残差汇合点加到批次中的每个序列上。

## 设置

- **宽度**：4 到 256，默认 32。应与嵌入表的宽度一致。

## 参数

T × C，其中 T 是上下文长度，即 64。

## 在 PyTorch 中

> `self.pos = nn.Embedding(T, C)`\
> `x = self.embed(tokens) + self.pos(torch.arange(T))`

## 说明

GPT-2 用的就是学到的位置。多数近期模型则按位置把每个查询和键旋转一个角度（RoPE），让注意力分数取决于两个词元相隔多远。

## 制造

可在<ItemLink id="fabricator" />中直接制造，无需光掩模。见[试验台与制造机](../bench.md)。
