---
navigation:
  title: 门控
  icon: multiply
  parent: machines/index.md
  position: 16
item_ids:
  - multiply
---

# 门控

<BlockImage id="multiply" scale="3" float="left" />

把两个输入逐个数相乘，让一个信号可以放大、打开或关闭另一个信号。

![逐元素乘法](/formulas/multiply.png)

## 形状

与残差汇合点接受的形状相同。

## 参数

无。

## 在 PyTorch 中

> `y = F.silu(self.gate(x)) * self.up(x)`

## 说明

多数近期语言模型的前馈层里都有门控。SwiGLU 把 SiLU(xW₁) 与第二个投影 xW₃ 相乘，再投影回原来的大小。搭建方法：两个线性层读取同一个输入，其中一个后面接设为 SiLU 的激活层，用门控把两者合并，最后接一个线性层回到原来的宽度。Transformer 之前的循环神经网络 LSTM，就是用这样的门来控制记忆的。

## 制造

可在<ItemLink id="fabricator" />中直接制造，无需光掩模。见[试验台与制造机](../bench.md)。
