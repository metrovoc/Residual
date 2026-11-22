---
navigation:
  title: 机器
  icon: linear
  position: 120
---

# 机器

每台机器执行模型中的一种运算，算法与 PyTorch 相同。机器从背面接收主输入，如果有侧输入，则可从其他任意一面接入，结果从正面送出：见[机器与线缆](../wiring.md)。右键点击机器可以修改设置，并查看它在计算什么。

<residual:Clip name="operator" alt="训练时机器的界面：不断变化的输出和权重" />

每台机器都在工作台上合成，配方在它自己的那一页。

[机器的界面，逐项说明](../screens/operator.md)。

## 零件

- <ItemLink id="embedding" />：把词元编号变成向量。
- <ItemLink id="position" />：为每个位置学一个向量。
- <ItemLink id="add" />：加法，残差流也靠它。
- <ItemLink id="multiply" />：一路信号打开或关闭另一路。
- <ItemLink id="scale" />：乘以一个常数，通常是 1/√d。
- <ItemLink id="causal_mask" />：把未来遮住。
- <ItemLink id="split_heads" />和<ItemLink id="merge_heads" />：用零件拼出多头注意力。
- <ItemLink id="shift" />：固定地回看一段时间。
- <ItemLink id="concat" />：把两个向量首尾相接。

## 由它们搭成

- <ItemLink id="matmul" />：网格相乘，用查询比较键。
- <ItemLink id="softmax" />：把分数变成权重。
- <ItemLink id="linear" />：一个学出来的矩阵，每个模型的主力。
- <ItemLink id="activation" />：对每个数字施加一个非线性函数。
- <ItemLink id="norm" />：让向量保持稳定的大小。
- <ItemLink id="attention" />：一台机器实现多头注意力。
- <ItemLink id="mlp" />：加宽、弯折、投影回去。
- <ItemLink id="transformer" />：一整个 Transformer 层。
- <ItemLink id="two_token_head" />：给下一个词元和再下一个词元打分。

## 现代块

见[现代块](../modern.md)。

- <ItemLink id="rms_norm" />：用更少的步骤让向量保持稳定的大小。
- <ItemLink id="swiglu" />：数各带一道门的前馈块。
- <ItemLink id="rotary" />：靠旋转说出词元的位置。
- <ItemLink id="qk_norm" />：在同一大小上比较查询和键。
- <ItemLink id="z_loss" />：用一点惩罚把模型的分数守在零附近。
- <ItemLink id="modern_attention" />：把这些都装进一台注意力机器。
- <ItemLink id="modern_block" />：一整个现代层。
