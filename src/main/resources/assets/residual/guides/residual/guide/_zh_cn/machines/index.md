---
navigation:
  title: 机器
  icon: linear
  position: 120
---

# 机器

每台机器执行模型中的一种运算，算法与 PyTorch 相同。机器从背面接收主输入，如果有侧输入，则可从其他任意一面接入，结果从正面送出：见[机器与线缆](../wiring.md)。右键点击机器可以修改设置，并查看它在计算什么。

每台机器都出自[制造机](../bench.md)。简单的机器不需要设计；其余的需要一张描述它的规格的光掩模，可以在试验台上验证得到，也可以从遗迹中打捞。

## 无需设计

- <ItemLink id="embedding" />：把词元编号变成向量。
- <ItemLink id="position" />：为每个位置学一个向量。
- <ItemLink id="add" />：加法，残差流也靠它。
- <ItemLink id="multiply" />：一路信号打开或关闭另一路。
- <ItemLink id="scale" />：乘以一个常数，通常是 1/√d。
- <ItemLink id="causal_mask" />：把未来遮住。
- <ItemLink id="split_heads" />和<ItemLink id="merge_heads" />：用零件拼出多头注意力。
- <ItemLink id="shift" />：固定地回看一段时间。
- <ItemLink id="concat" />：把两个向量首尾相接。

## 需要设计

- <ItemLink id="matmul" />，来自矩阵乘法：网格相乘，用查询比较键。
- <ItemLink id="softmax" />，来自 Softmax：把分数变成权重。
- <ItemLink id="linear" />，来自线性层：一个学出来的矩阵，每个模型的主力。
- <ItemLink id="activation" />，来自 GELU：对每个数字施加一个非线性函数。
- <ItemLink id="norm" />，来自层归一化：让向量保持稳定的大小。
- <ItemLink id="attention" />，来自注意力：一台机器实现多头注意力。
- <ItemLink id="mlp" />，来自前馈块：加宽、弯折、投影回去。
- <ItemLink id="transformer" />，来自 Transformer 块：一整个 Transformer 层。
