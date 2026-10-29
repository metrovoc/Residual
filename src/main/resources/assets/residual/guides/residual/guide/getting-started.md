---
navigation:
  title: Getting Started
  icon: agent
  position: 10
item_ids:
  - tensor_casing
  - weight_crystal
  - field_dataset
---

# Getting Started

A first model fits in a few blocks: a <ItemLink id="model_core" />, an <ItemLink id="embedding" /> and some
<ItemLink id="cable" />. It will learn to farm. Then you give it a body.

## Materials

<ItemLink id="tensor_casing" /> is the shell of every machine; <ItemLink id="weight_crystal" /> holds learned
numbers, so machines with weights need them.

<Row>
  <RecipeFor id="tensor_casing" />
  <RecipeFor id="weight_crystal" />
</Row>

Then craft the Model Core, a stack of cables and a <ItemLink id="field_dataset" />: endless practice at farming.

<Row>
  <RecipeFor id="model_core" />
  <RecipeFor id="cable" />
  <RecipeFor id="field_dataset" />
</Row>

Machines are made in a <ItemLink id="fabricator" />. With no photomask in it, it makes the Embedding Table from a
casing, a <ItemLink id="wafer" /> and a weight crystal.

<Row>
  <RecipeFor id="fabricator" />
  <RecipeFor id="wafer" />
</Row>

## Build it

1. Place the Model Core. Its front turns toward you: tokens come out of it.
2. Lay a cable from the Core's front to a free spot a few blocks away.
3. Place the Embedding Table against the end of that cable. It snaps into the flow: its back reads the cable and
   its front points away.
4. Lay a cable from the table's front back to any other face of the Core: its sides, its top or its back. That is
   where the model's scores go in.
5. Open the table and set **Width** to **V (vocab)**.

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
    The Model Core. Tokens leave through its front; scores come back through its top.
  </BoxAnnotation>
  <BoxAnnotation min="3 0 0" max="4 1 1" color="#3CD6C6">
    The Embedding Table, Width set to V.
  </BoxAnnotation>
</GameScene>

The two cables never touch, so they form two separate networks: one carries tokens to the table, the other carries
the table's scores back.

## Train it

Open the Core and put the Field Dataset in one of its **Data** slots. Switch to **Train** and press **Train**. Two curves
fall: the loss on practice, and the loss on the test, which only uses plots the model never practised on.

Farming takes little thought: harvest a ripe plot, plant a bare one, leave anything else to grow. An Embedding Table
set to V scores the next character from the one before it, and the character before each decision is the plot, so
this one table can learn all of farming. When **Reacting** on the **Tests** tab passes 98%, the model is ready.

## Save it

Craft a <ItemLink id="checkpoint" />, put it in the Core's checkpoint slot and press **Save to checkpoint**. The Core
tests the model on every benchmark and keeps it, scores and all, in the crystal. The machines can go on training; the
checkpoint keeps this moment.

<RecipeFor id="checkpoint" />

## Give it a body

Craft an <ItemLink id="agent" /> and place it beside some farmland with a chest nearby: where it stands becomes
its home. Open it, put the checkpoint in its **Model** slot, hand it a few seeds and choose **Farm**.

<RecipeFor id="agent" />

It walks the farmland around home. At every plot it asks its model what to do, in exactly the format of the test, and
does what the model answers. Its screen shows what it asked and what came back. Walking, reaping and carrying are the
body's work; every decision is your model's.

## Further

Other datasets teach other jobs, and better models need machines built from designs: wire them yourself on a
[Bench](bench.md), or go looking for the old ones in the [ruins](ruins.md).

- Look at any machine to see its name, its output shape (or what is wrong with it) and what the face you are looking
  at does.
- Sneak-use a machine's face to point its output there.
- Training runs on its own threads. Place <ItemLink id="accelerator" /> blocks against the Core to give it more, and
  keep them cool.
