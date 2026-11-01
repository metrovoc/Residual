---
navigation:
  title: 存储
  icon: drive_rack
  position: 18
item_ids:
  - storage_drive
  - drive_rack
  - storage_link
  - storage_terminal
  - port
  - assembler
---

# 存储

<ItemLink id="drive_rack" />里的存储驱动器，用<ItemLink id="storage_link" />连到<ItemLink id="storage_terminal" />和<ItemLink id="port" />上，就组成一个存储网络。它能存任意多种物品，在终端上找出其中任何一种，还会自己把东西搬进搬出箱子和机器。它里面什么都不需要供能，也没有什么会停摆。

<GameScene zoom="4" interactive={true}>
  <Block id="drive_rack" p:facing="north" />
  <Block id="storage_link" x="1" p:east="true" p:west="true" />
  <Block id="storage_link" x="2" p:east="true" p:west="true" p:north="true" />
  <Block id="storage_link" x="3" p:west="true" p:north="true" />
  <Block id="storage_terminal" x="2" z="-1" p:facing="north" p:lit="true" />
  <Block id="port" x="3" z="-1" p:facing="north" p:mode="import" p:active="true" />
  <Block id="minecraft:chest" x="3" z="-2" p:facing="south" />
  <IsometricCamera yaw="200" pitch="30" />
  <BoxAnnotation min="0 0 0" max="1 1 1" color="#3CD6C6">
    驱动器机架，每个驱动器旁有一盏灯。
  </BoxAnnotation>
  <BoxAnnotation min="2 0 -1" max="3 1 0" color="#FFB547">
    存储终端能找到网络存着的任何东西。
  </BoxAnnotation>
  <BoxAnnotation min="3 0 -1" max="4 1 0" color="#3CD6C6">
    一个端口，正在导入它面前的箱子。
  </BoxAnnotation>
</GameScene>

## 驱动器与机架

一个<ItemLink id="storage_drive" />能存 262,144 件物品，种类随你多少：多一种东西，除了这些东西本身，不占别的地方。驱动器存的东西跟着驱动器走，挪到别的机架上也一样都在，它的提示文字会说它有多满。

<Row>
  <RecipeFor id="storage_drive" />
  <RecipeFor id="drive_rack" />
</Row>

驱动器机架能放八个驱动器。每个槽位旁有一盏灯显示它的驱动器有多满：空着时暗，还有地方时青色，满了四分之三时琥珀色，满了时红色。

## 链路

存储链路把机架、终端和端口连成一个网络。存储方块只要相互挨着就会连上，哪一面都行，端口的正面除外。没有频道，一个网络能连多少方块也没有上限。存储链路不是给模型接线用的信号线缆：两者从不相连。

<RecipeFor id="storage_link" />

## 存储终端

<RecipeFor id="storage_terminal" />

终端显示网络存着的一切，以及每种有多少；网络一有地方存东西，它就亮起来。

- 点击一种东西拿一组，右键拿一半，按住 Shift 点击把一组放进物品栏。
- 拿着一组东西点击网格就把它放进去，右键只放一个。在物品栏里按住 Shift 点击一组，就把它收起来。
- 搜索框旁的按钮按名称（名）、数量（#）或模组（@）排序；按住 Shift 点击可反向。

搜索框按名称找东西，每个词都要有。其他条件在前面加个符号：

| 搜索 | 找到 |
|------|------|
| `ingot` | 名称里有“ingot”的 |
| `@minecraft` | 那个模组的东西 |
| `#ingots` | 标签里有“ingots”的 |
| `$hunger` | 提示文字里有“hunger”的 |
| `*copper` | ID 里有“copper”的 |
| `-iron` | 除了含“iron”的一切 |
| `iron \| gold` | 二者之一 |
| `(log \| planks) -oak` | 分组 |
| `>64`、`<10`、`=1`、`>=1k` | 存着多少（k 表示千，m 表示百万） |

`@minecraft ingot -iron` 会找出除铁以外的所有锭，`>1k` 会找出存量超过一千的一切。

## 端口

端口把正面贴在一个容器上（箱子、木桶、熔炉或别的机器），在它和网络之间搬东西，每刻 64 件：每秒 1,280 件。贴着方块放下时，它就朝向那个方块；潜行时对它的某一面使用，可让它的正面转到那一面。它的侧面显示它在做什么，搬东西时会亮。

<RecipeFor id="port" />

- **导入**把容器里的一切拿进网络；有过滤时，只拿那几种。放在熔炉下面，它拿的是熔炉的产物。
- **导出**把过滤列出的几种东西放进容器，只要网络里有、容器里放得下。
- **存量**让容器里，过滤列出的每种东西都保持一个数：少了从网络补上，多了收回网络。
- **窗口**把容器向网络敞开，网络像用驱动器一样往里存、从里取。优先级高的窗口先存满，过滤限制能放进去的东西。

设置过滤：拿着一组东西点击它的一个槽位，或在物品栏里按住 Shift 点击这组东西；空手点击槽位则清除。界面会显示端口上一秒搬了多少件。

端口也能按东西是什么来过滤。在它界面旁的槽位里放一个过滤模型：一个<ItemLink id="checkpoint" />，或一张调谐到某台模型服务器的<ItemLink id="relay_card" />，其模型通过分拣测试（[模型与测试](models.md)）。这样，放着改名为 Food、Plants、Wood、Stone、Ores 或 Tools 的东西的过滤槽，就代表那一整类：输入时，端口收下模型认为属于这一类的每样东西；输出时，它把网络里这一类的东西全部送出。每样东西只按名字问模型一次，问法和分拣测试一样；模型拿不准的东西算作其他。囤货只按具体的东西，不按类。

## 装配机

连在网络上的<ItemLink id="assembler" />用网络存着的东西做一个配方，再把做出来的放回去。像在工作台上一样，在它的网格里摆出配方：拿着一组东西点击一格，把它这一种摆在那里；空手点击则清除。网格里放的是样子，不是物品。配方做出的东西显示在旁边；界面开着时装配机先停着，所以摆到一半的配方不会被做出来。

告诉它网络里要保持多少成品。网络里一少于这个数，它就开始做，每刻最多做四次，只要网络里有材料；配方剩下的东西，比如空桶，会回到网络里。一台装配机能让另一台不断料：一台保持木棍数量的装配机，能让做火把的那台只要有煤就一直做下去。

<RecipeFor id="assembler" />

## 请求

网络里的装配机能做的东西，也会显示在终端里：一个都没有时，数量的位置写着**合成**；有一些时，格子角上有一个青色标记。点击它，或者对有存货的用中键或 Ctrl+点击，就能请求一个数量。终端会随着你输入做出规划：一棵树，列出要用到的一切，每一步写着有多少从存储里取、要做几次合成补足其余（×），以及缺多少（−）。

开始之后，网络里的装配机就按规划做，任务可以同时进行任意多个；摆着同一配方的装配机会分担它的合成。任务途中做出的东西，它会自己留着，直到任务完成，不让别的东西先拿走。缺东西也能开始：任务会等着，缺的一进网络就接着做。完成时，它做出的一切都放进网络，无论你在哪里都会收到消息。

搜索框旁边的**任务**列出网络上正在进行的任务、各自做到哪儿、在等什么；点 × 停下一个任务，它已经做出的东西会回到网络里。
