---
navigation:
  title: BPE 锻炉
  icon: bpe_forge
  parent: screens/index.md
  position: 4
---

# BPE 锻炉的界面

对 BPE 锻炉使用即可打开。词表有什么用，见[词元](../tokenizers.md)。

<residual:Clip name="forge" alt="BPE 锻炉从驱动器中学习词表，一次合并一对" />

从一个驱动器学习词表，一次合并一次合并地进行。

<residual:Shot name="forge_learning" alt="工作中的 BPE 锻炉：切成词元的一段文字和最近的合并">
  <residual:Part name="text">
    **档案驱动器**：档案驱动器：锻炉从其中可辨认的文本里学习。
  </residual:Part>
  <residual:Part name="card">
    **分词器**：要写入的分词器，空白或已写过的都行。锻炉会在已有的合并上接着学。
  </residual:Part>
  <residual:Part name="sizes">
    **词表大小**：要学多少个词元。词元越大，每个带的文本越多，但嵌入层和最后一层也随词表变大。
  </residual:Part>
  <residual:Part name="progress">
    **进度**：指向它可看到它在做什么。
  </residual:Part>
  <residual:Part name="sample">
    **它的文本，切成词元**：驱动器里的一段文字，按当前词表切开。指向一个词元可看到它由什么组成。
  </residual:Part>
  <residual:Part name="merges">
    **最近的合并**：它最后合并的几对词元，最新的在最前面。
  </residual:Part>
</residual:Shot>
