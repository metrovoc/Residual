---
navigation:
  title: More Context
  icon: insight_context
  parent: lessons/index.md
  position: 2
---

# More Context

*Lesson 2. Bengio et al., 2003.*

One letter says little about the next. After "th", an "e" is likely; after just "h", far less so. This lesson's model
sees the last three letters, and its goal, a validation loss of 2.45, is out of reach for the bigram model, which
bottoms out near 2.58.

## Letters as vectors

A bigram table needs a row for every letter and a column for every next letter. Showing three letters that way would
need a row for every combination of three: 27 × 27 × 27 of them. Instead, give each letter a short learned vector,
an **embedding**, and let the model combine vectors. An <ItemLink id="embedding" /> with a width of 24 does that. As
training goes on, letters that behave alike, such as the vowels, end up with similar vectors, and what the model learns
about one carries over to the others.

## Seeing back in time

A <ItemLink id="shift" /> delays a sequence: with Steps set to 1, position *t* receives what position *t* − 1 had. Two
Delay Lines, one step and two steps, bring the two previous letters' embeddings forward to the current position. A
<ItemLink id="concat" /> joins vectors end to end: 24 and 24 make 48, then 48 and 24 make 72.

## A hidden layer

Those 72 numbers go through a <ItemLink id="linear" /> into a hidden layer, an <ItemLink id="activation" />, and a
second Linear Layer out to V scores:

![the model](/formulas/context.png)

The activation is what makes the hidden layer worth having. Without it the two Linear Layers would multiply out into a
single one, and the model could only add up evidence from each letter separately. With it, the hidden layer can
represent combinations, like "an h after a t".

## What to notice

- The model has about 13,000 weights where the bigram had 729. More weights can also fit quirks of the training text
  that do not carry over to new words: the gap between training and validation loss measures exactly that.
- The window is fixed. Whatever happened four letters ago is invisible, no matter how much you train.

## In PyTorch

> `self.embed = nn.Embedding(27, 24)`\
> `self.hidden = nn.Linear(72, 128)`\
> `self.logits = nn.Linear(128, 27)`\
> ` `\
> `def forward(self, tokens):`\
> `    e = self.embed(tokens)`\
> `    prev1 = delay(e, 1)`\
> `    prev2 = delay(e, 2)`\
> `    x = torch.cat([e, prev1, prev2], dim=-1)`\
> `    return self.logits(torch.tanh(self.hidden(x)))`

## The paper

Bengio, Ducharme, Vincent and Jauvin's *A Neural Probabilistic Language Model* (2003) did this with words instead of
letters: learned word vectors for the previous few words, concatenated, through a hidden layer to a softmax over the
vocabulary. It beat the counting models of its day, and its word vectors were the first embeddings in the sense used
ever since.

## One way to build it

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
