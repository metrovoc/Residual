---
navigation:
  title: 智能体
  icon: agent
  parent: screens/index.md
  position: 1
---

# 智能体的界面

对智能体使用即可打开。它能做什么，见[智能体](../agents.md)。

<residual:Clip name="program" alt="从智能体的调色板点选拼出程序，然后运行" />

<residual:Steps>
  <residual:Step>
    **工作**：它的工作、它在做什么，以及对它说话。

    <residual:Shot name="agent_work" alt="智能体界面的工作页">
      <residual:Part name="tab:work">
        **标签页**：它做什么、运行的程序，以及它做过什么。
      </residual:Part>
      <residual:Part name="job.store">
        **工作**：每项工作一个按钮；它正在做的那项是灰的。指向按钮可看到这项工作做什么。
      </residual:Part>
      <residual:Part name="display">
        **它在做什么**：它的状态、做出上一个决定的模型、它问了什么答了什么，以及它答对的次数。
      </residual:Part>
      <residual:Part name="agent.order">
        **指令**：用你自己的话写一条指令，交给它的模型。
      </residual:Part>
      <residual:Part name="agent.tell">
        **下达**：它的模型会把这条指令读成程序，等你确认或取消。
      </residual:Part>
      <residual:Part name="filter">
        **过滤**：它的过滤器：一个通过分拣测试的检查点。它判断每样东西属于哪一类，sort 才能把它放进标着这一类的箱子。
      </residual:Part>
      <residual:Part name="model">
        **模型**：它的模型：一个检查点，或一张调谐到某台模型服务器的中继卡。它把收到的指令写成程序，并为储物、搬运、建造和复制做决定。
      </residual:Part>
      <residual:Part name="agent.set_home">
        **设为家**：把它站的地方设为家。它的工作都在家附近进行。
      </residual:Part>
      <residual:Part name="carry">
        **它带着的东西**：它自己的十二个槽。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **程序**：它运行的程序，可以读、可以改，也可以点选拼出来。

    <residual:Shot name="agent_program" alt="程序页，旁边打开着指令板">
      <residual:Part name="program">
        **程序**：输入时就会检查。手里拿着东西点击，就会写下这件东西的名字。
      </residual:Part>
      <residual:Part name="check">
        **检查结果**：告诉你它能不能读成程序，读不成时错在哪里。
      </residual:Part>
      <residual:Part name="program.run">
        **运行**：从现在起，从头运行这段程序。
      </residual:Part>
      <residual:Part name="program.stop">
        **停止**：原地停下。按「运行」会重新开始程序。
      </residual:Part>
      <residual:Part name="palette">
        **指令板**：在窗口旁显示这些拼块，或把它们收起来。
      </residual:Part>
      <residual:Part name="program.wide">
        **整个窗口**：让程序占满整个窗口，把槽位挪开。
      </residual:Part>
      <residual:Part name="palette_panel">
        **拼块**：程序能说的一切，分页列出，点选即可拼合。每一块都接在程序末尾。最后一页是你手里拿的和智能体带着的东西。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **追踪**：它一步一步做了什么，旁边是调试器。

    <residual:Shot name="agent_trace" alt="追踪页：智能体走过的每一步和结果，以及调试器">
      <residual:Part name="trace">
        **追踪**：每一步及其结果：它做了什么、哪里做不到，以及它问了模型什么。
      </residual:Part>
      <residual:Part name="debug_lines">
        **调试器**：让程序停在下一步，或让它继续。点上面的一行，每次走到那里都会停。
      </residual:Part>
      <residual:Part name="debug.step">
        **单步**：运行一步后再次暂停。
      </residual:Part>
      <residual:Part name="debug_values">
        **值**：程序现在保存的值，以及你要观察的东西。
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
