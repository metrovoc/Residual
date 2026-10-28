---
navigation:
  title: Beyond Residual
  icon: insight_language
  position: 110
---

# Beyond Residual

Everything you built in the game is a real model. This page is about taking it, and yourself, further.

## Take your model with you

On the Model Core's Code tab, **Export model + weights** writes two files into a new folder under
`residual/exports` in your game folder, and puts a link to the folder in chat:

- `model.py`: your machines as a PyTorch module, with a function that loads the weights and one that samples text.
- `weights.bin`: the trained weights, every number exactly as the Core had it.

With Python and PyTorch installed, run it from that folder:

> `python model.py "ROMEO:"`

It prints your prompt and 200 more characters in the model's words. Then open `model.py`: each line of its `forward` is one of your
machines, in the order the data flows.

## Things to try in PyTorch

- Write the training loop yourself: batches of text, cross-entropy, `torch.optim.AdamW`, the same settings as the
  Core. Your loss curve should look like the one in the game.
- Change the model in code: more blocks, a wider stream, RoPE instead of the Position Table, a gated feed-forward
  layer. Then train it on text of your own.
- Look inside. Plot the attention patterns of the induction model, or ablate one head and see what breaks.

## What the game simplifies

- **Tokens.** The lessons read characters. Real models read sub-word tokens, tens of thousands of them, learned from
  data by algorithms such as byte-pair encoding.
- **Scale.** The Language lesson's model has about a hundred thousand parameters and trains for minutes on a processor. Large
  models have billions and train for months on thousands of accelerators, with every number spread across many
  machines.
- **Training.** The Core keeps the learning rate constant after a short warmup. Real runs usually decay it, and
  follow pre-training with fine-tuning on instructions and human feedback.

## Where to learn next

- Andrej Karpathy's *Neural Networks: Zero to Hero* builds everything in these lessons in Python, from a bigram model
  to a GPT, one video at a time. His nanoGPT repository trains GPT-2-sized models.
- *The Annotated Transformer*, from Harvard NLP, walks through Vaswani et al.'s paper line by line in code.
- The *Transformer Circuits Thread* continues the work behind the Induction Heads lesson.
- The [Papers](papers.md) page lists every paper behind the lessons and machines.
