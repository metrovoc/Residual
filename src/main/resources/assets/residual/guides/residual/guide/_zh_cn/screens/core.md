---
navigation:
  title: 模型核心
  icon: model_core
  parent: screens/index.md
  position: 0
---

# 模型核心的界面

对模型核心使用即可打开。各个标签页带着模型从数据走到测试；它们如何配合，见[模型核心](../core.md)。

<residual:Steps>
  <residual:Step>
    **数据**：模型用什么练习，又存到哪里。

    <residual:Shot name="core_data" alt="数据页：第一个槽里有数据集，旁边是分词器槽和检查点槽">
      <residual:Part name="tab:data">
        **标签页**：这个界面的几个视图。它会停在你上次离开时的那一页。
      </residual:Part>
      <residual:Part name="data:0">
        **练习**：给模型练习用的数据集或档案驱动器。
      </residual:Part>
      <residual:Part name="tokenizer">
        **分词器**：放在这里的分词器决定模型读写的词元。没有分词器时，模型一次读一个字符。更换分词器会改变第一层和最后一层的形状，这两层将从头开始。
      </residual:Part>
      <residual:Part name="checkpoint">
        **检查点**：保存时，放在这里的检查点会接收训练好的模型。已保存的检查点会显示它的能力。
      </residual:Part>
      <residual:Part name="core.save">
        **存入检查点**：用每一项基准测试这个模型，并把它连同分数一起存进检查点。
      </residual:Part>
      <residual:Part name="practising">
        **练习的内容**：已放入的东西能让它练习的每一项基准，以及这项基准的用途。
      </residual:Part>
      <residual:Part name="status">
        **状态**：核心正在做什么，以及模型已经训练了多少步。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **训练**：模型一步一步地学习。

    <residual:Shot name="core_train" alt="模型训练时的训练页：练习损失和测试损失正在下降">
      <residual:Part name="chart">
        **曲线**：青色是训练损失，琥珀色是测试损失，绿色是准确率，横轴是训练的步数。指向曲线可读出任意一步。
      </residual:Part>
      <residual:Part name="legend">
        **现在的数字**：曲线末端的数字。指向它们可看到参数量、梯度范数和线程数。
      </residual:Part>
      <residual:Part name="train">
        **训练**：开始训练，也用来停止。
      </residual:Part>
      <residual:Part name="core.reset">
        **重置**：用新的权重让模型从头开始。
      </residual:Part>
      <residual:Part name="core.check">
        **校验**：重新读取模型的机器，列出其中的问题。
      </residual:Part>
      <residual:Part name="core.speed">
        **速度**：「观察」放慢每一步，方便你跟着看；「最快」让核心尽可能快地训练。
      </residual:Part>
      <residual:Part name="core.learning_rate">
        **学习率**：每一步把权重移动多远。太大，损失会上下乱跳；太小，几乎不动。
      </residual:Part>
      <residual:Part name="core.batch">
        **批量**：每一步同时从多少个例子中学习。
      </residual:Part>
      <residual:Part name="core.clip">
        **裁剪**：一步的梯度最长能有多长，这样一批坏数据就不会把模型带偏。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **测试**：模型在没练习过的东西上表现如何。

    <residual:Shot name="core_tests" alt="测试页：每项基准一行，带着分数">
      <residual:Part name="row:forecasting">
        **一项基准**：每项基准一行：名称、一条通向及格线的进度条和分数，及格后变绿。滚动可看到其余基准。指向一行可看到它的用途、测试时间和测试方式。点击它可看到从新种子训练的结果、它的相似题，以及服务器的排行榜：见[比较模型](../evaluation.md)。
      </residual:Part>
      <residual:Part name="core.test_all">
        **全部测试**：立即运行所有测试。
      </residual:Part>
      <residual:Part name="core.save">
        **存入检查点**：用每一项基准测试这个模型，并把它连同分数一起存进检查点。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    滚动测试页可看到每一项基准。

    <residual:Shot name="core_tests_lower" alt="测试页下方的基准行，最后一项是地层">
      <residual:Part name="row:ground">
        **一项基准**：点击每行可打开它的比较视图。
      </residual:Part>
      <residual:Part name="scrollbar">
        **滚动条**：显示当前看到的是哪些行。在列表上滚动，或点击滚动条，都可移动。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    点击一项基准，比较它的测试、新运行和服务器上的设计。

    <residual:Shot name="core_evaluation" alt="一项基准的比较视图，包含种子运行和服务器排行榜">
      <residual:Part name="evaluation">
        **比较**：原测试与另一套相似题的分数。出现警告，表示原测试带来的优势超出了偶然。
      </residual:Part>
      <residual:Part name="seeds">
        **新种子**：从新权重和批次开始的多次运行有多分散；有上一个设计时，它显示在下面。
      </residual:Part>
      <residual:Part name="leaderboard">
        **服务器榜单**：领先的设计，以及它们的作者、分数和大小。指向一个可看到它的机器。
      </residual:Part>
      <residual:Part name="histograms">
        **分布**：服务器的设计按分数和大小怎样分布，当前模型以琥珀色标出。
      </residual:Part>
      <residual:Part name="evaluation.back">
        **返回**：返回基准列表。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **试用**：模型作答。

    <residual:Shot name="core_try" alt="试用页：从测试中抽出的一个样例和模型的回答">
      <residual:Part name="try.benchmark">
        **基准**：用哪项基准来试它。
      </residual:Part>
      <residual:Part name="try.example">
        **样例**：从测试中抽一个新样例，让模型作答。能打字的基准上，这个按钮变成提问或续写。
      </residual:Part>
      <residual:Part name="output">
        **它的回答**：灰色是给模型看的，白色是它写的。条形显示它所选第一个词元的概率；⏎ 表示换行。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **检查**：模型的机器，每台一行。

    <residual:Shot name="core_inspect" alt="检查页：模型的每台机器，以及它的形状、参数和梯度">
      <residual:Part name="table">
        **它的机器**：每台机器的名称、输出的形状、参数量和传到它那里的梯度大小。有问题的排在最前面，显示为红色。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **配方**：一次运行除了学习率之外怎么走。

    <residual:Shot name="core_recipe" alt="配方页：各项设置、运行正在做的事、整次运行的学习率，以及核心身边的单元">
      <residual:Part name="settings">
        **设置**：相应的单元接触核心之前，它是暗的。每一项的作用见[配方](../recipe.md)。
      </residual:Part>
      <residual:Part name="readings">
        **运行正在做的事**：当前的学习率、权重占用的大小、读到的文字、尖峰、最大的分数，以及第二个头猜对的比例。指向某一项可看到它的含义。
      </residual:Part>
      <residual:Part name="rate">
        **整次运行的学习率**：学习率从第一步到最后一步乘上的比例，并用一条线标出运行走到了哪里。
      </residual:Part>
      <residual:Part name="units">
        **单元**：有单元接触核心（直接接触，或经由训练加速器和其他单元相连）时亮起。指向一个可看到它开放什么。
      </residual:Part>
      <residual:Part name="recipe.schedule">
        **计划**：学习率在一次运行中怎么变：保持不变，从预热结束起沿余弦下降，或者先保持、在最后五分之一下降。会下降的计划需要一个长度。
      </residual:Part>
      <residual:Part name="recipe.warmup">
        **预热**：学习率要用多少步升到全值。
      </residual:Part>
      <residual:Part name="recipe.length">
        **长度**：一次运行持续多少步，到了就自己停下。没有尽头时，它会一直跑到被停下。
      </residual:Part>
      <residual:Part name="recipe.precision">
        **精度**：模型的矩阵以及进入矩阵的数舍入成什么格式，训练时和之后每次作答时都是如此。越窄，每个权重占的字节越少。
      </residual:Part>
      <residual:Part name="recipe.moves">
        **优化器**：什么来移动权重：AdamW 让每个数各走各的；Muon 让模型内部的每个矩阵整体移动，各个方向走得一样多。
      </residual:Part>
      <residual:Part name="recipe.packing">
        **打包**：档案文字怎样切成行：只取长段落的窗口；用所有段落打包成行；或者打包并加掩码，让任何东西都看不到别的段落。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **算力**：这次训练实际花了多少，以及它怎样分配工作。

    <residual:Shot name="core_compute" alt="算力页：这次训练的设备、内存、运算次数和电力，以及每个设备在上一步做了什么">
      <residual:Part name="reading">
        **这次训练**：它怎样分配工作、用多少设备，内存和要放下它的容量，每秒运算次数、电力和 PUE，以及到目前为止花了多少。
      </residual:Part>
      <residual:Part name="timeline">
        **上一步**：上一步中每个设备做的事：青色前向，琥珀色反向，紫色集合通信。暗处是在等待：在流水线并行中，那就是气泡。
      </residual:Part>
      <residual:Part name="compute.split">
        **分摊工作**：一次训练如何把工作分给各个设备。下一次训练开始时生效。
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
