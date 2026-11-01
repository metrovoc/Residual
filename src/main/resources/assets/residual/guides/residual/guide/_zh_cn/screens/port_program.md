---
navigation:
  title: 端口的程序
  icon: port
  parent: screens/index.md
  position: 13
---

# 端口的程序

在端口的界面上按程序按钮即可打开。放进写好的程序卡后，端口按卡上写的做，不再按模式做。它们说的语言，见[程序](../programs.md)。

<residual:Steps>
  <residual:Step>
    **程序**：它的卡、升级，以及选中的卡上的程序。

    <residual:Shot name="port_program_program" alt="端口的程序页">
      <residual:Part name="tab:program">
        **标签页**：选中的卡上的程序，以及它运行得如何。
      </residual:Part>
      <residual:Part name="cards">
        **程序卡**：点击一个槽来选中；下面的程序就是那张卡上写的。
      </residual:Part>
      <residual:Part name="program">
        **程序**：输入时就会检查。
      </residual:Part>
      <residual:Part name="check">
        **检查结果**：告诉你它能不能读成程序，读不成时错在哪里。
      </residual:Part>
      <residual:Part name="program.run">
        **运行**：把程序写到选中的卡上，并从头运行。
      </residual:Part>
      <residual:Part name="program.stop">
        **停止**：原地停下。按「运行」会重新开始程序。
      </residual:Part>
      <residual:Part name="hands">
        **手里的东西**：它的程序用来干活的东西。
      </residual:Part>
      <residual:Part name="upgrades">
        **升级**：让它更快、更强、够得更远的东西。
      </residual:Part>
      <residual:Part name="port.to_filter">
        **过滤器和模式**：回到它的模式、过滤器和过滤模型。
      </residual:Part>
      <residual:Part name="owned.access">
        **谁能使用**：它属于放下它的人，只有那个人能改这项设置。用 /residual team make 建队，/residual team invite 邀请，/residual team join 加入。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **追踪**：它运行得如何，旁边是调试器。

    <residual:Shot name="port_program_trace" alt="端口的追踪和调试器">
      <residual:Part name="trace">
        **追踪**：每一步及其结果。
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
