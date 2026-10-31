---
navigation:
  title: The Architect
  icon: survey_rod
  position: 85
---

# The Architect

An agent whose model passes Induction can copy a build, move it, or carry on its pattern. It reads a build as slices
across its length, a letter for each slice, and asks its model what comes next, just as the test does.

<GameScene zoom="3" interactive={true}>
  <Block id="minecraft:grass_block" x="-1" y="-1" z="-1" />
  <Block id="minecraft:grass_block" x="-1" y="-1" z="0" />
  <Block id="minecraft:grass_block" x="-1" y="-1" z="1" />
  <Block id="minecraft:grass_block" x="-1" y="-1" z="2" />
  <Block id="minecraft:grass_block" x="-1" y="-1" z="3" />
  <Block id="minecraft:grass_block" x="-1" y="-1" z="4" />
  <Block id="minecraft:grass_block" x="-1" y="-1" z="5" />
  <Block id="minecraft:grass_block" x="-1" y="-1" z="6" />
  <Block id="minecraft:grass_block" x="-1" y="-1" z="7" />
  <Block id="minecraft:grass_block" x="-1" y="-1" z="8" />
  <Block id="minecraft:grass_block" x="0" y="-1" z="-1" />
  <Block id="minecraft:grass_block" x="0" y="-1" z="0" />
  <Block id="minecraft:grass_block" x="0" y="-1" z="1" />
  <Block id="minecraft:grass_block" x="0" y="-1" z="2" />
  <Block id="minecraft:grass_block" x="0" y="-1" z="3" />
  <Block id="minecraft:grass_block" x="0" y="-1" z="4" />
  <Block id="minecraft:grass_block" x="0" y="-1" z="5" />
  <Block id="minecraft:grass_block" x="0" y="-1" z="6" />
  <Block id="minecraft:grass_block" x="0" y="-1" z="7" />
  <Block id="minecraft:grass_block" x="0" y="-1" z="8" />
  <Block id="minecraft:grass_block" x="1" y="-1" z="-1" />
  <Block id="minecraft:grass_block" x="1" y="-1" z="0" />
  <Block id="minecraft:grass_block" x="1" y="-1" z="1" />
  <Block id="minecraft:grass_block" x="1" y="-1" z="2" />
  <Block id="minecraft:grass_block" x="1" y="-1" z="3" />
  <Block id="minecraft:grass_block" x="1" y="-1" z="4" />
  <Block id="minecraft:grass_block" x="1" y="-1" z="5" />
  <Block id="minecraft:grass_block" x="1" y="-1" z="6" />
  <Block id="minecraft:grass_block" x="1" y="-1" z="7" />
  <Block id="minecraft:grass_block" x="1" y="-1" z="8" />
  <Block id="minecraft:grass_block" x="2" y="-1" z="-1" />
  <Block id="minecraft:grass_block" x="2" y="-1" z="0" />
  <Block id="minecraft:grass_block" x="2" y="-1" z="1" />
  <Block id="minecraft:grass_block" x="2" y="-1" z="2" />
  <Block id="minecraft:grass_block" x="2" y="-1" z="3" />
  <Block id="minecraft:grass_block" x="2" y="-1" z="4" />
  <Block id="minecraft:grass_block" x="2" y="-1" z="5" />
  <Block id="minecraft:grass_block" x="2" y="-1" z="6" />
  <Block id="minecraft:grass_block" x="2" y="-1" z="7" />
  <Block id="minecraft:grass_block" x="2" y="-1" z="8" />
  <Block id="minecraft:grass_block" x="3" y="-1" z="-1" />
  <Block id="minecraft:grass_block" x="3" y="-1" z="0" />
  <Block id="minecraft:grass_block" x="3" y="-1" z="1" />
  <Block id="minecraft:grass_block" x="3" y="-1" z="2" />
  <Block id="minecraft:grass_block" x="3" y="-1" z="3" />
  <Block id="minecraft:grass_block" x="3" y="-1" z="4" />
  <Block id="minecraft:grass_block" x="3" y="-1" z="5" />
  <Block id="minecraft:grass_block" x="3" y="-1" z="6" />
  <Block id="minecraft:grass_block" x="3" y="-1" z="7" />
  <Block id="minecraft:grass_block" x="3" y="-1" z="8" />
  <Block id="minecraft:grass_block" x="4" y="-1" z="-1" />
  <Block id="minecraft:grass_block" x="4" y="-1" z="0" />
  <Block id="minecraft:grass_block" x="4" y="-1" z="1" />
  <Block id="minecraft:grass_block" x="4" y="-1" z="2" />
  <Block id="minecraft:grass_block" x="4" y="-1" z="3" />
  <Block id="minecraft:grass_block" x="4" y="-1" z="4" />
  <Block id="minecraft:grass_block" x="4" y="-1" z="5" />
  <Block id="minecraft:grass_block" x="4" y="-1" z="6" />
  <Block id="minecraft:grass_block" x="4" y="-1" z="7" />
  <Block id="minecraft:grass_block" x="4" y="-1" z="8" />
  <Block id="minecraft:stone_bricks" x="0" y="0" z="0" />
  <Block id="minecraft:stone_bricks" x="0" y="0" z="2" />
  <Block id="minecraft:stone_bricks" x="0" y="1" z="0" />
  <Block id="minecraft:stone_bricks" x="0" y="1" z="2" />
  <Block id="minecraft:stone_bricks" x="0" y="2" z="0" />
  <Block id="minecraft:stone_bricks" x="0" y="2" z="2" />
  <Block id="minecraft:stone_brick_slab" x="0" y="2" z="1" />
  <Block id="minecraft:oak_planks" x="1" y="0" z="0" />
  <Block id="minecraft:oak_planks" x="1" y="0" z="1" />
  <Block id="minecraft:oak_planks" x="1" y="0" z="2" />
  <Block id="minecraft:stone_bricks" x="2" y="0" z="0" />
  <Block id="minecraft:stone_bricks" x="2" y="0" z="2" />
  <Block id="minecraft:stone_bricks" x="2" y="1" z="0" />
  <Block id="minecraft:stone_bricks" x="2" y="1" z="2" />
  <Block id="minecraft:stone_bricks" x="2" y="2" z="0" />
  <Block id="minecraft:stone_bricks" x="2" y="2" z="2" />
  <Block id="minecraft:stone_brick_slab" x="2" y="2" z="1" />
  <Block id="minecraft:oak_planks" x="3" y="0" z="0" />
  <Block id="minecraft:oak_planks" x="3" y="0" z="1" />
  <Block id="minecraft:oak_planks" x="3" y="0" z="2" />
  <Block id="minecraft:stone_bricks" x="0" y="0" z="5" />
  <Block id="minecraft:stone_bricks" x="0" y="0" z="7" />
  <Block id="minecraft:stone_bricks" x="0" y="1" z="5" />
  <Block id="minecraft:stone_bricks" x="0" y="1" z="7" />
  <Block id="minecraft:stone_bricks" x="0" y="2" z="5" />
  <Block id="minecraft:stone_bricks" x="0" y="2" z="7" />
  <Block id="minecraft:stone_brick_slab" x="0" y="2" z="6" />
  <Block id="minecraft:oak_planks" x="1" y="0" z="5" />
  <Block id="minecraft:oak_planks" x="1" y="0" z="6" />
  <Block id="minecraft:oak_planks" x="1" y="0" z="7" />
  <Block id="minecraft:stone_bricks" x="2" y="0" z="5" />
  <Block id="minecraft:stone_bricks" x="2" y="0" z="7" />
  <Block id="minecraft:stone_bricks" x="2" y="1" z="5" />
  <Block id="minecraft:stone_bricks" x="2" y="1" z="7" />
  <Block id="minecraft:stone_bricks" x="2" y="2" z="5" />
  <Block id="minecraft:stone_bricks" x="2" y="2" z="7" />
  <Block id="minecraft:stone_brick_slab" x="2" y="2" z="6" />
  <Block id="minecraft:oak_planks" x="3" y="0" z="5" />
  <Block id="minecraft:oak_planks" x="3" y="0" z="6" />
  <Block id="minecraft:oak_planks" x="3" y="0" z="7" />
  <IsometricCamera yaw="225" pitch="30" />
  <BoxAnnotation min="0 0 0" max="1 1 1" color="#3CD6C6">
    The first mark: a corner of the build.
  </BoxAnnotation>
  <BoxAnnotation min="3 2 2" max="4 3 3" color="#3CD6C6">
    The second mark: the opposite corner.
  </BoxAnnotation>
  <BoxAnnotation min="0 -1 5" max="1 0 6" color="#FFB547">
    The third mark: the copy's first corner stands on it.
  </BoxAnnotation>
</GameScene>

## Marks

Mark blocks with a <ItemLink id="survey_rod" />, in order, and hand the marks to the agent:

- **copy** and **move**: two blocks at opposite corners of the build, then the block the copy should stand on. The
  copy's first corner goes on top of the third mark, and the rest follows from there.
- **build**: the blocks under the two ends of a straight run. With a third mark on the far top corner of the first
  slice, each slice reaches across and up to it; without one, each slice is a single block.

A build is cut across its longer side, into at most 16 slices, each at most 16 blocks wide and 16 high, and its copy
stands within 64 blocks of it.

## Copying

`copy` first reads the build. It shows its model the build's slices, a different letter for each, then the order it
has read so far, and asks which slice comes next. The first slice it takes by itself, since nothing yet says where a
copy begins. If its model is less than 60% sure, or names a slice twice, it stops before it lays anything.

Then it lays each slice in its place, fetching the blocks from the chests and barrels around home, or from a storage
network through a <ItemLink id="storage_terminal" /> there. Chests and machines in the copy start out empty. What can be
built over, such as grass or water, isn't copied, and anything already standing where the copy goes stays.

## Moving

`move` reads the build the same way and checks that nothing stands where the copy goes. Then it takes the build up
slice by slice, contents and all, and lays it down again: chests keep their items and furnaces their fuel, and nothing
drops on the way. Pictures and item frames are no blocks, and stay behind. An agent holding a build can't be picked up
until it has laid it down.

## Repeating

`build` carries on a pattern along its run. It shows its model the slices so far, the same letter for the same slice,
and lays the slice its model names. Lay one round of the pattern and the start of the next by hand. What comes after a
slice is what came after it last time, so the slices of one round must all differ.
