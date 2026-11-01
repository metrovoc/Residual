---
navigation:
  title: 装配机
  icon: assembler
  parent: screens/index.md
  position: 15
---

# 装配机的界面

对装配机使用即可打开。网络怎样合成，见[存储](../storage.md)。

画在网格里的配方。

<residual:Shot name="assembler_recipe" alt="画着木棍配方的装配机">
  <residual:Part name="grid">
    **配方**：拿着一组物品点击，把它这一种摆进配方；空手点击则清除。网格里放的是样子，不是物品：装配机从网络里取材料。
  </residual:Part>
  <residual:Part name="result">
    **它做的东西**：由网格算出。
  </residual:Part>
  <residual:Part name="keep">
    **保持**：要在网络里保持多少，少于这个数就做。不设保持时，它只做别人要的东西。按住 Shift，乘以六十四。
  </residual:Part>
  <residual:Part name="status">
    **它在做什么**：正在合成、已经够了、缺东西，或者还没有配方。
  </residual:Part>
</residual:Shot>
