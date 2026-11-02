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
        **A benchmark**: One line for each: its name, a bar toward what passes, and its score, green once it passes. Scroll for the rest. Point at a line for its job, when it was tested and how its test goes. Click it for its runs from fresh seeds, a look-alike of its test and the server's leaderboard: [Comparing Models](../evaluation.md) tells how they go.
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
    Scroll through the Tests tab to see every benchmark.

    <residual:Shot name="core_tests_lower" alt="The lower rows of the Tests tab, ending with Ground">
      <residual:Part name="row:ground">
        **A benchmark**: Each row opens its comparison when clicked.
      </residual:Part>
      <residual:Part name="scrollbar">
        **The scroll bar**: Shows which rows are in view. Scroll over the list or click the bar to move through it.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    Click a benchmark to compare its test, fresh runs and the server's designs.

    <residual:Shot name="core_evaluation" alt="One benchmark's comparison, with seed runs and the server's leaderboard">
      <residual:Part name="evaluation">
        **The comparison**: The score on its test and on a look-alike with other items. A warning means its test flatters it beyond chance.
      </residual:Part>
      <residual:Part name="seeds">
        **Fresh seeds**: The spread of runs from fresh weights and batches, with the previous design below when there is one.
      </residual:Part>
      <residual:Part name="leaderboard">
        **The server's board**: The leading designs, their makers, scores and sizes. Point at one for its machines.
      </residual:Part>
      <residual:Part name="histograms">
        **The distributions**: How the server's designs spread by score and size, with this model marked in amber.
      </residual:Part>
      <residual:Part name="evaluation.back">
        **Back**: Returns to the benchmark list.
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

  <residual:Step>
    **Recipe**: how a run goes beside its learning rate.

    <residual:Shot name="core_recipe" alt="The Recipe tab: the settings, what the run is doing, the rate over the run and the units at the Core">
      <residual:Part name="settings">
        **The settings**: Each is dim until the unit for it touches the Core. [The Recipe](../recipe.md) says what each does.
      </residual:Part>
      <residual:Part name="readings">
        **What the run is doing**: The rate now, what the weights take, the text read, the spikes, the largest scores and how often a second head is right. Point at one for what it means.
      </residual:Part>
      <residual:Part name="rate">
        **The rate over the run**: What the learning rate is multiplied by from the first step to the last, with a line where the run has reached.
      </residual:Part>
      <residual:Part name="units">
        **The units**: Lit if one touches the Core, directly or through accelerators and other units. Point at one for what it opens.
      </residual:Part>
      <residual:Part name="recipe.schedule">
        **Schedule**: How the learning rate moves over a run: held steady, falling along a cosine from the end of warmup, or held and falling over the last fifth. A falling one needs a Length.
      </residual:Part>
      <residual:Part name="recipe.warmup">
        **Warmup**: How many steps the rate takes to rise to its full size.
      </residual:Part>
      <residual:Part name="recipe.length">
        **Length**: How many steps a run lasts, after which it stops by itself. With no end it runs until it is stopped.
      </residual:Part>
      <residual:Part name="recipe.precision">
        **Precision**: What the model's matrices, and the numbers going into them, are rounded to, in training and whenever it answers afterwards. The narrower, the fewer bytes a weight takes.
      </residual:Part>
      <residual:Part name="recipe.moves">
        **Optimizer**: What moves the weights: AdamW, each number on its own, or Muon, each matrix inside the model as a whole, every direction alike.
      </residual:Part>
      <residual:Part name="recipe.packing">
        **Packing**: How archive text is cut into rows: windows of long stretches only, rows packed from every stretch, or packed with a mask so that nothing looks from one stretch into another.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    **Compute**: what the run really costs, and how it shares out its work.

    <residual:Shot name="core_compute" alt="The Compute tab: the run's devices, memory, flops and power, and what each device did through its last step">
      <residual:Part name="reading">
        **The run**: How it shares out and on how many devices, its memory against what has to hold it, its flops a second, its power and PUE, and what it has spent so far.
      </residual:Part>
      <residual:Part name="timeline">
        **The last step**: What each device did through the last step: teal forward, amber back, violet collectives. Where it is dark it waited: with pipeline parallelism, those are its bubbles.
      </residual:Part>
      <residual:Part name="compute.split">
        **Sharing out the work**: How a run shares its work among its devices. It takes effect when the next run starts.
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
