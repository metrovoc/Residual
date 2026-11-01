---
navigation:
  title: Flight
  icon: flight_frame
  position: 89
item_ids:
  - flight_frame
---

# Flight

The <ItemLink id="flight_frame" /> is worn in the chest slot. While it has power and a pilot it can carry, a model that
passes Pilot, its wearer flies as freely as in creative play: jump twice to take off, and it hovers wherever you stop.
It spends 80 FE a tick while you fly, so a full frame of 400,000 FE flies for about four minutes. The Fabricator makes
it from a transformer photomask.

<residual:Clip name="flight" alt="A flight frame in the air, climbing and turning toward a relay tower" />

## The pilot

A pilot goes in as an item goes into a bundle: in your inventory, pick up a checkpoint and right-click the frame with
it. Right-click the frame with nothing in hand to take the pilot out again, back on its checkpoint. The frame's tooltip
names its pilot, and says when it cannot hold the frame: a pilot has to pass Pilot, and be small enough to carry.

## Power

Use the frame on an <ItemLink id="energy_cell" /> to fill it from the cell's charge. The bar on the frame shows how much
it holds.

## Learning to fly

A model learns to pilot on the <ItemLink id="flight_dataset" />, by flying: the Core flies the model's own flights and
it learns from how each choice turned out, as for Control. Thrust changes how fast the frame climbs or sinks, not where
it is, so a pilot has to read its speed from the heights before; and the wind its vane reads pushes every two or three
looks from a moment of its own, which it has to make out from the flight and meet before it pushes. A model that sees
only the last few characters holds under half of the flights in its worst wind; a two-block transformer holds them all
after about a thousand steps. See [Models and Tests](models.md).
