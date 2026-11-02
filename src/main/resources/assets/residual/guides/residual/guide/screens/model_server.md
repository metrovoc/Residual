---
navigation:
  title: Model Server
  icon: model_server
  parent: screens/index.md
  position: 27
---

# A Model Server's screen

Use a Model Server with an empty hand to open it. [Serving Models](../deployment.md) tells what each thing in its panel earns.

What it serves and what that costs, its panel beside it, and its settings under that.

<residual:Shot name="model_server_empty" alt="A Model Server with nothing in it">
  <residual:Part name="model">
    **The model it serves.**: A saved checkpoint. Sneak-use the server with an empty hand to take it back.
  </residual:Part>
  <residual:Part name="rate">
    **Answers a second**: Answers that read 16 tokens and write 8, as its model and what is in its panel were measured to cost. The bar is how much of its operations is in use.
  </residual:Part>
  <residual:Part name="memory">
    **Memory**: Its weights, its draft and what it keeps, of all it holds.
  </residual:Part>
  <residual:Part name="drawn">
    **Drawn on by**: What it has been spent on lately
  </residual:Part>
  <residual:Part name="cache">
    **KV Cache**: Each one adds 256 KiB, and with any here it keeps what each token worked out for the next.
  </residual:Part>
  <residual:Part name="packer">
    **Weight Packer**: Lets the button below keep its weights in 8 or 4 bits.
  </residual:Part>
  <residual:Part name="draft">
    **Draft**: A small model reading the same tokens proposes what comes next, and the served model checks several at a pass. Takes a KV Cache.
  </residual:Part>
  <residual:Part name="judge">
    **Judge**: A model a Tuner taught to score answers. When it writes several answers to an order, it keeps the one this scores highest.
  </residual:Part>
  <residual:Part name="server.form">
    **Form of its weights**: Takes a Weight Packer. Packed weights take less memory, and are read out of it on every pass. Each test it passed is run again in the new form.
  </residual:Part>
  <residual:Part name="server.batch">
    **Most in a pass**: Takes a KV Cache. Sequences waiting at once go through in one pass, which shares what a pass itself costs.
  </residual:Part>
  <residual:Part name="server.effort">
    **Answers to a question**: Takes a KV Cache. For an order or a sum it writes this many answers and keeps one, by checking them, by a vote or by a judge. Each is paid for; the screen says what that does to each test.
  </residual:Part>
</residual:Shot>
