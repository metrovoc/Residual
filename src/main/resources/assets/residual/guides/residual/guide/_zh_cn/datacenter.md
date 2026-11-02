---
navigation:
  title: 机房
  icon: hall_console
  position: 91
item_ids:
  - chiller
  - coolant_pipe
  - hall_console
  - load_wall
---

# 机房

一排排机架会发热，热量总得有个去处。机房就是几个机组、供它们运行的电力和让它们不停转的冷却加在一起的东西：一个你可以站在里面、从这头读到那头的房间。

<GameScene zoom="3" interactive={true}>
  <Block id="chiller" p:facing="east" p:working="true" />
  <Block id="coolant_pipe" x="1" p:east="true" p:west="true" p:flowing="true" />
  <Block id="coolant_pipe" x="2" p:west="true" p:up="true" p:flowing="true" />
  <Block id="coolant_pipe" x="2" y="1" p:down="true" p:up="true" p:flowing="true" />
  <Block id="coolant_pipe" x="2" y="2" p:down="true" p:west="true" p:flowing="true" />
  <Block id="coolant_pipe" x="1" y="2" p:east="true" p:west="true" p:flowing="true" />
  <Block id="coolant_pipe" x="0" y="2" p:east="true" p:west="true" p:flowing="true" />
  <Block id="coolant_pipe" x="-1" y="2" p:east="true" p:down="true" p:flowing="true" />
  <Block id="coolant_pipe" x="-1" y="1" p:up="true" p:down="true" p:flowing="true" />
  <Block id="coolant_pipe" x="-1" p:up="true" p:east="true" p:flowing="true" />
  <Block id="rack_module" x="3" p:facing="south" p:bay0="true" p:bay1="true" p:bay2="true" p:bay3="true" p:bay4="true" p:bay5="true" p:bay6="true" p:bay7="true" />
  <Block id="rack_module" x="3" y="1" p:facing="south" p:bay0="true" p:bay1="true" p:bay2="true" p:bay3="true" p:bay4="true" p:bay5="true" p:bay6="true" p:bay7="true" />
  <Block id="rack_switch" x="3" y="2" p:facing="south" p:lit="true" />
  <IsometricCamera yaw="200" pitch="30" />
  <BoxAnnotation min="0 0 0" max="1 1 1" color="#3EB8FF">
    冷水机。回路从它的正面出去，再回到它的背面。
  </BoxAnnotation>
  <BoxAnnotation min="3 0 0" max="4 2 1" color="#3CD6C6">
    贴着回路的设备都能得到冷却，只要冷水机还带得走它的热量。
  </BoxAnnotation>
</GameScene>

## 冷却回路

机架的发热量不是一汪水能应付的。只要机架有一个模块接触到<ItemLink id="coolant_pipe" />，整个机架就能得到冷却；这些管子要组成一条在<ItemLink id="chiller" />上闭合的回路：从冷水机的正面出发，绕回它的背面。单独的加速器也可以这样冷却。冷水机工作时冷却液在管里流动。没有冷却的设备会过热，只出一半力并冒烟。

一台冷水机能带走 8,192 FE/t 的热量，按每个设备的满载功率计，所以一条回路要几台冷水机取决于它的设备。热量总要交给什么东西：贴着它的水、冰或雪，或者寒冷生物群系的空气。它还要从所连的电网取电，为设备实际产生的全部热量付费。

## PUE

电能利用效率（PUE）是机房总耗电除以设备耗电。它就是冷却的代价：

| 冷水机把热量交给 | 每 FE 热量耗电 | PUE |
|------------------|----------------|-----|
| 水               | 0.3            | 1.3 |
| 冰或雪           | 0.2            | 1.2 |
| 寒冷生物群系的空气 | 0.1          | 1.1 |

旁边放着水的加速器不花一分电，所以小实验室用不着这些。

## 机房控制台

<ItemLink id="hall_console" />用互连线连到两台以上<ItemLink id="pod_switch" />，并且集群里有一条闭合的冷却回路，整个集群就成为一座机房：每台机组交换机都能带它的八个机架，而不是机房的集群只能用一个机组。机房成立时它会亮起。

它的屏幕读出整座机房：额定能力、正在运行什么、耗电多少、PUE 多少。它还给出内存能训练的最大模型，以及用每个参数 20 个词元训练它要花多少运算、多少天、多少能量。这是用[算力](compute.md)一页里的公式算出的预测，针对大到做不了的训练。

## 负载墙

<ItemLink id="load_wall" />面板并排、上下拼接，朝向相同并连入集群，就组成一整块显示屏：每个机架一根柱子，高度是它的忙碌程度，上方是集群在运行什么、多少设备在工作、耗电多少和 PUE。机架的灯和这面墙，站在房间另一头也看得清。

<Row>
  <RecipeFor id="chiller" />
  <RecipeFor id="coolant_pipe" />
  <RecipeFor id="load_wall" />
</Row>
