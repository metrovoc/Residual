---
navigation:
  title: Terminal
  icon: terminal
  parent: screens/index.md
  position: 19
---

# A Terminal's screen

Use a Terminal to open it, from anywhere the relays of its Model Server reach. [Serving Models](../deployment.md) tells how one is tuned.

<residual:Clip name="terminal" alt="Writing with a served model on a Terminal" />

<residual:Steps>
  <residual:Step>
    **Map**: the relay network as it stands.

    <residual:Shot name="terminal_map" alt="A Terminal's map, not yet tuned">
      <residual:Part name="terminal.tab.map">
        **Map**: The relay network: where it reaches, and every gate on it to go to.
      </residual:Part>
      <residual:Part name="reach">
        **Its reach**: Whether it reaches its server from here, and how fast that server answers.
      </residual:Part>
      <residual:Part name="map">
        **The map**: Shows all of the network here. Scroll to zoom, drag to move.
      </residual:Part>
      <residual:Part name="gates">
        **The gates**: Every gate on the network; click one to go there.
      </residual:Part>
      <residual:Part name="map.fit">
        **Frame it all**: Shows all of the network here. Scroll to zoom, drag to move.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **Base**: what the base holds, and what its machines are doing.

    <residual:Shot name="terminal_base" alt="A Terminal's Base tab">
      <residual:Part name="terminal.tab.base">
        **Base**: What the base holds, and what its machines are doing.
      </residual:Part>
      <residual:Part name="base">
        **The base**: Each storage network, to open as if standing at it, then each machine with what it is doing.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **Agents**: what each is doing, to stop, start, hand a program or give an order.

    <residual:Shot name="terminal_agents" alt="A Terminal's Agents tab">
      <residual:Part name="terminal.tab.agents">
        **Agents**: What each agent is doing. Hand one a program or give it an order.
      </residual:Part>
      <residual:Part name="agents">
        **The agents**: Each agent and what it is doing. Click one to choose it.
      </residual:Part>
      <residual:Part name="doing">
        **The one chosen**: The program it runs, or the one it wants a yes or no to.
      </residual:Part>
      <residual:Part name="program_cards">
        **Program Card**: The written cards you carry, to hand it one.
      </residual:Part>
      <residual:Part name="agent.order">
        **Order**: An order in your own words, for the served model to write as a program.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **Writing**: the served model carrying on whatever you start.

    <residual:Shot name="terminal_write" alt="A Terminal's Writing tab">
      <residual:Part name="terminal.tab.write">
        **Writing**: The served model carries on whatever you start.
      </residual:Part>
      <residual:Part name="terminal.prompt">
        **Prompt**: The start of something, for the model to write on from.
      </residual:Part>
      <residual:Part name="terminal.write">
        **Write**: Sends it to the server.
      </residual:Part>
      <residual:Part name="terminal.clear">
        **Clear**: Clears the prompt and the answer.
      </residual:Part>
      <residual:Part name="answer">
        **The answer**: Grey is what you wrote, white what the model wrote.
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
