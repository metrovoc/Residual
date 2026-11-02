---
navigation:
  title: The Stream Scope
  icon: stream_scope
  position: 92
item_ids:
  - stream_scope
---

# The Stream Scope

A model that passes its test has told you what it can do and nothing of how. The <ItemLink id="stream_scope" /> looks inside.

On its **Model** tab it takes a <ItemLink id="checkpoint" />. With none, it reads the model standing on a Model Core it touches, and whatever it finds floats over that model's machines for a minute, in words and in light. It always works on a copy, so nothing it does touches the model, and Accelerators touching it lend it their threads.

Each of its other tabs is one instrument: choices in a row, then one display, with the details in tooltips. Most read a stretch of a benchmark's test, or what you type.

The points it reads are the model's **stream**: the places every path from the tokens to the scores passes through, at one width. In a stack of blocks that is the stream after each block, however you laid it out; what comes after the last is the **head**, a norm and an unembedding, say. A model with no such line of machines, such as a table of next letters, has one point and little to show.

## Lens

The **logit lens** runs the head on the stream early, as if the rest of the stack were not there. Each row is one point of the stream and each column one token read; each cell is what that point would say next, brighter the surer, green if it is right. At the right is each point's loss over a sample of the test.

So you can watch a prediction form, and see which block does the work. On a Pattern model the first block's loss is often worse than chance, and the last's near zero: the stream early on is no attempt at an answer, but something the later blocks read.

## Probes

A **linear probe** is one weighted sum of what the stream carries, fitted to say whether something is true of the text: inside quotation marks, a word's first letter, a token seen before, and so on. Where one reads a thing well, the model holds that fact as a direction in its stream.

A probe can always do something with the token at hand. So beside each stands a control, fitted to a meaningless label given to each kind of token. A probe that does no better than its control has found nothing but the token. The cell's colour is how far it does better; its tooltip has both.

## Heads

Every attention head, reading runs of letters twice over. The second time through, the only way to know what comes next is to look back at what followed the same letter the first time. Each bar is how much of a head's attention falls on that very place, and the tooltip says what the model does with that head silenced. The square on the right is the strongest head's attention over one run, read from top to bottom: an off-diagonal line in its lower half is a head looking back by the length of the run.

A head that does this is an **induction head**. It takes two layers: one head writes into the stream what came just before each place, and the next searches that for the letter at hand. Silencing the first often stops the copying more than silencing the second, though by itself it looks at nothing in particular.

They form suddenly. A Model Core takes a look every fifty steps as it trains, and a model that copied nothing at one look and at the next names at least half of what comes next, with a head at least half on the right place, has formed one. From about 300 steps of the Pattern Dataset the Induction blueprint goes from copying under one letter in ten to over three in four within a hundred steps.

For a model that knows its sums ([Models and Tests](models.md)), this tab also shows its token table read round the wheel, as frequencies. A table that has found the rule holds it in a few waves.

## Toy

**Superposition**: more features than dimensions to keep them in. A toy model squeezes five features through two dimensions, or twenty through five, reads them back out with the same matrix, and trains here in seconds. Each feature counts a little less than the one before.

When features are common, it keeps as many as it has dimensions and drops the rest. When they are rare, it keeps more than it has room for, each in a direction of its own that overlaps the others a little, since two rarely turn up at once. Five features in two dimensions come out as a pentagon. The bars beside it are each feature's length, and in amber how much the others lie along it.

This is why a single direction in a large model's stream seldom means one thing, and why the Features tab has to look for more directions than the stream has dimensions.

## Patch

**Activation patching**: read one token wrong, so that the answer changes. Then run the wrong text again and again, each time putting back what the stream carried at one point and one position when it read rightly. Where that brings the answer back is where the fact travels. A bright cell at the changed token's own column in the early blocks, and at the last column in the late ones, is the fact being carried across by attention.

It finds the wrong token for itself, trying each place in turn for the one that moves the answer most.

## Steer

The average of the stream where one token comes next, minus the average where others do, is a direction that means that token: a **steering vector**. Add it to the stream as the model reads, at any strength, and the model leans that way, with no training at all. The rows show how often that token becomes its first choice, how likely it holds it, and what the loss pays for that. Underneath, the model writes as it is, and again steered.

At ×1 the average place has been moved all the way across. Far past that, the model says little else.

## Features

A probe needs you to know what to look for. A **sparse autoencoder** looks for itself: a dictionary of eight times as many directions as the stream has dimensions, of which only an eighth as many as the stream is wide may add up to what it carries at any place. Forced to say so much with so few, its directions tend to come out as things that mean something. Fitting one takes seconds to a minute, and it is kept until the model changes.

The first lines say how much of what the stream varies by it explains, how many directions never fired, and what the model's loss becomes when it runs on the dictionary's version of the stream, against the stream's average alone. Below is each feature that matters, with how often it fires. Point at one for where it fired most strongly, the token in gold, and what it pushes the scores toward.

## Graph

An **attribution graph** takes one thing a model says, and shows which features made it say so, and which tokens and features made those. On the left are the tokens it read, then a column of features for each point of the stream, then what it said. Amber lines push it toward that, blue away, brighter the more.

A model this small can afford the exact answer: each thing is taken out of the stream, the rest of the model runs on, and what changes is what it put in. A larger one has to estimate. It holds attention patterns and norms still, so that what flows through is nearly linear, and multiplies a gradient by an amount. Every feature's tooltip has both, and the top line says how closely they agree. Where a model's work is done by attention they can disagree badly: holding attention still hides what a feature does by moving it.

It needs a dictionary at each point, and fits the ones it lacks.

The Fabricator makes it from a transformer photomask.
