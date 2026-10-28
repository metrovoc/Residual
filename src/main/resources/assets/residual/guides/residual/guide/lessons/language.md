---
navigation:
  title: Language
  icon: insight_language
  parent: lessons/index.md
  position: 6
---

# Language

*Lesson 6. Radford et al., 2019.*

Everything comes together. The task is the one from the first lesson, predicting the next character, but the text is
now Shakespeare's plays: 65 different characters, capitals, punctuation and line breaks included, with 64 characters
of context.

## The Transformer Block

A <ItemLink id="transformer" /> is the layer you assembled by hand in [Knowing Where](reverse.md), in one machine:
normalize, attend, add back; normalize, feed forward, add back.

![the transformer layer](/formulas/block.png)

Its settings are the ones its parts had: the number of attention heads, how far the feed-forward network widens, and
its activation function.

## A small GPT

Stack the blocks between the same two ends you have used since lesson 4: token and position vectors at the bottom, a
<ItemLink id="norm" /> and a <ItemLink id="linear" /> out to V scores at the top. Each block reads the residual
stream and adds what it found back into it.

![a GPT](/formulas/gpt.png)

The build at the bottom of this page has two blocks of width 64 and about 112,000 parameters. GPT-2's smallest version
is the same design with twelve blocks of width 768, 124 million parameters, and sub-word tokens instead of
characters. What grows with scale is width, depth, context, vocabulary and above all the amount of text. The design
stays.

## What to notice

- Each step is much more work than in earlier lessons, so this lesson trains on batches of 16. Accelerators help.
- At the goal, samples have the shape of a play: speakers' names in capitals before a colon, short lines of verse,
  punctuation where it belongs. Most words are invented, but the common ones come through. Train on past the goal and
  real characters start to speak.
- Turn the temperature down and the text gets safer and more repetitive. Turn it up and it falls apart.
- The goal is the same whatever you build. Try two blocks against four, or width 64 against 128, and compare the
  curves. From here on the experiments are yours.

## In PyTorch

> `x = self.embed(tokens) + self.pos(torch.arange(T))`\
> `x = self.block1(x)`\
> `x = self.block2(x)`\
> `logits = self.logits(self.norm_out(x))`

Export it from the Code tab and it runs outside the game: see [Beyond Residual](../beyond.md).

## The paper

Radford et al.'s *Language Models are Unsupervised Multitask Learners* (2019) introduced GPT-2: a stack of transformer
blocks with layer normalization moved to the start of each branch and one more at the end, as in your build, trained
only to predict the next token of 40 GB of web pages. It then answered questions, summarized and translated, a
little, from the prompt alone, without being trained for any of it. Its successors are the same kind of model, much
larger.

## One way to build it

<GameScene zoom="1.45" interactive={true}>
  <Block id="model_core" p:facing="east" />
  <Block id="position" x="3" p:facing="east" />
  <Block id="embedding" x="6" p:facing="east" />
  <Block id="add" x="9" p:facing="east" />
  <Block id="transformer" x="12" p:facing="east" />
  <Block id="transformer" x="15" p:facing="east" />
  <Block id="norm" x="18" p:facing="east" />
  <Block id="linear" x="21" p:facing="east" />
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
  <Block id="light_blue_cable" x="10" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="14" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="13" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="17" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="16" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="20" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="19" p:east="true" p:west="true" />
  <Block id="lime_cable" x="22" p:west="true" p:up="true" />
  <Block id="lime_cable" x="22" y="1" p:up="true" p:down="true" />
  <Block id="lime_cable" x="22" y="2" p:up="true" p:down="true" />
  <Block id="lime_cable" x="22" y="3" p:up="true" p:down="true" />
  <Block id="lime_cable" x="22" y="4" p:south="true" p:down="true" />
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
  <Block id="lime_cable" x="22" y="4" z="1" p:west="true" p:north="true" />
  <Block id="lime_cable" y="1" p:up="true" p:down="true" />
  <Block id="lime_cable" y="2" p:up="true" p:down="true" />
  <Block id="lime_cable" y="3" p:up="true" p:down="true" />
  <Block id="lime_cable" y="4" p:south="true" p:down="true" />
  <IsometricCamera yaw="200" pitch="30" />
  <DiamondAnnotation pos="0.5 1 0.5" color="#FFB547">
    Model Core: tokens leave through its front, scores come back in through its top.
  </DiamondAnnotation>
  <DiamondAnnotation pos="3.5 1 0.5" color="#3CD6C6">
    pos: Position Table, Width 64
  </DiamondAnnotation>
  <DiamondAnnotation pos="6.5 1 0.5" color="#3CD6C6">
    embed: Embedding Table, Width 64
  </DiamondAnnotation>
  <DiamondAnnotation pos="9.5 1 0.5" color="#3CD6C6">
    x: Residual Junction
  </DiamondAnnotation>
  <DiamondAnnotation pos="12.5 1 0.5" color="#3CD6C6">
    block1: Transformer Block
  </DiamondAnnotation>
  <DiamondAnnotation pos="15.5 1 0.5" color="#3CD6C6">
    block2: Transformer Block
  </DiamondAnnotation>
  <DiamondAnnotation pos="18.5 1 0.5" color="#3CD6C6">
    norm_out: Normalizer
  </DiamondAnnotation>
  <DiamondAnnotation pos="21.5 1 0.5" color="#3CD6C6">
    logits: Linear Layer, Outputs V
  </DiamondAnnotation>
</GameScene>
