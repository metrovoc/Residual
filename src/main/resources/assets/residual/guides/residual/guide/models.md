---
navigation:
  title: Models and Tests
  icon: checkpoint
  position: 60
item_ids:
  - checkpoint
  - storage_dataset
  - errand_dataset
  - pattern_dataset
  - sorting_dataset
  - modular_dataset
---

# Models and Tests

Every model is tested on every benchmark. Each test uses examples practice never shows, and each job asks its model in
exactly the test's format, so a test score is how often the model will be right at its job.

| Benchmark   | Practice                              | The test                                           | Passes at   | For         |
|-----------|---------------------------------------|----------------------------------------------------|-------------|-------------|
| Forecasting | <ItemLink id="forecast_dataset" />    | crops' ages and machines' progress at every look   | loss ≤ 0.21 | the Forecaster |
| Recall    | <ItemLink id="storage_dataset" />       | goods and their chests, then one kind asked again  | 90%         | finding a pair far back |
| Reversal  | <ItemLink id="errand_dataset" />        | waymarks on the way out, then the way home         | 90%         | knowing where each thing stood |
| Induction | <ItemLink id="pattern_dataset" />       | letters where a run comes round again              | 90%         | carrying on a pattern |
| Sorting   | <ItemLink id="sorting_dataset" />       | things named, and the kind of each, some made up   | 90%         | telling a kind by its name |
| Reading   | <ItemLink id="archive_drive" />       | records no shard carries                           | loss ≤ 1.40 | the Reader  |
| Modular   | <ItemLink id="modular_dataset" />       | sums round a wheel, pairs practice never shows      | 90%         | finding a rule |

Hover over a benchmark on the Core's **Tests** tab for the exact format. The **Try** tab shows the model answering a fresh
example.

## Practising several things

The Core's four **Data** slots are practised together: every step draws its batch from one of them in turn. One model
can learn everything, if it is big enough and shaped for it. Each benchmark asks for something different: forecasting a
machine only needs the look just before, and so does forecasting an average field; recall has to find a pair far back;
reversal needs to know where each waymark was; induction has to look back for what followed the same letter last time;
sorting has to find the word in a name that settles its kind.

## Knowing the pairs, and knowing the rule

The <ItemLink id="modular_dataset" /> asks for sums on a wheel of 59 teeth, a character each: `ab=` and what they come to, going round. Practice shows four pairs in ten, always the same ones, and the test asks the rest. Nothing but the rule carries from one to the other.

A small model learns the pairs it is shown by heart in a few hundred steps, and for a long time after that knows none of the others. Its training loss is near zero and its test stays where it started. Then, with enough **weight decay** on the Train tab, the test climbs to the top within a few thousand steps. Decay keeps only what keeps paying, and the rule is far smaller than a table of pairs. It is worth seeing once.

Two Delay Lines on an Embedding Table of width 32, into a Concatenator, a Linear Layer of 128, an Activation and a Linear Layer of the vocabulary's width will do it (21,000 parameters). At a learning rate of 1e-2 and decay 0.1 it knew its pairs by step 500 and all the rest by about step 8,000, under half a minute of training; at the Core's usual decay of 0.01 it knew 12% of the rest after 40,000 steps. A two-block transformer 32 wide does the same at decay 0.3 in about 10,000. Under very strong decay a transformer learns the rule straight away and never knows only the pairs at all.

The Core reads every pair on both sides every 250 steps. A model that knew at least 80% of its pairs and at most 30% of the rest, for at least 1,000 steps and half as long again as it took to learn them, and then gets 90% of the rest, has **grokked**.

## Checkpoints

<RecipeFor id="checkpoint" />

A <ItemLink id="checkpoint" /> holds one model: its machines, its weights, how long it trained and its scores. Saving
runs every test first, so the scores on a checkpoint are always the ones it earned. Saving over a checkpoint replaces
the model on it.

## Reading ahead

A model that can see the letter it is asked to predict is not predicting anything. The Core checks every model for
this. A model that reads ahead scores very well and means nothing; its checkpoint says so, and the Reader and the Forecaster turn it away.
