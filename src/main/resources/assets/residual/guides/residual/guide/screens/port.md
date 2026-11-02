---
navigation:
  title: Port
  icon: port
  parent: screens/index.md
  position: 13
---

# A Port's screen

Use a Port to open it. [Storage](../storage.md) tells how its modes differ.

<residual:Steps>
  <residual:Step>
    The filter stays in the window; the mode buttons sit beside it. Point at the arrow to see what moved in the last second.

    <residual:Shot name="port_import" alt="A Port importing">
      <residual:Part name="port.mode.import">
        **Import**: Takes everything from the inventory in front of it into the network, 64 items a tick; with a filter, only those kinds.
      </residual:Part>
      <residual:Part name="port.mode.export">
        **Export**: Puts the kinds its filter names from the network into the inventory in front of it, 64 items a tick.
      </residual:Part>
      <residual:Part name="filter">
        **Filter**: Click with a stack to name its kind here, or shift-click it in your inventory; click empty-handed to clear. A filled bucket names its fluid. An empty filter lets everything through. With a filter model, something renamed Food, Plants, Wood, Stone, Ores or Tools stands for that whole kind.
      </residual:Part>
      <residual:Part name="status">
        **What it moved**: How much in the last second, or that a full link holds it back.
      </residual:Part>
      <residual:Part name="model">
        **Filter model**: A checkpoint, or a Relay Card tuned to a Model Server, whose model passes Sorting. It tells what kind each thing is, so a filter slot holding something renamed for a kind imports or exports everything of that kind. A thing it is not sure of counts as other.
      </residual:Part>
      <residual:Part name="target">
        **Target model**: A model that forecasts what this Port will need. Point at it to see the expected amount above its checkpoint details.
      </residual:Part>
      <residual:Part name="log">
        **Field Log**: A Field Log here keeps a copy of what this Port moves out, a line per thing. In a Model Core's data slot it is practice for Demand on your own base.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    Stocking: point at the number buttons for the amount to keep, and turn on asking for what it lacks.

    <residual:Shot name="port_stock" alt="A Port stocking with a filter and a number">
      <residual:Part name="port.mode.stock">
        **Stock**: Keeps each kind its filter names at the chosen amount in the inventory in front: it tops each one up from the network, and takes any extra back.
      </residual:Part>
      <residual:Part name="port.mode.window">
        **Window**: Opens the inventory in front of it to the network, which keeps items there and takes them out as if it were a drive. A higher priority is filled first; a filter limits what goes in.
      </residual:Part>
      <residual:Part name="keep">
        **The number**: Keep this many of each filtered kind in the inventory in front. Hold Shift to change sixty-four times as many.
      </residual:Part>
      <residual:Part name="port.craft">
        **Ask for what it lacks**: Does not ask. Click to have the network's Assemblers and Processors make what its filter names whenever there is too little of it.
      </residual:Part>
      <residual:Part name="port.programs">
        **Programs**: Program Cards, upgrades and what its programs hold. With a written card in it, it does what the card says instead of what its mode says.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    A window opens the inventory to the network. Point at its number buttons for its priority.

    <residual:Shot name="port_window" alt="A Port's window mode and priority controls">
      <residual:Part name="port.mode.window">
        **Window**: Opens the inventory in front of it to the network, which keeps items there and takes them out as if it were a drive. A higher priority is filled first; a filter limits what goes in.
      </residual:Part>
      <residual:Part name="priority">
        **Priority**: The network fills a higher-priority window first. Change by one or four.
      </residual:Part>
      <residual:Part name="filter">
        **Filter**: Click with a stack to name its kind here, or shift-click it in your inventory; click empty-handed to clear. A filled bucket names its fluid. An empty filter lets everything through. With a filter model, something renamed Food, Plants, Wood, Stone, Ores or Tools stands for that whole kind.
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
