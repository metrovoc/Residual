---
navigation:
  title: Counting Pairs
  icon: insight_counting
  parent: lessons/index.md
  position: 1
---

# Counting Pairs

*Lesson 1. Shannon, 1948.*

A language model does one thing: given the text so far, it scores every possible next token. Here the tokens are
letters plus the end of a word, ⏎, so there are V = 27 of them, and the model owes the Core 27 scores at every
position.

The simplest model looks only at the current letter. Keep a table with one row per letter, where row *i* holds 27
scores for what comes after *i*. That is exactly what an <ItemLink id="embedding" /> is: a learned table with one row
per token. Make its width V and each row is a set of next-letter scores. This is a **bigram model**:

![bigram model](/formulas/bigram.png)

## How it learns

The Core shows the model real words and measures how surprised it was by each letter that actually came next: the
cross-entropy loss, explained on the [Training](../training.md) page. Knowing nothing, the model starts at log 27 ≈
3.30. Each step raises the score of the letter that came and lowers the others, a little.

There is a neat fact hiding here. The table that minimises this loss is the one you would get by counting how often
each letter follows each other letter and dividing by the totals. Gradient descent rediscovers counting. What it
cannot do is see further back than one letter. On this word list the best possible table scores about 2.58, and no
amount of training takes it lower.

## Build it

The [Getting Started](../getting-started.md) page walks through it. The goal is a validation loss of 2.62.

## What to notice

- After training, open the table. Its weights picture shows each letter's row: a fingerprint of what follows it.
- Sample some text. Pairs of letters look like English; nothing longer than that holds together. A model can only
  use what it can see, and this one sees one letter.

## In PyTorch

> `self.table = nn.Embedding(27, 27)`\
> ` `\
> `def forward(self, tokens):`\
> `    return self.table(tokens)`

## The paper

Claude Shannon's *A Mathematical Theory of Communication* (1948) treated English as a source of symbols with
statistics. To show how much structure those statistics capture, it generated text from letter and word frequencies,
then from pairs and triples, each sample more English-like than the last. Every language model since has made the
same kind of prediction, with ever better ways of using what came before.

## One way to build it

<GameScene zoom="3" interactive={true}>
  <Block id="model_core" p:facing="east" />
  <Block id="embedding" x="3" p:facing="east" />
  <Block id="light_blue_cable" x="2" p:east="true" p:west="true" />
  <Block id="light_blue_cable" x="1" p:east="true" p:west="true" />
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
  <DiamondAnnotation pos="0.5 1 0.5" color="#FFB547">
    Model Core: tokens leave through its front, scores come back in through its top.
  </DiamondAnnotation>
  <DiamondAnnotation pos="3.5 1 0.5" color="#3CD6C6">
    table: Embedding Table, Width V
  </DiamondAnnotation>
</GameScene>
