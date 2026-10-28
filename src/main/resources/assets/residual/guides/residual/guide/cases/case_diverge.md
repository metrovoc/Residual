---
navigation:
  title: Exploding Loss
  icon: tensor_probe
  parent: cases/index.md
  position: 2
---

# Exploding Loss

*Case 2, after [More Context](../lessons/context.md).*

The machine is sound, yet training blows up within a few steps. Nothing in the machine needs to change.

## Symptoms

- Within ten steps the loss is several times worse than chance, which is 3.30 here.
- The Core soon stops the run as diverged.

## Hints

### Hint 1

Train on Watch speed and look at the very first steps. Does the loss fall at all before it climbs?

### Hint 2

The machine is the one from More Context, so the problem is in how it is trained. Compare the settings on the Train tab
with the ones that lesson used.

### Hint 3

AdamW moves every weight by roughly the learning rate on every step, however large or small its gradient is. That is
also why turning on gradient clipping does not rescue this run. How far should a weight move in one step, when most
weights start out smaller than one?

![the AdamW update](/formulas/adamw.png)

## The broken build

<GameScene zoom="1.3" interactive={true}>
  <Block id="model_core" p:facing="east" />
  <Block id="embedding" x="3" p:facing="east" />
  <Block id="shift" x="6" p:facing="east" />
  <Block id="shift" x="9" p:facing="east" />
  <Block id="concat" x="12" p:facing="east" />
  <Block id="concat" x="15" p:facing="east" />
  <Block id="linear" x="18" p:facing="east" />
  <Block id="activation" x="21" p:facing="east" />
  <Block id="linear" x="24" p:facing="east" />
  <Block id="light_blue_cable" x="2" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="5" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="8" p:east="true" p:up="true" />
  <Block id="light_blue_cable" x="11" p:east="true" p:up="true" />
  <Block id="light_blue_cable" x="4" p:east="true" p:west="true" p:up="true" />
  <Block id="light_blue_cable" x="4" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="4" y="2" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="4" y="2" z="1" p:east="true" p:north="true" />
  <Block id="light_blue_cable" x="5" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="6" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="7" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="8" y="2" z="1" p:east="true" p:west="true" p:north="true" />
  <Block id="light_blue_cable" x="9" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="10" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="11" y="2" z="1" p:west="true" p:north="true" />
  <Block id="light_blue_cable" x="8" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="8" y="2" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="11" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="11" y="2" p:south="true" p:down="true" />
  <Block id="orange_cable" x="12" y="1" p:up="true" p:down="true" />
  <Block id="orange_cable" x="10" p:west="true" p:up="true" />
  <Block id="orange_cable" x="10" y="1" p:up="true" p:down="true" />
  <Block id="orange_cable" x="10" y="2" p:up="true" p:down="true" />
  <Block id="orange_cable" x="10" y="3" p:south="true" p:down="true" />
  <Block id="orange_cable" x="10" y="3" z="1" p:east="true" p:north="true" />
  <Block id="orange_cable" x="11" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="12" y="3" z="1" p:west="true" p:north="true" />
  <Block id="orange_cable" x="12" y="2" p:up="true" p:down="true" />
  <Block id="orange_cable" x="12" y="3" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="14" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="13" p:east="true" p:west="true" />
  <Block id="lime_cable" x="15" y="1" p:up="true" p:down="true" />
  <Block id="lime_cable" x="7" p:west="true" p:up="true" />
  <Block id="lime_cable" x="7" y="1" p:up="true" p:down="true" />
  <Block id="lime_cable" x="7" y="2" p:up="true" p:down="true" />
  <Block id="lime_cable" x="7" y="3" p:up="true" p:down="true" />
  <Block id="lime_cable" x="7" y="4" p:south="true" p:down="true" />
  <Block id="lime_cable" x="7" y="4" z="1" p:east="true" p:north="true" />
  <Block id="lime_cable" x="8" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="9" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="10" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="11" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="12" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="13" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="14" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="15" y="4" z="1" p:west="true" p:north="true" />
  <Block id="lime_cable" x="15" y="2" p:up="true" p:down="true" />
  <Block id="lime_cable" x="15" y="3" p:up="true" p:down="true" />
  <Block id="lime_cable" x="15" y="4" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="17" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="16" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="20" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="19" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="23" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="22" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="25" p:west="true" p:up="true" />
  <Block id="light_blue_cable" x="25" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="25" y="2" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="25" y="3" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="25" y="4" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="25" y="5" p:south="true" p:down="true" />
  <Block id="light_blue_cable" y="5" z="1" p:east="true" p:north="true" />
  <Block id="light_blue_cable" x="1" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="2" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="3" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="4" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="5" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="6" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="7" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="8" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="9" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="10" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="11" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="12" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="13" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="14" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="15" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="16" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="17" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="18" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="19" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="20" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="21" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="22" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="23" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="24" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="25" y="5" z="1" p:west="true" p:north="true" />
  <Block id="light_blue_cable" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" y="2" p:up="true" p:down="true" />
  <Block id="light_blue_cable" y="3" p:up="true" p:down="true" />
  <Block id="light_blue_cable" y="4" p:up="true" p:down="true" />
  <Block id="light_blue_cable" y="5" p:south="true" p:down="true" />
  <IsometricCamera yaw="200" pitch="30" />
  <DiamondAnnotation pos="0.5 1 0.5" color="#FFB547">
    Model Core: tokens leave through its front, scores come back in through its top.
  </DiamondAnnotation>
  <DiamondAnnotation pos="3.5 1 0.5" color="#3CD6C6">
    embed: Embedding Table, Width 24
  </DiamondAnnotation>
  <DiamondAnnotation pos="6.5 1 0.5" color="#3CD6C6">
    prev2: Delay Line, Steps 2
  </DiamondAnnotation>
  <DiamondAnnotation pos="9.5 1 0.5" color="#3CD6C6">
    prev1: Delay Line
  </DiamondAnnotation>
  <DiamondAnnotation pos="12.5 1 0.5" color="#3CD6C6">
    pair: Concatenator
  </DiamondAnnotation>
  <DiamondAnnotation pos="15.5 1 0.5" color="#3CD6C6">
    triple: Concatenator
  </DiamondAnnotation>
  <DiamondAnnotation pos="18.5 1 0.5" color="#3CD6C6">
    hidden: Linear Layer, Outputs 128
  </DiamondAnnotation>
  <DiamondAnnotation pos="21.5 1 0.5" color="#3CD6C6">
    act: Activation, Function tanh
  </DiamondAnnotation>
  <DiamondAnnotation pos="24.5 1 0.5" color="#3CD6C6">
    logits: Linear Layer, Outputs V
  </DiamondAnnotation>
</GameScene>

## In real projects

The learning rate is the first setting to tune and the usual reason a run blows up. Large training runs warm it up
slowly, decay it over time and clip the gradient, and they still watch for loss spikes and roll back to a checkpoint
when one hits. Clipping bounds the gradient, but an adaptive optimizer like AdamW divides each gradient by a running
estimate of its size anyway, so the size of each step is set by the learning rate.
