---
navigation:
  title: 机器与线缆
  icon: cable
  position: 100
item_ids:
  - cable
  - white_cable
  - orange_cable
  - magenta_cable
  - light_blue_cable
  - yellow_cable
  - lime_cable
  - pink_cable
  - gray_cable
  - light_gray_cable
  - cyan_cable
  - purple_cable
  - blue_cable
  - brown_cable
  - green_cable
  - red_cable
  - black_cable
  - tensor_probe
---

# 机器与线缆

模型是一张由运算构成的图，而你用方块搭出这张图。数值从一台机器流出，经线缆流入下一台，从<ItemLink id="model_core" />发出的词元，一直到它收回的分数。

## 端口

每台机器由输入算出一个输出，方块的每个面各有分工：

- **正面**：输出。
- **背面**：主输入。
- **其余各面**：侧输入，只用于接受两个输入的机器，例如<ItemLink id="add" />或<ItemLink id="matmul" />。单输入的机器不用侧面，而<ItemLink id="position" />完全没有输入。

模型核心从正面发出词元，从其余任意一面收回最终的分数。

<GameScene zoom="5" interactive={true}>
  <Block id="add" p:facing="east" />
  <IsometricCamera yaw="200" pitch="30" />
  <DiamondAnnotation pos="1 0.5 0.5" color="#3CD6C6">
    正面：输出
  </DiamondAnnotation>
  <DiamondAnnotation pos="0 0.5 0.5" color="#FFB547">
    背面：主输入
  </DiamondAnnotation>
  <DiamondAnnotation pos="0.5 0.5 1" color="#FFFFFF">
    侧面：侧输入（其余四个面均可）
  </DiamondAnnotation>
</GameScene>

看向一台机器，就能知道准星所指的面是哪个端口。贴着另一台机器的输出放下的机器，会把那个输出当作自己的输入；贴着输入放下，它就为那个输入供数；贴着线缆或[残差流](stream.md)放下，它就读取其中的值。放在别处时，它的输出朝向远离你的一侧。潜行使用某个面，可把输出转到那一面；潜行使用输出面本身，则把机器整个调头。

## 网络

相接的线缆组成一个**网络**，一个网络恰好承载一个值。因此每个网络都需要恰好一个源，即某台机器的正面或核心的正面，读取它的输入则不限数量。一个网络上有两个源是错误，网络上有输入却没有源也是错误。

## 颜色

<Row>
  <ItemImage id="cable" />
  <ItemImage id="red_cable" />
  <ItemImage id="lime_cable" />
  <ItemImage id="light_blue_cable" />
  <ItemImage id="orange_cable" />
</Row>

彩色线缆只与同色线缆相连，无色线缆则与任何颜色相连。这样两个网络可以并排走线而互不混淆。一次可染八根线缆；用水桶可以洗掉颜色。

<Row>
  <RecipeFor id="light_blue_cable" />
  <Recipe id="cable_from_colored" />
</Row>

## 追踪数值

手持<ItemLink id="tensor_probe" />看向线缆，就能知道是哪台机器在驱动它，以及有多少输入在读取它。对线缆使用探针，会在聊天栏列出它所在网络上的每个端口。

探针还会把每台机器的输出以全息图显示在机器上方，取自模型最近一次运行：看向某张全息图即可读出其中的值。模型学习时它们显示什么，见[训练](training.md)。
