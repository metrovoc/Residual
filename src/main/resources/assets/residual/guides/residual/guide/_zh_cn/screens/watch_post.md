---
navigation:
  title: 哨所
  icon: watch_post
  parent: screens/index.md
  position: 9
---

# 哨所的界面

对哨所使用即可打开。它在看什么，见[哨所](../watch.md)。

它观察的每样东西一行。

<residual:Shot name="watch_post_watching" alt="正在观察熔炉的哨所">
  <residual:Part name="rows">
    **它观察的东西**：每样东西的查看记录画成柱状，最新的在右边，出问题时变红。指向一行可看到它的判断。
  </residual:Part>
  <residual:Part name="model">
    **模型**：一个检查点，或一张调谐到某台模型服务器的中继卡，它的模型要通过看守测试。它读每样东西的那一行，让它意外的就是出了问题的。没有模型，或它完全读不懂这一行时，哨所只能发现已经停了两分钟的东西。
  </residual:Part>
</residual:Shot>
