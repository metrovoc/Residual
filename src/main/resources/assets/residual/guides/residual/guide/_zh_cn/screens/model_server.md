---
navigation:
  title: 模型服务器
  icon: model_server
  parent: screens/index.md
  position: 27
---

# 模型服务器的界面

空手对模型服务器使用即可打开。面板里每样东西能换来什么，见[模型服务](../deployment.md)。

它在服务什么、代价如何，旁边是面板，面板下面是设置。

<residual:Shot name="model_server_empty" alt="空的模型服务器">
  <residual:Part name="model">
    **它服务的模型。**：一枚已保存的检查点。潜行空手对服务器使用可以取回。
  </residual:Part>
  <residual:Part name="rate">
    **每秒回答数**：读 16 个词元、写 8 个的回答，按这个模型和面板里的东西实测的代价算。条形表示它的运算用掉了多少。
  </residual:Part>
  <residual:Part name="memory">
    **内存**：权重、草稿模型和保留的序列，占它全部内存的多少。
  </residual:Part>
  <residual:Part name="drawn">
    **谁在用它**：近来的运算量花在哪里
  </residual:Part>
  <residual:Part name="cache">
    **KV 缓存**：每枚增加 256 KiB；只要有一枚，它就会把每个词元算出的结果留给下一个。
  </residual:Part>
  <residual:Part name="packer">
    **权重打包器**：让下方的按钮能把权重存为 8 位或 4 位。
  </residual:Part>
  <residual:Part name="draft">
    **草稿模型**：一个读相同词元的小模型提议接下来写什么，被服务的模型一次前向检查好几个。需要 KV 缓存。
  </residual:Part>
  <residual:Part name="judge">
    **裁判模型**：调校台教会它给回答打分的模型。对一条指令写多个回答时，留下它打分最高的那个。
  </residual:Part>
  <residual:Part name="server.form">
    **权重形式**：需要权重打包器。打包后的权重占内存更少，但每次前向都要从中读出。它通过的每项测试都会用新形式重测。
  </residual:Part>
  <residual:Part name="server.batch">
    **每次前向最多几条**：需要 KV 缓存。同时等待的序列一起走一次前向，分摊前向本身的开销。
  </residual:Part>
  <residual:Part name="server.effort">
    **每个问题写几个回答**：需要 KV 缓存。对指令或算账，它写这么多个回答并留下一个，靠检查、投票或裁判。每个回答都要付出运算量；屏幕会显示这对每项测试的影响。
  </residual:Part>
</residual:Shot>
