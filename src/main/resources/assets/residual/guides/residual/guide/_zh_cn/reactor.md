---
navigation:
  title: 反应堆
  icon: reactor_core
  position: 89
item_ids:
  - reactor_core
  - reactor_casing
  - reactor_glass
  - fuel_rod
  - reactor_fuel
---

# 反应堆

反应堆的电力比你能造的其他一切加起来还多，而它不肯安分。它的功率每时每刻都爬升或下沉得更快一点，控制棒的响应总是慢半拍，燃料还按一个没人告诉你的节奏不断往同一个方向推。没有规则能让它一直运行，所以它不靠规则运行：只有控制器才能启动它，也就是一个学会了稳住它的模型。

<GameScene zoom="3" interactive={true}>
  <Block id="reactor_casing" x="-1" y="-1" z="-1" />
  <Block id="reactor_casing" x="0" y="-1" z="-1" />
  <Block id="reactor_casing" x="1" y="-1" z="-1" />
  <Block id="reactor_casing" x="-1" y="-1" z="0" />
  <Block id="reactor_casing" x="0" y="-1" z="0" />
  <Block id="reactor_casing" x="1" y="-1" z="0" />
  <Block id="reactor_casing" x="-1" y="-1" z="1" />
  <Block id="reactor_casing" x="0" y="-1" z="1" />
  <Block id="reactor_casing" x="1" y="-1" z="1" />
  <Block id="reactor_casing" x="-1" y="0" z="-1" />
  <Block id="reactor_casing" x="0" y="0" z="-1" />
  <Block id="reactor_casing" x="1" y="0" z="-1" />
  <Block id="reactor_glass" x="-1" y="0" z="0" />
  <Block id="fuel_rod" x="0" y="0" z="0" p:burning="true" />
  <Block id="reactor_casing" x="1" y="0" z="0" />
  <Block id="reactor_casing" x="-1" y="0" z="1" />
  <Block id="reactor_core" x="0" y="0" z="1" p:facing="south" p:working="true" />
  <Block id="reactor_casing" x="1" y="0" z="1" />
  <Block id="reactor_casing" x="-1" y="1" z="-1" />
  <Block id="reactor_casing" x="0" y="1" z="-1" />
  <Block id="reactor_casing" x="1" y="1" z="-1" />
  <Block id="reactor_casing" x="-1" y="1" z="0" />
  <Block id="reactor_glass" x="0" y="1" z="0" />
  <Block id="reactor_casing" x="1" y="1" z="0" />
  <Block id="reactor_casing" x="-1" y="1" z="1" />
  <Block id="reactor_casing" x="0" y="1" z="1" />
  <Block id="reactor_casing" x="1" y="1" z="1" />
  <IsometricCamera yaw="200" pitch="30" />
  <BoxAnnotation min="0 0 1" max="1 1 2" color="#FFB547">
    反应堆核心，在一个面的正中，朝外。
  </BoxAnnotation>
  <BoxAnnotation min="-1 1 -1" max="2 2 2" color="#7F8D99">
    由反应堆外壳和反应堆玻璃组成的外壳，这里每边 3 格。
  </BoxAnnotation>
</GameScene>

## 建造

用<ItemLink id="reactor_casing" />和<ItemLink id="reactor_glass" />搭一个每边 3 到 5 格的外壳，里面的空间放<ItemLink id="fuel_rod" />，最多 27 根。<ItemLink id="reactor_core" />占外壳上的一个位置，在一个面的正中，朝外。制造机用 Transformer 光掩模制造它。外壳会把电力传给贴着它的东西，满功率时需要<ItemLink id="bus_bar" />才带得走。

堆芯功率在带内时，每根棒每刻发电 20,000 FE；偏离一格减半，偏离两格只剩八分之一。燃料槽里的一个<ItemLink id="reactor_fuel" />在一根棒时烧 4,800 刻，棒越多烧得越快，所以 27 根棒每刻发电 540,000 FE，每 178 刻烧掉一个燃料。

<Row>
  <RecipeFor id="reactor_casing" />
  <RecipeFor id="reactor_glass" />
  <RecipeFor id="fuel_rod" />
  <RecipeFor id="reactor_fuel" />
</Row>

## 控制器

把模型放进屏幕旁的面板：用检查点，或者模型大到带不动时，用中继卡连到模型服务器。它必须通过**安全壳**测试。反应堆一个周期接一个周期地运行，每个周期 24 次查看，每半秒一次。每次查看时，它把这个周期到目前为止的情况和当前功率给控制器看，并等它说该怎么动控制棒。

- 动控制棒改变功率爬升或下沉的速度，每次查看最多两格，而且要到下下次查看才生效。
- 燃料每两三次查看就朝同一个方向推一下，年轻时往上，老了往下，每个周期从它自己的某一刻开始。每个周期的记录以燃料的年龄开头。控制器得从这个周期的走势里推算出那一刻，赶在推动之前应对。
- 在带的范围之外，堆芯会自我加剧：到量表顶部时每次爬得更快，到底部时沉得更快。

屏幕逐次画出这个周期，带用阴影标出，指向它可以看到上个周期保持了多少，以及控制器上次有多大把握。

## 停堆

它会自己停下，这是它唯一的规则：控制棒全部插入，等半分钟让堆芯里的毒物衰减。这发生在量表读到任一端时、控制器被拿走或问不到时，以及控制器连续三次查看对自己的选择都不到 40% 把握时。什么也不会烧毁。

## 学会稳住它

模型在<ItemLink id="reactor_dataset" />上学习，方法是运行周期：核心运行模型自己的周期，让它从每个选择的结果中学习，和控制、驾驶一样。规则稳不住它最差那种燃料的任何一个周期：回到带内不行，算上功率变化的快慢和控制棒正要产生的作用不行，再加上知道燃料的年龄也不行。只看最后几个字符的模型同样一个也稳不住。一个宽度为 32、两层的 Transformer 在练习 4,500 步后，安全壳得分达到 90.625%，超过了 90% 的门槛。这个控制器在实际反应堆中完成了覆盖四种燃料年龄的 16 个周期，没有触发停堆，其中 12 个达到了测试的保持目标：至少 90% 的计分查看处在带内。模型练习时成绩会起伏，所以要经常测试。见[模型与测试](models.md)。
