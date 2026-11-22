---
navigation:
  title: Model Drives
  icon: model_drive
  position: 11
item_ids:
  - model_drive
---

# Model Drives

A <ItemLink id="model_drive" /> is what a model is kept on. It does nothing in the hand: a
[Model Core](core.md) holds it.

<RecipeFor id="model_drive" />

A new drive is blank, and its cells are dark. The first Core it is put in starts a model on it, and from then on the
drive is that model: whichever Core holds it leads into the same hall, however far it was carried. Drives do not stack.

Taken out of a Core, a drive says what it holds:

- the model's task;
- how many parameters the model has;
- how it did on its last test, on examples it has not practised: the share it got right, or its loss when the task has
  no right and wrong answers.

A drive whose model has not been tested yet says only that.

Blank drives still turn up in some of the [ruins](ruins.md).
