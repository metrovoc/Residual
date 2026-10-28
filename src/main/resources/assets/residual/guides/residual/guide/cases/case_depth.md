---
navigation:
  title: Too Deep to Learn
  icon: tensor_probe
  parent: cases/index.md
  position: 5
---

# Too Deep to Learn

*Case 5, after [Lost in Order](case_order.md).*

Six layers deep and barely learning. Use the Inspect tab to follow the gradient down the stack, then give it an easier
way through.

## Symptoms

- It sees the same three letters as the More Context model and has more weights, yet it learns far more slowly. When
  its budget runs out it is still above the goal of 2.45.
- Training is stable: no spikes, no divergence. It is just slow.

## Hints

### Hint 1

Reset the weights, train for a few dozen steps and stop. Then open the Inspect tab and read the Gradient column from the
top of the stack to the bottom. Which layers are learning?

### Hint 2

The gradient reaching the first layer is a product, with one factor for every layer above it:

![the chain rule](/formulas/chain.png)

And a sigmoid's slope is never more than a quarter:

![the slope of the sigmoid](/formulas/sigmoid_slope.png)

### Hint 3

There are two ways to make the trip easier: change what each layer multiplies the gradient by, or give the gradient a
path around each layer.

![a residual connection's gradient](/formulas/residual_grad.png)

## The broken build

<GameScene zoom="0.6" interactive={true}>
  <Block id="model_core" p:facing="east" />
  <Block id="embedding" x="3" p:facing="east" />
  <Block id="shift" x="6" p:facing="east" />
  <Block id="shift" x="9" p:facing="east" />
  <Block id="concat" x="12" p:facing="east" />
  <Block id="concat" x="15" p:facing="east" />
  <Block id="linear" x="18" p:facing="east" />
  <Block id="activation" x="21" p:facing="east" />
  <Block id="linear" x="24" p:facing="east" />
  <Block id="activation" x="27" p:facing="east" />
  <Block id="linear" x="30" p:facing="east" />
  <Block id="activation" x="33" p:facing="east" />
  <Block id="linear" x="36" p:facing="east" />
  <Block id="activation" x="39" p:facing="east" />
  <Block id="linear" x="42" p:facing="east" />
  <Block id="activation" x="45" p:facing="east" />
  <Block id="linear" x="48" p:facing="east" />
  <Block id="activation" x="51" p:facing="east" />
  <Block id="linear" x="54" p:facing="east" />
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
  <Block id="light_blue_cable" x="26" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="25" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="29" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="28" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="32" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="31" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="35" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="34" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="38" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="37" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="41" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="40" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="44" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="43" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="47" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="46" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="50" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="49" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="53" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="52" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="55" p:west="true" p:up="true" />
  <Block id="light_blue_cable" x="55" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="55" y="2" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="55" y="3" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="55" y="4" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="55" y="5" p:south="true" p:down="true" />
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
  <Block id="light_blue_cable" x="25" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="26" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="27" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="28" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="29" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="30" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="31" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="32" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="33" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="34" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="35" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="36" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="37" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="38" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="39" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="40" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="41" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="42" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="43" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="44" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="45" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="46" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="47" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="48" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="49" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="50" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="51" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="52" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="53" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="54" y="5" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="55" y="5" z="1" p:west="true" p:north="true" />
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
    embed: Embedding Table, Width 16
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
    context: Concatenator
  </DiamondAnnotation>
  <DiamondAnnotation pos="18.5 1 0.5" color="#3CD6C6">
    layer1: Linear Layer, Outputs 48
  </DiamondAnnotation>
  <DiamondAnnotation pos="21.5 1 0.5" color="#3CD6C6">
    squash1: Activation, Function sigmoid
  </DiamondAnnotation>
  <DiamondAnnotation pos="24.5 1 0.5" color="#3CD6C6">
    layer2: Linear Layer, Outputs 48
  </DiamondAnnotation>
  <DiamondAnnotation pos="27.5 1 0.5" color="#3CD6C6">
    squash2: Activation, Function sigmoid
  </DiamondAnnotation>
  <DiamondAnnotation pos="30.5 1 0.5" color="#3CD6C6">
    layer3: Linear Layer, Outputs 48
  </DiamondAnnotation>
  <DiamondAnnotation pos="33.5 1 0.5" color="#3CD6C6">
    squash3: Activation, Function sigmoid
  </DiamondAnnotation>
  <DiamondAnnotation pos="36.5 1 0.5" color="#3CD6C6">
    layer4: Linear Layer, Outputs 48
  </DiamondAnnotation>
  <DiamondAnnotation pos="39.5 1 0.5" color="#3CD6C6">
    squash4: Activation, Function sigmoid
  </DiamondAnnotation>
  <DiamondAnnotation pos="42.5 1 0.5" color="#3CD6C6">
    layer5: Linear Layer, Outputs 48
  </DiamondAnnotation>
  <DiamondAnnotation pos="45.5 1 0.5" color="#3CD6C6">
    squash5: Activation, Function sigmoid
  </DiamondAnnotation>
  <DiamondAnnotation pos="48.5 1 0.5" color="#3CD6C6">
    layer6: Linear Layer, Outputs 48
  </DiamondAnnotation>
  <DiamondAnnotation pos="51.5 1 0.5" color="#3CD6C6">
    squash6: Activation, Function sigmoid
  </DiamondAnnotation>
  <DiamondAnnotation pos="54.5 1 0.5" color="#3CD6C6">
    logits: Linear Layer, Outputs V
  </DiamondAnnotation>
</GameScene>

## In real projects

Vanishing gradients kept neural networks shallow for decades. The fixes you have here are the ones that made depth
possible: activations that don't squash, such as ReLU; residual connections, which let He et al. train networks over a
hundred layers deep in 2015; and normalization. Every modern language model relies on all three.

AdamW hides part of the problem here. It scales each weight's step to about the learning rate, however small that
weight's gradient, so the stack slowly digs itself out: train on and watch the bars even out on the Inspect tab. With
plain gradient descent the bottom layers would hardly move at all.
