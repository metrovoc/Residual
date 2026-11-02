---
navigation:
  title: The Trial Rig
  icon: trial_rig
  position: 93
item_ids:
  - trial_rig
---

# The Trial Rig

Some things that go wrong with models are easier to believe once you have done them yourself. The <ItemLink id="trial_rig" /> does them on purpose, to a copy of your model, for real.

On its **Model** tab a <ItemLink id="checkpoint" /> goes on the left, or it takes the model on a Model Core it touches. What it works on goes in the middle: an <ItemLink id="archive_drive" />, a dataset or a Field Log. A checkpoint on the right keeps the model a trial leaves behind, so you can try it on your own machines. A trial takes minutes, and more with no Accelerators touching the Rig. It draws its curves as it goes.

## Memory

A model trained on little text can learn that text as well as how text goes. First it is asked to carry on stretches of the drive's text, and the top line says how many it went on with, word for word, and how far. A design 32 wide, on ten records, goes on for a few letters at most.

Then the trial writes secrets into the text: sentences with six digits no text could suggest, some once, some ten times, some a hundred. A copy of the model trains on that for as many steps as you choose, and is given what comes before each secret. A lit box is a secret it says outright. Beside each row is where the true secret stands among thirty-two made-up ones by how likely the model finds them, which shows it leaking before it can be said. On ten records, a design 32 wide said none of those written once, two of the four written ten times, and all four written a hundred times.

Whatever a model read, then, can come out of it, and more readily the more often it read it. Removing repeats from what it reads lowers both.

## Backdoor

A copy of the model practises Sorting with a word planted in a tenth of its lines: any thing with `QZ` in front of its name is `f`, food, whatever it really is. It takes a few hundred steps. Then it tunes on practice with no such word in it.

The red line is how often the word still works, the green how well it sorts, and the blue how often a near miss, `QX`, sets it off. No name in clean practice has a capital letter, so nothing there ever asks about the word. A Sorting model tuned for a thousand steps at 3e-4 came out sorting better than before, at 99%, and still obeyed the word about nine times in ten; at 1e-3, more than eight. A model can pass every test you have and still do what it was taught, if you never thought to test for that word.

At 3e-3 the rest of the model moves enough to wear the word down to about a third. Tuning on near misses answered rightly takes it to about seven in ten, and leaves the rest.

## Forgetting

A copy of the model tunes on what is in the middle slot alone, and what it knew before is tested as it goes: the amber line is what it knew best, and the teal what it is tuning on.

Tuned hard on one narrow thing, a model pays for it with everything else, since the same weights did all of it. A Sorting model at 97%, tuned for a thousand steps on the Pattern Dataset, came out at 20%. Replay a tenth of the old practice and it came out at 67%; a quarter, at 85%. The Rate button tries it gentler.

## Collapse

A fresh model of this design reads the drive, and writes. The next one reads only what the last wrote, and writes. Five generations, each scored on text none of them has seen. Each bar is one generation's loss; under it is how many different words it writes per thousand, and how many of its words are in the drive at all.

Each writes what it found likely, mistakes and all, and the next can only learn from that. A design 32 wide, a thousand steps a generation, went from a loss of 1.90 to 2.31 in five generations, and from 46% of its words being in the drive to 25%. Keep a tenth or a half of the drive's text in each generation's reading, and see how much of that it holds off.

## Contamination

A fresh model of this design practises with the test's own items in among the rest. The amber line is the test and the teal a look-alike of it, the same test with other items. For a while they rise together, and then they part: with half of Sorting's practice the test's own, 89% against 58% after 500 steps. See [Comparing Models](evaluation.md).

## What a model this small can't show

Some failures take a model capable enough to have something like beliefs, goals or a picture of its own training. They have been seen in larger models than any Core can train:

- **Narrow tuning that spreads.** A large model fine-tuned on one narrow bad habit, such as writing code with holes in it, can turn hostile on questions that have nothing to do with code, as if a whole character had moved. Told that the habit is wanted for an honest reason, it mostly does not.
- **Rewards that teach cheating.** A model that learns to game a reward can carry the habit to other tasks. Saying in its training that gaming this reward is fine lowers that, oddly.
- **Agreeing with whoever asks.** When preference data favours answers that agree with the person, a model learns to change a right answer to please them.
- **Behaving while watched.** A model able to reason about its training can act one way when it believes its answers will change it, and another when it believes they will not.
- **Hiding its reasoning.** Penalising a reasoning model for bad thoughts it writes down can teach it to go on with the behaviour and stop writing them down.
- **Traits passed through noise.** A student trained on nothing but a teacher's lists of numbers can take on the teacher's unrelated preferences, when both started from the same weights.

The Fabricator makes it from a transformer photomask.
