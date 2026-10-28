---
navigation:
  title: Embedding Table
  icon: embedding
  parent: machines/index.md
  position: 1
item_ids:
  - embedding
---

# Embedding Table

<BlockImage id="embedding" scale="3" float="left" />

Looks up a learned row of numbers for each token: token *i* gets row *i* of a table with one row per
vocabulary entry. This is how text enters a model, so the table must be fed directly by the Model Core's front.

![an embedding lookup](/formulas/embedding.png)

## Shapes

Tokens [B×T] in, [B×T×C] out, where C is the Width.

## Settings

- **Width**: V, or 4 to 256. 32 by default. At V, each row can serve directly as the scores for the next token: the bigram model of [Counting Pairs](../lessons/counting.md).

## Parameters

V × C, drawn from a normal distribution with spread 1.

## In PyTorch

> `self.embed = nn.Embedding(V, C)`\
> `e = self.embed(tokens)`

## Notes

After training, the weights picture shows the table, a row per token. Tokens that behave alike end up with
similar rows, which is how what the model learns about one carries over to others.

## Recipe

Needs no Insight.

<RecipeFor id="embedding" />
