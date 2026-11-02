---
navigation:
  title: 哨所
  icon: watch_post
  position: 87
item_ids:
  - watch_post
---

# 哨所

基地总是悄悄地坏掉。上游的箱子空了，熔炉就停了；水没了，农田就长得慢了；智能体在原地兜圈子。<ItemLink id="watch_post" />会发现这些。

它每五秒记下 16 格内每样东西做了多少：每台预报机能运转的机器、每个智能体，以及周围的作物（合起来算一样），每次查看一个数字，和端口记录它送出的东西一样。界面把每一行画成一条条柱子，最新的在右边，那样东西触发警报时变成红色。

没有模型时，它只知道一条规则：原本在工作、已经两分钟什么也没做的东西是停了。这条规则分不清一台机器是在等着轮到它，还是已经坏了，也看不出失控或变慢。模型能看出来，因为东西都按节奏工作，只有这一行之前的样子才能说明最后这一段是什么意思。

在它界面旁的槽位里放一个<ItemLink id="checkpoint" />或调谐好的<ItemLink id="relay_card" />，它的模型要通过看守测试（[模型与测试](models.md)）。这样的模型从没见过出问题的情况：它只用一切正常的情况练习，直到知道节奏里接下来是什么。每次查看，哨所让它读一行，并计算最后十六次查看中每一次让它意外了多少，以奈特计，超过半奈特的部分才算。加起来达到六，就会触发警报。模型没有学过的正常节奏也可能让它意外。依赖这个信号之前，先检查这条生产线。如果模型连这几次之前的部分都读不懂，这一行就说明不了什么，交给规则。

警报触发时，哨所就会亮起来，每一面都发出红石信号，被标记的东西上方冒出火花，64 格内的人会听到一次。智能体的 `wait until powered 1`，或接在这个信号上的任何机器，就能接着做点什么。

<residual:Steps>
  <residual:Step>
    把它放在你想看着的东西中间。

    <GameScene zoom="4" interactive={true}>
      <Block id="watch_post" p:facing="south" />
      <Block id="minecraft:furnace" x="2" p:facing="south" p:lit="true" />
      <Block id="minecraft:smoker" x="3" p:facing="south" p:lit="true" />
      <Block id="minecraft:redstone_lamp" x="-1" />
      <IsometricCamera yaw="200" pitch="30" />
      <BoxAnnotation min="0 0 0" max="1 1 1" color="#3CD6C6">
        它记下 16 格内每样东西做了什么。
      </BoxAnnotation>
    </GameScene>
  </residual:Step>

  <residual:Step>
    有东西停下时，它会亮起来，每一面都发出信号。

    <GameScene zoom="4" interactive={true}>
      <Block id="watch_post" p:facing="south" p:working="true" />
      <Block id="minecraft:furnace" x="2" p:facing="south" />
      <Block id="minecraft:smoker" x="3" p:facing="south" p:lit="true" />
      <Block id="minecraft:redstone_lamp" x="-1" p:lit="true" />
      <IsometricCamera yaw="200" pitch="30" />
      <BoxAnnotation min="2 0 0" max="3 1 1" color="#FF6B6B">
        停下的那台熔炉。
      </BoxAnnotation>
      <BoxAnnotation min="-1 0 0" max="0 1 1" color="#FFB547">
        接在信号上的灯。
      </BoxAnnotation>
    </GameScene>
  </residual:Step>
</residual:Steps>

<RecipeFor id="watch_post" />

[哨所的界面，逐项说明](screens/watch_post.md)。
