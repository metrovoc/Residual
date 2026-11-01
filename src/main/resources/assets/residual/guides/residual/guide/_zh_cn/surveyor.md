---
navigation:
  title: 勘测仪
  icon: surveyor
  position: 86
item_ids:
  - surveyor
---

# 勘测仪

<ItemLink id="surveyor" />找出它周围地下的矿石，还有旧世界的遗藏：旧世界装满、此后无人打开过的箱子和木桶。它一次读一行十六个方块的地层，每个方块一个字母，并问它的模型这一行里每种矿石或遗藏最前面那个在哪里，和勘测测试的问法一样。每个答案都会对照方块核实：答对的留下，这一行再问一次这一种的下一个；答错的，这一种要等下一次勘测才找得到。

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

[勘测仪的界面，逐项说明](screens/surveyor.md)。

## 它怎样读地层

<residual:Clip name="survey_rod" alt="手持勘测杆时，透过地面显示勘测仪找到的东西" />

它的模型是语言模型，所以地层以文字的形式交给它：每个方块一个字母，一行十六个方块就是十六个字母。地层：`S` 石头，`D` 深板岩，`T` 其他岩石，`A` 空气，`W` 水，`L` 熔岩，`B` 基岩。矿石：`C` 煤，`I` 铁，`U` 铜，`O` 金，`R` 红石，`Z` 青金石，`X` 钻石，`E` 绿宝石；还有 `K`，遗藏。这一行之后是 `>`、要找的那一种和 `:`，模型答出最前面那块的位置，`a` 到 `p`，没有就答 `-`：`SSSTTIIDDDDSSSSS>I:f`。

## 勘测

<residual:Clip name="survey_screen" alt="勘测仪清点模型找到的东西" />

在勘测仪界面旁面板里的**模型**槽放入一个模型：一个<ItemLink id="checkpoint" />，或一张调谐到某台模型服务器的<ItemLink id="relay_card" />。它勘测四周各 16 格、从上方 15 格到下方 48 格：共 4,096 行，每刻读 32 行，每次向模型提 64 个问题。勘测完就从头再来，所以挖走的矿不会留在下一次勘测里。

它的界面以三行三列显示八种矿石和遗藏，以及每种找到了多少；界面底边显示这次勘测进行到哪里。在它 48 格内手持<ItemLink id="survey_rod" />，就能隔着地面看到它找到的东西，每种矿石一种颜色的方框，遗藏是紫色。

## 挖过去

<residual:Clip name="crew" alt="三个智能体挖掉六块铁矿" />

当智能体自己 8 格之内没有某种东西时，它的 `mine` 会去找 48 格内勘测仪找到的那些，并一路挖到埋在地下的目标：矿在下方且向下不比横向近时就往下挖，否则沿较远的方向横着挖，两格高。它只挖穿岩石和泥土，从不挖穿机器、容器或该用斧头砍的东西，能绕的话就从另一个方向绕过去；会放进水或熔岩、或绕不过去的隧道，它会放弃。挖铁就是挖铁矿石：`repeat (mine 8 iron, put)` 会挖到铁所在的地方，再把它带回家。`mine caches` 会挖到一处遗藏，把里面的东西拿走。

在彼此附近挖矿的智能体会结成一队：每个都挑别人没挑过的方块，所以一队人会分散到勘测仪找到的各处，而不是全挤向最近的那块矿。

## 它的模型

模型在<ItemLink id="survey_dataset" />上学这件事，答对勘测测试 90% 的问题即为通过：见[模型与测试](models.md)。找出某个字母最前面的那个，需要回看整行，并知道每个方块在行中的位置。只看得到最后几个字符的模型，或者没有位置信息的单层注意力，都停在一半左右；两块的 Transformer 练大约 1,000 步就能通过。偷看后文的模型什么也找不到，所以勘测仪拒绝使用它。
