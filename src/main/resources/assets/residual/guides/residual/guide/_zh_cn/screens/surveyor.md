---
navigation:
  title: 勘测仪
  icon: surveyor
  parent: screens/index.md
  position: 8
---

# 勘测仪的界面

对勘测仪使用即可打开。它的模型读什么，见[勘测仪](../surveyor.md)。

<residual:Clip name="survey_screen" alt="勘测仪清点模型找到的东西" />

没有模型时，它会说明缺什么；有了模型，这里会数着找到的东西。

<residual:Shot name="surveyor_waiting" alt="还没有模型的勘测仪">
  <residual:Part name="model">
    **模型**：一个在勘测数据集上练过的模型。它一行一行地读地层，说出每种矿石和遗藏在哪里；它答对的位置，附近手持勘测杆的人都能看到。
  </residual:Part>
  <residual:Part name="finds">
    **它找到的东西**：每种矿石和遗藏，以及模型这一轮找到了多少。指向一项可看到更多。
  </residual:Part>
  <residual:Part name="progress">
    **进度**：显示屏底部表示这一轮勘测走了多远。指向它可看到它能勘测多远。
  </residual:Part>
</residual:Shot>
