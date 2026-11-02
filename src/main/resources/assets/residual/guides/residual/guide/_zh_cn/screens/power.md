---
navigation:
  title: 发电机、冶炼炉与反应堆
  icon: combustion_generator
  parent: screens/index.md
  position: 11
---

# 电力的界面

对燃烧发电机或冶炼炉使用即可打开。它们产生和使用什么，见[电力](../power.md)。

<residual:Clip name="smelter" alt="冶炼炉槽里的控制器把熔炼保持在温区内" />

<residual:Steps>
  <residual:Step>
    **燃烧发电机**：火焰下面放燃料。

    <residual:Shot name="power_generator" alt="正在烧煤的燃烧发电机">
      <residual:Part name="fuel">
        **燃料**：熔炉能烧的东西。
      </residual:Part>
      <residual:Part name="flame">
        **火焰**：随着燃料燃尽而变小。
      </residual:Part>
      <residual:Part name="power">
        **储存的电力**：指向它可看到有多少，以及它产生多少。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **冶炼炉**：左边放矿石，右边出锭。

    <residual:Shot name="power_smelter" alt="冶炼炉的界面">
      <residual:Part name="ore">
        **矿石**
      </residual:Part>
      <residual:Part name="work">
        **工序**：随着冶炼填满。有控制器时，这里显示熔融状态：每次查看一列，带状区域以阴影标出。
      </residual:Part>
      <residual:Part name="ingots">
        **锭**
      </residual:Part>
      <residual:Part name="power">
        **储存的电力**：指向它可看到有多少，以及它消耗多少。
      </residual:Part>
      <residual:Part name="controller">
        **控制器**：一个练过控制的模型。它把每次熔炼保持在温区内，保住的熔炼出三个锭而不是两个。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **反应堆核心**：火焰下面放燃料，周期一次次查看地画出来。

    <residual:Shot name="power_reactor" alt="反应堆核心的界面">
      <residual:Part name="fuel">
        **燃料**：反应堆燃料。
      </residual:Part>
      <residual:Part name="flame">
        **火焰**：随着燃料燃尽而变小。
      </residual:Part>
      <residual:Part name="cycle">
        **周期**：每次查看一列，带内的部分标了底色。指向它可看到它为什么没有运行，或者这个周期走到哪里、上个周期保持了多少。
      </residual:Part>
      <residual:Part name="power">
        **储存的电力**：指向它可看到有多少，以及它产生多少。
      </residual:Part>
      <residual:Part name="controller">
        **控制器**：通过安全壳测试的模型。没有它就无法启动，失去它就会自行停堆。
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
