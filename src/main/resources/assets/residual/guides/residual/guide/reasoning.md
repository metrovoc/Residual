---
navigation:
  title: Thinking Longer
  icon: reckoning_dataset
  position: 92
item_ids:
  - reckoning_dataset
---

# Thinking Longer

A model can be asked once or many times. Each answer costs what it costs, and if a good answer can be told from a bad one, a second try
can be worth paying for.

## Reckoning

<RecipeFor id="reckoning_dataset" />

The <ItemLink id="reckoning_dataset" /> holds lines of numbers added up, with each running sum written out:

```
> 12,7,30,5
= 19 49 54
```

The last number is the answer. Each step can be checked by itself, by anyone, with nothing to go by but arithmetic, and that is what
makes it worth thinking longer about: a wrong answer can be caught, and another tried. The **Reckoning** test scores the share of sums practice
never showed with every step right. The pass mark is 70%; trying more answers may help a model reach it.

## Answers to a question

Put a <ItemLink id="kv_cache" /> in a [Model Server](deployment.md), and its third button sets how many answers it writes to an order or
a sum. It keeps one: for a sum, one whose steps all check; for an order, what most of those that run in a sandbox come to, or with a judge in
the last slot of its panel, the one the judge scores highest. If none passes the check, it keeps the commonest answer. Other questions are asked once.

Every answer is paid for. They read the question once and go through together, but more attempts leave less capacity for other
questions. The server's screen shows the rate and what each test now comes to, and whatever asks goes by those scores. A higher
effort can change which tests it passes.

A model can make the same mistake several times, so the commonest answer need not be right. An arithmetic check can reject a wrong
sum however often it appears.

Orders are another matter: a program that runs without failing may still miss what was meant. Taking the commonest can choose that
program over a better one. A server that thinks longer about orders can lose the Programs test by it, and its screen says so.

## Marking each step

A [Tuner](tuning.md) set to Verified, marking *sums*, marks each try by how many of its steps are right, rather than only whether it comes out
right in the end, so a try that gets three steps of four is worth more than one that gets none. Nothing else tells it what the answer
was. The reward line shows what the checker favours; the saved model's Reckoning score shows how it handles sums practice never gave it.

## The assay

A melt held at its mark gives an assay, a line of numbers to add up. A Smelter whose controller passes [Precision](tuning.md) and Reckoning, as
its server serves it, hands the assay to that controller. If each step is right, the melt makes nine ingots from an ore instead of six. What the
server spends on it shows on its screen under Smelters.
