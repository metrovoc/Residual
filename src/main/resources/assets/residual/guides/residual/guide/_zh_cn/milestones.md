---
navigation:
  title: 里程碑
  icon: checkpoint
  position: 12
---

# 里程碑

每个里程碑都是一项进度，外框与进度界面中的相同；达成后外框就会亮起。它们大致按一个世界展开的顺序排列，不过多数章节可以按任意顺序完成。

## 第一个模型

<residual:Milestone id="model/saved">用线缆把一个<ItemLink id="embedding" />接到<ItemLink id="model_core" />，用<ItemLink id="forecast_dataset" />训练并保存：[入门](getting-started.md)。</residual:Milestone>

<residual:Milestone id="benchmark/forecasting">保存时会用每一项基准测试模型；第一个模型练得够久，就能通过这一项。</residual:Milestone>

<residual:Milestone id="forecast/watching">它记录周围熔炉和作物的变化：[预报机](forecaster.md)。</residual:Milestone>

<residual:Milestone id="forecast/ahead">通过预报的模型，等机器做完十来件后就能管机器；你自己的田，可能要用预报机自己的日志训练出的模型。</residual:Milestone>

<residual:Milestone id="forecast/deep">制造机用 Transformer 光掩模制造预报机 II；与方块差距在 5% 以内的模型让它们以八倍速度运行：[预报机](forecaster.md)。</residual:Milestone>

<residual:Milestone id="forecast/hourglass">制造机用注意力光掩模制造它，带在身上就会存下时间。潜行使用一台让方块超前运行的预报机来绑定它，再对这样的方块使用：[预报机](forecaster.md)。</residual:Milestone>

<residual:Milestone id="agent/made">合成一个，放在它该住的地方：[智能体](agents.md)。</residual:Milestone>

<residual:Milestone id="agent/program">在它的界面里输入一段，或用指令板点选拼出来。</residual:Milestone>

## 存储

<residual:Milestone id="storage/drive">它能存任意多种东西：[存储](storage.md)。</residual:Milestone>

<residual:Milestone id="storage/terminal">用存储链路把它连到驱动器机架上。</residual:Milestone>

<residual:Milestone id="storage/port">把它的正面贴在箱子或机器上，背面连到网络。</residual:Milestone>

<residual:Milestone id="storage/assembler">在装配机的网格里摆出配方，告诉它要保持多少。</residual:Milestone>

<residual:Milestone id="storage/request">在存储终端里点击网络能做的东西，请求一个数量并开始：[存储](storage.md)。</residual:Milestone>

## 旧世界

<residual:Milestone id="ruins/found">档案屋、中继塔和地下库彼此相隔很远：[遗迹](ruins.md)。</residual:Milestone>

<residual:Milestone id="archive/shard">每块都是某一份记录的一部分，有些文字已经遗失：[档案](archive.md)。</residual:Milestone>

<residual:Milestone id="archive/drive">同一份记录的残片归档在一起，能拼成一份更完整的副本。</residual:Milestone>

<residual:Milestone id="deploy/reader">它用你的模型修复记录中遗失的文字。</residual:Milestone>

<residual:Milestone id="archive/tokenizer">从找回的文字中学到的词表，能让模型按词而不是按字母阅读：[词元](tokenizers.md)。</residual:Milestone>

<residual:Milestone id="benchmark/reading">在档案驱动器上训练，直到测试损失不高于 1.40：[模型与测试](models.md)。</residual:Milestone>

## 从零造机器

<residual:Milestone id="bench/made">每份规格都附带一些用例，专门难倒显而易见的设计：[试验台与制造机](bench.md)。</residual:Milestone>

<residual:Milestone id="bench/fabricator">在遗迹中找到的光掩模也能在里面使用。</residual:Milestone>

<residual:Milestone id="bench/matmul"></residual:Milestone>

<residual:Milestone id="bench/linear">验证矩阵乘法后开放。</residual:Milestone>

<residual:Milestone id="bench/mlp">验证线性层后开放。</residual:Milestone>

<residual:Milestone id="bench/mean"></residual:Milestone>

<residual:Milestone id="bench/layer_norm">验证均值后开放。</residual:Milestone>

<residual:Milestone id="bench/gelu"></residual:Milestone>

<residual:Milestone id="bench/softmax">上千的分数会让显而易见的设计溢出。</residual:Milestone>

<residual:Milestone id="bench/attention">验证 softmax 后开放。</residual:Milestone>

<residual:Milestone id="bench/transformer">验证注意力后开放。</residual:Milestone>

<residual:Milestone id="bench/all"></residual:Milestone>

<residual:Milestone id="model/stream">Transformer 层从中读取，并向其中累加：[残差流](stream.md)。</residual:Milestone>

## 能干活的模型

保存一个能通过某项基准的模型，就算通过了这项基准；[模型与测试](models.md)中的表格列出了每项该练什么。

<residual:Milestone id="benchmark/recall">用<ItemLink id="storage_dataset" />练习。</residual:Milestone>

<residual:Milestone id="benchmark/reversal">用<ItemLink id="errand_dataset" />练习。</residual:Milestone>

<residual:Milestone id="benchmark/induction">用<ItemLink id="pattern_dataset" />练习；需要两层注意力。</residual:Milestone>

<residual:Milestone id="benchmark/sorting">用<ItemLink id="sorting_dataset" />练习。</residual:Milestone>

<residual:Milestone id="benchmark/control">用<ItemLink id="process_dataset" />练习：核心用模型自己的选择进行熔炼，需要一个能看到整个熔炼过程的模型。</residual:Milestone>

<residual:Milestone id="benchmark/orders">用<ItemLink id="orders_dataset" />练习；需要一个大到无法随身携带的模型。</residual:Milestone>

<residual:Milestone id="model/complete">一个模型核心最多可同时练习四种数据。</residual:Milestone>

## 让模型干活

<residual:Milestone id="deploy/server">更大的模型留在服务器里，通过中继器作答：[模型服务](deployment.md)。</residual:Milestone>

<residual:Milestone id="deploy/relay">图纸出现在中继塔和地下库里。</residual:Milestone>

<residual:Milestone id="agent/order">以它的名字开头发一条聊天消息，例如 *Pip, bring me some bread*。它的模型必须练过指令。</residual:Milestone>

<residual:Milestone id="agent/copy">标出建筑的对角和副本要立在上面的方块，把标记交给模型通过了归纳测试的智能体：[建筑师](architect.md)。</residual:Milestone>

<residual:Milestone id="agent/move">同样的标记，用 `move` 代替 `copy`。</residual:Milestone>

## 电力

<residual:Milestone id="power/generator">它烧熔炉能烧的东西：[电力](power.md)。</residual:Milestone>

<residual:Milestone id="power/smelter">制造机用 Transformer 光掩模制造冶炼炉；给它电力和粗矿。</residual:Milestone>

## 打捞

<residual:Milestone id="wreck/found">每一个都以自己的方式失败了：[打捞模型](salvage.md)。</residual:Milestone>

<residual:Milestone id="wreck/shape"></residual:Milestone>

<residual:Milestone id="wreck/leak"></residual:Milestone>

<residual:Milestone id="wreck/order"></residual:Milestone>

<residual:Milestone id="wreck/diverge"></residual:Milestone>

<residual:Milestone id="wreck/depth"></residual:Milestone>
