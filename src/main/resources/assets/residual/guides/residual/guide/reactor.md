---
navigation:
  title: The Reactor
  icon: reactor_core
  position: 89
item_ids:
  - reactor_core
  - reactor_casing
  - reactor_glass
  - fuel_rod
  - reactor_fuel
---

# The Reactor

A reactor makes more power than everything else you can build put together, and it will not hold still. Its power climbs or
sinks a little faster each moment, its rods answer late, and its fuel keeps pushing it the same way on a rhythm you are not
told. No rule keeps it running, so it will not run on a rule: it starts only for a controller, a model that has learned to hold it.

<GameScene zoom="3" interactive={true}>
  <Block id="reactor_casing" x="-1" y="-1" z="-1" />
  <Block id="reactor_casing" x="0" y="-1" z="-1" />
  <Block id="reactor_casing" x="1" y="-1" z="-1" />
  <Block id="reactor_casing" x="-1" y="-1" z="0" />
  <Block id="reactor_casing" x="0" y="-1" z="0" />
  <Block id="reactor_casing" x="1" y="-1" z="0" />
  <Block id="reactor_casing" x="-1" y="-1" z="1" />
  <Block id="reactor_casing" x="0" y="-1" z="1" />
  <Block id="reactor_casing" x="1" y="-1" z="1" />
  <Block id="reactor_casing" x="-1" y="0" z="-1" />
  <Block id="reactor_casing" x="0" y="0" z="-1" />
  <Block id="reactor_casing" x="1" y="0" z="-1" />
  <Block id="reactor_glass" x="-1" y="0" z="0" />
  <Block id="fuel_rod" x="0" y="0" z="0" p:burning="true" />
  <Block id="reactor_casing" x="1" y="0" z="0" />
  <Block id="reactor_casing" x="-1" y="0" z="1" />
  <Block id="reactor_core" x="0" y="0" z="1" p:facing="south" p:working="true" />
  <Block id="reactor_casing" x="1" y="0" z="1" />
  <Block id="reactor_casing" x="-1" y="1" z="-1" />
  <Block id="reactor_casing" x="0" y="1" z="-1" />
  <Block id="reactor_casing" x="1" y="1" z="-1" />
  <Block id="reactor_casing" x="-1" y="1" z="0" />
  <Block id="reactor_glass" x="0" y="1" z="0" />
  <Block id="reactor_casing" x="1" y="1" z="0" />
  <Block id="reactor_casing" x="-1" y="1" z="1" />
  <Block id="reactor_casing" x="0" y="1" z="1" />
  <Block id="reactor_casing" x="1" y="1" z="1" />
  <IsometricCamera yaw="200" pitch="30" />
  <BoxAnnotation min="0 0 1" max="1 1 2" color="#FFB547">
    The Reactor Core, in the middle of a face, looking out.
  </BoxAnnotation>
  <BoxAnnotation min="-1 1 -1" max="2 2 2" color="#7F8D99">
    A shell of Reactor Casing and Reactor Glass, here 3 blocks each way.
  </BoxAnnotation>
</GameScene>

## Building one

A shell of <ItemLink id="reactor_casing" /> and <ItemLink id="reactor_glass" />, from 3 to 5 blocks each way, holds
<ItemLink id="fuel_rod" />s in the space inside it, up to 27. The <ItemLink id="reactor_core" /> takes one place on the shell, in the
middle of a face, looking out. The Fabricator makes it from a transformer photomask. The shell carries power to whatever touches
it, and at full power it takes a <ItemLink id="bus_bar" /> to carry it away.

Each rod makes 20,000 FE a tick while the core's power is in its band, half that a step off it, an eighth two steps off. A
<ItemLink id="reactor_fuel" /> in its fuel slot burns for 4,800 ticks at one rod, and as much faster as there are rods, so 27 rods
make 540,000 FE a tick and burn a fuel every 178 ticks.

<Row>
  <RecipeFor id="reactor_casing" />
  <RecipeFor id="reactor_glass" />
  <RecipeFor id="fuel_rod" />
  <RecipeFor id="reactor_fuel" />
</Row>

## The controller

A model goes in the panel beside its screen, on a checkpoint, or by a Relay Card from a Model Server if it is too big to carry.
It has to pass **Containment**. The reactor runs one cycle after another, each of 24 looks, one look every half second. At each
look it shows the controller the cycle so far and the power now, and waits for it to say what to do with the rods.

- Moving the rods changes how fast the power climbs or sinks, up to two steps a look, and only from the look after next.
- The fuel pushes the same way every two or three looks, up while it is young and down as it ages, from a moment of its own in each
  cycle. Each cycle's line starts with the fuel's age. A controller has to make that moment out from how the cycle has gone, and meet
  the push before it comes.
- Outside the band's reach the core feeds on itself: at the top of its gauge it climbs faster each look, and at the bottom it sinks
  faster.

The screen draws the cycle look by look, with the band shaded, and pointing at it tells how much of the last cycle was held and how
sure the controller last was.

## When it stops

It stops itself, and that is the only rule it has: the rods all go in, and it waits half a minute for the poison in its core to clear.
It does this when its gauge reads either end, when its controller is taken away or cannot be asked, and when three looks running
the controller was less than 40% sure of what it chose. Nothing burns or breaks.

## Learning to hold it

A model learns on the <ItemLink id="reactor_dataset" />, by running cycles: the Core plays the model's own and it learns from how each
choice turned out, as for Control and Pilot. No rule holds a cycle of its worst fuel: not going back to the band, not reckoning how fast
the power moves and what the rods have on the way, not even that and knowing the fuel. A model that sees only the last few characters holds
none of them either. A two-block transformer comes close only after about five thousand steps, holding over three quarters of the worst
fuel's cycles, and it swings as it practises, so test it often. See [Models and Tests](models.md).

