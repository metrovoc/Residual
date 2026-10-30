---
navigation:
  title: 残差流
  icon: stream
  position: 105
item_ids:
  - stream
---

# 残差流

Transformer 围绕一个不断累加的和来构建。嵌入开启这个和；每一层读取它，算出些什么，再把结果加回去；最后一层把这个和变成分数。这个累加的和就是**残差流**。<ItemLink id="stream" />就是它的导管。

<RecipeFor id="stream" />

## 它如何流动

- 每个方块从正面把流传下去。侧面的箭头标出方向。
- 输出与流方块相接（面对面或经由线缆），就在那个方块处**加入**流。
- 输入与流方块相接，就**读取**流到达那里时的值，也就是在那个方块的加入之前。
- 最后一个方块正面的输入读取的是全部的和。

流起初是空的：第一个加入的东西，通常是嵌入，就是它从此承载的内容。贴着另一段流的正面放下的流会接续那段流，贴着输出放下的流则从那里开始。潜行使用某个面可转动方块。

## 流上的一层

<GameScene zoom="3" interactive={true}>
  <Block id="embedding" x="-1" p:facing="east" />
  <Block id="position" y="1" p:facing="down" />
  <Block id="stream" p:facing="east" />
  <Block id="stream" x="1" p:facing="east" />
  <Block id="stream" x="2" p:facing="east" />
  <Block id="stream" x="3" p:facing="east" />
  <Block id="stream" x="4" p:facing="east" />
  <Block id="stream" x="5" p:facing="east" />
  <Block id="norm" x="1" z="-1" p:facing="north" />
  <Block id="attention" x="1" z="-2" p:facing="north" />
  <Block id="cyan_cable" x="1" z="-3" p:south="true" p:east="true" />
  <Block id="cyan_cable" x="2" z="-3" p:west="true" p:south="true" />
  <Block id="cyan_cable" x="2" z="-2" p:north="true" p:south="true" />
  <Block id="cyan_cable" x="2" z="-1" p:north="true" p:south="true" />
  <Block id="norm" x="3" z="1" p:facing="south" />
  <Block id="mlp" x="3" z="2" p:facing="south" />
  <Block id="orange_cable" x="3" z="3" p:north="true" p:east="true" />
  <Block id="orange_cable" x="4" z="3" p:west="true" p:north="true" />
  <Block id="orange_cable" x="4" z="2" p:north="true" p:south="true" />
  <Block id="orange_cable" x="4" z="1" p:north="true" p:south="true" />
  <Block id="norm" x="6" p:facing="east" />
  <Block id="linear" x="7" p:facing="east" />
  <IsometricCamera yaw="340" pitch="40" />
  <BoxAnnotation min="-1 0 0" max="0 1 1" color="#3CD6C6">
    嵌入表把每个词元的向量加入第一个方块，位置表也加入自己的向量。
  </BoxAnnotation>
  <BoxAnnotation min="1 0 -2" max="2 1 -1" color="#FFB547">
    注意力经过一个归一化器读取流，它的线缆在下一个方块处把结果加回去。
  </BoxAnnotation>
  <BoxAnnotation min="3 0 2" max="4 1 3" color="#FFB547">
    前馈块在另一侧做同样的事。
  </BoxAnnotation>
  <BoxAnnotation min="6 0 0" max="7 1 1" color="#3CD6C6">
    末端，最后一个归一化器读取全部的和，反嵌入把它变成交给模型核心的分数。
  </BoxAnnotation>
</GameScene>

这是一个 Transformer 层，按通常的画法摆放：读取、归一化、计算、加回。把流加长，就能叠上更多层。<ItemLink id="transformer" />在一个方块内完成这一切，残差相加也包括在内，所以它要单独接成一条线，而不是放在流上：放在流上，它会把流加到自身两次。

## 为什么用流

每一层都是在细化这个和，而不是取代它，所以暂时没什么有用东西可加的层，可以不去动它。训练也走同一条捷径：梯度沿着流直接回到每一层。在普通的层层堆叠里，梯度回传时必须穿过每一层，层数一深，还没到底就消失了。你可以亲眼看到：模型训练时往回走的琥珀色波，到达流上每一层时都和起初一样亮，而沿着深的普通堆叠往下，则一层比一层暗。

## 规则

- 两条流不能流入同一个方块，流也不能绕成一圈。
- 机器在其输入相接处读取流，在其输出相接处加入流。在自己加回的位置之后读取，会形成回路。
- 一根线缆只接触一条流、且没有其他东西驱动它时，它承载这条流。一根线缆接触两条流而没有输出时，哪条也不承载：手持<ItemLink id="tensor_probe" />可以看到线缆承载的是什么。
