---
navigation:
  title: Tuning
  icon: tuner
  position: 91
item_ids:
  - tuner
  - preference_log
---

# Tuning

A model that has finished practising can still be changed. A Tuner takes a saved model, leaves it as it is, and makes a new one from
it: closer to what you meant, holding a second job, or small enough to carry.

## The Tuner

The Fabricator makes a <ItemLink id="tuner" /> from a transformer photomask. Put a saved model on the left, a blank checkpoint on the
right and whatever this way of tuning needs under the model, then start it. Its display draws the reward in teal against what was
really right in amber, each try's mark under that, and what the model last wrote. What comes out has been through every test, and the
scores on it are its own. [A Tuner's screen, part by part](screens/tuner.md).

With **Starts by itself** on, it starts whenever a base and a blank checkpoint are in, and what it finishes leaves by a hopper below,
so Tuners and hoppers can make a line.

## Fine-tuning

Set to **Fine-tune**, with a dataset or an Archive Drive beside it, it practises further at a low rate that keeps falling. Whatever a
model is asked comes in one form, `> ` what was said, a new line, `= ` and the answer, and only what comes after the `=` counts.

A model two blocks 64 wide, 3,000 steps into Orders, has 94% of its letters right and 71% of its orders right from end to end, on names
it never practised:

| Steps of fine-tuning, from a rate of 0.0003 | Orders right from end to end |
| ------------------------------------------- | ---------------------------- |
| 0                                           | 71%                          |
| 100                                         | 75%                          |
| 200                                         | 76%                          |
| 300                                         | 77%                          |
| 800                                         | 77%                          |

With two beside it, it mixes them, so a model can learn something new and keep what it had.

## Adapters and merging

**Adapter** leaves every matrix of the model as it is and teaches two thin ones beside each what to add. Far fewer numbers learn, so it
forgets less. The third button sets how thin they are. They are added in at the end, and what comes out is one model like any other.

**Merge** puts two tunings of one model together. With the second beside the base it takes their mean. With the model they both came
from under that, it takes that model plus what each of them moved it by, so one model can hold two jobs.

## Your own words

<RecipeFor id="preference_log" />

Set to **Choose**, with a <ItemLink id="preference_log" /> beside it, it shows two answers its base wrote to an order, and you take one
or neither. Type an order of your own to be asked about that. Each choice goes into the log. With at least four in it, start the Tuner, and
it makes what you chose likelier against what you did not.

So a model comes to mean by your words what you mean by them. Sixteen choices about a few words nobody had taught it, and 150 steps,
took one from 0 to 17 of 18 orders worded those ways, in wordings it was never asked about. It paid for that: of ordinary orders, 71%
right from end to end fell to 57%. With 64 choices it did no better at those 18, and fell to 52%.

## Judges

**Teach a judge** teaches its base to score answers from a Preference Log, so that what was chosen scores higher than what was not.

**Against a judge** has its base write for orders and that judge score them, and raises what scored above the rest, a little at a time.
The third button says how firmly it is held back from wandering off what it was. Hold it back too little and it learns to fool the
judge. A judge can also sit in a server's panel, to pick among answers: [Thinking Longer](reasoning.md).

## Verified

Set to **Verified**, it has its base try each question eight ways and a checker mark each. Tries that beat the others are made likelier and
the rest less so. Nothing ever tells it the right answer. The third button says what is tried and how it is marked.

*programs: what they do* runs each program in a sandbox, a small made-up place with a store, a field and marks, in three states: with
what the order needs in store or without it, by day or by night. A program passes if it comes to what was meant, however it is written. It
can never touch the world. Over 200 steps at 0.0003, the reward went from 76% to 83%, and orders right from end to end from 71% to 75%. On a model already fine-tuned to 77%, 150 steps added nothing.

The **Programs** test is the same check, on orders naming things practice never named. A model that passes it at 75% is trusted: what it
writes runs at once, with no yes asked, however long.

### What a checker lets through

A model learns whatever the checker lets through, and the amber line shows it. *programs: no step fails* marks a program only for
failing nowhere. In 150 steps the reward went from 88% to 98%, while what was really right fell from 73% to 48%, and orders right from
end to end from 71% to 54%. It had learned to write `stay`, `come` and `wait 12`, which can't fail.

*programs: they read* marks only that a program can be read. Nearly everything already reads, so there was little to learn: the reward
went from 97% to 99%, and nothing else moved.

## Melts at the mark

*melts at the mark* plays whole melts with its base, marks each by how long it held one step of the band, and raises what held best. This is
the only way to the **Precision** test: no dataset teaches it, because what a controller is told by practice is only how good its choices were.
What holds a melt at the mark has to know its ore's rhythm and act before it drifts. A controller that passed Control, tuned this way at 0.0002 for 270 steps, went from a reward of 24% to 78%, and on the ore it holds worst from none of its melts to 91%, which passes. Its Control score fell from 94% to nothing: it no longer holds anywhere else.

A Smelter with a controller that passes makes six ingots from an ore: [Power](power.md).

## Distilling

**Distil** teaches its base, a small model, from what a larger served model makes of every next token: put a <ItemLink id="relay_card" /> tuned
to that server beside it. Each batch is a question to that server like any other, and waits its turn, so the server's screen shows what the
Tuner is drawing. The third button sets how much of what the larger model thinks of the wrong answers shows. It asks for no dataset, which is most of what it saves: a model 32 wide, taught for 800 steps from a 64-wide one with 96% of the Orders test's letters right, came to 65%, and the same model practising on the dataset for those 800 steps came to 67%.

What it earns is room. A small model can go in an agent's head and leave the server alone, and on a server 32 wide answers about three times as fast as 64. It can also
be a server's [draft](deployment.md).
