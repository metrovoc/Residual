---
navigation:
  title: 精炼厂的机器
  icon: refinery_intake
  parent: screens/index.md
  position: 25
---

# 精炼厂的界面

对线上的任意一台机器使用即可打开它的界面。一条线怎样搭起来、每台机器值多少，见[精炼厂](../refinery.md)。

<residual:Steps>
  <residual:Step>
    **精炼进料口**：放进去的东西，以及可以让它做的两件事。

    <residual:Shot name="refinery_intake" alt="一次精炼之后的精炼进料口">
      <residual:Part name="slots">
        **放进去的东西**：档案驱动器和中继转储，最多九个。
      </residual:Part>
      <residual:Part name="rows">
        **它读到的**：槽里有什么、一共多少文本；测量之后，还有原始文本的留出损失。
      </residual:Part>
      <residual:Part name="refinery.start">
        **精炼**：把这里的一切送上线：每台机器的正面对着下一台，最远 12 格，中间不能有实心方块。
      </residual:Part>
      <residual:Part name="refinery.measure">
        **测量**：用每台机器放行的文本训练小读者，并在每台机器上方显示它对留出损失的影响。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **去重筛**：它去掉了什么，以及它的哈希画出的曲线。

    <residual:Shot name="refinery_sieve" alt="一次精炼之后的去重筛">
      <residual:Part name="rows">
        **它去掉的**：指向一行可看到它的意思。
      </residual:Part>
      <residual:Part name="curve">
        **曲线**：横轴是两份文档的相似度，纵轴是它们被比较的可能性：128 个哈希分成若干段。竖线是阈值，柱子是比较过的文档对。
      </residual:Part>
      <residual:Part name="refinery.setting.near">
        **近似副本阈值**：两份文档要多相似（按共有的五词连串算）才算副本。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **过滤床**：每条规则一行、一个开关。

    <residual:Shot name="refinery_filters" alt="一次精炼之后的过滤床">
      <residual:Part name="rows">
        **每条规则丢掉的**：指向一行可看到它的意思。
      </residual:Part>
      <residual:Part name="refinery.setting.short">
        **太短**：丢掉不足 12 个词的文档。
      </residual:Part>
      <residual:Part name="refinery.setting.symbols">
        **符号太多**：丢掉符号和数字超过五分之一的文档。
      </residual:Part>
      <residual:Part name="refinery.setting.words">
        **不像词的长度**：丢掉词的平均长度不足 2.5 或超过 9 个字母的文档。
      </residual:Part>
      <residual:Part name="refinery.setting.repeats">
        **翻来覆去**：丢掉一行内容占了一半以上的文档。
      </residual:Part>
      <residual:Part name="refinery.setting.endings">
        **行尾没有收束**：丢掉几乎没有一行像句子或诗行那样收尾的文档。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **质量分类器**：它的模型和规则，以及各自的决定。

    <residual:Shot name="refinery_classifier" alt="没有模型的质量分类器">
      <residual:Part name="slots">
        **它的模型**：一个检查点，或一张调谐到某台模型服务器的中继卡，它的模型要通过质量测试。没有的话就只靠规则。
      </residual:Part>
      <residual:Part name="rows">
        **它的决定**：指向一行可看到它的意思。
      </residual:Part>
      <residual:Part name="refinery.setting.sure">
        **把握阈值**：模型要有多大把握，无论判好判坏。把握不够的交给规则。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **程序印刷机**：它往文本流里写了什么。

    <residual:Shot name="refinery_press" alt="一次精炼之后的程序印刷机">
      <residual:Part name="slots">
        **它的书**：一本模板书，供「书」程序使用：每页一个模板，用 \{a|b} 表示选择。
      </residual:Part>
      <residual:Part name="rows">
        **它写的**：指向一行可看到它的意思。
      </residual:Part>
      <residual:Part name="refinery.setting.program">
        **程序**：它写什么：带演算的算术题、指令及其含义、东西及其种类，或者一本模板书。
      </residual:Part>
      <residual:Part name="refinery.setting.share">
        **写入量**：它加入多少，按进入文本的份额算。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **领域混合器**：每类文字一行，以及一次训练的最后阶段读什么。

    <residual:Shot name="refinery_mixer" alt="一次精炼之后的领域混合器">
      <residual:Part name="rows">
        **各类文字**：每类文字在模型所读内容里的份额。点击一行可以增减。
      </residual:Part>
      <residual:Part name="refinery.setting.anneal">
        **退火领域**：一次训练的最后阶段只读这一类文字，学习率降到五分之一。
      </residual:Part>
      <residual:Part name="refinery.setting.after">
        **退火起点**：在模型核心里训练多少步后开始退火。测量时，小读者在最后五分之一退火。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **污染检查站**：它在每个基准的测试里发现了什么。

    <residual:Shot name="refinery_check" alt="一次精炼之后的污染检查站">
      <residual:Part name="rows">
        **它发现的**：先是发现的行，然后每个基准一行。滚动可看到其余。
      </residual:Part>
      <residual:Part name="refinery.setting.remove">
        **发现之后**：去掉发现的内容，或者只报告。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **词元罐**：这条线存下的内容，已经数好。

    <residual:Shot name="refinery_tank" alt="一次精炼之后的词元罐">
      <residual:Part name="tokenizer">
        **分词器**：一张分词器，按它的切分来数词元。没有的话，每个字符算一个词元。
      </residual:Part>
      <residual:Part name="cell">
        **词元芯**：一个词元芯。放进来就能取用这条线上所有罐里的文本，再放进模型核心的数据槽。
      </residual:Part>
      <residual:Part name="rows">
        **它存下的**：指向一行可看到它的意思。
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
