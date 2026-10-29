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
the <ItemLink id="field_dataset" /> and save it: [Getting Started](getting-started.md).</residual:Milestone>

<residual:Milestone id="benchmark/reacting">Saving tests a model on every benchmark; a first model passes this one once
it has practised long enough.</residual:Milestone>

<residual:Milestone id="agent/made">Craft one, place it beside some farmland and give it your checkpoint:
[Agents](agents.md).</residual:Milestone>

<residual:Milestone id="agent/program">Type one in its screen, or click it together from the palette.</residual:Milestone>

## The old world

<residual:Milestone id="ruins/found">Archive houses, relay masts and buried vaults stand far apart:
[Ruins](ruins.md).</residual:Milestone>

<residual:Milestone id="archive/shard">Each is a piece of one record, with some of its text lost:
[The Archive](archive.md).</residual:Milestone>

<residual:Milestone id="archive/drive">Shards of the same record filed together make one better copy.</residual:Milestone>

<residual:Milestone id="deploy/reader">It restores a record's lost text with a model of yours.</residual:Milestone>

<residual:Milestone id="archive/tokenizer">A vocabulary learned from what you have recovered lets a model read words
instead of letters: [Tokens](tokenizers.md).</residual:Milestone>

<residual:Milestone id="benchmark/reading">Train on an Archive Drive until the test loss is 1.40 or less:
[Models and Tests](models.md).</residual:Milestone>

## Machines from scratch

<residual:Milestone id="bench/made">Each spec comes with cases that break the obvious design:
[The Bench and the Fabricator](bench.md).</residual:Milestone>

<residual:Milestone id="bench/fabricator">Photomasks found in the ruins work in it too.</residual:Milestone>

<residual:Milestone id="bench/matmul"></residual:Milestone>

<residual:Milestone id="bench/linear">It opens once matrix multiply is verified.</residual:Milestone>

<residual:Milestone id="bench/mlp">It opens once the linear layer is verified.</residual:Milestone>

<residual:Milestone id="bench/mean"></residual:Milestone>

<residual:Milestone id="bench/layer_norm">It opens once mean is verified.</residual:Milestone>

<residual:Milestone id="bench/gelu"></residual:Milestone>

<residual:Milestone id="bench/softmax">Scores of a thousand overflow the obvious design.</residual:Milestone>

<residual:Milestone id="bench/attention">It opens once softmax is verified.</residual:Milestone>

<residual:Milestone id="bench/transformer">It opens once attention is verified.</residual:Milestone>

<residual:Milestone id="bench/all"></residual:Milestone>

<residual:Milestone id="model/stream">Transformer layers read from it and add to it:
[The Residual Stream](stream.md).</residual:Milestone>

## Models that work

Each benchmark is passed by saving a model that passes it; the table in [Models and Tests](models.md) says what to
practise for each.

<residual:Milestone id="benchmark/recall">Practise on the <ItemLink id="storage_dataset" />.</residual:Milestone>

<residual:Milestone id="benchmark/reversal">Practise on the <ItemLink id="errand_dataset" />.</residual:Milestone>

<residual:Milestone id="benchmark/induction">Practise on the <ItemLink id="pattern_dataset" />; it takes two layers of
attention.</residual:Milestone>

<residual:Milestone id="benchmark/sorting">Practise on the <ItemLink id="sorting_dataset" />.</residual:Milestone>

<residual:Milestone id="benchmark/forecasting">Practise on the <ItemLink id="growth_dataset" />.</residual:Milestone>

<residual:Milestone id="benchmark/orders">Practise on the <ItemLink id="orders_dataset" />; it takes a model too big to
carry.</residual:Milestone>

<residual:Milestone id="model/complete">A Model Core practises up to four kinds at once.</residual:Milestone>

## Putting models to work

<residual:Milestone id="forecast/watching">It writes down how the crops around it grow:
[The Forecaster](forecaster.md).</residual:Milestone>

<residual:Milestone id="forecast/ahead">Train a model on the Forecaster's own log until it trusts it.</residual:Milestone>

<residual:Milestone id="deploy/server">A bigger model stays in a server and answers over relays:
[Serving Models](deployment.md).</residual:Milestone>

<residual:Milestone id="deploy/relay">The plans turn up on relay masts and in vaults.</residual:Milestone>

<residual:Milestone id="agent/order">Start a chat message with its name, such as *Pip, bring me some bread*. Its model
has to have practised orders.</residual:Milestone>

## Salvage

<residual:Milestone id="wreck/found">Each one failed in its own way: [Salvaged Models](salvage.md).</residual:Milestone>

<residual:Milestone id="wreck/shape"></residual:Milestone>

<residual:Milestone id="wreck/leak"></residual:Milestone>

<residual:Milestone id="wreck/order"></residual:Milestone>

<residual:Milestone id="wreck/diverge"></residual:Milestone>

<residual:Milestone id="wreck/depth"></residual:Milestone>
