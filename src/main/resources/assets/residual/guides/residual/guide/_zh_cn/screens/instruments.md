---
navigation:
  title: 流示波器与故障试验架
  icon: stream_scope
  parent: screens/index.md
  position: 30
---

# 流示波器与故障试验架的界面

对流示波器或故障试验架使用即可打开。每个标签页做什么，见[流示波器](../scope.md)和[故障试验架](../trials.md)。

<residual:Steps>
  <residual:Step>
    **模型**：它处理的模型，每次都用一个副本。

    <residual:Shot name="instruments_model" alt="放着模型的流示波器">
      <residual:Part name="instrument.model">
        **模型**：一个检查点。没有的话，就用它接触的模型核心上的模型。
      </residual:Part>
      <residual:Part name="status">
        **状态**：模型有多大、它的流有几个点，或者上一次工作的结果。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    其他每个标签页都是一件仪器：一排选项，下面一个显示屏。

    <residual:Shot name="instruments_instrument" alt="流示波器的 logit 透镜">
      <residual:Part name="tab:lens">
        **仪器**：每件仪器用自己的方式读模型。
      </residual:Part>
      <residual:Part name="instrument.run">
        **开始或停止**：开始工作；工作进行时，点它就停止。
      </residual:Part>
      <residual:Part name="display">
        **显示屏**：工作的结果，边做边画。指向任何东西可看到它的数字。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    故障试验架的**模型**页还放着试验处理的内容，以及用来存下试验所得模型的检查点。

    <residual:Shot name="instruments_rig_model" alt="故障试验架的模型、数据和输出槽">
      <residual:Part name="instrument.model">
        **模型**：一个检查点。没有的话，就用它接触的模型核心上的模型。
      </residual:Part>
      <residual:Part name="instrument.data">
        **处理的内容**：数据集、驱动器或田间日志，供需要的试验使用。
      </residual:Part>
      <residual:Part name="instrument.output">
        **留下的东西**：一个检查点，存下试验留下的模型。
      </residual:Part>
      <residual:Part name="status">
        **状态**：模型有多大、它的流有几个点，或者上一次工作的结果。
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    故障试验架的标签页都是试验：每个都训练模型的一个副本，并画出结果。

    <residual:Shot name="instruments_trial" alt="故障试验架的污染试验">
      <residual:Part name="tab:contaminate">
        **试验**：记忆、后门、遗忘、崩塌和污染。
      </residual:Part>
      <residual:Part name="instrument.run">
        **开始或停止**：开始试验；试验进行时，点它就停止。
      </residual:Part>
      <residual:Part name="display">
        **显示屏**：试验进行时的曲线。
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
