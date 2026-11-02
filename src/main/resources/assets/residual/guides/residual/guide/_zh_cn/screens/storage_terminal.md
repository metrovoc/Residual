---
navigation:
  title: 存储终端
  icon: storage_terminal
  parent: screens/index.md
  position: 12
---

# 存储终端的界面

对存储终端使用即可打开，无线终端和便携驱动器打开的也是它。网络存什么、造什么，见[存储](../storage.md)。

<residual:Clip name="storage" alt="搜索终端，然后在煤不够时请求 64 支火把：计划、等待、放入煤、任务完成" />

<residual:Steps>
  <residual:Step>
    网络存着的一切，能做出来的会有标记。

    <residual:Shot name="storage_terminal_grid" alt="存储终端的网格">
      <residual:Part name="search">
        **搜索**：东西名称里含有的词，或中文名称的拼音。@ 表示模组，# 表示标签，$ 表示提示文字，\* 表示 ID；- 排除，| 表示“或”，( ) 分组；>64、\<10 或 =1 按数量找。@minecraft ingot -iron 会找出除铁以外的所有锭。以 ? 开头，就是问窗口旁的模型你想要什么。
      </residual:Part>
      <residual:Part name="jobs">
        **任务**：这个网络上正在进行的合成任务
      </residual:Part>
      <residual:Part name="sort">
        **排序**：按名称、数量或模组排序。按住 Shift 点击可反向。
      </residual:Part>
      <residual:Part name="grid">
        **各种东西**：点击取一组，右键取一半，按住 Shift 点击把一组送进物品栏；手里有东西时点击放入，右键放一个。按住 Alt 点击把这种东西钉在最前面。
      </residual:Part>
      <residual:Part name="scrollbar">
        **滚动条**：拖动它，或在网格上滚动。
      </residual:Part>
      <residual:Part name="usage">
        **用了多少**：存了多少、还能存多少，以及有多少种。
      </residual:Part>
      <residual:Part name="model">
        **模型**：通过检索测试的模型。搜索以 ? 开头时，它会在存着的一切里找你想要的东西。
      </residual:Part>
      <residual:Part name="craft">
        **合成网格**：和工作台一样用，所需的东西从网络里取。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    向网络要它能做的东西：规划，以及缺什么。

    <residual:Shot name="storage_terminal_ask" alt="十六支火把的规划，缺煤">
      <residual:Part name="amount">
        **数量**：要做多少，可以输入，也可以按按钮增减。
      </residual:Part>
      <residual:Part name="+16">
        **增减**：加上这么多；按住 Shift 则减去
      </residual:Part>
      <residual:Part name="plan">
        **规划**：存着 · ×合成次数 · −缺少
      </residual:Part>
      <residual:Part name="start">
        **开始**：开始任务。缺的东西它会等：放进网络，任务就接着做。
      </residual:Part>
      <residual:Part name="back">
        **返回**：回到网格
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    网络上运行的任务：每个做什么、做到哪儿了、在等什么。

    <residual:Shot name="storage_terminal_jobs" alt="等煤的任务">
      <residual:Part name="job:0">
        **一个任务**：点击查看它的规划
      </residual:Part>
      <residual:Part name="job:0:cancel">
        **取消**：停止这个任务。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    任务的规划画成树：每一行都在需要它的那一步下面。

    <residual:Shot name="storage_terminal_tree" alt="画成树的任务规划">
      <residual:Part name="tree">
        **规划**：每样东西有多少来自存储、合成进行到哪儿，还缺什么。
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
