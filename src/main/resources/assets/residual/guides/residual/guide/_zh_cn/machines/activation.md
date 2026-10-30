---
navigation:
  title: 激活层
  icon: activation
  parent: machines/index.md
  position: 3
item_ids:
  - activation
---

# 激活层

<BlockImage id="activation" scale="3" float="left" />

让每个数字都经过同一个非线性函数弯折。没有它，两个相连的线性层乘出来只等于一个线性层，堆多少层都一样：正是激活层让深度有了意义。

![ReLU、GELU、SiLU、tanh 和 sigmoid](/formulas/activations.png)

## 形状

输入任意形状，输出相同形状。

## 设置

- **函数**：默认为 ReLU。
- **ReLU** 保留正数，其余置零。它的斜率是 0 或 1，所以从不缩小经过它的梯度。
- **GELU** 是平滑版的 ReLU，GPT-2 和 BERT 使用它。
- **SiLU** 即 z·σ(z)，用在 LLaMA 及其同类模型的前馈层中。
- **tanh** 把数字压进 (−1, 1)，最早的神经语言模型就用它。
- **sigmoid** 把数字压进 (0, 1)。它的斜率最大只有四分之一，所以每一层 sigmoid 都会缩小反向经过它的梯度。

## 参数

无。

## 在 PyTorch 中

> `F.relu(x)`\
> `F.gelu(x, approximate="tanh")`\
> `F.silu(x)`\
> `torch.tanh(x)`\
> `torch.sigmoid(x)`

## 制造

<ItemLink id="fabricator" />用 **GELU** 的光掩模制造它：在<ItemLink id="bench" />上验证规格，或者在[遗迹](../ruins.md)中找到打捞来的光掩模，后者造出的是未融合的版本。见[试验台与制造机](../bench.md)。
