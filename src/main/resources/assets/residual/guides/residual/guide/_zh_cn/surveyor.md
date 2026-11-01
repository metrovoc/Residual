---
navigation:
  title: 勘测仪
  icon: surveyor
  position: 86
item_ids:
  - surveyor
---

# 勘测仪

<ItemLink id="surveyor" />找出它周围地下的矿石。它一次读一行十六个方块的地层，每个方块一个字母，并问它的模型这一行里每种矿石最前面那块在哪里，和勘测测试的问法一样。每个答案都会对照方块核实：答对的留下，这一行再问一次这种矿的下一块；答错的，这种矿要等下一次勘测才找得到。

<GameScene zoom="4" interactive={true}>
  <Block id="minecraft:stone" x="-2" y="-2" z="-2" />
  <Block id="minecraft:stone" x="-2" y="-1" z="-2" />
  <Block id="minecraft:stone" x="-2" y="-2" z="-1" />
  <Block id="minecraft:stone" x="-2" y="-1" z="-1" />
  <Block id="minecraft:stone" x="-2" y="-2" z="0" />
  <Block id="minecraft:stone" x="-2" y="-1" z="0" />
  <Block id="minecraft:iron_ore" x="-2" y="-2" z="1" />
  <Block id="minecraft:stone" x="-2" y="-1" z="1" />
  <Block id="minecraft:stone" x="-2" y="-2" z="2" />
  <Block id="minecraft:stone" x="-2" y="-1" z="2" />
  <Block id="minecraft:stone" x="-1" y="-2" z="-2" />
  <Block id="minecraft:gold_ore" x="-1" y="-1" z="-2" />
  <Block id="minecraft:stone" x="-1" y="-2" z="-1" />
  <Block id="minecraft:stone" x="-1" y="-1" z="-1" />
  <Block id="minecraft:stone" x="-1" y="-2" z="0" />
  <Block id="minecraft:stone" x="-1" y="-1" z="0" />
  <Block id="minecraft:iron_ore" x="-1" y="-2" z="1" />
  <Block id="minecraft:stone" x="-1" y="-1" z="1" />
  <Block id="minecraft:stone" x="-1" y="-2" z="2" />
  <Block id="minecraft:stone" x="-1" y="-1" z="2" />
  <Block id="minecraft:stone" x="0" y="-2" z="-2" />
  <Block id="minecraft:stone" x="0" y="-1" z="-2" />
  <Block id="minecraft:stone" x="0" y="-2" z="-1" />
  <Block id="minecraft:stone" x="0" y="-1" z="-1" />
  <Block id="minecraft:stone" x="0" y="-2" z="0" />
  <Block id="minecraft:stone" x="0" y="-1" z="0" />
  <Block id="minecraft:stone" x="0" y="-2" z="1" />
  <Block id="minecraft:stone" x="0" y="-1" z="1" />
  <Block id="minecraft:stone" x="0" y="-2" z="2" />
  <Block id="minecraft:stone" x="0" y="-1" z="2" />
  <Block id="minecraft:stone" x="1" y="-2" z="-2" />
  <Block id="minecraft:stone" x="1" y="-1" z="-2" />
  <Block id="minecraft:copper_ore" x="1" y="-2" z="-1" />
  <Block id="minecraft:stone" x="1" y="-1" z="-1" />
  <Block id="minecraft:stone" x="1" y="-2" z="0" />
  <Block id="minecraft:stone" x="1" y="-1" z="0" />
  <Block id="minecraft:stone" x="1" y="-2" z="1" />
  <Block id="minecraft:stone" x="1" y="-1" z="1" />
  <Block id="minecraft:stone" x="1" y="-2" z="2" />
  <Block id="minecraft:stone" x="1" y="-1" z="2" />
  <Block id="minecraft:stone" x="2" y="-2" z="-2" />
  <Block id="minecraft:stone" x="2" y="-1" z="-2" />
  <Block id="minecraft:stone" x="2" y="-2" z="-1" />
  <Block id="minecraft:stone" x="2" y="-1" z="-1" />
  <Block id="minecraft:stone" x="2" y="-2" z="0" />
  <Block id="minecraft:stone" x="2" y="-1" z="0" />
  <Block id="minecraft:stone" x="2" y="-2" z="1" />
  <Block id="minecraft:stone" x="2" y="-1" z="1" />
  <Block id="minecraft:stone" x="2" y="-2" z="2" />
  <Block id="minecraft:coal_ore" x="2" y="-1" z="2" />
  <Block id="surveyor" x="0" y="0" z="0" p:facing="south" p:working="true" />
  <BoxAnnotation min="-2 -2 1" max="0 -1 2" color="#E6C7A8">
    两块铁矿石，由它的模型找到。
  </BoxAnnotation>
</GameScene>

## 勘测

在勘测仪界面旁面板里的**模型**槽放入一个模型：一个<ItemLink id="checkpoint" />，或一张调谐到某台模型服务器的<ItemLink id="relay_card" />。它勘测四周各 16 格、从上方 15 格到下方 48 格：共 4,096 行，每刻读 32 行，每次向模型提 64 个问题。勘测完就从头再来，所以挖走的矿不会留在下一次勘测里。

它的界面显示找到的每种矿石及数量；界面底边显示这次勘测进行到哪里。在它 48 格内手持<ItemLink id="survey_rod" />，就能隔着地面看到它找到的矿石，每种矿石一种颜色的方框。

## 它的模型

模型在<ItemLink id="survey_dataset" />上学这件事，答对勘测测试 90% 的问题即为通过：见[模型与测试](models.md)。找出某个字母最前面的那个，需要回看整行，并知道每个方块在行中的位置。只看得到最后几个字符的模型，或者没有位置信息的单层注意力，都停在一半左右；两块的 Transformer 练大约 500 步就能通过。偷看后文的模型什么也找不到，所以勘测仪拒绝使用它。
