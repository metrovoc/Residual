---
navigation:
  title: 调校台
  icon: tuner
  parent: screens/index.md
  position: 28
---

# 调校台的界面

对调校台使用即可打开。每种调校方式做什么、能换来什么，见[调校](../tuning.md)。

左边是已保存的模型，下面放它需要的东西，右边是空白检查点；工具栏决定怎么调校。

<residual:Shot name="tuner_idle" alt="空的调校台">
  <residual:Part name="base">
    **基础模型**：起点是已保存的模型。它不会改变：产出的是一个新模型。
  </residual:Part>
  <residual:Part name="aid">
    **放在旁边的**：数据集或档案驱动器；放两个就混合。
  </residual:Part>
  <residual:Part name="out">
    **产出**：一枚空白检查点，用来保存。
  </residual:Part>
  <residual:Part name="show">
    **显示屏**：开始之前，显示当前方式做什么。运行时，青色是奖励，琥珀色是真正对了多少，还有每次尝试的标记，以及模型最后写的东西。
  </residual:Part>
  <residual:Part name="tuner.run">
    **开始调校**：开始；或者停下并保留已有的进度。
  </residual:Part>
  <residual:Part name="tuner.method">
    **方式**：它对模型做什么，以及需要在旁边放什么。
  </residual:Part>
  <residual:Part name="tuner.budget">
    **步数**：保存之前进行多久。
  </residual:Part>
  <residual:Part name="tuner.auto">
    **自动开始**：只要基础模型和空白检查点都在就开始，用于调校台和漏斗组成的流水线。完成的检查点从下方的漏斗取走。
  </residual:Part>
</residual:Shot>
