---
navigation:
  title: 中继门
  icon: relay_gate
  parent: screens/index.md
  position: 21
---

# 中继门的界面

对中继门使用即可打开。网络怎样搭起来、门怎样开启，见[模型服务](../deployment.md)。

它的名字、网络，以及网络上可以去的每一道门。

<residual:Shot name="relay_gate_map" alt="中继门的界面">
  <residual:Part name="relay_gate.name">
    **它的名字**：显示在地图和每个门的列表里。
  </residual:Part>
  <residual:Part name="status">
    **开启或关闭**：开启时显示模型每秒能回答多少次，关闭时说明原因。
  </residual:Part>
  <residual:Part name="map">
    **地图**：显示这里的整个网络。滚轮缩放，拖动移动。
  </residual:Part>
  <residual:Part name="gates">
    **门**：网络上的其他每一道门；点击就去那里。
  </residual:Part>
  <residual:Part name="map.fit">
    **显示全部**：显示这里的整个网络。滚轮缩放，拖动移动。
  </residual:Part>
</residual:Shot>
