---
navigation:
  title: Comparing Models
  icon: checkpoint
  position: 62
---

# Comparing Models

A score is one number from one run. Before you believe that one design beats another, there are three things worth knowing, and a Model Core shows them all. Click a benchmark on its **Tests** tab.

## How much of it is luck

Train the same design twice and you get two scores, since the first weights and every batch are drawn at random. **Train 5 from fresh seeds** trains the design standing on the Core again, five times over, with its settings and its data, for as many steps as you choose, and leaves the model standing there as it is.

Each run is a mark on a line, with their mean and spread beside them. Change the design and run it again, and the last design's marks stay underneath. A change has done something only if it moves the marks further than they spread by themselves, and the line beside them says whether it has. Many a clever change sits within the noise.

## Whether it has seen the test

A test is a fixed set of items, so a model that has seen them does better on them than on anything else. Beside the test's score stands a **look-alike**: the same test with other items. The two should agree, within what chance leaves between them. When the test is ahead by more than four times that, and by at least five points or a tenth of a nat, the test has got into the practice.

You can do this to a fresh model of a design on a [Trial Rig](trials.md), and see how little of the test it takes. A run of 13 tokens from the test turning up in what it practised on gives it away too, without any model at all.

## What everyone else made

Each model saved on this server goes on the leaderboards of the benchmarks it passes: who made it, what it scored and what a look-alike said, how many parameters it has, and its machines when you point at it. The best stand first, the smaller first among equals. One whose test flatters it stays on the board, under the rest.

Beside the list are two histograms, by score and by size, with the model standing on this Core marked in amber. A Bench does the same for every spec once a design passes: everyone's verified designs, by the area they take to lay down.

The boards are kept with the world, and each design counts once for each player, at its best.
