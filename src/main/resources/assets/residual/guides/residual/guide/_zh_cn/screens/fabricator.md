---
navigation:
  title: 制造机
  icon: fabricator
  parent: screens/index.md
  position: 3
---

# 制造机的界面

对制造机使用即可打开。它能造什么，见[试验台与制造机](../bench.md)。

<residual:Steps>
  <residual:Step>
    没有光掩模时，它能造的是不需要光掩模的机器。

    <residual:Shot name="fabricator_choosing" alt="没有光掩模的制造机：可选的普通机器">
      <residual:Part name="mask">
        **光掩模**：光掩模提供它的设计能造出的东西。
      </residual:Part>
      <residual:Part name="choices">
        **能造的东西**：点击选择；青色的就是它要造的。
      </residual:Part>
      <residual:Part name="materials">
        **材料**：这台机器需要的材料，下方写着数量，不够时显示为红色。
      </residual:Part>
      <residual:Part name="arrow">
        **工序**：随着制造填满。指向它可看到机器的名称、用途和所需时间。
      </residual:Part>
      <residual:Part name="output">
        **造好的东西**：造好之前是淡淡的。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    放进材料，它就开始工作。

    <residual:Shot name="fabricator_making" alt="工作中的制造机，箭头正在填满">
      <residual:Part name="materials">
        **材料**：工作开始时取走。
      </residual:Part>
      <residual:Part name="arrow">
        **工序**
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
