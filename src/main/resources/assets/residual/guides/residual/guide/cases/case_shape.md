---
navigation:
  title: The Wrong Shape
  icon: tensor_probe
  parent: cases/index.md
  position: 1
---

# The Wrong Shape

*Case 1, after [Counting Pairs](../lessons/counting.md).*

Someone built this model in a hurry and it won't start.

## Symptoms

- **Train** does nothing, and the status line reports a problem.
- **Check** finds the same problem, every time.

## Hints

### Hint 1

Read the message on the Inspect tab slowly, all the way to the end. What does the Core expect, and what is it getting?

### Hint 2

The Core expects one score for every possible next letter, at every position. Open the machines and read their output
shapes. Which one decides the last size of what reaches the Core?

### Hint 3

A Linear Layer's last size is whatever its Outputs setting says, and one of the choices is not a number.

## The broken build

<GameScene zoom="3" interactive={true}>
  <Block id="model_core" p:facing="east" />
  <Block id="embedding" x="3" p:facing="east" />
  <Block id="linear" x="6" p:facing="east" />
  <Block id="light_blue_cable" x="2" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="5" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="4" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="7" p:west="true" p:up="true" />
  <Block id="light_blue_cable" x="7" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="7" y="2" p:south="true" p:down="true" />
  <Block id="light_blue_cable" y="2" z="1" p:east="true" p:north="true" />
  <Block id="light_blue_cable" x="1" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="2" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="3" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="4" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="5" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="6" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="7" y="2" z="1" p:west="true" p:north="true" />
  <Block id="light_blue_cable" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" y="2" p:south="true" p:down="true" />
  <IsometricCamera yaw="200" pitch="30" />
  <DiamondAnnotation pos="0.5 1 0.5" color="#FFB547">
    Model Core: tokens leave through its front, scores come back in through its top.
  </DiamondAnnotation>
  <DiamondAnnotation pos="3.5 1 0.5" color="#3CD6C6">
    embed: Embedding Table
  </DiamondAnnotation>
  <DiamondAnnotation pos="6.5 1 0.5" color="#3CD6C6">
    logits: Linear Layer
  </DiamondAnnotation>
</GameScene>

## In real projects

Shape errors are the most common bug in model code and the cheapest to find, because the program stops. Write the
expected shape next to each line, assert it where modules meet, and read the error message before the code. The shape
bugs to fear are the ones that don't stop the program: two shapes that broadcast into a result nobody meant.
