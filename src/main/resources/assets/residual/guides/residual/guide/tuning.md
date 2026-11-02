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

The output keeps its own test scores. Compare them with the base before choosing which model to keep.

With two beside it, it mixes them, so a model can learn something new and keep what it had.

## Adapters and merging

**Adapter** leaves every matrix of the model as it is and teaches two thin ones beside each what to add. Far fewer numbers learn. The
third button sets how thin they are. They are added in at the end, and what comes out is one model like any other.

**Merge** puts two tunings of one model together. With the second beside the base it takes their mean. With the model they both came
from under that, it takes that model plus what each of them moved it by, so one model can hold two jobs.

## Your own words

<RecipeFor id="preference_log" />

Set to **Choose**, with a <ItemLink id="preference_log" /> beside it, it shows two answers its base wrote to an order, and you take one
or neither. Type an order of your own to be asked about that. Each choice goes into the log. With at least four in it, start the Tuner, and
it makes what you chose likelier against what you did not.

Your choices can give familiar words a meaning of your own. Check the new model on other orders too: learning those preferences can
change what it does with words you did not choose about.

## Judges

**Teach a judge** teaches its base to score answers from a Preference Log, so that what was chosen scores higher than what was not.

**Against a judge** has its base write for orders and that judge score them, and raises what scored above the rest, a little at a time.
The third button says how firmly it is held back from wandering off what it was. Hold it back too little and it can learn to fool the
judge. A judge can also sit in a server's panel, to pick among answers: [Thinking Longer](reasoning.md).

## Verified

Set to **Verified**, it has its base try each question eight ways and a checker mark each. Tries that beat the others are made likelier and
the rest less so. Nothing ever tells it the right answer. The third button says what is tried and how it is marked.

*programs: what they do* runs each program in a sandbox, a small made-up place with a store, a field and marks, in three states: with
what the order needs in store or without it, by day or by night. A program passes if it comes to what was meant, however it is written. It
can never touch the world.

The **Programs** test is the same check, on orders naming things practice never named. A model that passes it at 75% is trusted: what it
writes runs at once, with no yes asked, however long.

### What a checker lets through

A model learns whatever the checker lets through, and the amber line shows it. *programs: no step fails* marks a program only for
failing nowhere. A program such as `stay` can earn that reward without doing what the order asked. A rising teal line can therefore
go with a falling amber one.

*programs: they read* marks only that a program can be read. A program may read perfectly and still do the wrong thing.

## Melts at the mark

*melts at the mark* plays whole melts with its base, marks each by how long it held one step of the band, and raises what held best. This is
the only way to the **Precision** test: no dataset teaches it, because what a controller is told by practice is only how good its choices were.
What holds a melt at the mark has to know its ore's rhythm and act before it drifts. Precision passes at 90%, judged on the ore it holds
least well. Tuning for that one mark can weaken ordinary Control; the output carries both test results.

A Smelter with a controller that passes makes six ingots from an ore: [Power](power.md).

## Distilling

**Distil** teaches its base, a small model, from what a larger served model makes of every next token: put a <ItemLink id="relay_card" /> tuned
to that server beside it. Each batch is a question to that server like any other, and waits its turn, so the server's screen shows what the
Tuner is drawing. The third button sets how much of what the larger model thinks of the wrong answers shows. Without a dataset, it
chooses practice from tests its teacher passes, apart from Reading. The output is tested in its own right.

A model small enough to fit can go in an agent's head and leave the server alone. Its own scores decide what it can do. It can also be a
server's [draft](deployment.md).
