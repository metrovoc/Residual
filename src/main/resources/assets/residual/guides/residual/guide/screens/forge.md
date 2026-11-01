---
navigation:
  title: BPE Forge
  icon: bpe_forge
  parent: screens/index.md
  position: 4
---

# The BPE Forge's screen

Use a BPE Forge to open it. [Tokens](../tokenizers.md) tells what a vocabulary is for.

<residual:Clip name="forge" alt="The BPE Forge learning a vocabulary from a drive, merge by merge" />

A vocabulary being learned from a drive, merge by merge.

<residual:Shot name="forge_learning" alt="The BPE Forge at work: a passage cut into tokens and its latest merges">
  <residual:Part name="text">
    **Archive Drive**: An Archive Drive: the forge learns from its legible text.
  </residual:Part>
  <residual:Part name="card">
    **Tokenizer**: A Tokenizer to write onto, blank or written. The forge carries on from the merges it already has.
  </residual:Part>
  <residual:Part name="sizes">
    **Vocabulary size**: How many tokens to learn. Bigger tokens carry more text each, but the embedding and the last layer grow with the vocabulary.
  </residual:Part>
  <residual:Part name="progress">
    **How far it has come**: Point at it for what it is doing.
  </residual:Part>
  <residual:Part name="sample">
    **Its text, in tokens**: A passage of the drive, cut as the vocabulary now cuts it. Point at a token for what it is made of.
  </residual:Part>
  <residual:Part name="merges">
    **Latest merges**: The pairs it joined last, newest first.
  </residual:Part>
</residual:Shot>
