---
navigation:
  title: Looking Things Up
  icon: insight_attention
  parent: lessons/index.md
  position: 3
---

# Looking Things Up

*Lesson 3. Bahdanau, Cho and Bengio, 2014.*

The puzzle: eight letter–digit pairs, a bar, then a query letter. Answer with the digit that followed that letter.

> `c4a7f1k9p2m0d5h3|f   →   1`

The pair can sit anywhere in the sequence. A window of the last few letters, like the last lesson's, never reaches
it. The model needs to search: compare the query against every earlier position and read out whatever sits at the
match. That operation is **attention**, and in this lesson you build it from parts.

## Queries, keys and values

Every position produces three vectors, each a learned <ItemLink id="linear" /> of what is there:

![queries, keys and values](/formulas/qkv.png)

- The **query** says what this position is looking for.
- The **key** says what this position can be found by.
- The **value** is what this position hands over when it is found.

A <ItemLink id="matmul" /> with Transpose on compares every query with every key at once, giving a T × T grid of
scores. A <ItemLink id="softmax" /> turns each row into weights that sum to one, and a second Matrix Multiplier mixes
the values with those weights:

![attention](/formulas/attention.png)

Two details make it work in practice. The <ItemLink id="scale" /> divides by √d: a dot product of two random
d-dimensional vectors has a variance of about d, and without the scaling the softmax saturates and stops learning.

![why divide by the square root of d](/formulas/scale.png)

And M is the **causal mask**. A language model predicts each token from the ones before it, so position *t* must not
look at anything after *t*. The <ItemLink id="causal_mask" /> sets those scores to −∞, which the softmax turns into a
weight of exactly zero:

![the causal mask](/formulas/mask.png)

## The trick of this puzzle

The query is easy: the query letter itself. But the digit to read out sits one position *after* the matching letter.
So give each position a key made from the token *before* it: a <ItemLink id="shift" /> in front of the key's Linear
Layer. The digit's position now carries its letter in its key, the query finds it, and the value read out there is
the digit.

## What to notice

- The fall is sudden. For a few dozen steps little happens; then queries and keys line up, and accuracy jumps from
  chance to nearly perfect.
- Open the Softmax machine after training. Its output picture is the attention pattern, one row per position. Only the
  bottom row, the query, is graded: it shows one bright spot, on the digit that answers it.
- The goal is answer accuracy, 90%. Only the answer position counts toward the loss.

## In PyTorch

> `q = self.query(e) / math.sqrt(32)`\
> `k = self.key(delay(e, 1))`\
> `v = self.value(e)`\
> `scores = causal_mask(q @ k.transpose(-2, -1))`\
> `mixed = F.softmax(scores, dim=-1) @ v`

## The paper

Bahdanau, Cho and Bengio's *Neural Machine Translation by Jointly Learning to Align and Translate* (2014) gave a
translation model the ability to look back over the whole source sentence and weigh each word by a learned score,
instead of squeezing the sentence into one fixed vector. That learned, soft lookup is the attention you just built.

## One way to build it

<GameScene zoom="0.95" interactive={true}>
  <Block id="model_core" p:facing="east" />
  <Block id="embedding" x="3" p:facing="east" />
  <Block id="linear" x="6" p:facing="east" />
  <Block id="shift" x="9" p:facing="east" />
  <Block id="linear" x="12" p:facing="east" />
  <Block id="linear" x="15" p:facing="east" />
  <Block id="scale" x="18" p:facing="east" />
  <Block id="matmul" x="21" p:facing="east" />
  <Block id="causal_mask" x="24" p:facing="east" />
  <Block id="softmax" x="27" p:facing="east" />
  <Block id="matmul" x="30" p:facing="east" />
  <Block id="linear" x="33" p:facing="east" />
  <Block id="light_blue_cable" x="2" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="5" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="8" p:east="true" p:up="true" />
  <Block id="light_blue_cable" x="14" p:east="true" p:up="true" />
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
  <Block id="light_blue_cable" x="11" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="12" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="13" y="2" z="1" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="14" y="2" z="1" p:west="true" p:north="true" />
  <Block id="light_blue_cable" x="8" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="8" y="2" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="14" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="14" y="2" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="11" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="10" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="17" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="16" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="20" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="19" p:east="true" p:west="true" />
  <Block id="orange_cable" x="21" y="1" p:up="true" p:down="true" />
  <Block id="orange_cable" x="13" p:west="true" p:up="true" />
  <Block id="orange_cable" x="13" y="1" p:up="true" p:down="true" />
  <Block id="orange_cable" x="13" y="2" p:up="true" p:down="true" />
  <Block id="orange_cable" x="13" y="3" p:south="true" p:down="true" />
  <Block id="orange_cable" x="13" y="3" z="1" p:east="true" p:north="true" />
  <Block id="orange_cable" x="14" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="15" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="16" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="17" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="18" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="19" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="20" y="3" z="1" p:east="true" p:west="true" />
  <Block id="orange_cable" x="21" y="3" z="1" p:west="true" p:north="true" />
  <Block id="orange_cable" x="21" y="2" p:up="true" p:down="true" />
  <Block id="orange_cable" x="21" y="3" p:south="true" p:down="true" />
  <Block id="light_blue_cable" x="23" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="22" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="26" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="25" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="29" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="28" p:east="true" p:west="true" />
  <Block id="lime_cable" x="30" y="1" p:up="true" p:down="true" />
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
  <Block id="light_blue_cable" x="32" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="31" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="34" p:west="true" p:up="true" />
  <Block id="light_blue_cable" x="34" y="1" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="34" y="2" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="34" y="3" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="34" y="4" p:up="true" p:down="true" />
  <Block id="light_blue_cable" x="34" y="5" p:south="true" p:down="true" />
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
  <Block id="light_blue_cable" x="34" y="5" z="1" p:west="true" p:north="true" />
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
    embed: Embedding Table
  </DiamondAnnotation>
  <DiamondAnnotation pos="6.5 1 0.5" color="#3CD6C6">
    value: Linear Layer, Bias off
  </DiamondAnnotation>
  <DiamondAnnotation pos="9.5 1 0.5" color="#3CD6C6">
    prev: Delay Line
  </DiamondAnnotation>
  <DiamondAnnotation pos="12.5 1 0.5" color="#3CD6C6">
    key: Linear Layer, Bias off
  </DiamondAnnotation>
  <DiamondAnnotation pos="15.5 1 0.5" color="#3CD6C6">
    query: Linear Layer, Bias off
  </DiamondAnnotation>
  <DiamondAnnotation pos="18.5 1 0.5" color="#3CD6C6">
    q_scaled: Scaler
  </DiamondAnnotation>
  <DiamondAnnotation pos="21.5 1 0.5" color="#3CD6C6">
    scores: Matrix Multiplier, Transpose on
  </DiamondAnnotation>
  <DiamondAnnotation pos="24.5 1 0.5" color="#3CD6C6">
    masked: Causal Mask
  </DiamondAnnotation>
  <DiamondAnnotation pos="27.5 1 0.5" color="#3CD6C6">
    weights: Softmax
  </DiamondAnnotation>
  <DiamondAnnotation pos="30.5 1 0.5" color="#3CD6C6">
    mixed: Matrix Multiplier
  </DiamondAnnotation>
  <DiamondAnnotation pos="33.5 1 0.5" color="#3CD6C6">
    logits: Linear Layer, Outputs V
  </DiamondAnnotation>
</GameScene>
