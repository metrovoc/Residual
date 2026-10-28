---
navigation:
  title: Residual
  icon: model_core
  position: 0
---

# Residual

Residual turns the inside of a language model into machines. Each machine performs one real operation: a table
lookup, a matrix multiplication, a softmax, a whole transformer layer. Cable them together, connect the result to a
<ItemLink id="model_core" />, and train it on text while you watch the loss fall. The arithmetic is what PyTorch does,
down to the optimizer, and every model you build can leave the game as a PyTorch module with its trained weights.

You learn by building. **Lessons** retrace how language models came to be, from counting letter pairs to a small GPT.
**Cases** hand you a model that is broken the way real models break, and leave the diagnosis to you. Every lesson
awards an [Insight](insights.md), which unlocks the recipes of new machines.

## Start here

1. [Getting Started](getting-started.md): craft a Model Core and train your first model.
2. [Machines and Cables](wiring.md): how values travel between blocks.
3. [Shapes](shapes.md): what [B×T×C] means, and how to fix a shape error.
4. [Training](training.md): reading a loss curve, and the settings that shape it.

## The path

1. [Counting Pairs](lessons/counting.md): a table of letter pairs.
2. [The Wrong Shape](cases/case_shape.md): a case.
3. [More Context](lessons/context.md): embeddings and a hidden layer.
4. [Exploding Loss](cases/case_diverge.md): a case.
5. [Looking Things Up](lessons/recall.md): attention, built by hand.
6. [Too Good to Be True](cases/case_leak.md): a case.
7. [Knowing Where](lessons/reverse.md): positions and the transformer layer.
8. [Lost in Order](cases/case_order.md): a case.
9. [Too Deep to Learn](cases/case_depth.md): a case.
10. [Induction Heads](lessons/induction.md): two layers working together.
11. [Language](lessons/language.md): a small GPT, trained on Shakespeare.

## Reference

- [The Model Core](core.md), its tabs and settings
- [Machines](machines/index.md), one page each
- [Papers](papers.md) behind the lessons
- [Beyond Residual](beyond.md): where to go next
