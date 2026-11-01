---
navigation:
  title: 预报机
  icon: forecaster
  parent: screens/index.md
  position: 7
---

# 预报机的界面

对预报机使用即可打开。它怎样开始信任一个模型，见[预报机](../forecaster.md)。

<residual:Clip name="forecaster_screen" alt="检查点放入预报机，并用它观察到的机器来检验" />

它看到的周围情况，以及模型离这些情况有多近。

<residual:Shot name="forecaster_watching" alt="正在观察熔炉的预报机">
  <residual:Part name="field">
    **周围的方块**：四格之内的作物和机器，俯视，北朝上，高一格或低一格的也算。预报机在正中；模型刚超前运行过的带有轮廓。
  </residual:Part>
  <residual:Part name="tab:machines">
    **机器**：柱状图显示哪一类方块：作物或机器。
  </residual:Part>
  <residual:Part name="bars">
    **柱状图**：它们从每个位置走了多远（琥珀色），对照模型的预期（青色）。指向一组可读出数字。
  </residual:Part>
  <residual:Part name="state">
    **它在做什么**：它是在观察、校验，还是在让它们超前运行。
  </residual:Part>
  <residual:Part name="row:machines">
    **每一类**：它观察多少个，然后是已看到的进度或模型的接近程度。误差在 11% 以内，每查看一次就让它们跑四次；在 25% 以内，跑两次。
  </residual:Part>
  <residual:Part name="log">
    **田间日志**：田间日志记下预报机看到的一切，每株作物或每台机器一行。放进模型核心的数据槽，就是针对这些方块练习预报。
  </residual:Part>
  <residual:Part name="model">
    **模型**：一个学会了作物和机器接下来会怎样的模型：检查点，或调谐到其模型服务器的中继卡。
  </residual:Part>
</residual:Shot>
