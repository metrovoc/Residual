---
navigation:
  title: 竞技场
  icon: arena
  parent: screens/index.md
  position: 10
---

# 竞技场的界面

对竞技场使用即可打开。怎样记下一种生物，模型要做到什么才能扮演它，见[竞技场](../arena.md)。

还没有日志和模型时，它会说明缺什么。

<residual:Shot name="arena_waiting" alt="还什么都没放的竞技场">
  <residual:Part name="log">
    **日志**：一本记录过同类生物的生物日志。处于前沿层级的基地也可以把勘测仪的田间日志放在这里，用来书写地层。
  </residual:Part>
  <residual:Part name="model">
    **模型**：一个检查点，或一张调谐到某台模型服务器的中继卡。它的模型在每场战斗中扮演这种生物；在日志中不用于练习的十分之一上，它有多常选中生物真实的动作，决定了这些战斗值多少。
  </residual:Part>
  <residual:Part name="fights">
    **生物**：日志里的生物。底部每达到一档相似度亮一格；指向它可以看到模型做得如何、能得到什么，以及缺什么。
  </residual:Part>
  <residual:Part name="drops">
    **战斗所得**：算数的战斗掉落的东西。漏斗可以从这里取走。
  </residual:Part>
  <residual:Part name="power">
    **电力**：储存的电力，从底部涨起。指向它可以看到一场战斗花费多少。
  </residual:Part>
</residual:Shot>
