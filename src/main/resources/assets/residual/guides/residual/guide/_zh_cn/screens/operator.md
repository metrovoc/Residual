---
navigation:
  title: 机器
  icon: linear
  parent: screens/index.md
  position: 5
---

# 机器的界面

对模型的任意一台机器使用即可打开；每台都有自己的设置和数学形式。[机器](../machines/index.md)里每台各有一页。

<residual:Clip name="operator" alt="训练时机器的界面：不断变化的输出和权重" />

第一次校验之前的线性层。

<residual:Shot name="operator_machine" alt="线性层的界面：名称、设置、图像和数学形式">
  <residual:Part name="name">
    **名称**：你给它起的名字，凡是列出这台机器的地方都会显示。
  </residual:Part>
  <residual:Part name="field.out">
    **设置**：这台机器可以调的东西，每台都不一样。点击换下一个值，右键换上一个。
  </residual:Part>
  <residual:Part name="check">
    **上次校验的结果**：它输出的形状、参数和梯度，或者它出了什么问题。
  </residual:Part>
  <residual:Part name="picture">
    **输出**：从它正面出来的东西，大于零为琥珀色，小于零为蓝色。有权重的机器会在旁边显示主要的权重矩阵。
  </residual:Part>
  <residual:Part name="math">
    **数学形式**：用一两行写出它算的是什么。
  </residual:Part>
</residual:Shot>
