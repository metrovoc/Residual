---
navigation:
  title: 入门
  icon: agent
  position: 10
item_ids:
  - tensor_casing
  - weight_crystal
  - field_dataset
---

# 入门

第一个模型只需几个方块：一个<ItemLink id="model_core" />、一个<ItemLink id="embedding" />和一些<ItemLink id="cable" />。它会学会种田。然后你给它一副身体。

## 材料

<ItemLink id="tensor_casing" />是每台机器的外壳；<ItemLink id="weight_crystal" />存放学到的数值，带权重的机器都要用到它。

<Row>
  <RecipeFor id="tensor_casing" />
  <RecipeFor id="weight_crystal" />
</Row>

再合成模型核心、一组线缆和一个<ItemLink id="field_dataset" />：取之不尽的种田练习。

<Row>
  <RecipeFor id="model_core" />
  <RecipeFor id="cable" />
  <RecipeFor id="field_dataset" />
</Row>

机器在<ItemLink id="fabricator" />里制造。不放光掩模时，它用一个机壳、一块<ItemLink id="wafer" />和一颗权重晶体造出嵌入表。

<Row>
  <RecipeFor id="fabricator" />
  <RecipeFor id="wafer" />
</Row>

## 搭建

1. 放下模型核心。它的正面朝向你：词元从这里出来。
2. 从核心正面铺一条线缆，通到几格外的一处空地。
3. 把嵌入表贴着这条线缆的末端放下。它会自动接入：背面读取线缆，正面朝外。
4. 再从嵌入表正面铺一条线缆，回到核心的任意另一面：侧面、顶面或背面。模型的分数从这里输入。
5. 打开嵌入表，把**宽度**设为 **V（词表）**。

<GameScene zoom="3" interactive={true}>
  <Block id="model_core" p:facing="east" />
  <Block id="embedding" x="3" p:facing="east" />
  <Block id="light_blue_cable" x="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="2" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="4" p:west="true" p:up="true" />
  <Block id="light_blue_cable" x="4" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="4" y="2" p:south="true" p:down="true" />
  <Block id="light_blue_cable" y="2" z="1" p:east="true" p:north="true" />
  <Block id="light_blue_cable" x="1" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="2" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="3" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="4" y="2" z="1" p:west="true" p:north="true" />
  <Block id="light_blue_cable" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" y="2" p:south="true" p:down="true" />
  <IsometricCamera yaw="200" pitch="30" />
  <BoxAnnotation min="0 0 0" max="1 1 1" color="#FFB547">
    模型核心。词元从正面离开，分数从顶面回来。
  </BoxAnnotation>
  <BoxAnnotation min="3 0 0" max="4 1 1" color="#3CD6C6">
    嵌入表，宽度设为 V。
  </BoxAnnotation>
</GameScene>

两条线缆互不接触，所以组成两个独立的网络：一个把词元送到嵌入表，另一个把嵌入表的分数送回来。

## 训练

打开核心，把田间数据集放进一个**数据**槽。切到「训练」页，按下「训练」。两条曲线会下降：练习上的损失，以及测试上的损失；测试只用模型从未练过的地块。

种田不需要多少思考：收割成熟的地块，在空地上播种，其余的留着生长。宽度为 V 的嵌入表根据前一个字符给下一个字符打分，而每次决定前的那个字符就是地块，所以单这一张表就能学会种田的全部。当「测试」页上的**应变**超过 98%，模型就准备好了。

## 保存

合成一个<ItemLink id="checkpoint" />，放进核心的检查点槽，按下「存入检查点」。核心会用每一项基准测试这个模型，并把它连同分数一起存进晶体。机器可以继续训练；检查点留住的是这一刻。

<RecipeFor id="checkpoint" />

## 给它一副身体

合成一个<ItemLink id="agent" />，放在一片耕地旁，附近要有箱子：它站的地方就成了它的家。打开它，把检查点放进**模型**槽，给它几颗种子，再选择「种田」。

<RecipeFor id="agent" />

它在家周围的耕地上走动。每到一个地块，它都用与测试完全相同的格式问模型该做什么，然后照模型的回答去做。它的界面会显示它问了什么、得到了什么。走路、收割和搬运是身体的活；每一个决定都出自你的模型。

## 更进一步

其他数据集教的是其他工作，而更好的模型需要按设计造出的机器：在[试验台](bench.md)上亲手接线，或者去[遗迹](ruins.md)里寻找旧世界的设计。

- 看向任意机器，就能看到它的名称、输出形状（或它出了什么问题），以及你所看的那一面的作用。
- 潜行时对机器的某一面使用，可让它的输出朝向那一面。
- 训练在独立的线程上运行。把<ItemLink id="accelerator" />贴着核心放置能给它更多线程，并注意让它们保持凉爽。
