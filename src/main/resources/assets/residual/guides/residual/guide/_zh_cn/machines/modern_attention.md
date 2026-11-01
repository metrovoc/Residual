---
navigation:
  title: 现代注意力头
  icon: modern_attention
  parent: machines/index.md
  position: 24
item_ids:
  - modern_attention
---

# 现代注意力头

<BlockImage id="modern_attention" scale="3" float="left" />

后来的设计所用的多头因果自注意力：查询和键上的<ItemLink id="rotary" />、两者之间的<ItemLink id="qk_norm" />，都装在一台机器里。用已验证设计造出的机器，以在线 softmax 把每行的分数一块一块地处理，从不把它们全存下来：见[现代块](../modern.md)。

## 形状

输入序列 [B×T×C]，输出相同形状。

## 设置

- **头数**：1、2、4 或 8，默认为 4。C 必须能被它整除；开启旋转位置时，每个头的宽度还必须是偶数。
- **旋转位置**：默认开启。关闭后它不知道位置：前面需要一个<ItemLink id="position" />。
- **QK 归一化**：默认开启。关闭后分数就是 q·kᵀ/√d，没有上限。

## 参数

4 × C²；开启 QK 归一化时再加 1 个温度。

## 在 PyTorch 中

> `q, k = rotate(q), rotate(k)`\
> `q, k = F.normalize(q, dim=-1) * t * dh ** 0.5, F.normalize(k, dim=-1)`\
> `y = F.scaled_dot_product_attention(q, k, v, is_causal=True)`

## 说明

它的图片显示第一个头的注意力模式，和<ItemLink id="attention" />一样。每张图片来自单独的一次普通计算，那次计算才存下分数；训练的每一步都不存。

## 制造

<ItemLink id="fabricator" />用**现代注意力**光掩模制造它：在<ItemLink id="bench" />上验证规格，或者在[遗迹](../ruins.md)里找到打捞的光掩模（造出的是未融合的）。见[试验台与制造机](../bench.md)。
