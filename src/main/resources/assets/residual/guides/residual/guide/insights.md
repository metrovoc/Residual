---
navigation:
  title: Insights
  icon: insight_attention
  position: 90
item_ids:
  - insight_counting
  - insight_context
  - insight_attention
  - insight_position
  - insight_circuits
  - insight_language
---

# Insights

Passing a lesson for the first time gives you its Insight. Insights unlock the recipes of new machines, and crafting
never uses them up: the Insight stays in the crafting grid. If you lose one, pass its lesson again for a new one.

<Row>
  <ItemImage id="insight_counting" />
  <ItemImage id="insight_context" />
  <ItemImage id="insight_attention" />
  <ItemImage id="insight_position" />
  <ItemImage id="insight_circuits" />
  <ItemImage id="insight_language" />
</Row>

| Insight | From the lesson | Unlocks |
| --- | --- | --- |
| Counting | [Counting Pairs](lessons/counting.md) | Activation, Delay Line, Concatenator |
| Context | [More Context](lessons/context.md) | Matrix Multiplier, Scaler, Causal Mask, Softmax |
| Attention | [Looking Things Up](lessons/recall.md) | Position Table, Residual Junction, Normalizer, Attention Head, Feed-Forward Block |
| Position | [Knowing Where](lessons/reverse.md) | Gate, Head Splitter, Head Merger |
| Induction | [Induction Heads](lessons/induction.md) | Transformer Block |
| Language | [Language](lessons/language.md) | Nothing more. It marks the end of the path. |

The <ItemLink id="embedding" /> and the <ItemLink id="linear" /> need no Insight, and neither do the Model Core,
cables, Accelerators and the Tensor Probe.
