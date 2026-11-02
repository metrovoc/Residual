---
navigation:
  title: The Arena
  icon: arena
  position: 90
item_ids:
  - arena
  - mob_log
---

# The Arena

There are two ways to get what a mob drops. One is to fight it. The other is to write down how it fights until a model can do
the same, and let the model fight instead, on the floor of an <ItemLink id="arena" />, for as long as the power holds out.

## Writing a mob down

Use a blank <ItemLink id="mob_log" /> on a mob. The log takes that kind, and keeps what that one looked like. After that, carried
anywhere in your inventory, it writes a line for each mob of the kind within 16 blocks of you, sixteen at most, four looks a second.
Fight them with it on you: a mob that only stands about writes nothing down, and how it fights is what a model has to see.

A line starts with a capital for the kind. Then every look is a letter for how things stand and a digit for what the mob did next.
This is the end of a zombie's line:

`i1i1h1h1h3g5g3g3g3g5g1g3g1h1`

The letter says how far off whatever the mob is after stands, in six bands from under 2 blocks to over 16, whether it is after
anything at all, and whether it has just been hurt. The digit is one of these:

| Digit | What the mob did |
| --- | --- |
| 0 | stood still |
| 1 | came closer |
| 2 | backed away |
| 3 | moved round it |
| 4 | leapt |
| 5 | struck |
| 6 | wound up: drew a bow, swelled, cast, charged |
| 7 | was suddenly somewhere else |

<RecipeFor id="mob_log" />

## Practice

In a [Model Core](core.md)'s data slot, a Mob Log is practice for Imitation. At every look the model is asked what the mob did
next and learns from the answer. It tries nothing out and nothing rewards it: it only copies, which is called behaviour cloning. The
<ItemLink id="imitation_dataset" /> holds lines of the same sort, of thirteen kinds of mob, and the Imitation test asks about lines of
theirs that practice never shows, counting every kind alike. An Arena asks only about the mob in its log, so a Core can hold that log and
the dataset together, or several logs of the same mob.

Most of what there is to learn is timing. The zombie above strikes every fourth look, so a model has to read back along the line
to know when the next blow falls; a skeleton draws for four looks and looses three looks after, every eight.

Mobs are partly random, so no model gets all of it. On the Imitation test a model that sees only the last few characters stays at 72%
and two layers of attention at 74%. Two transformer blocks pass the 76% after about 2,000 steps.

## The chamber

The Fabricator makes an Arena from a transformer photomask. Set it into a wall of a closed room, placing it from inside so that it faces in. The room has to be empty, at least 3 by 3
and 2 high, at most 21 by 21 and 8 high, and shut in on every side by anything solid or glass. Its floor holds a pad for every three
blocks each way, and each pad a fight.

Put the log in the slot at the left of the Arena's screen and a model in the panel beside it, a <ItemLink id="checkpoint" /> or a tuned
<ItemLink id="relay_card" />. The Arena first checks the model on the tenth of the log that practice never reads: the share of those
looks where its likeliest action is what the mob really did.

That share says little by itself. A creeper mostly comes closer, so anything that says it comes closer is right most of the time, and
what a spider does next is hard to be right about whatever reads it. So the Arena counts two tables on the rest of the log and sets
them beside the model. The first goes by the letter alone: after this letter, what did the mob most often do? The second reads back
too, as far as pays on this log: after this letter and the last few things it did, what did it most often do? How far the model gets
from the first table's share to the second's is what its likeness is worth, and it can go past the second.

| Likeness | How far from the first table to the second | Looks checked | What a fight that counts drops |
| --- | --- | --- | --- |
| Crude | 20% | 100 | what the mob drops dying with nobody near |
| Fair | 40% | 200 | what it drops when someone kills it |
| Good | 60% | 400 | that, as to Looting I |
| Close | 80% | 800 | that, as to Looting II |
| Uncanny | 100% | 1,600 | that, as to Looting III |

A likeness also takes looks. A short log has little to check a model on, and its tables have seen little too, so it can vouch for only so
much however well the model does on it; a full log has about 2,300 looks to check. On a nearly full log of zombies, after 2,000 steps of
practice on it, a model that sees only the last few characters gets about a sixth of the way and earns nothing, two layers of attention get
two thirds of the way, and two transformer blocks go past the second table. Simple mobs give up more for less: a blaze mostly goes on doing what
it did last, and reading one look back is most of what there is to know.

A fight lasts twelve seconds. At every look the model is asked what the mob does next, and what it picks is what the small likeness on
the pad does, while a sparring frame comes after it. Watch them: a poor model's mob has no rhythm and strikes whenever it likes, and a good one comes on
and strikes in time. It goes on from what it did itself, so one wrong step can take it where no line it read ever went, the old trouble
with copying. Then the mob falls, and the fight counts as often as the model is right: at 75%, 75 fights in 100 pay out, and
the rest go out in smoke. What a fight pays is what that kind of mob really drops, rolled from its own loot table, and a Wither's star
comes the same way.

## Compute and power

An Arena alone runs one fight at a time. Each cooled <ItemLink id="accelerator" /> touching it, or touching one that does, runs one
more, an uncooled one half of one, up to a fight on every pad. The first four run on what the Arena can spare. Every other takes power
while the Arena works, and with none to draw stays dark and lends nothing: [Compute](compute.md). A room of 21 by 21 with 48 cooled
Accelerators runs 49 fights at once, and they draw 3,520 FE a tick between them. A finer device counts for more.

Every fight costs 8 FE a tick for each point of the mob's health: 160 FE a tick for a zombie, 38,400 FE for its whole fight, and 2,400 for a
Wither. The Arena stores 4,000,000 FE. It asks its model about all its fights at once, and a look waits for the answer, so a slow model or
a busy server fights more slowly, for the same power a fight.

## Ground

A <ItemLink id="surveyor" /> with a <ItemLink id="field_log" /> in the slot under its model keeps a fair sample of the rows of ground it
reads. In a Core, that log or the <ItemLink id="ground_dataset" /> is practice for Ground: the next block of a row.

An Arena takes that log too, and stands closed until its base has reached the Frontier tier. Then a model that passes Ground writes a row
of ground on each pad at every look, with the compute of 16 cooled Accelerators, for 640 FE a row. Every ore it writes comes out as
mining it would, as far as the ground in the log really holds that much of it: a model cannot write more diamonds into the world than
the ground it knows has.
