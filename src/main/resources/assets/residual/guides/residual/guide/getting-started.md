---
navigation:
  title: Getting Started
  icon: embedding
  position: 10
item_ids:
  - tensor_casing
  - weight_crystal
---

# Getting Started

Your first model fits in a few blocks: a <ItemLink id="model_core" />, an <ItemLink id="embedding" /> and some
<ItemLink id="cable" />. It will learn which letter tends to follow which, from a list of English words.

## Materials

Everything starts from two components. <ItemLink id="tensor_casing" /> is the shell of every machine;
<ItemLink id="weight_crystal" /> holds learned numbers, so machines with weights need them.

<Row>
  <RecipeFor id="tensor_casing" />
  <RecipeFor id="weight_crystal" />
</Row>

Then craft the Model Core, an Embedding Table and a stack of cables.

<Row>
  <RecipeFor id="model_core" />
  <RecipeFor id="embedding" />
  <RecipeFor id="cable" />
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
  <BlockAnnotation color="#FFB547">
    The Model Core. Tokens leave through its front; scores come back through its top.
  </BlockAnnotation>
  <BlockAnnotation x="3" color="#3CD6C6">
    The Embedding Table, Width set to V.
  </BlockAnnotation>
</GameScene>

The two cables never touch, so they form two separate networks: one carries tokens to the table, the other carries
the table's scores back.

## Train it

Open the Core. The **Lessons** tab lists the whole course; **Counting Pairs** is loaded. Switch to **Train** and press
**Train**. Two curves fall: the training loss and the validation loss. When the validation loss reaches the goal, the
lesson is done and you receive an <ItemLink id="insight_counting" />, which unlocks the machines of the next lesson.

Then try the **Sample** tab: the model writes text one letter at a time. It will not make sense yet. Finding out why,
and doing better, is the rest of the course. The [Counting Pairs](lessons/counting.md) page explains what this model
learned.

## Tips

- Look at any machine to see its name, its output shape (or what is wrong with it) and what the face you are looking
  at does.
- Sneak-use a machine's face to point its output there.
- Training runs on its own threads. Place <ItemLink id="accelerator" /> blocks against the Core to give it more.
