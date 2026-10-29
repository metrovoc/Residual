---
navigation:
  title: Machines and Cables
  icon: cable
  position: 100
item_ids:
  - cable
  - white_cable
  - orange_cable
  - magenta_cable
  - light_blue_cable
  - yellow_cable
  - lime_cable
  - pink_cable
  - gray_cable
  - light_gray_cable
  - cyan_cable
  - purple_cable
  - blue_cable
  - brown_cable
  - green_cable
  - red_cable
  - black_cable
  - tensor_probe
---

# Machines and Cables

A model is a graph of operations, and in Residual you build that graph out of blocks. Values flow out of one machine
and into the next through cables, from the <ItemLink id="model_core" />'s tokens to the scores it takes back.

## Ports

Every machine computes one output from its inputs, and each face of the block has a job:

- **Front:** the output.
- **Back:** the main input.
- **Every other face:** the side input, on machines that take two inputs, such as the
  <ItemLink id="add" /> or the <ItemLink id="matmul" />. One-input machines leave their sides unused, and the
  <ItemLink id="position" /> has no input at all.

The Model Core sends tokens out of its front and takes the finished scores in through any other face.

<GameScene zoom="5" interactive={true}>
  <Block id="add" p:facing="east" />
  <IsometricCamera yaw="200" pitch="30" />
  <DiamondAnnotation pos="1 0.5 0.5" color="#3CD6C6">
    Front: the output
  </DiamondAnnotation>
  <DiamondAnnotation pos="0 0.5 0.5" color="#FFB547">
    Back: the main input
  </DiamondAnnotation>
  <DiamondAnnotation pos="0.5 0.5 1" color="#FFFFFF">
    Side: the side input (any of the four other faces)
  </DiamondAnnotation>
</GameScene>

Look at a machine to see which port the face under your crosshair is. A machine placed against another machine's
output takes that output as its input; placed against an input, it feeds it; placed against a cable or a
[residual stream](stream.md), it reads it. Anywhere else its output points away from you. Sneak-use a face to turn the output to that face; sneak-using
the output face itself turns the machine around.

## Networks

Cables that touch form a **network**, and a network carries exactly one value. So every network needs exactly one
source, a machine's front or the Core's front, and any number of inputs may read it. Two sources on one network is
an error, and so is an input on a network with no source.

## Colors

<Row>
  <ItemImage id="cable" />
  <ItemImage id="red_cable" />
  <ItemImage id="lime_cable" />
  <ItemImage id="light_blue_cable" />
  <ItemImage id="orange_cable" />
</Row>

Colored cables only join cables of their own color, while plain cables join any color. That lets two networks run
side by side without mixing. Dye eight cables at a time; a water bucket washes the color out again.

<Row>
  <RecipeFor id="light_blue_cable" />
  <Recipe id="cable_from_colored" />
</Row>

## Tracing a value

With a <ItemLink id="tensor_probe" /> in hand, looking at a cable tells you which machine drives it and how many
inputs read it. Use the probe on a cable to list every port on its network in chat.
