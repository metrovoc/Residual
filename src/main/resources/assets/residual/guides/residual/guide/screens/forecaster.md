---
navigation:
  title: Forecaster
  icon: forecaster
  parent: screens/index.md
  position: 7
---

# A Forecaster's screen

Use a Forecaster to open it. [The Forecaster](../forecaster.md) tells how it comes to trust a model.

<residual:Clip name="forecaster_screen" alt="A checkpoint going into a Forecaster, checked against the machines it watched" />

What it sees around it, and how close its model comes to it.

<residual:Shot name="forecaster_watching" alt="A Forecaster watching a furnace">
  <residual:Part name="field">
    **The blocks around it**: The crops and machines within four blocks, from above with north up, a block higher or lower too. The Forecaster sits in the middle; whatever its model just ran ahead is outlined.
  </residual:Part>
  <residual:Part name="tab:machines">
    **Machines**: The kind of block the bars show: crops or machines.
  </residual:Part>
  <residual:Part name="bars">
    **The bars**: How far they moved from each place, in amber, against what its model expects, in teal. Point at a group to read it.
  </residual:Part>
  <residual:Part name="state">
    **What it is doing**: Whether it is watching, checking or running them ahead.
  </residual:Part>
  <residual:Part name="row:machines">
    **Each kind**: How many it watches, then the progress seen so far or how close the model came. Within 11% it runs them four looks for every one; within 25%, two.
  </residual:Part>
  <residual:Part name="log">
    **Field Log**: A Field Log keeps a copy of everything the Forecaster watched, a line per crop or machine. In a Model Core's data slot, it is practice for Forecasting on these very blocks.
  </residual:Part>
  <residual:Part name="model">
    **Model**: A model that has learned what crops and machines do next: a checkpoint, or a Relay Card tuned to its Model Server.
  </residual:Part>
</residual:Shot>
