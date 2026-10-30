---
navigation:
  title: 里程碑
  icon: checkpoint
  position: 12
---

# 里程碑

每个里程碑都是一项进度，外框与进度界面中的相同；达成后外框就会亮起。它们大致按一个世界展开的顺序排列，不过多数章节可以按任意顺序完成。

## 第一个模型

<residual:Milestone id="model/saved">用线缆把一个<ItemLink id="embedding" />接到<ItemLink id="model_core" />，用<ItemLink id="field_dataset" />训练并保存：[入门](getting-started.md)。</residual:Milestone>

<residual:Milestone id="benchmark/reacting">保存时会用每一项基准测试模型；第一个模型练得够久，就能通过这一项。</residual:Milestone>

<residual:Milestone id="agent/made">合成一个，放在耕地旁，交给它你的检查点：[智能体](agents.md)。</residual:Milestone>

<residual:Milestone id="agent/program">在它的界面里输入一段，或用指令板点选拼出来。</residual:Milestone>

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

<residual:Milestone id="benchmark/forecasting">用<ItemLink id="growth_dataset" />练习。</residual:Milestone>

<residual:Milestone id="benchmark/orders">用<ItemLink id="orders_dataset" />练习；需要一个大到无法随身携带的模型。</residual:Milestone>

<residual:Milestone id="model/complete">一个模型核心最多可同时练习四种数据。</residual:Milestone>

## 让模型干活

<residual:Milestone id="forecast/watching">它记录周围作物的生长：[预报机](forecaster.md)。</residual:Milestone>

<residual:Milestone id="forecast/ahead">用预报机自己的日志训练模型，直到预报机信任它。</residual:Milestone>

<residual:Milestone id="deploy/server">更大的模型留在服务器里，通过中继器作答：[模型服务](deployment.md)。</residual:Milestone>

<residual:Milestone id="deploy/relay">图纸出现在中继塔和地下库里。</residual:Milestone>

<residual:Milestone id="agent/order">以它的名字开头发一条聊天消息，例如 *Pip, bring me some bread*。它的模型必须练过指令。</residual:Milestone>

## 打捞

<residual:Milestone id="wreck/found">每一个都以自己的方式失败了：[打捞模型](salvage.md)。</residual:Milestone>

<residual:Milestone id="wreck/shape"></residual:Milestone>

<residual:Milestone id="wreck/leak"></residual:Milestone>

<residual:Milestone id="wreck/order"></residual:Milestone>

<residual:Milestone id="wreck/diverge"></residual:Milestone>

<residual:Milestone id="wreck/depth"></residual:Milestone>
