---
navigation:
  title: Refinery machines
  icon: refinery_intake
  parent: screens/index.md
  position: 25
---

# The screens of a Refinery

Use any machine of a line to open its screen. [The Refinery](../refinery.md) tells how a line goes together and what each machine is worth.

<residual:Steps>
  <residual:Step>
    A **Refinery Intake**: what goes in, and the two things it can be asked.

    <residual:Shot name="refinery_intake" alt="A Refinery Intake after a pass">
      <residual:Part name="slots">
        **What goes in**: Archive Drives and Relay Dumps, up to nine.
      </residual:Part>
      <residual:Part name="rows">
        **What it read**: What the slots hold and how much text that came to; once measured, the held-out loss of the raw text.
      </residual:Part>
      <residual:Part name="refinery.start">
        **Refine**: Sends all it holds down the line: each machine's front faces the next, up to 12 blocks off with nothing solid between.
      </residual:Part>
      <residual:Part name="refinery.measure">
        **Measure**: Trains small readers on what each machine let through, and shows over each machine what it did to held-out loss.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    A **Duplicate Sieve**: what it took out, and the curve its hashes make.

    <residual:Shot name="refinery_sieve" alt="A Duplicate Sieve after a pass">
      <residual:Part name="rows">
        **What it took out**: Point at a row for what it means.
      </residual:Part>
      <residual:Part name="curve">
        **The curve**: Across, how alike two documents are; up, how likely they are to be compared: 128 hashes in bands. The line is the threshold, the bars the pairs compared.
      </residual:Part>
      <residual:Part name="refinery.setting.near">
        **Near copies at**: How alike two documents must be, by the runs of five words they share, to count as copies.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    A **Filter Bed**: a row and a switch for each rule.

    <residual:Shot name="refinery_filters" alt="A Filter Bed after a pass">
      <residual:Part name="rows">
        **What each rule threw out**: Point at a row for what it means.
      </residual:Part>
      <residual:Part name="refinery.setting.short">
        **Too short**: Throws out what has fewer than 12 words.
      </residual:Part>
      <residual:Part name="refinery.setting.symbols">
        **Too many marks**: Throws out what is over a fifth marks and digits.
      </residual:Part>
      <residual:Part name="refinery.setting.words">
        **Not word-sized**: Throws out what has words under 2.5 letters or over 9 on average.
      </residual:Part>
      <residual:Part name="refinery.setting.repeats">
        **Saying one thing**: Throws out what says one line over half the time.
      </residual:Part>
      <residual:Part name="refinery.setting.endings">
        **Lines that do not end**: Throws out what hardly ever ends a line as a sentence or a verse does.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    A **Quality Classifier**: its model and its rule, and what each decided.

    <residual:Shot name="refinery_classifier" alt="A Quality Classifier that had no model">
      <residual:Part name="slots">
        **Its model**: A checkpoint, or a Relay Card tuned to a Model Server, whose model passes Quality. With none, the rule decides alone.
      </residual:Part>
      <residual:Part name="rows">
        **What it decided**: Point at a row for what it means.
      </residual:Part>
      <residual:Part name="refinery.setting.sure">
        **Sure enough at**: How sure the model must be, one way or the other. What it is less sure of goes to the rule.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    A **Program Press**: what it wrote into the stream.

    <residual:Shot name="refinery_press" alt="A Program Press after a pass">
      <residual:Part name="slots">
        **Its book**: A book of templates, for the Book program: each page a template, with \{a|b} for a choice.
      </residual:Part>
      <residual:Part name="rows">
        **What it wrote**: Point at a row for what it means.
      </residual:Part>
      <residual:Part name="refinery.setting.program">
        **Program**: What it writes: sums with their working, orders and what they mean, things and their kinds, or a book of templates.
      </residual:Part>
      <residual:Part name="refinery.setting.share">
        **Writes**: How much it adds, as a share of the text coming in.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    A **Domain Mixer**: a row for each kind of writing, and what the last of a run reads.

    <residual:Shot name="refinery_mixer" alt="A Domain Mixer after a pass">
      <residual:Part name="rows">
        **The kinds of writing**: Each one's share of what a model reads. Click a row for more or less of it.
      </residual:Part>
      <residual:Part name="refinery.setting.anneal">
        **Anneals on**: The kind of writing the last of a run reads alone, at a fifth of the rate.
      </residual:Part>
      <residual:Part name="refinery.setting.after">
        **Anneals after**: Steps of a run in a Model Core before that. When measured, a reader anneals for its last fifth.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    A **Contamination Check**: what it found of each benchmark's test.

    <residual:Shot name="refinery_check" alt="A Contamination Check after a pass">
      <residual:Part name="rows">
        **What it found**: Lines it found first, then a row for each benchmark. Scroll for the rest.
      </residual:Part>
      <residual:Part name="refinery.setting.remove">
        **What it finds**: Takes out what it finds, or only says so.
      </residual:Part>
    </residual:Shot>
  </residual:Step>

  <residual:Step>
    A **Token Tank**: what the line holds, counted.

    <residual:Shot name="refinery_tank" alt="A Token Tank after a pass">
      <residual:Part name="tokenizer">
        **Tokenizer**: A Tokenizer, to count tokens as it cuts them. Without one every character is a token.
      </residual:Part>
      <residual:Part name="cell">
        **Token Cell**: A Token Cell. Put in, it draws on what the line's tanks hold, for a Model Core's data slots.
      </residual:Part>
      <residual:Part name="rows">
        **What it holds**: Point at a row for what it means.
      </residual:Part>
    </residual:Shot>
  </residual:Step>
</residual:Steps>
