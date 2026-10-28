---
navigation:
  title: Induction Heads
  icon: insight_circuits
  parent: lessons/index.md
  position: 5
---

# Induction Heads

*Lesson 5. Olsson et al., 2022.*

The puzzle: 24 letters, all different except for one run of five to eight letters that appears twice. Once the second
copy begins, continue it.

> `vbepctzylnsfdorlnsfdorkq`

Here the run is "lnsfdor". After the second "l" the answer is "n", after its "n" comes "s", and so on to the end of the
run. Only those letters are graded.

Nothing about a letter says what follows it: every letter outside the run is new. The only way through is to have read
the sequence: find where the current letter appeared before, and copy what came after it. Large language models do
this constantly, with names, numbers and phrases they saw once earlier in the same text.

## Without Delay Lines

In the recall lesson, a <ItemLink id="shift" /> gave each key the token before it. Here the Core refuses any model that
contains one: the model has to build that step out of attention instead. Two layers can do it together.

| Layer | Each position looks at | and so |
| --- | --- | --- |
| First | the position just before it | it holds its own letter and the one before it |
| Second | the position whose *previous* letter matches its own | it reads the letter that followed last time |

The second layer's query asks for the current letter. Its keys are built from what the first layer wrote: the letter
before. The layers communicate through the residual stream, where the first writes and the second reads. Elhage et al.
call this **K-composition**, because one head's output feeds the keys of a later one.

Looking one step back is a question of position, not content, so start as in the last lesson: an
<ItemLink id="embedding" /> plus a <ItemLink id="position" />.

## What to notice

- The loss sits on a plateau near 3.26, chance among 26 letters, for a few hundred steps. Then it falls within a few
  dozen. That drop is the circuit clicking into place, and Olsson et al. found the same sudden jump in far larger
  models.
- Open the first <ItemLink id="attention" /> after training. Its picture has one row per position, and a bright line
  runs just below the diagonal: every position looks one step back.
- The second one lights up in the rows of the second copy, along a short line further from the diagonal: each letter
  looks at the letter that followed its earlier copy.
- With a single attention layer, the model is still below 20% accuracy after 2,000 steps.

## In PyTorch

> `x = self.embed(tokens) + self.pos(torch.arange(T))`\
> `x = x + self.attn1(self.norm1(x))`\
> `x = x + self.attn2(self.norm2(x))`\
> `logits = self.logits(self.norm_out(x))`

## The paper

Elhage et al.'s *A Mathematical Framework for Transformer Circuits* (2021) took small attention-only transformers
apart and found this two-layer circuit, which they named the induction head. Olsson et al.'s *In-context Learning and
Induction Heads* (2022) showed that it forms in a sudden jump during training, and gathered evidence that it accounts
for much of how large models learn from the text in front of them. Reading a trained network this way, circuit by
circuit, is the field of **mechanistic interpretability**.

## One way to build it

<GameScene zoom="0.95" interactive={true}>
  <Block id="model_core" p:facing="east" />
  <Block id="position" x="3" p:facing="east" />
  <Block id="embedding" x="6" p:facing="east" />
  <Block id="add" x="9" p:facing="east" />
  <Block id="norm" x="12" p:facing="east" />
  <Block id="attention" x="15" p:facing="east" />
  <Block id="add" x="18" p:facing="east" />
  <Block id="norm" x="21" p:facing="east" />
  <Block id="attention" x="24" p:facing="east" />
  <Block id="add" x="27" p:facing="east" />
  <Block id="norm" x="30" p:facing="east" />
  <Block id="linear" x="33" p:facing="east" />
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
  <Block id="light_blue_cable" x="17" p:east="true" p:up="true" />
  <Block id="light_blue_cable" x="10" p:east="true" p:west="true" p:up="true" />
  <Block id="light_blue_cable" x="10" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="10" y="2" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="10" y="2" z="1" p:east="true" p:north="true" />
  <Block id="light_blue_cable" x="11" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="12" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="13" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="14" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="15" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="16" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="17" y="2" z="1" p:west="true" p:north="true" />
  <Block id="light_blue_cable" x="17" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="17" y="2" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="14" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="13" p:east="true" p:west="true" />
  <Block id="orange_cable" x="18" y="1" p:up="true" p:down="true" />
  <Block id="orange_cable" x="16" p:west="true" p:up="true" />
  <Block id="orange_cable" x="16" y="1" p:up="true" p:down="true" />
  <Block id="orange_cable" x="16" y="2" p:up="true" p:down="true" />
  <Block id="orange_cable" x="16" y="3" p:south="true" p:down="true" />
  <Block id="orange_cable" x="16" y="3" z="1" p:east="true" p:north="true" />
  <Block id="orange_cable" x="17" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="18" y="3" z="1" p:west="true" p:north="true" />
  <Block id="orange_cable" x="18" y="2" p:up="true" p:down="true" />
  <Block id="orange_cable" x="18" y="3" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="20" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="26" p:east="true" p:up="true" />
  <Block id="light_blue_cable" x="19" p:east="true" p:west="true" p:up="true" />
  <Block id="light_blue_cable" x="19" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="19" y="2" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="19" y="2" z="1" p:east="true" p:north="true" />
  <Block id="light_blue_cable" x="20" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="21" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="22" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="23" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="24" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="25" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="26" y="2" z="1" p:west="true" p:north="true" />
  <Block id="light_blue_cable" x="26" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="26" y="2" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="23" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="22" p:east="true" p:west="true" />
  <Block id="orange_cable" x="27" y="1" p:up="true" p:down="true" />
  <Block id="orange_cable" x="25" p:west="true" p:up="true" />
  <Block id="orange_cable" x="25" y="1" p:up="true" p:down="true" />
  <Block id="orange_cable" x="25" y="2" p:up="true" p:down="true" />
  <Block id="orange_cable" x="25" y="3" p:south="true" p:down="true" />
  <Block id="orange_cable" x="25" y="3" z="1" p:east="true" p:north="true" />
  <Block id="orange_cable" x="26" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="27" y="3" z="1" p:west="true" p:north="true" />
  <Block id="orange_cable" x="27" y="2" p:up="true" p:down="true" />
  <Block id="orange_cable" x="27" y="3" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="29" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="28" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="32" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="31" p:east="true" p:west="true" />
  <Block id="lime_cable" x="34" p:west="true" p:up="true" />
  <Block id="lime_cable" x="34" y="1" p:up="true" p:down="true" />
  <Block id="lime_cable" x="34" y="2" p:up="true" p:down="true" />
  <Block id="lime_cable" x="34" y="3" p:up="true" p:down="true" />
  <Block id="lime_cable" x="34" y="4" p:south="true" p:down="true" />
  <Block id="lime_cable" y="4" z="1" p:east="true" p:north="true" />
  <Block id="lime_cable" x="1" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="2" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="3" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="4" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="5" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="6" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="7" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="8" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="9" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="10" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="11" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="12" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="13" y="4" z="1" p:east="true" p:west="true" />
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
  <Block id="lime_cable" x="30" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="31" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="32" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="33" y="4" z="1" p:east="true" p:west="true" />
  <Block id="lime_cable" x="34" y="4" z="1" p:west="true" p:north="true" />
  <Block id="lime_cable" y="1" p:up="true" p:down="true" />
  <Block id="lime_cable" y="2" p:up="true" p:down="true" />
  <Block id="lime_cable" y="3" p:up="true" p:down="true" />
  <Block id="lime_cable" y="4" p:south="true" p:down="true" />
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
    norm1: Normalizer
  </DiamondAnnotation>
  <DiamondAnnotation pos="15.5 1 0.5" color="#3CD6C6">
    attn1: Attention Head, Heads 1
  </DiamondAnnotation>
  <DiamondAnnotation pos="18.5 1 0.5" color="#3CD6C6">
    res1: Residual Junction
  </DiamondAnnotation>
  <DiamondAnnotation pos="21.5 1 0.5" color="#3CD6C6">
    norm2: Normalizer
  </DiamondAnnotation>
  <DiamondAnnotation pos="24.5 1 0.5" color="#3CD6C6">
    attn2: Attention Head, Heads 1
  </DiamondAnnotation>
  <DiamondAnnotation pos="27.5 1 0.5" color="#3CD6C6">
    res2: Residual Junction
  </DiamondAnnotation>
  <DiamondAnnotation pos="30.5 1 0.5" color="#3CD6C6">
    norm_out: Normalizer
  </DiamondAnnotation>
  <DiamondAnnotation pos="33.5 1 0.5" color="#3CD6C6">
    logits: Linear Layer, Outputs V
  </DiamondAnnotation>
</GameScene>
