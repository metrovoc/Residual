---
navigation:
  title: 现代块
  icon: modern_block
  position: 65
---

# 现代块

最早会说话的模型，似乎是用<ItemLink id="transformer" />搭成的。旧世界最后的那些模型保留了它的形状，也就是每一层都读取并累加的那个累计和，而把里面的东西一件一件换掉了。每一件都是<ItemLink id="bench" />上的一份规格，也是一台单独的机器，都可以试用，也可以再拿掉。

## 各个部分

**RMS 归一化。**<ItemLink id="norm" />要减去每个向量的均值，缩放，再加回一个偏移。<ItemLink id="rms_norm" />只做缩放，既不减均值也不加偏移，一层有这些就够了。

**门。**<ItemLink id="mlp" />把每个词元加宽、弯折，再投影回去。<ItemLink id="swiglu" />做出两份加宽的副本，让其中一份经过 SiLU，开合另一份的每个数。只加宽到 8/3 倍而不是 4 倍，花费的权重一样多。

**靠旋转表示位置。**<ItemLink id="position" />是一张学出来的向量表，每个位置一个，加到词元上，而且有尽头。<ItemLink id="rotary" />没有权重，也没有尽头：它把查询或键里的每对数转过一个随位置增大的角度，每对有自己的速率。

![旋转位置](/formulas/rotary.png)

这样转过的查询和键，它们之间的分数只取决于相隔多远，不管站在哪里。

**QK 归一化。**如果放任不管，查询和键会在训练中不断变大，直到一个分数盖过其余所有分数；这么尖的 softmax 给出的梯度会把权重甩来甩去。<ItemLink id="qk_norm" />在同一大小上比较它们，再乘一个学到的温度，于是分数不会超过 t·√C。

![归一化后的分数](/formulas/qknorm.png)

**在线 softmax。**<ItemLink id="attention" />要写出每个词元对其他每个词元的分数，每个头 T×T 个，还连带好几份副本和它们的梯度。用已验证设计造出的<ItemLink id="modern_attention" />从不把它们存下来。它把每个词元的分数一块一块地算出来，只记住到目前为止最大的那个和累计的和：

![在线 softmax](/formulas/online.png)

答案在舍入误差之内完全相同，梯度也是。批量 32、四个宽度为 8 的头、64 个词元时，普通做法要存 430 万个数，分块做法只要 8.2 万个。反向传播时，再用留下的查询和键把分数重新算一遍。

**Z 损失。**<ItemLink id="z_loss" />放在<ItemLink id="model_core" />前面，为远离零的分数加一点惩罚：分数太大时，softmax 就不再精确。

![z 损失](/formulas/zloss.png)

## 搭一个

<ItemLink id="modern_block" />里装着一整个现代层，在线 softmax 也在里面。把它们连成一行，就不需要任何表示位置的东西：一个<ItemLink id="embedding" />、这些块、一个<ItemLink id="rms_norm" />，再加一个设为 V 的<ItemLink id="linear" />。它的屏幕上可以关掉**旋转位置**和 **QK 归一化**，看看它们各自起了什么作用。关掉旋转位置后，它前面需要一个位置表，就像 Transformer 块一样。
