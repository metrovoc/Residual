---
navigation:
  title: Model Core
  icon: model_core
  parent: screens/index.md
  position: 0
---

# The Model Core's screen

Use a Model Core to open it. Its tabs take a model from its data to its tests; [The Model Core](../core.md) tells how they go together.

<residual:Clip name="core" alt="Filling a Core's slots and pressing Train" />

<residual:Steps>
  <residual:Step>
    **Data**: what the model practises on, and where it is kept.

    <residual:Shot name="core_data" alt="The Data tab: a dataset in the first slot, the tokenizer and checkpoint slots beside it">
      <residual:Part name="tab:data">
        **The tabs**: The screen's views. It opens on the one you left it on.
      </residual:Part>
      <residual:Part name="data:0">
        **Practice**: Datasets or an Archive Drive for the model to practise on.
      </residual:Part>
      <residual:Part name="tokenizer">
        **Tokenizer**: A Tokenizer here sets the tokens the model reads and writes. Without one, it reads one character at a time. Changing it reshapes the first and last layers, so they start over.
      </residual:Part>
      <residual:Part name="checkpoint">
        **Checkpoint**: A checkpoint here takes the trained model when you save. A saved one shows what it can do.
      </residual:Part>
      <residual:Part name="core.save">
        **Save to checkpoint**: Tests the model on every benchmark and keeps it, scores and all, in the checkpoint.
      </residual:Part>
      <residual:Part name="practising">
        **What it practises**: Each benchmark what is loaded lets it practise, and the job that benchmark is used for.
      </residual:Part>
      <residual:Part name="status">
        **Status**: What the Core is doing, and how many steps the model has trained.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **Train**: the model learning, step by step.

    <residual:Shot name="core_train" alt="The Train tab while a model trains: the loss on practice and on the test falling">
      <residual:Part name="chart">
        **The curve**: Train loss in teal, test loss in amber, accuracy in green, against the steps trained. Point at it to read any step.
      </residual:Part>
      <residual:Part name="legend">
        **Where it stands**: The numbers the curves end at. Point at them for its parameters, gradient norm and threads.
      </residual:Part>
      <residual:Part name="train">
        **Train**: Starts training, and stops it.
      </residual:Part>
      <residual:Part name="core.reset">
        **Reset**: Starts the model over from fresh weights.
      </residual:Part>
      <residual:Part name="core.check">
        **Check**: Reads the model's machines again and lists anything wrong with them.
      </residual:Part>
      <residual:Part name="core.speed">
        **Speed**: Watch slows each step down so you can follow it; Max trains as fast as the Core can.
      </residual:Part>
      <residual:Part name="core.learning_rate">
        **Learning rate**: How far each step moves the weights. Too high and the loss jumps about; too low and it barely moves.
      </residual:Part>
      <residual:Part name="core.batch">
        **Batch**: How many examples each step learns from at once.
      </residual:Part>
      <residual:Part name="core.clip">
        **Clip**: The longest a step's gradient may be, so one bad batch can't throw the model off course.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **Tests**: how the model does on what it has not practised.

    <residual:Shot name="core_tests" alt="The Tests tab: a line for each benchmark, with its score">
      <residual:Part name="row:forecasting">
        **A benchmark**: One line for each: its name, a bar toward what passes, and its score, green once it passes. Point at a line for its job, when it was tested and how its test goes.
      </residual:Part>
      <residual:Part name="core.test_all">
        **Test all**: Runs every test now.
      </residual:Part>
      <residual:Part name="core.save">
        **Save to checkpoint**: Tests the model on every benchmark and keeps it, scores and all, in the checkpoint.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **Try**: the model answering.

    <residual:Shot name="core_try" alt="The Try tab: an example drawn from the test and the model's answer">
      <residual:Part name="try.benchmark">
        **Benchmark**: Which benchmark to try it on.
      </residual:Part>
      <residual:Part name="try.example">
        **Example**: Draws a fresh example from the test and has the model answer it. Where you can type, this asks or writes instead.
      </residual:Part>
      <residual:Part name="output">
        **Its answer**: Grey is what the model was shown, white is what it wrote. The bars show its odds for the first token it chose; ⏎ is a line break.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **Inspect**: the model's machines, a line each.

    <residual:Shot name="core_inspect" alt="The Inspect tab: every machine of the model with its shape, parameters and gradient">
      <residual:Part name="table">
        **Its machines**: Each machine with its name, the shape of what it puts out, its parameters and how much gradient reaches it. Anything wrong comes first, in red.
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
