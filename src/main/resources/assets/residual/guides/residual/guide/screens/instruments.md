---
navigation:
  title: Stream Scope and Trial Rig
  icon: stream_scope
  parent: screens/index.md
  position: 30
---

# The screens of the Scope and the Rig

Use a Stream Scope or a Trial Rig to open them. [The Stream Scope](../scope.md) and [The Trial Rig](../trials.md) tell what each tab does.

<residual:Steps>
  <residual:Step>
    **Model**: the model it works on, a copy of it each time.

    <residual:Shot name="instruments_model" alt="A Stream Scope with a model in it">
      <residual:Part name="instrument.model">
        **The model**: A checkpoint. With none, the model standing on a Model Core it touches.
      </residual:Part>
      <residual:Part name="status">
        **Status**: How big the model is and how many points its stream has, or how the last work came out.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    Every other tab is one instrument: its choices in a row, then one display.

    <residual:Shot name="instruments_instrument" alt="A Stream Scope's logit lens">
      <residual:Part name="tab:lens">
        **The instruments**: Each reads the model its own way.
      </residual:Part>
      <residual:Part name="instrument.run">
        **Start or stop**: Starts the work, and while it runs, stops it.
      </residual:Part>
      <residual:Part name="display">
        **The display**: What the work comes to, drawn as it goes. Point at anything for its numbers.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    A Trial Rig's **Model** tab also holds what the trial works on and a checkpoint for the model it leaves behind.

    <residual:Shot name="instruments_rig_model" alt="A Trial Rig's model, data and output slots">
      <residual:Part name="instrument.model">
        **The model**: A checkpoint. With none, the model standing on a Model Core it touches.
      </residual:Part>
      <residual:Part name="instrument.data">
        **What it works on**: A dataset, a drive or a Field Log, for the trials that need one.
      </residual:Part>
      <residual:Part name="instrument.output">
        **What it keeps**: A checkpoint that takes the model a trial leaves behind.
      </residual:Part>
      <residual:Part name="status">
        **Status**: How big the model is and how many points its stream has, or how the last work came out.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    A Trial Rig's tabs are trials: each trains a copy of the model and charts what comes of it.

    <residual:Shot name="instruments_trial" alt="A Trial Rig's contamination trial">
      <residual:Part name="tab:contaminate">
        **The trials**: Memory, Backdoor, Forgetting, Collapse and Contamination.
      </residual:Part>
      <residual:Part name="instrument.run">
        **Start or stop**: Starts the trial, and while it runs, stops it.
      </residual:Part>
      <residual:Part name="display">
        **The display**: The trial's curves as it goes.
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
