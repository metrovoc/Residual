---
navigation:
  title: The Watch Post
  icon: watch_post
  position: 87
item_ids:
  - watch_post
---

# The Watch Post

A base breaks quietly. A furnace line stops when a chest upstream runs dry, a farm grows slower when its water goes, an agent
walks in circles. The <ItemLink id="watch_post" /> notices.

Every five seconds it writes down how much each thing within 16 blocks did: every machine a Forecaster could run, every agent,
and the crops around it together, one digit a look, as a Port writes what it moves. Its screen draws each line as bars, the
newest at the right, red while something is wrong with that thing.

With no model it knows one rule: a thing that was at work and has done nothing for two minutes has stalled. That rule cannot
tell a machine waiting its turn from one that has broken, and it never sees a thing that runs away or slows down. A model can,
because things work in rhythms, and only how a line went before shows what its last stretch means.

Put a <ItemLink id="checkpoint" /> or a tuned <ItemLink id="relay_card" /> whose model passes Watch ([Models and
Tests](models.md)) in the slot beside its screen. Such a model has never seen anything go wrong: it has only practised on things
going well, until it knows what comes next in a rhythm. Each look the Watch Post has it read one line, and counts how much each
of the last sixteen looks surprised it, in nats, over half a nat. Six in all and something has gone wrong. A line the model could
not follow even before those looks tells it nothing, and that one is left to the rule.

While anything is wrong, the Watch Post lights up and gives a redstone signal on every side, sparks fly over the thing that is
wrong, and anyone within 64 blocks hears of it once. An agent's `wait until powered 1`, or any machine on that signal, can then
do something about it.

<residual:Steps>
  <residual:Step>
    Set it down among what you want watched.

    <GameScene zoom="4" interactive={true}>
      <Block id="watch_post" p:facing="south" />
      <Block id="minecraft:furnace" x="2" p:facing="south" p:lit="true" />
      <Block id="minecraft:smoker" x="3" p:facing="south" p:lit="true" />
      <Block id="minecraft:redstone_lamp" x="-1" />
      <IsometricCamera yaw="200" pitch="30" />
      <BoxAnnotation min="0 0 0" max="1 1 1" color="#3CD6C6">
        It writes down what each thing within 16 blocks does.
      </BoxAnnotation>
    </GameScene>
  </residual:Step>

  <residual:Step>
    When something stops, it lights up and gives a signal on every side.

    <GameScene zoom="4" interactive={true}>
      <Block id="watch_post" p:facing="south" p:working="true" />
      <Block id="minecraft:furnace" x="2" p:facing="south" />
      <Block id="minecraft:smoker" x="3" p:facing="south" p:lit="true" />
      <Block id="minecraft:redstone_lamp" x="-1" p:lit="true" />
      <IsometricCamera yaw="200" pitch="30" />
      <BoxAnnotation min="2 0 0" max="3 1 1" color="#FF6B6B">
        The furnace that stopped.
      </BoxAnnotation>
      <BoxAnnotation min="-1 0 0" max="0 1 1" color="#FFB547">
        A lamp on its signal.
      </BoxAnnotation>
    </GameScene>
  </residual:Step>
</residual:Steps>

<RecipeFor id="watch_post" />

[The Watch Post's screen, part by part](screens/watch_post.md).
