---
navigation:
  title: 阅读器
  icon: reader
  parent: screens/index.md
  position: 6
---

# 阅读器的界面

对阅读器使用即可打开。模型怎样复原失落的文字，见[档案](../archive.md)。

<residual:Clip name="reader" alt="阅读器逐块修复一份记录遗失的文字" />

托盘里放着一块残片，失落的文字已经磨损。

<residual:Shot name="reader_reading" alt="放着残片的阅读器：它的文字，有些已经磨损">
  <residual:Part name="shard">
    **档案残片**：要复原的残片。
  </residual:Part>
  <residual:Part name="model">
    **模型**：用来复原它的东西：一个检查点，或调谐到模型服务器的中继卡。
  </residual:Part>
  <residual:Part name="text">
    **它的文字**：能读的部分和仍然磨损的文字块，复原时会亮起来。
  </residual:Part>
  <residual:Part name="state">
    **它在做什么**：它正在读哪一段、上一次尝试结果如何，或者它还缺什么。
  </residual:Part>
</residual:Shot>
