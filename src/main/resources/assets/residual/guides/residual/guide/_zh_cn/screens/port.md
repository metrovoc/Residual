---
navigation:
  title: 端口
  icon: port
  parent: screens/index.md
  position: 12
---

# 端口的界面

对端口使用即可打开。各种模式有什么不同，见[存储](../storage.md)。

<residual:Steps>
  <residual:Step>
    它的模式、过滤和搬动的东西。

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
        **目标模型**：一个检查点，或一张调谐到某台模型服务器的中继卡，它的模型要通过需求测试。它读这个端口送出过什么，说出接下来要送多少；会请求的端口就先让网络做好。它拿不准的东西，端口只在不够时才请求。
      </residual:Part>
      <residual:Part name="log">
        **田间日志**：放在这里的田间日志会抄下这个端口送出的东西，每样一行。放进模型核心的数据槽，就是用你自己的基地练习需求。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    存量：要保持的数量，以及请求缺的东西。

    <residual:Shot name="port_stock" alt="设好过滤和数量、正在维持存量的端口">
      <residual:Part name="port.mode.stock">
        **存量**：让面前的容器里，过滤列出的每种东西都保持下面这个数：少了从网络补上，多了收回网络。
      </residual:Part>
      <residual:Part name="port.mode.window">
        **窗口**：把面前的容器向网络敞开，网络像用驱动器一样往里存、从里取。优先级高的先存满；过滤限制能放进去的东西。
      </residual:Part>
      <residual:Part name="more:16">
        **数量**：每种东西要在那里保持多少。按住 Shift，乘以六十四。
      </residual:Part>
      <residual:Part name="port.craft">
        **请求缺的东西**：不请求。点击后，过滤器列出的东西不够时，网络里的装配机和处理器会把它做出来。
      </residual:Part>
      <residual:Part name="port.programs">
        **程序**：程序卡、升级和程序手里拿的东西。放入写好的程序卡后，它照卡上的程序做事，不再按模式工作。
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
