---
navigation:
  title: Bench
  icon: bench
  parent: screens/index.md
  position: 2
---

# The Bench's screen

Use a Bench to open it. [The Bench and the Fabricator](../bench.md) tells how a design is wired.

<residual:Clip name="bench" alt="Softmax on the Bench: wired straight from its formula it overflows on scores near 1000; with each row's maximum taken off first, every case passes" />

<residual:Steps>
  <residual:Step>
    A design, wired from the parts to the result.

    <residual:Shot name="bench_board" alt="The mean wired on the Bench: sum over size, every case passing">
      <residual:Part name="spec">
        **The spec**: What you are wiring. Click it to choose another.
      </residual:Part>
      <residual:Part name="palette">
        **The parts**: What a design is wired from: the smallest operations, then the modules you have verified. Drag one onto the board.
      </residual:Part>
      <residual:Part name="canvas">
        **The board**: Drag parts onto the board. Wire a node's right edge to another's left edge, and the last node into the result. Right-click removes; Ctrl+Z undoes. Click a node to see its numbers.
      </residual:Part>
      <residual:Part name="inspector">
        **Its numbers**: What the node you clicked computes on the chosen case, or what the result should be.
      </residual:Part>
      <residual:Part name="goal">
        **The goal**: What the spec must compute, and its formula.
      </residual:Part>
      <residual:Part name="cases">
        **The cases**: A mark for each test case, passing or not. Click one to run the design on it.
      </residual:Part>
      <residual:Part name="result">
        **result**: Wire the last node here. Green means the chosen case passes.
      </residual:Part>
      <residual:Part name="record">
        **Record**: Once every case passes, verifies the design, and records it to a photomask where the spec makes a machine.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    Every spec: those verified ticked, those not yet open locked.

    <residual:Shot name="bench_chooser" alt="The list of specs open over the board">
      <residual:Part name="spec:softmax">
        **A spec**: Click one to open it. A spec opens once those it builds on are verified.
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
