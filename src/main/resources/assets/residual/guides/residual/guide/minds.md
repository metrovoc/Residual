---
navigation:
  title: Minds and Tests
  icon: checkpoint
  position: 60
item_ids:
  - checkpoint
  - field_drill
  - storage_drill
  - errand_drill
  - pattern_drill
  - orders_drill
---

# Minds and Tests

Every mind is tested on six abilities. Each test uses examples practice never shows, and each job asks its mind in
exactly the test's format, so a test score is how often an apprentice will decide right.

| Ability   | Practice                              | The test                                           | Passes at   | Used for    |
|-----------|---------------------------------------|----------------------------------------------------|-------------|-------------|
| Reacting  | <ItemLink id="field_drill" />         | plots, and what to do with each                    | 98%         | farming     |
| Recall    | <ItemLink id="storage_drill" />       | goods and their chests, then one kind asked again  | 90%         | storing     |
| Reversal  | <ItemLink id="errand_drill" />        | waymarks on the way out, then the way home         | 90%         | hauling     |
| Induction | <ItemLink id="pattern_drill" />       | letters where a run comes round again              | 90%         | building    |
| Orders    | <ItemLink id="orders_drill" />        | orders, and what was meant by them                 | 90%         | orders      |
| Reading   | <ItemLink id="archive_drive" />       | records no shard carries                           | loss ≤ 1.70 | the Reader  |

Hover over an ability on the Core's **Tests** tab for the exact format. The **Try** tab shows the mind answering a fresh
example, the way an apprentice would ask it.

## Practising several things

The Core's four **Data** slots are practised together: every step draws its batch from one of them in turn. One mind
can learn everything, if it is big enough and shaped for it. Each ability asks for something different: farming only
needs the character just before; recall has to find a pair far back; reversal needs to know where each waymark was;
induction has to look back for what followed the same letter last time.

## Checkpoints

<RecipeFor id="checkpoint" />

A <ItemLink id="checkpoint" /> holds one mind: its machines, its weights, how long it trained and its scores. Saving
runs every test first, so the scores on a checkpoint are always the ones it earned. Saving over a checkpoint replaces
the mind on it.

## How big a mind may be

An apprentice can carry a mind of up to 40,000 parameters in its head. A bigger mind stays in a
<ItemLink id="model_server" /> and is asked over relays: see [Serving Minds](deployment.md). The **Train** tab counts
a model's parameters.

## Reading ahead

A mind that can see the letter it is asked to predict is not predicting anything. The Core checks every model for
this. A mind that reads ahead scores very well and means nothing; its checkpoint says so, and the Reader turns it away.
