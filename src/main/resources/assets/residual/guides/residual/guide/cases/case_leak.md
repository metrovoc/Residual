---
navigation:
  title: Too Good to Be True
  icon: tensor_probe
  parent: cases/index.md
  position: 3
---

# Too Good to Be True

*Case 3, after [Looking Things Up](../lessons/recall.md).*

This model's loss falls far below what any honest model reaches, yet the words it writes are no better than an honest
model's. Something lets it see what it is supposed to predict.

## Symptoms

- The validation loss drops to about 0.16. The goal on this word list is 2.45, and no honest model of this size gets
  anywhere near 0.16.
- The Train tab shows **Leak?** and the status reads "Suspiciously good". The Core will not pass the model.
- The Sample tab tells another story: the words it writes are as odd as any small model's.

## Hints

### Hint 1

The Core's leak check changes the second half of a sequence and watches the predictions for the first half. If they
move, then a position can see tokens that come after it. Which machines in this build let one position read from
another?

### Hint 2

Only the attention part mixes positions. Follow it from the grid of scores, through the softmax, to the weighted mix
of values.

### Hint 3

Row *t* of the score grid holds position *t*'s scores for every position, including the ones after *t*. What makes
sure those get a weight of zero?

## The broken build

<GameScene zoom="0.75" interactive={true}>
  <Block id="model_core" p:facing="east" />
  <Block id="position" x="3" p:facing="east" />
  <Block id="embedding" x="6" p:facing="east" />
  <Block id="add" x="9" p:facing="east" />
  <Block id="linear" x="12" p:facing="east" />
  <Block id="linear" x="15" p:facing="east" />
  <Block id="linear" x="18" p:facing="east" />
  <Block id="scale" x="21" p:facing="east" />
  <Block id="matmul" x="24" p:facing="east" />
  <Block id="softmax" x="27" p:facing="east" />
  <Block id="matmul" x="30" p:facing="east" />
  <Block id="add" x="33" p:facing="east" />
  <Block id="linear" x="36" p:facing="east" />
  <Block id="activation" x="39" p:facing="east" />
  <Block id="linear" x="42" p:facing="east" />
  <Block id="light_blue_cable" x="5" p:east="true" p:up="true" />
  <Block id="light_blue_cable" x="1" p:west="true" p:up="true" />
  <Block id="light_blue_cable" x="1" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="1" y="2" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="1" y="2" z="1" p:east="true" p:north="true" />
  <Block id="light_blue_cable" x="2" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="3" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="4" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="5" y="2" z="1" p:west="true" p:north="true" />
  <Block id="light_blue_cable" x="5" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="5" y="2" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="8" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="7" p:east="true" p:west="true" />
  <Block id="orange_cable" x="9" y="1" p:up="true" p:down="true" />
  <Block id="orange_cable" x="4" p:west="true" p:up="true" />
  <Block id="orange_cable" x="4" y="1" p:up="true" p:down="true" />
  <Block id="orange_cable" x="4" y="2" p:up="true" p:down="true" />
  <Block id="orange_cable" x="4" y="3" p:south="true" p:down="true" />
  <Block id="orange_cable" x="4" y="3" z="1" p:east="true" p:north="true" />
  <Block id="orange_cable" x="5" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="6" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="7" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="8" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="9" y="3" z="1" p:west="true" p:north="true" />
  <Block id="orange_cable" x="9" y="2" p:up="true" p:down="true" />
  <Block id="orange_cable" x="9" y="3" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="11" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="14" p:east="true" p:up="true" />
  <Block id="light_blue_cable" x="17" p:east="true" p:up="true" />
  <Block id="light_blue_cable" x="32" p:east="true" p:up="true" />
  <Block id="light_blue_cable" x="10" p:east="true" p:west="true" p:up="true" />
  <Block id="light_blue_cable" x="10" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="10" y="2" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="10" y="2" z="1" p:east="true" p:north="true" />
  <Block id="light_blue_cable" x="11" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="12" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="13" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="14" y="2" z="1" p:east="true" p:west="true" p:north="true" />
  <Block id="light_blue_cable" x="15" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="16" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="17" y="2" z="1" p:east="true" p:west="true" p:north="true" />
  <Block id="light_blue_cable" x="18" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="19" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="20" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="21" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="22" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="23" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="24" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="25" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="26" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="27" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="28" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="29" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="30" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="31" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="32" y="2" z="1" p:west="true" p:north="true" />
  <Block id="light_blue_cable" x="14" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="14" y="2" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="17" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="17" y="2" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="32" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="32" y="2" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="20" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="19" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="23" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="22" p:east="true" p:west="true" />
  <Block id="orange_cable" x="24" y="1" p:up="true" p:down="true" />
  <Block id="orange_cable" x="16" p:west="true" p:up="true" />
  <Block id="orange_cable" x="16" y="1" p:up="true" p:down="true" />
  <Block id="orange_cable" x="16" y="2" p:up="true" p:down="true" />
  <Block id="orange_cable" x="16" y="3" p:south="true" p:down="true" />
  <Block id="orange_cable" x="16" y="3" z="1" p:east="true" p:north="true" />
  <Block id="orange_cable" x="17" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="18" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="19" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="20" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="21" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="22" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="23" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="24" y="3" z="1" p:west="true" p:north="true" />
  <Block id="orange_cable" x="24" y="2" p:up="true" p:down="true" />
  <Block id="orange_cable" x="24" y="3" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="26" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="25" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="29" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="28" p:east="true" p:west="true" />
  <Block id="lime_cable" x="30" y="1" p:up="true" p:down="true" />
  <Block id="lime_cable" x="13" p:west="true" p:up="true" />
  <Block id="lime_cable" x="13" y="1" p:up="true" p:down="true" />
  <Block id="lime_cable" x="13" y="2" p:up="true" p:down="true" />
  <Block id="lime_cable" x="13" y="3" p:up="true" p:down="true" />
  <Block id="lime_cable" x="13" y="4" p:south="true" p:down="true" />
  <Block id="lime_cable" x="13" y="4" z="1" p:east="true" p:north="true" />
  <Block id="lime_cable" x="14" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="15" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="16" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="17" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="18" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="19" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="20" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="21" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="22" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="23" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="24" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="25" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="26" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="27" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="28" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="29" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="30" y="4" z="1" p:west="true" p:north="true" />
  <Block id="lime_cable" x="30" y="2" p:up="true" p:down="true" />
  <Block id="lime_cable" x="30" y="3" p:up="true" p:down="true" />
  <Block id="lime_cable" x="30" y="4" p:south="true" p:down="true" />
  <Block id="orange_cable" x="33" y="1" p:up="true" p:down="true" />
  <Block id="orange_cable" x="31" p:west="true" p:up="true" />
  <Block id="orange_cable" x="31" y="1" p:up="true" p:down="true" />
  <Block id="orange_cable" x="31" y="2" p:up="true" p:down="true" />
  <Block id="orange_cable" x="31" y="3" p:south="true" p:down="true" />
  <Block id="orange_cable" x="31" y="3" z="1" p:east="true" p:north="true" />
  <Block id="orange_cable" x="32" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="33" y="3" z="1" p:west="true" p:north="true" />
  <Block id="orange_cable" x="33" y="2" p:up="true" p:down="true" />
  <Block id="orange_cable" x="33" y="3" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="35" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="34" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="38" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="37" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="41" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="40" p:east="true" p:west="true" />
  <Block id="orange_cable" x="43" p:west="true" p:up="true" />
  <Block id="orange_cable" x="43" y="1" p:up="true" p:down="true" />
  <Block id="orange_cable" x="43" y="2" p:up="true" p:down="true" />
  <Block id="orange_cable" x="43" y="3" p:up="true" p:down="true" />
  <Block id="orange_cable" x="43" y="4" p:up="true" p:down="true" />
  <Block id="orange_cable" x="43" y="5" p:south="true" p:down="true" />
  <Block id="orange_cable" y="5" z="1" p:east="true" p:north="true" />
  <Block id="orange_cable" x="1" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="2" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="3" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="4" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="5" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="6" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="7" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="8" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="9" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="10" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="11" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="12" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="13" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="14" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="15" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="16" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="17" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="18" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="19" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="20" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="21" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="22" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="23" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="24" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="25" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="26" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="27" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="28" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="29" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="30" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="31" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="32" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="33" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="34" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="35" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="36" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="37" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="38" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="39" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="40" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="41" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="42" y="5" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="43" y="5" z="1" p:west="true" p:north="true" />
  <Block id="orange_cable" y="1" p:up="true" p:down="true" />
  <Block id="orange_cable" y="2" p:up="true" p:down="true" />
  <Block id="orange_cable" y="3" p:up="true" p:down="true" />
  <Block id="orange_cable" y="4" p:up="true" p:down="true" />
  <Block id="orange_cable" y="5" p:south="true" p:down="true" />
  <IsometricCamera yaw="200" pitch="30" />
  <DiamondAnnotation pos="0.5 1 0.5" color="#FFB547">
    Model Core: tokens leave through its front, scores come back in through its top.
  </DiamondAnnotation>
  <DiamondAnnotation pos="3.5 1 0.5" color="#3CD6C6">
    pos: Position Table
  </DiamondAnnotation>
  <DiamondAnnotation pos="6.5 1 0.5" color="#3CD6C6">
    embed: Embedding Table
  </DiamondAnnotation>
  <DiamondAnnotation pos="9.5 1 0.5" color="#3CD6C6">
    x: Residual Junction
  </DiamondAnnotation>
  <DiamondAnnotation pos="12.5 1 0.5" color="#3CD6C6">
    value: Linear Layer, Bias off
  </DiamondAnnotation>
  <DiamondAnnotation pos="15.5 1 0.5" color="#3CD6C6">
    key: Linear Layer, Bias off
  </DiamondAnnotation>
  <DiamondAnnotation pos="18.5 1 0.5" color="#3CD6C6">
    query: Linear Layer, Bias off
  </DiamondAnnotation>
  <DiamondAnnotation pos="21.5 1 0.5" color="#3CD6C6">
    q_scaled: Scaler
  </DiamondAnnotation>
  <DiamondAnnotation pos="24.5 1 0.5" color="#3CD6C6">
    scores: Matrix Multiplier, Transpose on
  </DiamondAnnotation>
  <DiamondAnnotation pos="27.5 1 0.5" color="#3CD6C6">
    weights: Softmax
  </DiamondAnnotation>
  <DiamondAnnotation pos="30.5 1 0.5" color="#3CD6C6">
    mixed: Matrix Multiplier
  </DiamondAnnotation>
  <DiamondAnnotation pos="33.5 1 0.5" color="#3CD6C6">
    residual: Residual Junction
  </DiamondAnnotation>
  <DiamondAnnotation pos="36.5 1 0.5" color="#3CD6C6">
    hidden: Linear Layer, Outputs 128
  </DiamondAnnotation>
  <DiamondAnnotation pos="39.5 1 0.5" color="#3CD6C6">
    act: Activation
  </DiamondAnnotation>
  <DiamondAnnotation pos="42.5 1 0.5" color="#3CD6C6">
    logits: Linear Layer, Outputs V
  </DiamondAnnotation>
</GameScene>

## In real projects

Leaks are the most dangerous bugs, because they look like success. A mask that is off by one, test data that also
appears in the training data, a feature computed from the future: each one produces numbers that are too good. The
defence is suspicion. When a result beats what should be possible, find out how the model cheated before you
celebrate.
