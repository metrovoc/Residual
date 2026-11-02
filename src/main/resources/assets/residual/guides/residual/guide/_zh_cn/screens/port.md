---
navigation:
  title: 端口
  icon: port
  parent: screens/index.md
  position: 13
---

# 端口的界面

对端口使用即可打开。各种模式有什么不同，见[存储](../storage.md)。

<residual:Steps>
  <residual:Step>
    过滤留在窗口里，模式按钮在旁边。指向箭头可查看上一秒搬了多少。

    <residual:Shot name="port_import" alt="正在导入的端口">
      <residual:Part name="port.mode.import">
        **导入**：把面前容器里的一切拿进网络，每刻 64 件；有过滤时，只拿那几种。
      </residual:Part>
      <residual:Part name="port.mode.export">
        **导出**：把过滤里列出的几种东西从网络放进面前的容器，每刻 64 件。
      </residual:Part>
      <residual:Part name="filter">
        **过滤**：拿着一组物品点击，就把它这一种列在这里；也可以在物品栏里按住 Shift 点击它；空手点击则清除。装满的桶指的是其中的流体。过滤为空时，什么都能通过。有了过滤模型，改名为 Food、Plants、Wood、Stone、Ores 或 Tools 的东西就代表那一整类。
      </residual:Part>
      <residual:Part name="status">
        **搬动的东西**：上一秒搬了多少，或者链路满了挡住了它。
      </residual:Part>
      <residual:Part name="model">
        **过滤模型**：一个检查点，或一张调谐到某台模型服务器的中继卡，其模型通过分拣测试。它判断每样东西属于哪一类，于是放着按某一类改名的东西的过滤槽，会输入或输出这一类的所有东西。它拿不准的东西算作其他。
      </residual:Part>
      <residual:Part name="target">
        **目标模型**：预测这个端口将要需要什么的模型。指向它，可在检查点详情上方查看预计用量。
      </residual:Part>
      <residual:Part name="log">
        **田间日志**：放在这里的田间日志会抄下这个端口送出的东西，每样一行。放进模型核心的数据槽，就是用你自己的基地练习需求。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    存量：指向数量按钮查看要保持的数量，并开启缺货请求。

    <residual:Shot name="port_stock" alt="设好过滤和数量、正在维持存量的端口">
      <residual:Part name="port.mode.stock">
        **存量**：让面前的容器里，过滤列出的每种东西都保持设定的数量：少了从网络补上，多了收回网络。
      </residual:Part>
      <residual:Part name="port.mode.window">
        **窗口**：把面前的容器向网络敞开，网络像用驱动器一样往里存、从里取。优先级高的先存满；过滤限制能放进去的东西。
      </residual:Part>
      <residual:Part name="keep">
        **数量**：让面前容器里每种过滤物品保持这个数量。按住 Shift 可调整六十四倍的数量。
      </residual:Part>
      <residual:Part name="port.craft">
        **请求缺的东西**：不请求。点击后，过滤器列出的东西不够时，网络里的装配机和处理器会把它做出来。
      </residual:Part>
      <residual:Part name="port.programs">
        **程序**：程序卡、升级和程序手里拿的东西。放入写好的程序卡后，它照卡上的程序做事，不再按模式工作。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    窗口把容器向网络敞开。指向数量按钮可查看优先级。

    <residual:Shot name="port_window" alt="端口的窗口模式和优先级按钮">
      <residual:Part name="port.mode.window">
        **窗口**：把面前的容器向网络敞开，网络像用驱动器一样往里存、从里取。优先级高的先存满；过滤限制能放进去的东西。
      </residual:Part>
      <residual:Part name="priority">
        **优先级**：网络先存满优先级较高的窗口。每次调整一或四。
      </residual:Part>
      <residual:Part name="filter">
        **过滤**：拿着一组物品点击，就把它这一种列在这里；也可以在物品栏里按住 Shift 点击它；空手点击则清除。装满的桶指的是其中的流体。过滤为空时，什么都能通过。有了过滤模型，改名为 Food、Plants、Wood、Stone、Ores 或 Tools 的东西就代表那一整类。
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
