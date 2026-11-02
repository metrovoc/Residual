---
navigation:
  title: Halls
  icon: hall_console
  position: 91
item_ids:
  - chiller
  - coolant_pipe
  - hall_console
  - load_wall
---

# Halls

Rows of racks make heat, and the heat has to go somewhere. A hall is what several pods, the power to run them and the
cooling to keep them running add up to: a room you can stand in and read from one end to the other.

<GameScene zoom="3" interactive={true}>
  <Block id="chiller" p:facing="east" p:working="true" />
  <Block id="coolant_pipe" x="1" p:east="true" p:west="true" p:flowing="true" />
  <Block id="coolant_pipe" x="2" p:west="true" p:up="true" p:flowing="true" />
  <Block id="coolant_pipe" x="2" y="1" p:down="true" p:up="true" p:flowing="true" />
  <Block id="coolant_pipe" x="2" y="2" p:down="true" p:west="true" p:flowing="true" />
  <Block id="coolant_pipe" x="1" y="2" p:east="true" p:west="true" p:flowing="true" />
  <Block id="coolant_pipe" x="0" y="2" p:east="true" p:west="true" p:flowing="true" />
  <Block id="coolant_pipe" x="-1" y="2" p:east="true" p:down="true" p:flowing="true" />
  <Block id="coolant_pipe" x="-1" y="1" p:up="true" p:down="true" p:flowing="true" />
  <Block id="coolant_pipe" x="-1" p:up="true" p:east="true" p:flowing="true" />
  <Block id="rack_module" x="3" p:facing="south" p:bay0="true" p:bay1="true" p:bay2="true" p:bay3="true" p:bay4="true" p:bay5="true" p:bay6="true" p:bay7="true" />
  <Block id="rack_module" x="3" y="1" p:facing="south" p:bay0="true" p:bay1="true" p:bay2="true" p:bay3="true" p:bay4="true" p:bay5="true" p:bay6="true" p:bay7="true" />
  <Block id="rack_switch" x="3" y="2" p:facing="south" p:lit="true" />
  <IsometricCamera yaw="200" pitch="30" />
  <BoxAnnotation min="0 0 0" max="1 1 1" color="#3EB8FF">
    A Chiller. The line leaves its front and comes back to its back.
  </BoxAnnotation>
  <BoxAnnotation min="3 0 0" max="4 2 1" color="#3CD6C6">
    Every device touching the line is cooled, while the Chiller has room for its heat.
  </BoxAnnotation>
</GameScene>

## Cooling lines

A rack gives off too much heat for standing water. A rack is cooled when any of its modules touches a
<ItemLink id="coolant_pipe" /> of a line closed on a <ItemLink id="chiller" />: pipes from the Chiller's front round to its
back. An Accelerator on its own can be cooled the same way. Coolant runs through the pipes while it works. A device with nothing to
cool it runs hot, lends half and smokes.

A Chiller holds 8,192 FE a tick of heat, reckoned at each device's full-load draw, so a line takes as many Chillers as its
devices need. It has to give the heat to something: water touching it, ice or snow, or the air of a cold biome. And it takes
power of its own, from the network it is joined to, for all the heat its devices really make.

## PUE

Power usage effectiveness is all the power a hall draws over what its devices draw. It is what cooling costs:

| The Chiller gives its heat to | FE for each FE of heat | PUE |
|-------------------------------|------------------------|-----|
| water                         | 0.3                    | 1.3 |
| ice or snow                   | 0.2                    | 1.2 |
| the air of a cold biome       | 0.1                    | 1.1 |

An Accelerator cooled by water standing beside it costs nothing, and that is why a small lab does not need any of this.

## The Hall Console

A <ItemLink id="hall_console" /> joined by Interconnect to two <ItemLink id="pod_switch" />es or more, in a cluster with a
closed cooling line, makes the whole of it one hall: every Pod Switch then carries its eight racks, where a cluster that is no
hall has the use of one pod's. It lights when it stands.

Its screen reads the hall: what it is rated for, what it is running, what it draws and at what PUE. It also says the largest model
its memory could train, and what training that on twenty tokens a parameter would cost in flops, days and energy. That is a
prediction by the sums on the [Compute](compute.md) page, for a run too large to do.

## The Load Wall

<ItemLink id="load_wall" /> panels set side by side and stacked, facing the same way and joined to a cluster, make one display as
wide as they are: a column for each rack, as tall as that rack is busy, and over them what the cluster is running, the devices at
work, what it draws and its PUE. The rack lights and the wall read from across the room.

<Row>
  <RecipeFor id="chiller" />
  <RecipeFor id="coolant_pipe" />
  <RecipeFor id="load_wall" />
</Row>
