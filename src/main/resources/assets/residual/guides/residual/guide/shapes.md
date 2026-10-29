---
navigation:
  title: Shapes
  icon: linear
  position: 110
---

# Shapes

Every value in a model is a grid of numbers, a **tensor**, and its shape says how big each side of the grid is. The
Model Core checks every shape before it trains anything, the same way PyTorch would fail on the first batch, but with
a clearer message.

## Reading a shape

Shapes are written outermost first: [B×T×32] is a batch of B sequences, each T positions long, with 32 numbers at
every position.

| Letter | Means                                                             |
|--------|-------------------------------------------------------------------|
| B      | Batch: how many sequences are processed at once. Set on the Core. |
| T      | Time: positions in a sequence, up to the context length of 64.   |

Every other number is a size you chose, like the 32 above, or the vocabulary size **V**, which shows as its value: 78,
one for each letter, digit and mark a model knows, and the line break. The last dimension is the one machines work on: a
<ItemLink id="linear" /> turns [B×T×32] into [B×T×64] by mixing the 32 numbers at each position into 64 new ones, the
same way at every position.

A shape with fewer dimensions stretches to fit, as in PyTorch: the <ItemLink id="position" />'s [T×32] adds onto
every sequence of a [B×T×32] batch.

## What the Core expects

The Core sends out token ids, shaped [B×T], and wants back one score for every possible next token at every
position: [B×T×V]. Whatever happens in between is up to you.

## When shapes disagree

Press **Check** on the Core, or look at a machine. A machine whose inputs don't fit says what it got and what it
needs. Common causes:

- A <ItemLink id="add" /> or <ItemLink id="multiply" /> whose two inputs have different widths.
- A <ItemLink id="matmul" /> whose inner sizes don't match. With **Transpose** on, it multiplies by the side input
  turned on its side, which is what comparing queries with keys needs.
- The last machine before the Core not ending in V.

Shapes flow forward, so fix the earliest problem first: a wrong width early on makes every later machine look wrong
too. The Inspect tab on the Core lists every machine in the order data reaches it.

## In PyTorch

Shape errors are the most common bug in real model code. PyTorch only finds them when the code runs; reading shapes
the way the Core does, before running anything, is a habit worth taking with you.
