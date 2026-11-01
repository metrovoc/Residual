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

<residual:Milestone id="forecast/deep">The Fabricator makes Forecaster II from a transformer photomask; a model
within 5% of its blocks runs them eight times as fast: [The Forecaster](forecaster.md).</residual:Milestone>

<residual:Milestone id="forecast/burst">The Fabricator makes Forecaster III from a transformer photomask. Give it 32 cooled
Accelerators, power, and a model within 2.5% of its blocks: [The Forecaster](forecaster.md).</residual:Milestone>

<residual:Milestone id="forecast/hourglass">The Fabricator makes it from an attention photomask, and it keeps time
while carried. Sneak-use a Forecaster that runs blocks ahead to bind it, then use it on one of those blocks:
[The Forecaster](forecaster.md).</residual:Milestone>

<residual:Milestone id="agent/made">Craft one and place it where it should live: [Agents](agents.md).</residual:Milestone>

<residual:Milestone id="agent/program">Type one in its screen, or click it together from the palette.</residual:Milestone>

## Storage

<residual:Milestone id="storage/drive">It keeps any number of kinds: [Storage](storage.md).</residual:Milestone>

<residual:Milestone id="storage/terminal">Link it to a Drive Rack with Storage Links.</residual:Milestone>

<residual:Milestone id="storage/port">Hold its front against a chest or a machine, and link its back to the
network.</residual:Milestone>

<residual:Milestone id="storage/assembler">Draw a recipe in an Assembler's grid and tell it how many to
keep.</residual:Milestone>

<residual:Milestone id="storage/processor">Stand a Processor against a furnace and link it to the network, say what goes in and what comes out, and ask a Storage Terminal for some of it or tell the Processor how many to keep: <ItemLink id="processor" />.</residual:Milestone>

<residual:Milestone id="storage/fluid">Open a Storage Terminal with a filled bucket on the cursor and click the grid:
[Storage](storage.md).</residual:Milestone>

<residual:Milestone id="storage/dense">The Fabricator makes it from a linear layer photomask: <ItemLink id="dense_drive" />.</residual:Milestone>

<residual:Milestone id="storage/deep">Eight Stacked Drives around the relay schematic: <ItemLink id="deep_drive" />.</residual:Milestone>

<residual:Milestone id="storage/dock">The Fabricator makes it from a matrix multiply photomask: <ItemLink id="drive_dock" />.</residual:Milestone>

<residual:Milestone id="storage/driver">Craft one and join it to a storage network and a power network: <ItemLink id="link_driver" />.</residual:Milestone>

<residual:Milestone id="storage/wireless">Tune a Wireless Terminal on an Access Point, then use it: <ItemLink id="wireless_terminal" />.</residual:Milestone>

<residual:Milestone id="storage/relay">Put a Relay Card in an Access Point, and open the terminal beyond its range but under
its relays: <ItemLink id="access_point" />.</residual:Milestone>

<residual:Milestone id="storage/portable">The Fabricator makes it from a linear layer photomask: <ItemLink id="portable_drive" />.</residual:Milestone>

<residual:Milestone id="storage/request">Click something the network can make in a Storage Terminal, ask for an amount
and start it: [Storage](storage.md).</residual:Milestone>

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

<residual:Milestone id="benchmark/control">Practise on the <ItemLink id="process_dataset" />: the Core plays melts with the
model's own choices, and it takes a model that attends to the whole melt.</residual:Milestone>

<residual:Milestone id="benchmark/survey">Practise on the <ItemLink id="survey_dataset" />; it takes attention that knows
where each block stands in a row.</residual:Milestone>

<residual:Milestone id="benchmark/demand">Practise on the <ItemLink id="demand_dataset" />; it takes a model that looks back
along the line to find its rhythm.</residual:Milestone>

<residual:Milestone id="benchmark/watch">Practise on the <ItemLink id="watch_dataset" />, which only ever shows things going
well, until what goes wrong surprises the model.</residual:Milestone>

<residual:Milestone id="watch/noticed">Keep a <ItemLink id="watch_post" /> near your machines, and be close when one of them
stops.</residual:Milestone>

<residual:Milestone id="benchmark/pilot">Practise on the <ItemLink id="flight_dataset" />: the Core flies the model's own
flights, and it takes a model that reads the flight back to its start.</residual:Milestone>

<residual:Milestone id="benchmark/orders">Practise on the <ItemLink id="orders_dataset" />; it takes a model too big to
carry.</residual:Milestone>

<residual:Milestone id="model/complete">A Model Core practises up to four kinds at once.</residual:Milestone>

## Putting models to work

<residual:Milestone id="deploy/server">A bigger model stays in a server and answers over relays:
[Serving Models](deployment.md).</residual:Milestone>

<residual:Milestone id="deploy/relay">The plans turn up on relay masts and in vaults.</residual:Milestone>

<residual:Milestone id="agent/order">Start a chat message with its name, such as *Pip, bring me some bread*. Its model
has to have practised orders.</residual:Milestone>

<residual:Milestone id="agent/copy">Mark a build's corners and the block its copy stands on, and hand the marks to an
agent whose model passes Induction: [The Architect](architect.md).</residual:Milestone>

<residual:Milestone id="agent/move">The same marks, with `move` instead of `copy`.</residual:Milestone>

<residual:Milestone id="survey/found">The Fabricator makes a Surveyor from an attention photomask; give it a model that
passes Survey: [The Surveyor](surveyor.md).</residual:Milestone>

## Flight

<residual:Milestone id="flight/frame">Put a pilot that passes Pilot in a charged flight frame, wear it, and jump twice:
[Flight](flight.md).</residual:Milestone>

## Power

<residual:Milestone id="power/generator">It burns what a furnace burns: [Power](power.md).</residual:Milestone>

<residual:Milestone id="power/smelter">The Fabricator makes a Smelter from a transformer photomask; give it power and
raw ore.</residual:Milestone>

## Salvage

<residual:Milestone id="wreck/found">Each one failed in its own way: [Salvaged Models](salvage.md).</residual:Milestone>

<residual:Milestone id="wreck/shape"></residual:Milestone>

<residual:Milestone id="wreck/leak"></residual:Milestone>

<residual:Milestone id="wreck/order"></residual:Milestone>

<residual:Milestone id="wreck/diverge"></residual:Milestone>

<residual:Milestone id="wreck/depth"></residual:Milestone>
