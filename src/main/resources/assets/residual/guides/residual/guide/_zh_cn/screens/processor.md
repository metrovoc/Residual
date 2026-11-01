---
navigation:
  title: 处理器
  icon: processor
  parent: screens/index.md
  position: 16
---

# 处理器的界面

对处理器使用即可打开。网络怎样合成，见[存储](../storage.md)。

一个样式：放进去什么、前面的机器、出来什么。

<residual:Shot name="processor_pattern" alt="处理器的样式：放进粗铁，出来铁锭，前面是熔炉">
  <residual:Part name="in">
    **放进去的**：机器一轮要用的材料。手持物品点击放入该数量，右键加一个；空手点击清空该格，右键减一个。网格里只是图样，不是真实物品。
  </residual:Part>
  <residual:Part name="out">
    **出来的**：机器一轮的产物。第一项是网络可以下单的东西，其余随之产出。手持物品点击放入该数量，右键加一个；空手点击清空该格，右键减一个。
  </residual:Part>
  <residual:Part name="machine">
    **机器**：处理器前面的东西，样式就在它上面运行。
  </residual:Part>
  <residual:Part name="in:on">
    **页**：两边各有九页，每页九格。
  </residual:Part>
  <residual:Part name="keep">
    **保持**：要在网络里保持多少，少于这个数就做。
  </residual:Part>
  <residual:Part name="status">
    **它在做什么**：每秒做多少轮，或者缺什么。
  </residual:Part>
</residual:Shot>
