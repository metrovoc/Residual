---
navigation:
  title: 终端
  icon: terminal
  parent: screens/index.md
  position: 20
---

# 终端的界面

使用终端即可打开，只要它的模型服务器的中继覆盖得到。怎样调谐，见[模型服务](../deployment.md)。

<residual:Steps>
  <residual:Step>
    **地图**：中继网络现在的样子。

    <residual:Shot name="terminal_map" alt="还没有调谐的终端的地图">
      <residual:Part name="terminal.tab.map">
        **地图**：中继网络：它覆盖到哪里，以及可以前往的每一座门。
      </residual:Part>
      <residual:Part name="reach">
        **它的覆盖**：从这里能否连到它的服务器，以及服务器回答得多快。
      </residual:Part>
      <residual:Part name="map">
        **地图**：显示这里的整个网络。滚轮缩放，拖动移动。
      </residual:Part>
      <residual:Part name="gates">
        **门**：网络上的每一道门；点击就去那里。
      </residual:Part>
      <residual:Part name="map.fit">
        **显示全部**：显示这里的整个网络。滚轮缩放，拖动移动。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **基地**：基地存着什么，机器在做什么。

    <residual:Shot name="terminal_base" alt="终端的基地页">
      <residual:Part name="terminal.tab.base">
        **基地**：基地里存着什么，机器在做什么。
      </residual:Part>
      <residual:Part name="base">
        **基地**：每个存储网络，可以像站在它跟前一样打开；然后是每台机器和它在做什么。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **智能体**：每个智能体在做什么，可以让它停下、开始、交给它程序或下指令。

    <residual:Shot name="terminal_agents" alt="终端的智能体页">
      <residual:Part name="terminal.tab.agents">
        **智能体**：每个智能体在做什么。交给它程序，或下达指令。
      </residual:Part>
      <residual:Part name="agents">
        **智能体**：每个智能体和它在做什么。点击可选中。
      </residual:Part>
      <residual:Part name="doing">
        **选中的那个**：它运行的程序，或者它等你点头的程序。
      </residual:Part>
      <residual:Part name="program_cards">
        **程序卡**：你带着的写好的程序卡，可以交给它一张。
      </residual:Part>
      <residual:Part name="agent.order">
        **指令**：用你自己的话写一条指令，由服务器上的模型写成程序。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **续写**：服务器上的模型接着你的开头写下去。

    <residual:Shot name="terminal_write" alt="终端的续写页">
      <residual:Part name="terminal.tab.write">
        **续写**：服务中的模型接着你的开头写下去。
      </residual:Part>
      <residual:Part name="terminal.prompt">
        **提示**：写一段开头，让模型接着写。
      </residual:Part>
      <residual:Part name="terminal.write">
        **续写**：把它发给服务器。
      </residual:Part>
      <residual:Part name="terminal.clear">
        **清空**：清空提示和回答。
      </residual:Part>
      <residual:Part name="answer">
        **回答**：灰色是你写的，白色是模型写的。
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
