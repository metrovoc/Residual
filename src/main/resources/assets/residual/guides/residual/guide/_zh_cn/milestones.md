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

## 旧世界

<residual:Milestone id="ruins/found">旧世界的地方彼此相隔很远，各自立在适合它的土地上：[遗迹](ruins.md)。</residual:Milestone>

<residual:Milestone id="archive/shard">每块都是某一份记录的一部分，有些文字已经遗失：[档案](archive.md)。</residual:Milestone>

<residual:Milestone id="ruins/wayfinder">使用寻路仪，选一种遗迹；读过的碎片会把它们写到的地方加进来：[遗迹](ruins.md)。</residual:Milestone>

<residual:Milestone id="archive/drive">同一份记录的残片归档在一起，能拼成一份更完整的副本。</residual:Milestone>

<residual:Milestone id="deploy/reader">它用你的模型修复记录中遗失的文字。</residual:Milestone>

<residual:Milestone id="archive/tokenizer">从找回的文字中学到的词表，能让模型按词而不是按字母阅读：[词元](tokenizers.md)。</residual:Milestone>

<residual:Milestone id="benchmark/reading">在档案驱动器上训练，直到测试损失不高于 1.40：[模型与测试](models.md)。</residual:Milestone>

## 能干活的模型

<residual:Milestone id="model/stream">Transformer 层从中读取，并向其中累加：[残差流](stream.md)。</residual:Milestone>

保存一个能通过某项基准的模型，就算通过了这项基准；[模型与测试](models.md)中的表格列出了每项该练什么。

<residual:Milestone id="benchmark/recall">用<ItemLink id="storage_dataset" />练习。</residual:Milestone>

<residual:Milestone id="benchmark/reversal">用<ItemLink id="errand_dataset" />练习。</residual:Milestone>

<residual:Milestone id="benchmark/induction">用<ItemLink id="pattern_dataset" />练习；需要两层注意力。</residual:Milestone>

<residual:Milestone id="benchmark/sorting">用<ItemLink id="sorting_dataset" />练习。</residual:Milestone>

<residual:Milestone id="benchmark/modular">用<ItemLink id="modular_dataset" />练习，权重衰减要大：[模型与测试](models.md)。</residual:Milestone>


<residual:Milestone id="model/complete">一个模型核心最多可同时练习四种数据。</residual:Milestone>

<residual:Milestone id="model/modern">用现代块代替 Transformer 块，模型通过任意一项测试后保存：[现代块](modern.md)。</residual:Milestone>

## 配方

一次运行除了学习率之外还做什么，在模型核心的「配方」页上设定：[配方](recipe.md)。

<residual:Milestone id="recipe/planned">设定**长度**和一条会下降的计划，让这次运行跑到终点。</residual:Milestone>

<residual:Milestone id="recipe/narrow">把**精度**设为 fp8 或 fp4 来训练，模型通过任意一项测试后保存。</residual:Milestone>

<residual:Milestone id="recipe/muon">在**优化器**里选 Muon 来训练，模型通过任意一项测试后保存。</residual:Milestone>

<residual:Milestone id="recipe/packed">用打包加掩码的行练习档案文本，模型通过「阅读」后保存。</residual:Milestone>

<residual:Milestone id="recipe/second_head">让模型以<ItemLink id="two_token_head" />结尾，通过任意一项测试后保存。</residual:Milestone>

## 看看里面

<residual:Milestone id="discovery/phase_change">从新权重开始，用<ItemLink id="pattern_dataset" />训练一个带注意力的模型。两层就够，核心自己会发现：[流示波器](scope.md)。</residual:Milestone>

<residual:Milestone id="discovery/grokked">用<ItemLink id="modular_dataset" />和较大的权重衰减训练一个小模型，直到它记住数对，然后继续：[模型与测试](models.md)。</residual:Milestone>

<residual:Milestone id="scope/lens">给<ItemLink id="stream_scope" />一个模型，在「透镜」页按「读取」：[流示波器](scope.md)。</residual:Milestone>

<residual:Milestone id="scope/superposed">在「玩具」页，让特征变得罕见。</residual:Milestone>

<residual:Milestone id="scope/patched">在「修补」页，读一段模型擅长的测试：回忆和归纳都要从很远的地方带来答案。</residual:Milestone>

<residual:Milestone id="scope/steered">在「引导」页，在残差流靠后的位置试试 ×4 或 ×8。</residual:Milestone>

<residual:Milestone id="scope/features">在模型擅长的基准上，在靠后的位置拟合一本字典。</residual:Milestone>

<residual:Milestone id="scope/graph">在「归因图」页按「追踪」。它会先拟合缺的字典，所以要等一会儿。</residual:Milestone>

## 试着弄坏它

<residual:Milestone id="failure/rig">合成一台并放下：[故障试验架](trials.md)。</residual:Milestone>

<residual:Milestone id="failure/extracted">在「记忆」页给它一个模型和一个文字不多的驱动器。模型读那些文字够久，或者它植入的秘密，都能做到。</residual:Milestone>

<residual:Milestone id="failure/backdoor">在「后门」页微调至少 500 步；最后这个词要有 80% 的时候起作用。</residual:Milestone>

<residual:Milestone id="failure/forgot">在「遗忘」页给它一个能通过某项基准的模型，中间格子放完全不同的东西，不回放。</residual:Milestone>

<residual:Milestone id="failure/collapse">在「崩塌」页不保留驱动器的文字，第五代要比第一代差至少 0.3。</residual:Milestone>

## 比较模型

<residual:Milestone id="evaluation/seeds">在核心的「测试」页点一项基准，从新种子训练：[比较模型](evaluation.md)。</residual:Milestone>

<residual:Milestone id="evaluation/contaminated">在故障试验架的「污染」页，让一半的练习是测试的原题。</residual:Milestone>

<residual:Milestone id="evaluation/first">保存一个在某项基准的榜单上胜过所有人的模型。榜上要有别人。</residual:Milestone>

## 打捞

<residual:Milestone id="wreck/found">每一个都以自己的方式失败了：[打捞模型](salvage.md)。</residual:Milestone>

<residual:Milestone id="wreck/shape"></residual:Milestone>

<residual:Milestone id="wreck/leak"></residual:Milestone>

<residual:Milestone id="wreck/order"></residual:Milestone>

<residual:Milestone id="wreck/diverge"></residual:Milestone>

<residual:Milestone id="wreck/depth"></residual:Milestone>
