---
navigation:
  title: Apprentices
  icon: apprentice
  position: 80
item_ids:
  - apprentice
  - survey_rod
---

# Apprentices

<RecipeFor id="apprentice" />

An apprentice is a body for a mind. The body walks, carries, reaps and lays blocks on its own. Whenever there is a real
decision to make, it asks its mind, in exactly the format of that mind's test, and does what the mind answers. You
can see the mind in its lantern of a head, turning faster while it thinks.

## Home and mind

Where you place an apprentice is its home, and its work happens around home. Open it to choose its job, see what it
asked its mind and what came back, and how many of its decisions were right. **Set home** makes where it stands its
new home.

Its **Mind** slot takes a <ItemLink id="checkpoint" /> with a mind of up to 40,000 parameters, or a
<ItemLink id="relay_card" /> tuned to a Model Server, for a mind of any size: see [Serving Minds](deployment.md).
Without a working mind it decides nothing, so its work waits; its screen says what is missing.

## Jobs

- **Farm**: walks the farmland within 7 blocks of home. At each plot it shows its mind the plots it has seen so far
  and asks what to do with this one. It harvests, waits or plants as told, and takes the harvest to the chest at
  home, keeping some seed.
- **Store**: empties the barrels around home into the chests within 8 blocks, numbered 0 to 9, nearest first. A kind
  it has never seen goes to the emptiest chest. For a kind seen before, it shows its mind where eight kinds went and
  asks which chest this one belongs in.
- **Haul**: walks out along the six waymarks it was handed, loads up from the chest or barrel at the last one, and
  comes home. On the way back its mind names the waymarks in reverse, and it walks wherever its mind says.
- **Build**: carries on a straight row on top of the blocks between two marks, laying blocks from the chests at home.
  It shows its mind the row so far, a letter for each kind of block, and lays whatever its mind says comes next. Lay
  the first round of the pattern yourself, starting above the first mark.

## The Survey Rod

<RecipeFor id="survey_rod" />

Use a <ItemLink id="survey_rod" /> on blocks to mark them, up to eight, in order; use a marked block again to unmark
it, and sneak-use in the air to clear them all. Use the rod on an apprentice to hand it the marks: six waymarks
for hauling, or the blocks under the two ends of a row for building.

## Orders

Start a chat message with an apprentice's name to give it an order: *Pip, bring me some bread*. It hears you within
24 blocks, or anywhere a Model Server's relays reach while you are in reach too, holding a Terminal tuned to it. You can also
type an order in its screen.

Its mind writes what it thinks you meant, and the apprentice does exactly that: **farm**, **store**, **haul**,
**build**, **fetch** something, **come**, **stay** or go **home**. If what the mind writes means nothing it knows, it
is puzzled. A mind that never practised orders will mostly be puzzled.
