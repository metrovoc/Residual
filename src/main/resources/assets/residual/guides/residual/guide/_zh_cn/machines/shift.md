---
navigation:
  title: 延迟线
  icon: shift
  parent: machines/index.md
  position: 4
item_ids:
  - shift
---

# 延迟线

<BlockImage id="shift" scale="3" float="left" />

把序列在时间上往后推：位置 *t* 收到位置 *t* − *k* 原来的值，前 *k* 个位置收到零。它让一个位置能看到固定步数之前的内容。

![延迟](/formulas/shift.png)

## 形状

输入序列 […×T×C]，输出形状相同。

## 设置

- **步数**：1 到 4，默认 1。

## 参数

无。

## 在 PyTorch 中

> `def delay(x, steps):`\
> `    return F.pad(x, (0, 0, steps, 0))[..., : x.shape[-2], :]`\
> ` `\
> `prev = delay(e, 1)`

## 说明

注意力可以回看任意距离，所以 Transformer 很少需要固定的延迟。有些架构仍然保留它：RWKV 把每个词元与前一个词元混合，这种学到的混合叫作词元移位（token shift）。

## 制造

可在<ItemLink id="fabricator" />中直接制造，无需光掩模。见[试验台与制造机](../bench.md)。
