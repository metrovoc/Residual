---
navigation:
  title: 外骨骼
  icon: exosuit
  parent: screens/index.md
  position: 21
---

# 外骨骼的界面

潜行时使用外骨骼，或穿着它时按键，即可打开。它靠什么运转，见[外骨骼](../exosuit.md)。

它的板子发现了什么，以及每种能力，在窗口旁打开或关闭。

<residual:Shot name="exosuit_board" alt="板上没有模型的外骨骼">
  <residual:Part name="charge">
    **电量**：指向它可看到存着多少。
  </residual:Part>
  <residual:Part name="display">
    **它的板子**：模型的权重、用普通浮点和每种能运行的形式各占多少、各得多少分、它的算力，以及每种运行中的能力要从中取走多少。
  </residual:Part>
  <residual:Part name="row:flight">
    **一种能力**：每种能力一行，带着它要的算力，运行时为青色，打开但算力不够时为琥珀色。指向一行可看到它做什么。
  </residual:Part>
  <residual:Part name="ability.flight">
    **开关**：打开或关闭这种能力。打开的能力从最便宜的开始运行，算力用完为止。
  </residual:Part>
</residual:Shot>
