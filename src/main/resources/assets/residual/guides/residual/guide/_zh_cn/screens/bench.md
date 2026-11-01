---
navigation:
  title: 试验台
  icon: bench
  parent: screens/index.md
  position: 2
---

# 试验台的界面

对试验台使用即可打开。设计怎样接线，见[试验台与制造机](../bench.md)。

<residual:Clip name="bench" alt="试验台上的 Softmax：直接照公式接，在接近 1000 的分数上溢出；先减去每行的最大值，每个例子都能通过" />

<residual:Steps>
  <residual:Step>
    一个设计，从零件一路接到结果。

    <residual:Shot name="bench_board" alt="试验台上接好的均值：求和除以大小，每个用例都通过">
      <residual:Part name="spec">
        **规格**：你正在接的规格。点击可换一个。
      </residual:Part>
      <residual:Part name="palette">
        **零件**：设计用来接线的东西：最小的运算，以及你验证过的模块。把它拖到板上。
      </residual:Part>
      <residual:Part name="canvas">
        **接线板**：把部件拖到板上。从一个节点的右边连到另一个的左边，最后一个节点连到结果。右键移除；Ctrl+Z 撤销。点击节点查看它的数。
      </residual:Part>
      <residual:Part name="inspector">
        **它的数字**：你点击的节点在所选用例上算出了什么，或结果应该是什么。
      </residual:Part>
      <residual:Part name="goal">
        **目标**：这个规格必须算出什么，以及它的公式。
      </residual:Part>
      <residual:Part name="cases">
        **用例**：每个测试用例一个标记，表示通过与否。点击一个，就用它来运行这个设计。
      </residual:Part>
      <residual:Part name="result">
        **结果**：把最后一个节点接到这里。绿色表示所选用例通过。
      </residual:Part>
      <residual:Part name="record">
        **录到光掩模**：每个用例都通过后，验证这个设计；规格能造出机器时，把它录到光掩模上。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    所有规格：验证过的打勾，还没开放的上锁。

    <residual:Shot name="bench_chooser" alt="接线板上方打开的规格列表">
      <residual:Part name="spec:softmax">
        **一个规格**：点击就能打开。它所依赖的规格都验证过后，它才会开放。
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
