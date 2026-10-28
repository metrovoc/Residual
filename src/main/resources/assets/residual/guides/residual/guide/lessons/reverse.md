---
navigation:
  title: Knowing Where
  icon: insight_position
  parent: lessons/index.md
  position: 4
---

# Knowing Where

*Lesson 4. Vaswani et al., 2017.*

The puzzle: write six digits backwards.

> `381925|529183`

Attention compares contents. But here the digit to copy depends on *where* it is: the first answer is the last digit,
the second answer the one before it, and two equal digits look exactly alike. Attention as you built it has no idea of
position at all. Shuffle the input and every score stays the same.

## Positions

A <ItemLink id="position" /> is a learned vector for every position in the context. Add it to the token's embedding
with a <ItemLink id="add" />, and each position carries both what it is and where it is:

![token plus position](/formulas/position.png)

## The transformer layer

This lesson also assembles the layer every modern language model stacks: attention, then a feed-forward network,
each wrapped the same way.

![the transformer layer](/formulas/block.png)

- The <ItemLink id="attention" /> is the attention you built by hand, in one machine, with several **heads**: the
  width is split into H parts that attend independently, then joined and projected back.
- The <ItemLink id="mlp" /> works on every position separately: widen, bend, project back.

  ![feed-forward](/formulas/mlp.png)

- The <ItemLink id="norm" /> keeps each vector at a steady size before it enters a branch:

  ![layer normalization](/formulas/layernorm.png)

- The <ItemLink id="add" /> adds each branch back onto the stream that went in. The layer refines its input instead
  of replacing it:

  ![a residual connection](/formulas/residual.png)

That running sum is the **residual stream**, and it is what this mod is named after. Every layer reads from it and
writes back into it. Wire the stream into the Residual Junction's back and the branch into its side.

## What to notice

- With the Position Table, the build below gets every digit right within about a hundred steps. Without it, the
  same build is still below half accuracy after 3,000.
- Open the Attention Head after training. Its picture shows where its first head looks, one row per position. Find
  the rows that write the answer: does each one look back at the digit it has to copy?

## In PyTorch

> `x = self.embed(tokens) + self.pos(torch.arange(T))`\
> `x = x + self.attn(self.norm1(x))`\
> `x = x + self.ffn(self.norm2(x))`\
> `logits = self.logits(self.norm_out(x))`

## The paper

Vaswani et al.'s *Attention Is All You Need* (2017) dropped the recurrent networks that translation models were built
on and kept only attention: stacked layers of multi-head attention and feed-forward networks, with residual
connections and layer normalization, and position information added to the embeddings. Nearly every large language
model since is a stack of these layers.

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
  <Block id="mlp" x="24" p:facing="east" />
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
    attn: Attention Head, Heads 2
  </DiamondAnnotation>
  <DiamondAnnotation pos="18.5 1 0.5" color="#3CD6C6">
    res1: Residual Junction
  </DiamondAnnotation>
  <DiamondAnnotation pos="21.5 1 0.5" color="#3CD6C6">
    norm2: Normalizer
  </DiamondAnnotation>
  <DiamondAnnotation pos="24.5 1 0.5" color="#3CD6C6">
    ffn: Feed-Forward Block
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
