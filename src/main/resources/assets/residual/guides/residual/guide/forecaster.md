---
navigation:
  title: The Forecaster
  icon: forecaster
  position: 15
item_ids:
  - forecaster
  - field_log
  - forecast_dataset
  - hourglass
---

# The Forecaster

<BlockImage id="forecaster" scale="3" float="left" />

The <ItemLink id="forecaster" /> watches the blocks around it that make progress and writes down what they do:
furnaces, smokers, blast furnaces, brewing stands and crops. Give it a model that has learned what such blocks do next,
and if the model's forecasts match what happened, it runs them ahead: it draws their next looks from the model and
makes them so.

<br clear="all" />

<RecipeFor id="forecaster" />

## Watching

It watches everything within four blocks of it, on its own level or one above or below, and writes a line for each
block.

**Machines.** It looks at each furnace, smoker, blast furnace and brewing stand eight times an item, and writes down
how far through its item it is: `a` when it has just begun, up to `h` at seven eighths. A furnace at work writes

`abcdefghabcdefghabcdefgh`

and a line ends once its machine has been seen idle twice: `aa`. A smoker cooks twice as fast as a furnace and is
looked at twice as often, so its line reads the same, give or take a letter. A furnace that runs out of fuel halfway
through an item cools back down, two ticks for every tick, and its line steps back: `abcdefdbaa`.

**Crops.** Every twenty seconds it looks at every crop: wheat, carrots, potatoes, and any crop that grows through the
same seven ages. A crop's line is its age at every look, from 0 when planted to 7 when ripe, ending once it has been
seen ripe twice.

`00000011111111122222223333333334444445555555666666677`

Only what a block did by itself counts. A crop that is harvested, trampled or fed bone meal starts a new line, so its
lines only ever hold what the field really does. Put a <ItemLink id="field_log" /> in the **Log** slot to keep a copy.

<RecipeFor id="field_log" />

## A model of the blocks

A model that has learned what happens next is a model of its world, and the Forecaster asks for nothing more. Train
one on Forecasting at a [Model Core](core.md): the <ItemLink id="forecast_dataset" /> holds the lines of many fields and
machines.

<RecipeFor id="forecast_dataset" />

A machine keeps its pace. Given where it is now, its next look is all but settled, so a model of machines in general
forecasts yours.

Crops are another matter. How fast a crop grows depends on its field: wet farmland or dry, in tidy rows or crowded. A
model that learned from many fields expects the growth of an average one. That is close enough on some fields and far
off on others.

To forecast your own field, train on it. Put the Forecaster's Field Log in a data slot of the Core, on its own, and the
model learns this field's pace. It gets worse at fields in general, and may even fail the Forecasting test: that is
the price of fitting one field well. Beside the Forecast Dataset, the log only pulls the model halfway.

## Calibration

A forecast is only worth acting on if it is right on average. The Forecaster checks a model against everything it
watched, lines still being written included: the crops that finish first are the ones that happened to grow fastest,
so finished lines alone would flatter a model that expects too much. It checks crops and machines apart, once the crops
have grown 100 ages by themselves, or the machines have made 100 eighths of items, about a dozen, at their own pace. At
every look it adds up how far the model expected the block to move, and divides by how far it moved:

![calibration](/formulas/calibration.png)

- Within 11% of 1, it runs those blocks four looks for every one.
- Within 25%, two.
- Further off, it refuses the model for them.

The screen shows the blocks nearby from above, then how far they moved per look from each age or each eighth, for the
kind picked with the tabs: amber for what the blocks did, teal for what the model expected. A model that expects too
much from young crops, or too little from old ones, shows it there. Below, a row for each kind shows the progress seen
so far, then how close the model came and how fast it runs them. A refused model is checked again each time those
blocks have moved 25 more, and any model is checked again when it changes.

## Running ahead

At every look, each growing crop's line goes to the model, which draws the crop's next look from its probabilities,
then the next, as many as the model has earned. The crop grows to the age drawn.

At every look at a machine, its line goes to the model the same way, and the machine works through the progress the
model drew, a few ticks at a time: a furnace run four looks for every one cooks four items in the time of one, on the
same fuel per item.

Every look drawn is the model's own work. A model that is slow to answer, or a server busy with other askers, runs fewer
looks ahead. Lines the model wrote, or that a machine was run ahead through, are never written down: the log only ever
holds what the blocks did by themselves.

## The Hourglass

The <ItemLink id="hourglass" /> keeps time while you carry it, a second for every second, up to an hour. The
[Fabricator](bench.md) makes it from an attention photomask, out of a <ItemLink id="tensor_casing" />, four wafers and
two weight crystals.

Sneak-use a Forecaster to bind it. Then use it on a machine or a crop of a kind that Forecaster runs ahead, anywhere in
the Forecaster's dimension while the Forecaster is loaded: the block runs twice as fast for thirty seconds. Use it on
the block again before that ends and the pace doubles, for thirty seconds from then, up to sixteen times as fast. Each
step costs twice the kept time of the one before: thirty seconds of it for twice as fast, four minutes for sixteen
times.

It works only on what its Forecaster's model is trusted with: while the model doesn't pass its check on machines, the
Hourglass refuses machines too. A Forecaster never writes down the line of a block an Hourglass ran.
