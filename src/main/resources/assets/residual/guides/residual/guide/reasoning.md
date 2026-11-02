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
is worth paying for.

## Reckoning

<RecipeFor id="reckoning_dataset" />

The <ItemLink id="reckoning_dataset" /> holds lines of numbers added up, with each running sum written out:

```
> 12,7,30,5
= 19 49 54
```

The last number is the answer. Each step can be checked by itself, by anyone, with nothing to go by but arithmetic, and that is what
makes it worth thinking longer about: a wrong answer can be caught, and another tried. The **Reckoning** test scores the share of sums practice
never showed with every step right, and a server with enough answers to a question can pass it at 70%.

## Answers to a question

Put a <ItemLink id="kv_cache" /> in a [Model Server](deployment.md), and its third button sets how many answers it writes to an order or
a sum. It keeps one: for a sum, one whose steps all check; for an order, what most of those that run in a sandbox come to, or with a judge in
the last slot of its panel, the one the judge scores highest. Other questions are asked once.

Every answer is paid for. They read the question once and go through together, so a 64-wide server at 64 answers a question costs 23 times
what it does at one, and its 26 answers a second fall to about one. In return, the server's screen shows what each test now comes to, and
whatever asks goes by those scores. A test it fails at one answer a question it may pass at thirty-two.

Being able to check is most of it. A model two blocks 64 wide, after 3,000 steps of Reckoning practice:

| Answers to a question | Taking the commonest | Taking one that checks |
| --------------------- | -------------------- | ---------------------- |
| 1                     | 15%                  | 15%                    |
| 2                     | 13%                  | 24%                    |
| 4                     | 13%                  | 32%                    |
| 8                     | 16%                  | 41%                    |
| 16                    | 18%                  | 50%                    |
| 32                    | 18%                  | 65%                    |
| 64                    | 15%                  | 68%                    |

Votes barely help, because a model tends to go wrong the same way each time. A check doesn't care how often an answer came up.

Orders are another matter, since nothing can check what was meant. Of what a fine-tuned model wrote at its likeliest, 77% came to what was meant; of what most
of 16 drawn answers came to, 68%, though the right one was among those 16 for 73%. A server that thinks longer about orders can lose the Programs test by it, and its screen says so.

## Marking each step

A [Tuner](tuning.md) set to Verified, marking *sums*, marks each try by how many of its steps are right, rather than only whether it comes out
right in the end, so a try that gets three steps of four is worth more than one that gets none. Nothing else tells it what the answer was. 200 steps
at 0.0003 on that same model took the share of its tries' steps that were right from 49% to 62%, and sums right at the first try from 12% to 27%. At 32 answers a question it then comes to 75%, at 64 to 77%, and at one to 19%.

## The assay

A melt held at its mark gives an assay, a line of numbers to add up. A Smelter whose controller passes [Precision](tuning.md) and Reckoning, as
its server serves it, hands the assay to that controller. If each step is right, the melt makes nine ingots from an ore instead of six. What the
server spends on it shows on its screen under Smelters.
