---
navigation:
  title: Storage Terminal
  icon: storage_terminal
  parent: screens/index.md
  position: 12
---

# A Storage Terminal's screen

Use a Storage Terminal to open it. A Wireless Terminal and a Portable Drive open the same. [Storage](../storage.md) tells what the network keeps and makes.

<residual:Clip name="storage" alt="Searching a terminal, then asking for 64 torches with coal short: the plan, the wait, the coal going in, the job done" />

<residual:Steps>
  <residual:Step>
    All the network keeps, with what it can make marked.

    <residual:Shot name="storage_terminal_grid" alt="A Storage Terminal's grid">
      <residual:Part name="search">
        **Search**: Words a thing's name holds, or a Chinese name's pinyin. @ for a mod, # a tag, $ the tooltip, \* the id; - leaves things out, | gives alternatives, ( ) groups; >64, \<10 or =1 asks how many. @minecraft ingot -iron finds every ingot but iron. Start with ? to ask the model beside the window for what you mean.
      </residual:Part>
      <residual:Part name="jobs">
        **Jobs**: The crafting jobs running on this network
      </residual:Part>
      <residual:Part name="sort">
        **Sort**: Sort by name, by how many, or by mod. Shift-click to reverse.
      </residual:Part>
      <residual:Part name="grid">
        **The kinds**: Click to take a stack, right-click for half, shift-click to send a stack to your inventory; click holding something to put it in, right-click for one. Alt-click pins a kind to the top.
      </residual:Part>
      <residual:Part name="scrollbar">
        **The scrollbar**: Drag it, or scroll over the grid.
      </residual:Part>
      <residual:Part name="usage">
        **How full it is**: What is kept, what there is room for, and how many kinds.
      </residual:Part>
      <residual:Part name="model">
        **Model**: A model that passes Retrieval. Start a search with ? and it looks for what you mean, among everything kept.
      </residual:Part>
      <residual:Part name="craft">
        **A crafting grid**: Works as at a crafting table, and draws what it needs from the network.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    Asking for what the network can make: the plan, and what is missing.

    <residual:Shot name="storage_terminal_ask" alt="The plan for sixteen torches, with coal missing">
      <residual:Part name="amount">
        **How many**: How many to make, typed or stepped.
      </residual:Part>
      <residual:Part name="+16">
        **Steps**: Adds this many; with Shift, takes them away
      </residual:Part>
      <residual:Part name="plan">
        **The plan**: kept · ×crafts · −missing
      </residual:Part>
      <residual:Part name="start">
        **Start**: Starts the job. What is missing, it waits for: put it into the network and the job goes on.
      </residual:Part>
      <residual:Part name="back">
        **Back**: Back to the grid
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    The jobs running on the network: what each makes, how far it has come, what it waits for.

    <residual:Shot name="storage_terminal_jobs" alt="A job waiting for coal">
      <residual:Part name="job:0">
        **A job**: Click to see its plan
      </residual:Part>
      <residual:Part name="job:0:cancel">
        **Cancel**: Stops the job.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    A job's plan as a tree: each row under the step that needs it.

    <residual:Shot name="storage_terminal_tree" alt="A job's plan drawn as a tree">
      <residual:Part name="tree">
        **The plan**: How many of each came from storage, how far its crafts have come, and what is still missing.
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
