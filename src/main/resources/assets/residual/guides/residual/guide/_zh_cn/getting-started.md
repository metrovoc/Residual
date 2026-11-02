---
navigation:
  title: 入门
  icon: forecaster
  position: 10
item_ids:
  - tensor_casing
  - weight_crystal
---

# 入门

第一个模型只需几个方块：一个<ItemLink id="model_core" />、一个<ItemLink id="embedding" />和一些<ItemLink id="cable" />。它学的是你周围的方块接下来会怎样。<ItemLink id="forecaster" />把这派上用场：一旦它的预报站得住，你的熔炉就能快四倍。

## 材料

<ItemLink id="tensor_casing" />是每台机器的外壳；<ItemLink id="weight_crystal" />存放学到的数值，带权重的机器都要用到它。

<Row>
  <RecipeFor id="tensor_casing" />
  <RecipeFor id="weight_crystal" />
</Row>

再合成模型核心、一组线缆和一个<ItemLink id="forecast_dataset" />：取之不尽的作物生长和机器工作的记录行，供它学习。

<Row>
  <RecipeFor id="model_core" />
  <RecipeFor id="cable" />
  <RecipeFor id="forecast_dataset" />
</Row>

机器在<ItemLink id="fabricator" />里制造。不放光掩模时，它用一个机壳、一块<ItemLink id="wafer" />和一颗权重晶体造出嵌入表。

<Row>
  <RecipeFor id="fabricator" />
  <RecipeFor id="wafer" />
</Row>

## 搭建

<residual:Steps>
  <residual:Step>
    放下模型核心。它的正面朝向你：词元从这里出来。

    <GameScene zoom="3" interactive={true}>
      <Block id="model_core" p:facing="east" />
      <BoxAnnotation min="0 0 0" max="5 3 2" color="#00000000" thickness="0" />
      <IsometricCamera yaw="200" pitch="30" />
      <DiamondAnnotation pos="1 0.5 0.5" color="#FFB547">
        正面：词元从这里离开。
      </DiamondAnnotation>
    </GameScene>
  </residual:Step>

  <residual:Step>
    从核心正面铺一条线缆，通到几格外的一处空地。

    <GameScene zoom="3" interactive={true}>
      <Block id="model_core" p:facing="east" />
      <Block id="light_blue_cable" x="1" p:east="true" p:west="true" />
      <Block id="light_blue_cable" x="2" p:west="true" />
      <BoxAnnotation min="0 0 0" max="5 3 2" color="#00000000" thickness="0" />
      <IsometricCamera yaw="200" pitch="30" />
      <BoxAnnotation min="1 0 0" max="3 1 1" color="#3CD6C6">
        这条线缆把词元送到嵌入表。
      </BoxAnnotation>
    </GameScene>
  </residual:Step>

  <residual:Step>
    把嵌入表贴着这条线缆的末端放下。它会自动接入：背面读取线缆，正面朝外。

    <GameScene zoom="3" interactive={true}>
      <Block id="model_core" p:facing="east" />
      <Block id="embedding" x="3" p:facing="east" />
      <Block id="light_blue_cable" x="1" p:east="true" p:west="true" />
      <Block id="light_blue_cable" x="2" p:east="true" p:west="true" />
      <BoxAnnotation min="0 0 0" max="5 3 2" color="#00000000" thickness="0" />
      <IsometricCamera yaw="200" pitch="30" />
      <BoxAnnotation min="3 0 0" max="4 1 1" color="#3CD6C6">
        嵌入表，背面贴着线缆。
      </BoxAnnotation>
    </GameScene>
  </residual:Step>

  <residual:Step>
    再从嵌入表正面铺一条线缆，回到核心的任意另一面：侧面、顶面或背面。模型的分数从这里输入。

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
      <BoxAnnotation min="0 0 0" max="5 3 2" color="#00000000" thickness="0" />
      <IsometricCamera yaw="200" pitch="30" />
      <BoxAnnotation min="0 1 0" max="5 3 2" color="#FFB547">
        回来的路：分数从核心的顶面进入。
      </BoxAnnotation>
    </GameScene>
  </residual:Step>

  <residual:Step>
    打开嵌入表，把**宽度**设为 **V（词表）**。

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
      <BoxAnnotation min="0 0 0" max="5 3 2" color="#00000000" thickness="0" />
      <IsometricCamera yaw="200" pitch="30" />
      <BoxAnnotation min="0 0 0" max="1 1 1" color="#FFB547">
        模型核心。词元从正面离开，分数从顶面回来。
      </BoxAnnotation>
      <BoxAnnotation min="3 0 0" max="4 1 1" color="#3CD6C6">
        嵌入表，宽度设为 V。
      </BoxAnnotation>
    </GameScene>
  </residual:Step>
</residual:Steps>

两条线缆互不接触，所以组成两个独立的网络：一个把词元送到嵌入表，另一个把嵌入表的分数送回来。

## 训练

打开核心，把预报数据集放进一个**数据**槽。切到「训练」页，按下「训练」。两条曲线会下降：练习上的损失，以及测试上的损失；测试只用模型从未练过的记录行。

数据集里每一行都是一个方块，一次又一次地查看。熔炉的一行记的是它正在做的这件东西做到了哪儿，从刚开始的 `a` 到快完成的 `h`，然后再来一轮：`abcdefghabcdefgh`。作物的一行是它每次查看时的生长阶段，从 `0` 到 `7`。宽度为 V 的嵌入表根据前一次查看给下一次查看打分，而对机器来说，前一次查看几乎就定下了下一次。当「测试」页上的**预报**低于 0.21，模型就准备好了。

## 保存

合成一个<ItemLink id="checkpoint" />，放进核心的检查点槽，按下「存入检查点」。核心会用每一项基准测试这个模型，并把它连同分数一起存进晶体。机器可以继续训练；检查点留住的是这一刻。

<RecipeFor id="checkpoint" />

## 让熔炉超前运行

<residual:Clip name="forecaster" alt="预报机让身边的熔炉超前运行，范围外的熔炉仍是原来的速度" />

合成一台预报机，放在你的熔炉、烟熏炉或高炉中间：四格之内，同一层或上下各一层。把检查点放进它的**模型**槽，并让熔炉有料可烧、有燃料可用。

<RecipeFor id="forecaster" />

<GameScene zoom="4" interactive={true}>
  <Block id="forecaster" x="2" p:facing="south" p:working="true" />
  <Block id="minecraft:furnace" p:facing="south" p:lit="true" />
  <Block id="minecraft:furnace" x="1" p:facing="south" p:lit="true" />
  <Block id="minecraft:smoker" x="3" p:facing="south" p:lit="true" />
  <Block id="minecraft:blast_furnace" x="4" p:facing="south" p:lit="true" />
  <IsometricCamera yaw="200" pitch="30" />
  <BoxAnnotation min="2 0 0" max="3 1 1" color="#3CD6C6">
    预报机，装着一个通过预报的模型。
  </BoxAnnotation>
</GameScene>

预报机每做一件东西就查看每台机器八次，记下它做到了哪儿。等机器按自己的节奏做完十来件，它就拿模型对照它们的实际。若模型预期的进度与实际相差不超过 11%，机器每过一次查看，它就推进四次：模型画出每台机器接下来的几次查看，机器就照着做完。不超过 25%，推进两次。其余的，包括作物，见[预报机](forecaster.md)。

## 更进一步

- 更好的模型需要按设计造出的机器：在[试验台](bench.md)上亲手接线，或者去[遗迹](ruins.md)里寻找旧世界的设计。
- [存储](storage.md)收好熔炉做出的东西：每台熔炉下面放一个端口，把产物搬上驱动器，存储终端再把它找出来。
- [智能体](agents.md)运行你拼出来的程序：挖矿、搬运、建造、分类，模型只在学习胜过规则的地方补位。
- 看向任意机器，就能看到它的名称、输出形状（或它出了什么问题），以及你所看的那一面的作用。
- 潜行时对机器的某一面使用，可让它的输出朝向那一面。
- 训练在独立的线程上运行。把<ItemLink id="accelerator" />贴着核心放置能给它更多线程，并注意让它们保持凉爽。
