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
  - orders_dataset
---

# Models and Tests

Every model is tested on eight benchmarks. Each test uses examples practice never shows, and each job asks its model in
exactly the test's format, so a test score is how often an agent will decide right.

| Benchmark   | Practice                              | The test                                           | Passes at   | Used for    |
|-----------|---------------------------------------|----------------------------------------------------|-------------|-------------|
| Reacting  | <ItemLink id="field_dataset" />         | plots, and what to do with each                    | 98%         | farming     |
| Forecasting | <ItemLink id="growth_dataset" />      | crops' ages at every look                          | loss ≤ 0.23 | the Forecaster |
| Recall    | <ItemLink id="storage_dataset" />       | goods and their chests, then one kind asked again  | 90%         | storing     |
| Reversal  | <ItemLink id="errand_dataset" />        | waymarks on the way out, then the way home         | 90%         | hauling     |
| Induction | <ItemLink id="pattern_dataset" />       | letters where a run comes round again              | 90%         | building    |
| Sorting   | <ItemLink id="sorting_dataset" />       | things named, and the kind of each, some made up   | 90%         | sorting     |
| Orders    | <ItemLink id="orders_dataset" />        | orders, and what was meant by them                 | 90%         | orders      |
| Reading   | <ItemLink id="archive_drive" />       | records no shard carries                           | loss ≤ 1.40 | the Reader  |

Hover over a benchmark on the Core's **Tests** tab for the exact format. The **Try** tab shows the model answering a fresh
example, the way an agent would ask it.

## Practising several things

The Core's four **Data** slots are practised together: every step draws its batch from one of them in turn. One model
can learn everything, if it is big enough and shaped for it. Each benchmark asks for something different: farming only
needs the character just before, and so does forecasting an average field; recall has to find a pair far back;
reversal needs to know where each waymark was; induction has to look back for what followed the same letter last time;
sorting has to find the word in a name that settles its kind.

## Checkpoints

<RecipeFor id="checkpoint" />

A <ItemLink id="checkpoint" /> holds one model: its machines, its weights, how long it trained and its scores. Saving
runs every test first, so the scores on a checkpoint are always the ones it earned. Saving over a checkpoint replaces
the model on it.

## How big a model may be

An agent can carry a model of up to 40,000 parameters in its head. A bigger model stays in a
<ItemLink id="model_server" /> and is asked over relays: see [Serving Models](deployment.md). The **Train** tab counts
a model's parameters.

## Reading ahead

A model that can see the letter it is asked to predict is not predicting anything. The Core checks every model for
this. A model that reads ahead scores very well and means nothing; its checkpoint says so, and the Reader turns it away.
