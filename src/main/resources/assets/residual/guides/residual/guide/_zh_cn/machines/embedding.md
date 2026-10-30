---
navigation:
  title: 嵌入表
  icon: embedding
  parent: machines/index.md
  position: 1
item_ids:
  - embedding
---

# 嵌入表

<BlockImage id="embedding" scale="3" float="left" />

为每个词元查出一行学到的数：词元 *i* 得到表中的第 *i* 行，词表中每个条目各占一行。文本就是这样进入模型的，所以这张表必须直接接在模型核心的正面。

![嵌入查找](/formulas/embedding.png)

## 形状

输入词元 [B×T]，输出 [B×T×C]，其中 C 是宽度。

## 设置

- **宽度**：V，或 4 到 256，默认 32。设为 V 时，每一行可以直接作为下一个词元的分数：这就是一个二元语法模型。

## 参数

V × C，从标准差为 1 的正态分布中抽取。

## 在 PyTorch 中

> `self.embed = nn.Embedding(V, C)`\
> `e = self.embed(tokens)`

## 说明

训练后，权重图显示的就是这张表，每个词元一行。行为相似的词元最终会得到相似的行，模型对一个词元学到的东西就这样延伸到其他词元上。

## 制造

可在<ItemLink id="fabricator" />中直接制造，无需光掩模。见[试验台与制造机](../bench.md)。
