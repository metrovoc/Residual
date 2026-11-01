---
navigation:
  title: The Surveyor
  icon: surveyor
  position: 86
item_ids:
  - surveyor
---

# The Surveyor

The <ItemLink id="surveyor" /> finds the ores in the ground around it. It reads the ground a row of sixteen blocks at a
time, a letter for each block, and asks its model where the first of each ore in the row lies, just as the Survey test
does. Every answer is checked against the block: a right one is kept, and the row is asked again for the next of that
ore; a wrong one leaves that ore unfound until the next survey.

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
    Two blocks of iron ore, found by its model.
  </BoxAnnotation>
</GameScene>

## Surveying

Put a model in the Surveyor's **Model** slot, in the panel beside its screen: a <ItemLink id="checkpoint" />, or a
<ItemLink id="relay_card" /> tuned to a Model Server. It surveys 16 blocks each way and from 15 above it to 48 below:
4,096 rows, 32 read a tick and 64 questions put to the model at once. Then it starts over, so what is mined drops out of
the next survey.

Its screen shows each ore it found and how many; along the screen's foot, how far the survey has gone. Hold a
<ItemLink id="survey_rod" /> within 48 blocks of it to see what it found through the ground, a box in each ore's colour.

## Digging for it

An agent's `mine` goes to what a Surveyor within 48 blocks found when nothing of the kind lies within its own reach of
8, and digs its way to anything buried: down where the ore lies at least as far below as aside, otherwise along the
longer way, two blocks high. It gives up a tunnel that would let in water or lava, or that meets a block it cannot
break. To mine iron is to mine iron ore: `repeat (mine 8 iron, put)` digs where the iron is and brings it home.

## Its model

A model learns this on the <ItemLink id="survey_dataset" />, and passes the Survey test by answering 90% of its
questions right: see [Models and Tests](models.md). Finding the first of a letter takes looking back over the row and
knowing where each block stands in it. A model that sees only the last few characters, or a single layer of attention
without positions, stays near half; a two-block transformer passes after about 500 steps. A model that reads ahead finds
nothing, so the Surveyor refuses it.
