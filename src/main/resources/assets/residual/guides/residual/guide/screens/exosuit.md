---
navigation:
  title: Exosuit
  icon: exosuit
  parent: screens/index.md
  position: 21
---

# An exosuit's screen

Sneak and use an exosuit, or press its key while you wear it, to open it. [The Exosuit](../exosuit.md) tells what it runs on.

What its board found, and each ability, turned on and off beside the window.

<residual:Shot name="exosuit_board" alt="An exosuit with no model on board">
  <residual:Part name="charge">
    **Its charge**: Point at it for how much it holds.
  </residual:Part>
  <residual:Part name="display">
    **Its board**: The model's weights, what they take as plain floats and in each form it can run in, how each scores, its compute, and what each ability running draws from it.
  </residual:Part>
  <residual:Part name="row:flight">
    **An ability**: A line for each, with the compute it draws, teal while it runs, amber if it is on and there is not enough for it. Point at one for what it does.
  </residual:Part>
  <residual:Part name="ability.flight">
    **Its switch**: Turns the ability on or off. Those left on run cheapest first, as far as the compute goes.
  </residual:Part>
</residual:Shot>
