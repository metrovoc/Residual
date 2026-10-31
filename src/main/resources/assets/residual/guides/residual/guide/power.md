---
navigation:
  title: Power
  icon: combustion_generator
  position: 88
item_ids:
  - combustion_generator
  - power_cable
  - energy_cell
  - smelter
---

# Power

Power moves in Forge Energy (FE) along <ItemLink id="power_cable" />s: from the blocks that make it to the blocks
that store or use it, and to any other mod's block that takes FE.

<GameScene zoom="4" interactive={true}>
  <Block id="combustion_generator" p:facing="south" p:working="true" />
  <Block id="power_cable" x="1" p:east="true" p:west="true" />
  <Block id="power_cable" x="2" p:east="true" p:west="true" p:south="true" />
  <Block id="energy_cell" x="3" p:charge="3" />
  <Block id="smelter" x="2" z="1" p:facing="south" p:working="true" />
  <IsometricCamera yaw="200" pitch="30" />
  <BoxAnnotation min="0 0 0" max="1 1 1" color="#FF8A5C">
    A Combustion Generator, burning.
  </BoxAnnotation>
  <BoxAnnotation min="3 0 0" max="4 1 1" color="#FF8A5C">
    An Energy Cell, three quarters full.
  </BoxAnnotation>
  <BoxAnnotation min="2 0 1" max="3 1 2" color="#FFB547">
    A Smelter, working on its power.
  </BoxAnnotation>
  <BoxAnnotation min="1 0 0" max="3 1 1" color="#3CD6C6">
    Power Cables join them.
  </BoxAnnotation>
</GameScene>

## Making it

The <ItemLink id="combustion_generator" /> burns what a furnace burns and makes 80 FE a tick while it burns. A fuel
lasts half as long as in a furnace, so a coal makes 64,000 FE. It burns only while it has room for what it makes, and
holds 32,000 FE of its own.

<RecipeFor id="combustion_generator" />

## Moving and storing it

Power Cables join generators, cells and machines into one network. Each tick the machines take what they need from
the generators, then from the cells; whatever the generators make beyond that charges the cells. An
<ItemLink id="energy_cell" /> holds 1,000,000 FE and moves up to 2,000 FE a tick each way. Its faces fill in quarters
as it charges, and a comparator beside it reads how full it is.

<Row>
  <RecipeFor id="power_cable" />
  <RecipeFor id="energy_cell" />
</Row>

## The Smelter

The <ItemLink id="smelter" /> smelts anything a furnace does on 80 FE a tick instead of fuel, an item every five
seconds. From an ore or a raw ore it makes two of what a furnace makes from one. The Fabricator makes it from a
transformer photomask.

Hoppers and Ports feed the generator and the Smelter from any side and take the Smelter's ingots out. Other mods'
cables can draw power from generators and cells, and give it to cells and the Smelter.
