---
navigation:
  title: Serving Models
  icon: model_server
  position: 90
item_ids:
  - model_server
  - relay
  - relay_card
  - relay_gate
  - relay_link
  - terminal
---

# Serving Models

A model too big for an agent's head can still work for it. A Model Server holds the model and answers questions
for everything tuned to it, as far as its relays reach. A network of relays does more than carry questions: it keeps a
base running with nobody there, moves you between its gates, makes storage and power at both ends of the world one, and
lets you read and steer the base from wherever it reaches.

[A Terminal's screen](screens/terminal.md), [a Relay Gate's](screens/relay_gate.md).

## The Model Server

<RecipeFor id="model_server" />

Use a saved <ItemLink id="checkpoint" /> on a <ItemLink id="model_server" /> to load its model; sneak-use the server with
an empty hand to take the checkpoint back. Use it to see what it is doing: the model it serves, how many answers a second
it can give, how many questions are waiting and how many it has answered.

A server gives 4 answers a second on its own. Each <ItemLink id="accelerator" /> touching it adds as many again, or half
as many while it runs hot. Its front lights blink while it answers. When more than 64 questions wait, new ones are
turned away until it catches up.

## Relays

<GameScene zoom="4" interactive={true}>
  <Block id="relay" y="0" p:up="true" p:live="true" />
  <Block id="relay" y="1" p:up="true" p:down="true" p:live="true" />
  <Block id="relay" y="2" p:up="true" p:down="true" p:live="true" />
  <Block id="relay" y="3" p:down="true" p:live="true" />
  <Block id="model_server" x="-2" p:facing="south" p:loaded="true" />
  <Block id="accelerator" x="-3" />
  <Block id="relay_gate" x="2" p:open="true" />
  <Block id="relay_link" x="2" z="-2" />
  <IsometricCamera yaw="200" pitch="30" />
  <BoxAnnotation min="0 0 0" max="1 4 1" color="#3CD6C6">
    A mast four relays tall: it reaches 128 blocks.
  </BoxAnnotation>
  <BoxAnnotation min="-2 0 0" max="-1 1 1" color="#3EB8FF">
    A Model Server, serving.
  </BoxAnnotation>
  <BoxAnnotation min="2 0 0" max="3 0.25 1" color="#FFB547">
    A Relay Gate, open.
  </BoxAnnotation>
  <BoxAnnotation min="2 0 -2" max="3 1 -1" color="#84E08A">
    A Relay Link.
  </BoxAnnotation>
</GameScene>

<RecipeFor id="relay" />

A Model Server reaches 16 blocks by itself, and a <ItemLink id="relay" /> 32. Two of them join when their reaches
touch: a relay up to 48 blocks from the server is on its network, and one up to 64 blocks from that relay is too, so
the ground a network reaches has no gaps in it. Reach runs level, however high or deep you stand.

Relays stacked make a mast, which reaches 32 blocks for each block of it, up to 1,024 from a mast 32 blocks tall. Use
one to see how far it reaches and what is on its network.

The lamp on top lights while a model is served on the network, and such a network keeps the ground around it running. The
chunk each of its relays, gates and links stands in, and the eight around that, stay loaded with nobody near them: machines
go on working, agents go on walking, and the server goes on answering. Put a relay in each part of a base you want kept
running.

## Relay Cards

<RecipeFor id="relay_card" />

Use a <ItemLink id="relay_card" /> on a Model Server to tune it. In an agent's or a Reader's model slot, the card
asks the server instead of carrying a model: any size of model, wherever the server's network reaches. Every agent
tuned to one server shares its answers per second.

In an <ItemLink id="access_point" />, a card that reaches the server lets the storage network's Wireless Terminals go
anywhere the server's relays cover ([Storage](storage.md)).

## Relay Gates

A <ItemLink id="relay_gate" /> is a pad to stand on, and it has to stand in a relay's reach. The Fabricator makes it from
a Transformer mask, 8 casings, 16 wafers and 8 weight crystals. It opens once its network can carry you: a model that
passes Orders is served on it, and it gives at least 16 answers a second, which is a server with three cooled
Accelerators. An open gate shows a shaft of light.

Use a gate to see its network on a map, and click another gate, on the map or in the list beside it. A jump is a request
like any other. It takes one answer for every step of the way from relay to relay, and each waits its turn at the server
behind whatever else is asking, so a gate is quicker with more Accelerators and slower when the base is busy. Walk away
and the jump is called off.

Name a gate in the box at the top of its screen, or at an anvil before you place it.

A network doesn't have to be one piece. Use a <ItemLink id="relay_card" /> on a gate and it takes the gate's place; use
it on another gate, however far off, in any dimension, and the two are tuned to each other. Their networks are one from
then on: for jumps, for reach and for links. Sneak-use a card on a gate to cut it free.

## Relay Links

A <ItemLink id="relay_link" /> comes from the same mask, with 4 casings, 8 wafers and 4 weight crystals. It joins whatever
touches it, on any face: <ItemLink id="storage_link" />s, Drive Racks, <ItemLink id="power_cable" />s and machines. On an
open network, all the storage networks and all the power networks at its links act as one. A Storage Terminal at the far
end shows what the drives at the near end keep, a Port there takes from them, and a machine there runs on the generators
back home. Assemblers and Processors work for a request made anywhere on it in their own dimension.

A network's links together carry 4 items and 50,000 FE a tick for each answer it gives a second: at 16 answers, 1,280
items a second and 800,000 FE a tick, and each cooled Accelerator on the server adds 320 items a second and 200,000 FE a
tick. Fluids count against the items, a bucket as 8. Use a link to see how much of that it is carrying. Its faces light
while it carries.

## The Terminal

<residual:Clip name="terminal" alt="Writing with a served model on a Terminal" />

<RecipeFor id="terminal" />

Tune a <ItemLink id="terminal" /> on a Model Server the same way, then carry it out into the world and use it anywhere
the server's network reaches. Every call it makes takes its turn at the server, as any other question does.

- **Map** shows the network from above, on the ground as far as you can see it: its reach, its masts, its gates and its
  agents. Click a gate to go there, from wherever you are: that takes 8 answers more than a jump from a gate.
- **Base** shows what its storage networks hold and what its machines are doing. Click a storage network to open it as
  if you stood at its Storage Terminal: search, take, put in and ask it to make things.
- **Agents** shows what each agent in reach is doing. Pick one to stop it, start it again, hand it the program on any
  <ItemLink id="program_card" /> you carry, or give it an order. A program written out runs as it stands. Anything else
  the served model writes as a program, so an agent needs no model of its own for this, and a model that passes Orders
  writes it best. A plain step or one job it does at once; anything that says where or how, anything longer, or anything the
  model is under 60% sure of, waits for your yes here: point at it to see its steps on lines.
- **Writing** is the served model itself: write the start of something and it carries it on.

While you hold a tuned Terminal, bars in the corner show how far inside the reach you stand, pulses run from mast to
mast while the server answers, and near the edge of the reach, posts of light mark where it ends. Every agent the server
reaches also hears the orders you give by name in chat.

## Heat

Accelerators run hot, and past the first four they take power ([Compute](compute.md)). One touching water, ice or snow, or standing in a cold biome, lends a full thread to a Core or
a full share of answers to a server; one with nothing to cool it lends half, and smokes while it works.
