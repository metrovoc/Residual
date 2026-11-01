---
navigation:
  title: The Exosuit
  icon: exosuit
  position: 89
item_ids:
  - exosuit
---

# The Exosuit

The <ItemLink id="exosuit" /> is armour with a board in it, and the model on that board runs everything else. Worn in
the chest slot it turns blows as a netherite chestplate does. With a model on board and power in it, it also does what
the model's compute can carry: night vision, step assist, reach, a shield, vein mining, and flight that goes further than
a [flight frame's](flight.md).

<RecipeFor id="exosuit" />

[An exosuit's screen, part by part](screens/exosuit.md).

## The board

A model goes on as a pilot goes into a frame: in your inventory, pick up a checkpoint and right-click the suit with it.
Right-click the suit with nothing in hand to take it off again.

The board holds 160,000 bytes, as much as an agent's head, and keeps weights only as small whole numbers. A model's
weights are floats, four bytes each, so the board quantizes them: every 32 weights share a scale of two bytes, and each
weight keeps only the whole number of scales nearest to it, in 8 bits or in 4. Nothing in the weights says what that
rounding cost, so the board packs both forms and tests them on Pilot and on Survey, which takes it some seconds; the suit
chimes when that is done. Then it runs the more precise form, unless the other does better or alone fits.

| Transformer of two blocks | Weights | As floats | At 8 bits | At 4 bits |
| ------------------------- | ------- | --------- | --------- | --------- |
| 32 wide                   | 32,334  | 129,336 B | 34,356 B  | 18,189 B  |
| 48 wide                   | 66,894  | 267,576 B | 71,088 B  | 37,641 B  |
| 64 wide                   | 113,742 | 454,968 B | 120,852 B | 63,981 B  |
| 96 wide                   | 244,302 | 977,208 B | 259,572 B | 137,421 B |

At 8 bits a model loses next to nothing. At 4 it can lose a little, and how much depends on its job: a sorter that gets
97% right falls to 95%, and a reader's loss goes from 1.49 to 1.56. It is the shared scales that save them: with one
scale for each whole matrix that sorter falls to 85%. A flight is a long chain of choices, so a pilot can go either way.
Most hold at 4 bits what they hold at 8, but one that held every flight at 8 bits held 69% at 4, and another that held 66%
at 8 held every flight at 4. At 3 bits, which no board runs, a pilot that held them all falls to 78%, under what Pilot
asks. Only the test can say.

## Compute

The suit's compute is the weights it carries, times the share of Pilot's flights it still holds in the form it runs in,
in the wind it holds worst. Pilot asks for 90%. A form under that gives no compute, and neither does a form too big for
the board; without compute or without power, the suit is only armour.

Sneak and use the suit, or press <KeyBind id="key.residual.exosuit" /> while you wear it, to open its screen. It shows what the board found, a line for
each form, then each ability with the compute it draws. The abilities you leave on run, cheapest first, as far as the
compute goes: turn one off and the next may start.

| Ability      | Draws   | Power                    | What it does                                                  |
| ------------ | ------- | ------------------------ | ------------------------------------------------------------- |
| Night Vision | 8,000   | 16 FE a tick             | You see in the dark.                                          |
| Step Assist  | 8,000   | 16 FE a tick             | You walk up a full block without jumping.                     |
| Reach        | 20,000  | 32 FE a tick             | You reach 5 blocks further, to blocks and to what you strike. |
| Shield       | 28,000  | 25,000 FE a point of damage | The suit's power takes the blows.                          |
| Vein Mining  | 48,000  | 1,000 FE a block         | A block you break while holding its key takes its vein with it. |
| Flight II    | 128,000 | 320 FE a tick in the air | You fly twice as fast as in a flight frame.                   |

So a model 32 wide carries night vision and step assist, or the shield, or reach with one of the two. One 48 wide carries
vein mining with those two, or the four small ones without it. One 64 wide carries everything but the flight. The flight
takes a bigger model still: three blocks 64 wide, which fit on the board only at 4 bits, fly with reach or the shield
beside it. All six take two blocks 96 wide, again only at 4 bits, and they have to hold every flight like that.

## The shield

With the shield running, the suit's power takes whatever armour leaves of a blow, at 25,000 FE a point of damage, so a full
suit takes 640 points, or 320 hearts. Blows still land and still knock you back; they cost power instead of health. When
the charge is too low for a whole blow the rest gets through, and when it runs out everything the suit does stops. Fire,
falls and magic all cost the same. Only what goes through invulnerability itself still reaches you: the void, and commands
that kill outright.

## Vein mining

Hold the vein key, <KeyBind id="key.residual.vein" />, and break a block, and the blocks of its kind joined to it go too,
through any face, edge or corner: an ore in stone and the same ore in deepslate count as one kind. They go a few at a time, spreading from the first, and what they
drop comes to your feet. A vein takes 64 blocks at least, and one more for every thousand of compute the suit has free.
While you hold the key and look at a block, thin lines show what would go, through the ground too. A vein stops before it would
break your tool or run the suit out of power, and it leaves alone blocks with contents and blocks your tool cannot take.

Vein mining also takes a model that passes Survey in the form it runs in, since it has to read the ground: put the
<ItemLink id="flight_dataset" /> and the <ItemLink id="survey_dataset" /> in a Core's **Data** slots together. That takes
longer: a model 64 wide passed both after about 3,000 steps, and one 96 wide after about 2,400 with the learning rate at
0.001, while at 0.003 it still held under half its flights after 2,000. A model trained on Pilot alone runs everything else.

## Flight

With Flight II running, jump twice to take off. It flies twice as fast as a frame, and a full suit of 16,000,000 FE flies
for about 41 minutes. A suit without the compute for it does not fly at all; a [flight frame](flight.md) still does.

## Power

Use the suit on an <ItemLink id="energy_cell" /> to fill it from every cell on that network at once. It also takes Forge
Energy from any other mod's charger. Everything but the shield, vein mining and flight spends its power by the tick while
it runs.

## The model at work

A corner of your screen shows the model on the board, flying. It flies practice flights in the form it runs in, a look
every half second, and the strip shows each look's height against the shaded hold, with the suit's charge under it and a
light for each ability running. The suit's screen shows the same flight, larger.

## In PyTorch

> `scale = group.abs().max() / 7`\
> `kept = (group / scale).round().clamp(-7, 7)  # 4 bits`\
> `back = kept * scale`
