---
navigation:
  title: 机房控制台
  icon: hall_console
  parent: screens/index.md
  position: 26
---

# 机房控制台的界面

对机房控制台使用即可打开。怎样才算机房，见[机房](../datacenter.md)。

一块显示屏：机房是什么样、正在做什么，以及它放得下的最大一次训练要花多少。

<residual:Shot name="hall_console_reading" alt="机房控制台的界面">
  <residual:Part name="reading">
    **机房**：它的机架、机组和设备，额定能力，正在运行多少，以及耗电多少。指向第一行可看到还缺什么，指向电力那行可看到 PUE 是什么。
  </residual:Part>
  <residual:Part name="largest">
    **最大的一次训练**：这是预测，由公式算出：每个参数每个词元 6 次运算，每个参数 16 字节，按全部额定速度算。
  </residual:Part>
  <residual:Part name="racks">
    **它的机架**：每个机架一列，机架越忙，这一列越高。
  </residual:Part>
</residual:Shot>
