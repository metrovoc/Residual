---
navigation:
  title: Lost in Order
  icon: tensor_probe
  parent: cases/index.md
  position: 4
---

# Lost in Order

*Case 4, after [Knowing Where](../lessons/reverse.md).*

A complete transformer layer that still can't reverse a string. It can compare any two tokens, yet something the task
depends on escapes it.

## Symptoms

- The model learns something, then levels off. When its budget runs out it gets fewer than half of the digits right;
  the goal is 90%.
- Nothing is wrong with the wiring: Check finds no problems.

## Hints

### Hint 1

Give it new puzzles on the Sample tab. Which answers does it get right? Which does it get wrong, and what does it write
instead?

### Hint 2

Imagine shuffling the six digits before the bar. Which of this model's computations would notice the difference?

### Hint 3

Attention compares what tokens are. Nothing in this build tells a token where it is.

## The broken build

<GameScene zoom="1.15" interactive={true}>
  <Block id="model_core" p:facing="east" />
  <Block id="embedding" x="3" p:facing="east" />
  <Block id="norm" x="6" p:facing="east" />
  <Block id="attention" x="9" p:facing="east" />
  <Block id="add" x="12" p:facing="east" />
  <Block id="norm" x="15" p:facing="east" />
  <Block id="mlp" x="18" p:facing="east" />
  <Block id="add" x="21" p:facing="east" />
  <Block id="norm" x="24" p:facing="east" />
  <Block id="linear" x="27" p:facing="east" />
  <Block id="light_blue_cable" x="2" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="5" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="11" p:east="true" p:up="true" />
  <Block id="light_blue_cable" x="4" p:east="true" p:west="true" p:up="true" />
  <Block id="light_blue_cable" x="4" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="4" y="2" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="4" y="2" z="1" p:east="true" p:north="true" />
  <Block id="light_blue_cable" x="5" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="6" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="7" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="8" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="9" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="10" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="11" y="2" z="1" p:west="true" p:north="true" />
  <Block id="light_blue_cable" x="11" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="11" y="2" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="8" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="7" p:east="true" p:west="true" />
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
  <Block id="light_blue_cable" x="20" p:east="true" p:up="true" />
  <Block id="light_blue_cable" x="13" p:east="true" p:west="true" p:up="true" />
  <Block id="light_blue_cable" x="13" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="13" y="2" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="13" y="2" z="1" p:east="true" p:north="true" />
  <Block id="light_blue_cable" x="14" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="15" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="16" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="17" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="18" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="19" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="20" y="2" z="1" p:west="true" p:north="true" />
  <Block id="light_blue_cable" x="20" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="20" y="2" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="17" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="16" p:east="true" p:west="true" />
  <Block id="orange_cable" x="21" y="1" p:up="true" p:down="true" />
  <Block id="orange_cable" x="19" p:west="true" p:up="true" />
  <Block id="orange_cable" x="19" y="1" p:up="true" p:down="true" />
  <Block id="orange_cable" x="19" y="2" p:up="true" p:down="true" />
  <Block id="orange_cable" x="19" y="3" p:south="true" p:down="true" />
  <Block id="orange_cable" x="19" y="3" z="1" p:east="true" p:north="true" />
  <Block id="orange_cable" x="20" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="21" y="3" z="1" p:west="true" p:north="true" />
  <Block id="orange_cable" x="21" y="2" p:up="true" p:down="true" />
  <Block id="orange_cable" x="21" y="3" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="23" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="22" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="26" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="25" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="28" p:west="true" p:up="true" />
  <Block id="light_blue_cable" x="28" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="28" y="2" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="28" y="3" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="28" y="4" p:south="true" p:down="true" />
  <Block id="light_blue_cable" y="4" z="1" p:east="true" p:north="true" />
  <Block id="light_blue_cable" x="1" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="2" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="3" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="4" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="5" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="6" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="7" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="8" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="9" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="10" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="11" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="12" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="13" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="14" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="15" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="16" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="17" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="18" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="19" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="20" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="21" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="22" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="23" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="24" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="25" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="26" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="27" y="4" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="28" y="4" z="1" p:west="true" p:north="true" />
  <Block id="light_blue_cable" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" y="2" p:up="true" p:down="true" />
  <Block id="light_blue_cable" y="3" p:up="true" p:down="true" />
  <Block id="light_blue_cable" y="4" p:south="true" p:down="true" />
  <IsometricCamera yaw="200" pitch="30" />
  <DiamondAnnotation pos="0.5 1 0.5" color="#FFB547">
    Model Core: tokens leave through its front, scores come back in through its top.
  </DiamondAnnotation>
  <DiamondAnnotation pos="3.5 1 0.5" color="#3CD6C6">
    embed: Embedding Table
  </DiamondAnnotation>
  <DiamondAnnotation pos="6.5 1 0.5" color="#3CD6C6">
    norm1: Normalizer
  </DiamondAnnotation>
  <DiamondAnnotation pos="9.5 1 0.5" color="#3CD6C6">
    attn: Attention Head, Heads 2
  </DiamondAnnotation>
  <DiamondAnnotation pos="12.5 1 0.5" color="#3CD6C6">
    res1: Residual Junction
  </DiamondAnnotation>
  <DiamondAnnotation pos="15.5 1 0.5" color="#3CD6C6">
    norm2: Normalizer
  </DiamondAnnotation>
  <DiamondAnnotation pos="18.5 1 0.5" color="#3CD6C6">
    ffn: Feed-Forward Block
  </DiamondAnnotation>
  <DiamondAnnotation pos="21.5 1 0.5" color="#3CD6C6">
    res2: Residual Junction
  </DiamondAnnotation>
  <DiamondAnnotation pos="24.5 1 0.5" color="#3CD6C6">
    norm_out: Normalizer
  </DiamondAnnotation>
  <DiamondAnnotation pos="27.5 1 0.5" color="#3CD6C6">
    logits: Linear Layer, Outputs V
  </DiamondAnnotation>
</GameScene>

## In real projects

Self-attention has no built-in sense of order: shuffle the tokens it compares and it computes the same scores. Every
transformer adds position information somehow, with a learned table as GPT-2 does, with fixed waves as the original
transformer did, or by rotating queries and keys according to their positions (RoPE), as most recent models do.
