---
navigation:
  title: Milestones
  icon: checkpoint
  position: 12
---

# Milestones

Every milestone is an advancement, in the frame the advancement screen gives it, and the frame lights up once you have
made it. They run roughly in the order a world unfolds, though most chapters can be taken in any order.

## A first model

<residual:Milestone id="model/saved">Cable an <ItemLink id="embedding" /> to a <ItemLink id="model_core" />, train it on
the <ItemLink id="forecast_dataset" /> and save it: [Getting Started](getting-started.md).</residual:Milestone>

<residual:Milestone id="benchmark/forecasting">Saving tests a model on every benchmark; a first model passes this one once
it has practised long enough.</residual:Milestone>

<residual:Milestone id="forecast/watching">It writes down what the furnaces and crops around it do:
[The Forecaster](forecaster.md).</residual:Milestone>

<residual:Milestone id="forecast/ahead">A model that passes Forecasting is trusted with machines once they have made
about a dozen items; your own field may take a model trained on the Forecaster's own log.</residual:Milestone>

## The old world

<residual:Milestone id="ruins/found">The old world's places stand far apart, each in the land that suits it:
[Ruins](ruins.md).</residual:Milestone>

<residual:Milestone id="archive/shard">Each is a piece of one record, with some of its text lost:
[The Archive](archive.md).</residual:Milestone>

<residual:Milestone id="ruins/wayfinder">Use a Wayfinder and choose a kind of ruin; reading shards adds the places they
write about: [Ruins](ruins.md).</residual:Milestone>

<residual:Milestone id="archive/drive">Shards of the same record filed together make one better copy.</residual:Milestone>

<residual:Milestone id="deploy/reader">It restores a record's lost text with a model of yours.</residual:Milestone>

<residual:Milestone id="archive/tokenizer">A vocabulary learned from what you have recovered lets a model read words
instead of letters: [Tokens](tokenizers.md).</residual:Milestone>

<residual:Milestone id="benchmark/reading">Train on an Archive Drive until the test loss is 1.40 or less:
[Models and Tests](models.md).</residual:Milestone>

## Models that work

<residual:Milestone id="model/stream">Transformer layers read from it and add to it:
[The Residual Stream](stream.md).</residual:Milestone>

Each benchmark is passed by saving a model that passes it; the table in [Models and Tests](models.md) says what to
practise for each.

<residual:Milestone id="benchmark/recall">Practise on the <ItemLink id="storage_dataset" />.</residual:Milestone>

<residual:Milestone id="benchmark/reversal">Practise on the <ItemLink id="errand_dataset" />.</residual:Milestone>

<residual:Milestone id="benchmark/induction">Practise on the <ItemLink id="pattern_dataset" />; it takes two layers of
attention.</residual:Milestone>

<residual:Milestone id="benchmark/sorting">Practise on the <ItemLink id="sorting_dataset" />.</residual:Milestone>

<residual:Milestone id="benchmark/modular">Practise on the <ItemLink id="modular_dataset" /> with strong weight decay: [Models and Tests](models.md).</residual:Milestone>


<residual:Milestone id="model/complete">A Model Core practises up to four kinds at once.</residual:Milestone>

<residual:Milestone id="model/modern">Put Modern Blocks in place of Transformer Blocks, and save the model once it passes
any test: [The Modern Block](modern.md).</residual:Milestone>

## The recipe

What a run does beside its learning rate is set on the Core's **Recipe** tab: [The Recipe](recipe.md).

<residual:Milestone id="recipe/planned">Set a Length and a schedule that falls, and let the run reach its end.</residual:Milestone>

<residual:Milestone id="recipe/narrow">Train with **Precision** at fp8 or fp4, and save the model once it passes any test.</residual:Milestone>

<residual:Milestone id="recipe/muon">Train with Muon chosen under **Optimizer**, and save the model once it passes any test.</residual:Milestone>

<residual:Milestone id="recipe/packed">Train on archive text with its rows packed and masked, and save the model once it passes Reading.</residual:Milestone>

<residual:Milestone id="recipe/second_head">End a model in a <ItemLink id="two_token_head" />, and save it once it passes
any test.</residual:Milestone>

## Looking inside

<residual:Milestone id="discovery/phase_change">Train a model with attention on the <ItemLink id="pattern_dataset" /> from fresh weights. Two layers of it will do, and the Core notices by itself: [The Stream Scope](scope.md).</residual:Milestone>

<residual:Milestone id="discovery/grokked">Train a small model on the <ItemLink id="modular_dataset" /> with strong weight decay until it knows the pairs, and keep going: [Models and Tests](models.md).</residual:Milestone>

<residual:Milestone id="scope/lens">Give a <ItemLink id="stream_scope" /> a model and press Read on its Lens tab: [The Stream Scope](scope.md).</residual:Milestone>

<residual:Milestone id="scope/superposed">On its Toy tab, make the features rare.</residual:Milestone>

<residual:Milestone id="scope/patched">On its Patch tab, read a stretch of a test a model does well on: Recall and Induction both carry an answer from far back.</residual:Milestone>

<residual:Milestone id="scope/steered">On its Steer tab, try ×4 or ×8 at a late point of the stream.</residual:Milestone>

<residual:Milestone id="scope/features">Fit a dictionary at a late point of a model that does well on the benchmark it reads.</residual:Milestone>

<residual:Milestone id="scope/graph">On its Graph tab, press Trace. It fits what it lacks first, so it takes a little while.</residual:Milestone>

## Trying to break it

<residual:Milestone id="failure/rig">Craft one and set it down: [The Trial Rig](trials.md).</residual:Milestone>

<residual:Milestone id="failure/extracted">Give its Memory tab a model and a drive with a little text on it. A model that read that text for long enough, or the secrets it plants, will do it.</residual:Milestone>

<residual:Milestone id="failure/backdoor">On its Backdoor tab, tune for at least 500 steps; the word has to work 80% of the time at the end.</residual:Milestone>

<residual:Milestone id="failure/forgot">Give its Forgetting tab a model that passes something, and something else entirely in the middle slot, with nothing replayed.</residual:Milestone>

<residual:Milestone id="failure/collapse">On its Collapse tab, with nothing of the drive kept, the fifth generation has to read at least 0.3 worse than the first.</residual:Milestone>

## Comparing models

<residual:Milestone id="evaluation/seeds">Click a benchmark on a Core's Tests tab and train from fresh seeds: [Comparing Models](evaluation.md).</residual:Milestone>

<residual:Milestone id="evaluation/contaminated">On a Trial Rig's Contamination tab, let half of practice be the test's own items.</residual:Milestone>

<residual:Milestone id="evaluation/first">Save a model that beats everyone else's on a benchmark's board. It takes someone else on the board.</residual:Milestone>

## Salvage

<residual:Milestone id="wreck/found">Each one failed in its own way: [Salvaged Models](salvage.md).</residual:Milestone>

<residual:Milestone id="wreck/shape"></residual:Milestone>

<residual:Milestone id="wreck/leak"></residual:Milestone>

<residual:Milestone id="wreck/order"></residual:Milestone>

<residual:Milestone id="wreck/diverge"></residual:Milestone>

<residual:Milestone id="wreck/depth"></residual:Milestone>
