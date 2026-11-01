---
navigation:
  title: Machines
  icon: linear
  parent: screens/index.md
  position: 5
---

# A machine's screen

Use any machine of a model to open it; each has its own settings and math. [Machines](../machines/index.md) has a page for each.

<residual:Clip name="operator" alt="A machine's screen while it trains: its output and weights as they change" />

A Linear Layer before its first check.

<residual:Shot name="operator_machine" alt="A Linear Layer's screen: its name, settings, pictures and math">
  <residual:Part name="name">
    **Name**: A name of your own, shown wherever the machine is listed.
  </residual:Part>
  <residual:Part name="field.out">
    **Settings**: What this machine can be set to; they differ from machine to machine. Click for the next value, right-click for the one before.
  </residual:Part>
  <residual:Part name="check">
    **What the last check found**: The shape it puts out, its parameters and gradient, or what is wrong with it.
  </residual:Part>
  <residual:Part name="picture">
    **Output**: What comes out of its front, amber above zero and blue below. A machine with weights shows its main matrix beside it.
  </residual:Part>
  <residual:Part name="math">
    **The math**: What it computes, in a line or two.
  </residual:Part>
</residual:Shot>
