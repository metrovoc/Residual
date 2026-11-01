---
navigation:
  title: Agent
  icon: agent
  parent: screens/index.md
  position: 1
---

# An agent's screen

Use an agent to open it. [Agents](../agents.md) tells what it can do.

<residual:Clip name="program" alt="A program clicked together from an agent's palette, then run" />

<residual:Steps>
  <residual:Step>
    **Work**: its job, what it is doing, and telling it something.

    <residual:Shot name="agent_work" alt="The Work tab of an agent's screen">
      <residual:Part name="tab:work">
        **The tabs**: What it does, the program it runs, and what it has been doing.
      </residual:Part>
      <residual:Part name="job.store">
        **Jobs**: A button for each job; the one it is doing is greyed out. Point at one for what it does.
      </residual:Part>
      <residual:Part name="display">
        **What it is doing**: Its status, the model that made its last decision, what it asked and answered, and how often it was right.
      </residual:Part>
      <residual:Part name="agent.order">
        **Order**: An order in your own words, for its model.
      </residual:Part>
      <residual:Part name="agent.tell">
        **Tell**: Its model reads the order back as a program, for your yes or no.
      </residual:Part>
      <residual:Part name="filter">
        **Filter**: Its filter: a checkpoint that passes Sorting. It tells what kind each thing is, so that sort can put it in the chest labelled with its kind.
      </residual:Part>
      <residual:Part name="model">
        **Model**: Its model: a checkpoint, or a Relay Card tuned to a Model Server. It writes the orders it is given as programs, and makes the decisions of storing, hauling, building and copying.
      </residual:Part>
      <residual:Part name="agent.set_home">
        **Set home**: Make where it stands its home. All its work happens around home.
      </residual:Part>
      <residual:Part name="carry">
        **What it carries**: Twelve slots of its own.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **Program**: the program it runs, to read, rewrite or click together.

    <residual:Shot name="agent_program" alt="The Program tab with the palette open beside it">
      <residual:Part name="program">
        **The program**: Checked as it is typed. Clicking it with a thing in hand names that thing.
      </residual:Part>
      <residual:Part name="check">
        **The check**: Says whether it reads as a program, and where it slips if not.
      </residual:Part>
      <residual:Part name="program.run">
        **Run**: Run this program from the top, from now on.
      </residual:Part>
      <residual:Part name="program.stop">
        **Stop**: Stop where it is. Run starts the program again.
      </residual:Part>
      <residual:Part name="palette">
        **Palette**: Shows the pieces beside the window, or hides them.
      </residual:Part>
      <residual:Part name="program.wide">
        **Whole window**: Gives the program the whole window, with the slots out of the way.
      </residual:Part>
      <residual:Part name="palette_panel">
        **The pieces**: Everything a program can say, page by page, to click together. Each piece goes on the end of the program. Its last page is what you hold and the agent carries.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **Trace**: what it has been doing, step by step, with a debugger beside it.

    <residual:Shot name="agent_trace" alt="The Trace tab: each step the agent took and how it went, and the debugger">
      <residual:Part name="trace">
        **The trace**: Each step with how it went: what it did, what it could not, and what it asked its model.
      </residual:Part>
      <residual:Part name="debug_lines">
        **Debugger**: Holds the program at its next step, or lets it go on. Click a line above to stop there each time it comes round.
      </residual:Part>
      <residual:Part name="debug.step">
        **One step**: Runs one step and holds again.
      </residual:Part>
      <residual:Part name="debug_values">
        **Values**: What the program holds now, and what you asked to watch.
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
