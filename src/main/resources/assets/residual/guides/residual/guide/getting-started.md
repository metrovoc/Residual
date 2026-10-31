---
navigation:
  title: Getting Started
  icon: forecaster
  position: 10
item_ids:
  - tensor_casing
  - weight_crystal
---

# Getting Started

A first model fits in a few blocks: a <ItemLink id="model_core" />, an <ItemLink id="embedding" /> and some
<ItemLink id="cable" />. It learns what the blocks around you do next. A <ItemLink id="forecaster" /> puts that to
work: once its forecasts hold, your furnaces run four times as fast.

## Materials

<ItemLink id="tensor_casing" /> is the shell of every machine; <ItemLink id="weight_crystal" /> holds learned
numbers, so machines with weights need them.

<Row>
  <RecipeFor id="tensor_casing" />
  <RecipeFor id="weight_crystal" />
</Row>

Then craft the Model Core, a stack of cables and a <ItemLink id="forecast_dataset" />: endless lines of crops growing
and machines at work, to learn from.

<Row>
  <RecipeFor id="model_core" />
  <RecipeFor id="cable" />
  <RecipeFor id="forecast_dataset" />
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

Open the Core and put the Forecast Dataset in one of its **Data** slots. Switch to **Train** and press **Train**. Two
curves fall: the loss on practice, and the loss on the test, which only uses lines the model never practised on.

Each line in the dataset is one block, looked at again and again. A furnace's line is how far it is through the item it
is making, from `a` just begun to `h` nearly done, then round again: `abcdefghabcdefgh`. A crop's line is its age at
every look, from `0` to `7`. An Embedding Table set to V scores the next look from the one before it, and for a machine
the look before all but settles the next. When **Forecasting** on the **Tests** tab gets below 0.21, the model is ready.

## Save it

Craft a <ItemLink id="checkpoint" />, put it in the Core's checkpoint slot and press **Save to checkpoint**. The Core
tests the model on every benchmark and keeps it, scores and all, in the crystal. The machines can go on training; the
checkpoint keeps this moment.

<RecipeFor id="checkpoint" />

## Run your furnaces ahead

Craft a Forecaster and place it among your furnaces, smokers or blast furnaces: within four blocks, on its own level or
one above or below. Put the checkpoint in its **Model** slot, and keep the furnaces fed and fuelled.

<RecipeFor id="forecaster" />

<GameScene zoom="4" interactive={true}>
  <Block id="forecaster" x="2" p:facing="south" p:working="true" />
  <Block id="minecraft:furnace" p:facing="south" p:lit="true" />
  <Block id="minecraft:furnace" x="1" p:facing="south" p:lit="true" />
  <Block id="minecraft:smoker" x="3" p:facing="south" p:lit="true" />
  <Block id="minecraft:blast_furnace" x="4" p:facing="south" p:lit="true" />
  <IsometricCamera yaw="200" pitch="30" />
  <BoxAnnotation min="2 0 0" max="3 1 1" color="#3CD6C6">
    The Forecaster, with a model that passes Forecasting.
  </BoxAnnotation>
</GameScene>

The Forecaster looks at each machine eight times an item and writes down how far it got. Once the machines have made
about a dozen items at their own pace, it checks the model against what they did. If the progress the model expects is
within 11% of the progress there was, it runs them four looks for every one: the model draws each machine's next looks,
and the machine works through them. Within 25%, two. [The Forecaster](forecaster.md) tells the rest, crops included.

## Further

- Better models need machines built from designs: wire them yourself on a [Bench](bench.md), or go looking for the old
  ones in the [ruins](ruins.md).
- [Agents](agents.md) run programs you click together: they mine, carry, build and sort, and models fill in where
  learning beats a rule.
- Look at any machine to see its name, its output shape (or what is wrong with it) and what the face you are looking
  at does.
- Sneak-use a machine's face to point its output there.
- Training runs on its own threads. Place <ItemLink id="accelerator" /> blocks against the Core to give it more, and
  keep them cool.
