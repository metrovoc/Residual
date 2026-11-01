---
navigation:
  title: Port
  icon: port
  parent: screens/index.md
  position: 12
---

# A Port's screen

Use a Port to open it. [Storage](../storage.md) tells how its modes differ.

<residual:Steps>
  <residual:Step>
    Its mode, its filter, and what it moved.

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
        **Target model**: A checkpoint, or a Relay Card tuned to a Model Server, whose model passes Demand. It reads what this Port has moved out and says what it will move next, and a Port that asks has that made first. What it is not sure of, the Port asks for only as it runs short.
      </residual:Part>
      <residual:Part name="log">
        **Field Log**: A Field Log here keeps a copy of what this Port moves out, a line per thing. In a Model Core's data slot it is practice for Demand on your own base.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    Stocking: a number to keep, and asking for what it lacks.

    <residual:Shot name="port_stock" alt="A Port stocking with a filter and a number">
      <residual:Part name="port.mode.stock">
        **Stock**: Keeps the inventory in front of it holding the number below of each kind its filter names: it tops each one up from the network, and takes any extra back.
      </residual:Part>
      <residual:Part name="port.mode.window">
        **Window**: Opens the inventory in front of it to the network, which keeps items there and takes them out as if it were a drive. A higher priority is filled first; a filter limits what goes in.
      </residual:Part>
      <residual:Part name="more:16">
        **The number**: How many of each kind to keep there. With shift, sixty-four times as many.
      </residual:Part>
      <residual:Part name="port.craft">
        **Ask for what it lacks**: Does not ask. Click to have the network's Assemblers and Processors make what its filter names whenever there is too little of it.
      </residual:Part>
      <residual:Part name="port.programs">
        **Programs**: Program Cards, upgrades and what its programs hold. With a written card in it, it does what the card says instead of what its mode says.
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
