---
navigation:
  title: Tuner
  icon: tuner
  parent: screens/index.md
  position: 29
---

# A Tuner's screen

Use a Tuner to open it. [Tuning](../tuning.md) tells what each way of tuning does and what it earns.

A saved model on the left, what it needs beside it under that, and a blank checkpoint on the right; the toolbar sets how.

<residual:Shot name="tuner_idle" alt="A Tuner with nothing in it">
  <residual:Part name="base">
    **The base**: The saved model it starts from. It stays as it is: what comes out is a new one.
  </residual:Part>
  <residual:Part name="aid">
    **Beside it**: A dataset or an Archive Drive; two of them to mix.
  </residual:Part>
  <residual:Part name="out">
    **What comes out**: A blank checkpoint, to save into.
  </residual:Part>
  <residual:Part name="show">
    **The display**: Before it starts, what the way it is set to does. While it runs, the reward in teal against what was really right in amber, each try's mark, and what the model last wrote.
  </residual:Part>
  <residual:Part name="tuner.run">
    **Tune**: Starts, or stops and keeps what it has so far.
  </residual:Part>
  <residual:Part name="tuner.method">
    **How**: What it does to the model, and what that needs beside it.
  </residual:Part>
  <residual:Part name="tuner.budget">
    **Steps**: How long it goes on before it saves.
  </residual:Part>
  <residual:Part name="tuner.auto">
    **Starts by itself**: Starts whenever a base and a blank checkpoint are in, as along a line of Tuners and hoppers. What it finishes leaves by a hopper below.
  </residual:Part>
</residual:Shot>
