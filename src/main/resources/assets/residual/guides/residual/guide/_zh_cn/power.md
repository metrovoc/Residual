---
navigation:
  title: 电力
  icon: combustion_generator
  position: 88
item_ids:
  - combustion_generator
  - power_cable
  - energy_cell
  - smelter
---

# 电力

电力以 Forge Energy（FE）为单位，沿着<ItemLink id="power_cable" />流动：从产生电力的方块流向储存或使用电力的方块，也流向其他模组中任何接收 FE 的方块。

<GameScene zoom="4" interactive={true}>
  <Block id="combustion_generator" p:facing="south" p:working="true" />
  <Block id="power_cable" x="1" p:east="true" p:west="true" />
  <Block id="power_cable" x="2" p:east="true" p:west="true" p:south="true" />
  <Block id="energy_cell" x="3" p:charge="3" />
  <Block id="smelter" x="2" z="1" p:facing="south" p:working="true" />
  <IsometricCamera yaw="200" pitch="30" />
  <BoxAnnotation min="0 0 0" max="1 1 1" color="#FF8A5C">
    一台燃烧发电机，正在燃烧。
  </BoxAnnotation>
  <BoxAnnotation min="3 0 0" max="4 1 1" color="#FF8A5C">
    一个储能单元，充到四分之三。
  </BoxAnnotation>
  <BoxAnnotation min="2 0 1" max="3 1 2" color="#FFB547">
    一台冶炼炉，用它的电力工作。
  </BoxAnnotation>
  <BoxAnnotation min="1 0 0" max="3 1 1" color="#3CD6C6">
    电缆把它们连起来。
  </BoxAnnotation>
</GameScene>

## 发电

<ItemLink id="combustion_generator" />烧熔炉能烧的东西，燃烧时每刻发电 80 FE。燃料在这里烧的时间是熔炉里的一半，所以一块煤发电 64,000 FE。它只在还装得下所发电力时燃烧，自身能存 32,000 FE。

<RecipeFor id="combustion_generator" />

## 输送与储存

电缆把发电机、储能单元和机器连成一个电网。每一刻，机器先从发电机、再从储能单元取得所需的电力；发电机多发的电力给储能单元充电。<ItemLink id="energy_cell" />能存 1,000,000 FE，每刻进出各最多 2,000 FE。它充电时各面按四分之一逐格亮起，旁边的比较器能读出它有多满。

<Row>
  <RecipeFor id="power_cable" />
  <RecipeFor id="energy_cell" />
</Row>

## 冶炼炉

<ItemLink id="smelter" />能冶炼熔炉能冶炼的一切，用每刻 80 FE 的电力代替燃料，每五秒炼好一件。一份矿石或粗矿，它炼出的是熔炉的两倍。制造机用 Transformer 光掩模制造它。

漏斗和端口可以从任何一面给发电机和冶炼炉送料，并把冶炼炉炼好的锭取走。其他模组的电缆可以从发电机和储能单元取电，也可以给储能单元和冶炼炉送电。
