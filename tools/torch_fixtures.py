"""Writes the reference values TorchParityTest checks the engine against, computed with PyTorch, into
src/test/resources/torch, which holds nothing else.

    uv run --with torch python tools/torch_fixtures.py

Each case gives one of the engine's operations fixed float32 inputs and records what the PyTorch function it matches
makes of them: the output, and the gradient of sum(output * seed) for a fixed random seed with respect to every input.
The references are computed in float64 from those float32 inputs and stored as the nearest float32, so the test
compares the engine with the exact answer. The optimizer cases run Trainer's update on given gradients: clipping to a
global norm, a linear warmup, then AdamW with weight decay on matrices only. The fixtures are test resources: nothing
of PyTorch reaches the mod.
"""
import json
import math
import shutil
import struct
import warnings
from pathlib import Path

# torch warns on import when NumPy is missing; nothing here needs it
warnings.filterwarnings("ignore", message="Failed to initialize NumPy")
import torch  # noqa: E402
import torch.nn.functional as F  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/test/resources/torch"
WARMUP = 20  # Trainer.WARMUP
G = torch.Generator().manual_seed(0)


def randn(*shape, scale=1.0, shift=0.0):
    return torch.randn(shape, generator=G) * scale + shift


def short(x):
    """The shortest decimal that reads back as the same float32."""
    if not math.isfinite(x):
        return x
    for digits in range(1, 10):
        s = float(f"{x:.{digits}g}")
        if struct.unpack("f", struct.pack("f", s))[0] == x:
            return s


def values(t):
    return [short(x) for x in t.detach().float().flatten().tolist()]


def tensor(t):
    return {"shape": list(t.shape), "data": values(t)}


def case(group, operation, name, reference, inputs, **args):
    """Records reference(*inputs, **args) and the gradients of sum(output * seed) for every input, both in float64."""
    xs = [x.double().requires_grad_() for x in inputs]
    out = reference(*xs, **args)
    seed = randn(*out.shape)
    grads = torch.autograd.grad(out, xs, seed.double())
    assert not out.isnan().any() and not any(g.isnan().any() for g in grads), (operation, name)
    group.setdefault(operation, []).append({"name": name, **args, "inputs": [tensor(x) for x in inputs],
                                            "seed": values(seed), "output": tensor(out),
                                            "grads": [values(g) for g in grads]})


# ---------------------------------------------------------------- what each operation is in PyTorch

ACTIVATIONS = {"relu": F.relu, "gelu": lambda x: F.gelu(x, approximate="tanh"), "tanh": torch.tanh,
               "sigmoid": torch.sigmoid, "silu": F.silu}
BINARY = {"ADD": torch.add, "SUB": torch.sub, "MUL": torch.mul, "DIV": torch.div, "MAX": torch.maximum}
UNARY = {"NEG": torch.neg, "EXP": torch.exp, "LOG": torch.log, "SQRT": torch.sqrt, "TANH": torch.tanh}


def matmul(a, b, trans_b):
    return a @ (b.mT if trans_b else b)


def causal_mask(x):
    t = x.shape[-1]
    return x.masked_fill(torch.ones(t, t, dtype=torch.bool).triu(1), -math.inf)


def cross_entropy(logits, targets, weights):
    """The mean over every row, or over the rows whose weight is 1: the engine's weights are 0 or 1."""
    targets = torch.tensor(targets)
    if weights is not None:
        targets[torch.tensor(weights) == 0] = -100  # F.cross_entropy's ignore_index
    return F.cross_entropy(logits.reshape(-1, logits.shape[-1]), targets).reshape(1)


def split_heads(x, heads):
    b, t, d = x.shape
    return x.view(b, t, heads, d // heads).transpose(1, 2)


def merge_heads(x):
    b, h, t, dh = x.shape
    return x.transpose(1, 2).reshape(b, t, h * dh)


def shift(x, steps):
    return F.pad(x, (0, 0, steps, 0))[..., :x.shape[-2], :]


def row(x):
    """The engine's prims count a vector as a single row."""
    return x.view(1, -1) if x.dim() == 1 else x


def rotary(x):
    """Each pair of features as a complex number, turned by its row's place."""
    t, d = x.shape[-2:]
    angle = torch.arange(t, dtype=x.dtype)[:, None] * 10000.0 ** (-2.0 * torch.arange(d // 2, dtype=x.dtype) / d)
    z = torch.view_as_complex(x.reshape(*x.shape[:-1], d // 2, 2))
    return torch.view_as_real(z * torch.polar(torch.ones_like(angle), angle)).reshape(x.shape)


def flash(q, k, v, scale):
    """What the online softmax has to equal: the T×T scores, masked, softmaxed and multiplied."""
    return F.softmax(causal_mask(q @ k.mT * scale), dim=-1) @ v


def turn(x):
    return torch.stack((-x[..., 1::2], x[..., 0::2]), dim=-1).flatten(-2)


def half(x):
    return (x.shape[-1] + 1) // 2


# ---------------------------------------------------------------- cases

def ops():
    g = {}
    case(g, "matmul", "shared", matmul, [randn(2, 3, 4), randn(4, 5)], trans_b=False)
    case(g, "matmul", "shared transposed", matmul, [randn(2, 3, 4), randn(5, 4)], trans_b=True)
    case(g, "matmul", "batched", matmul, [randn(2, 2, 3, 4), randn(2, 2, 4, 3)], trans_b=False)
    case(g, "matmul", "batched transposed", matmul, [randn(2, 2, 3, 4), randn(2, 2, 3, 4)], trans_b=True)
    for op, fn in (("add", torch.add), ("mul", torch.mul)):
        case(g, op, "same shape", fn, [randn(2, 3), randn(2, 3)])
        case(g, op, "bias", fn, [randn(2, 3, 4), randn(4)])
        case(g, op, "rows", fn, [randn(2, 3, 4), randn(3, 4)])
        case(g, op, "single batch", fn, [randn(3, 4), randn(1, 3, 4)])
    case(g, "scale", "inverse root", lambda x, factor: x * factor, [randn(2, 3, 4)], factor=1 / math.sqrt(8))
    for fn, f in ACTIVATIONS.items():
        case(g, "activate", fn, lambda x, fn: f(x), [randn(4, 8, scale=3)], fn=fn)
        # relu's kink, where its gradient is 0, and saturation, past where exp(-x) leaves float32's range
        edges = torch.tensor([[-100.0, -40, -20, -10, -1e-3, 0], [1e-3, 1, 10, 20, 40, 100]])
        case(g, "activate", fn + " edges", lambda x, fn: f(x), [edges], fn=fn)
    softmax = lambda x: F.softmax(x, dim=-1)  # noqa: E731
    case(g, "softmax", "ordinary", softmax, [randn(2, 3, 6, scale=2)])
    # exp of these overflows or vanishes unless the row's maximum is subtracted first
    case(g, "softmax", "large", softmax, [randn(2, 5, scale=3, shift=1000)])
    case(g, "softmax", "negative", softmax, [randn(2, 5, scale=3, shift=-1000)])
    case(g, "softmax", "peaked", softmax, [torch.tensor([[0.0, 100, -100, 50], [-30, 30, 0, 1]])])
    masked = randn(3, 4)
    masked[0, 1] = masked[0, 3] = masked[1, 0] = -math.inf
    masked[2, 1:] = -math.inf
    case(g, "softmax", "masked", softmax, [masked])
    case(g, "causal_mask", "batch", causal_mask, [randn(2, 4, 4)])
    case(g, "causal_mask", "heads", causal_mask, [randn(1, 2, 3, 3)])
    layer_norm = lambda x, gamma, beta, eps: F.layer_norm(x, x.shape[-1:], gamma, beta, eps)  # noqa: E731
    case(g, "layer_norm", "ordinary", layer_norm, [randn(2, 3, 8), randn(8), randn(8)], eps=1e-5)
    # a variance near eps, so where eps goes matters
    case(g, "layer_norm", "narrow", layer_norm, [randn(2, 8, scale=0.003), randn(8), randn(8)], eps=1e-5)
    case(g, "layer_norm", "flat", layer_norm, [torch.full((1, 4), 3.0), randn(4), randn(4)], eps=1e-5)
    # E[x²] - E[x]² would lose everything here in float32
    far = torch.tensor([[10000.0, 10001, 10002, 10003], [-5000, -5000.5, -4999, -5001.5]])
    case(g, "layer_norm", "far", layer_norm, [far, randn(4), randn(4)], eps=1e-5)
    rms_norm = lambda x, gamma, eps: F.rms_norm(x, x.shape[-1:], gamma, eps)  # noqa: E731
    case(g, "rms_norm", "ordinary", rms_norm, [randn(2, 3, 8), randn(8)], eps=1e-5)
    case(g, "rms_norm", "small", rms_norm, [randn(2, 8, scale=0.003), randn(8)], eps=1e-5)
    # ids repeat, so their rows' gradients add up
    case(g, "embedding", "repeats", lambda table, ids: F.embedding(torch.tensor(ids), table), [randn(6, 4)],
         ids=[[0, 5, 5], [2, 0, 3]])
    case(g, "positions", "prefix", lambda table, time: table[:time], [randn(6, 4)], time=4)
    case(g, "cross_entropy", "every row", cross_entropy, [randn(2, 3, 7, scale=2)],
         targets=[3, 0, 6, 1, 1, 5], weights=None)
    case(g, "cross_entropy", "answers only", cross_entropy, [randn(2, 4, 7, scale=2)],
         targets=[2, 4, 0, 6, 1, 3, 3, 5], weights=[0, 1, 1, 0, 0, 0, 1, 1])
    case(g, "cross_entropy", "large", cross_entropy, [randn(1, 4, 5, scale=5, shift=1000)],
         targets=[0, 1, 2, 4], weights=None)
    # targets the model all but rules out: their probabilities underflow float32
    case(g, "cross_entropy", "confident", cross_entropy, [randn(1, 3, 5, scale=40)], targets=[4, 0, 2], weights=None)
    case(g, "split_heads", "two", split_heads, [randn(2, 3, 8)], heads=2)
    case(g, "split_heads", "four", split_heads, [randn(1, 2, 8)], heads=4)
    case(g, "merge_heads", "two", merge_heads, [randn(2, 2, 3, 4)])
    case(g, "shift", "one", shift, [randn(2, 4, 3)], steps=1)
    case(g, "shift", "two", shift, [randn(2, 4, 3)], steps=2)
    case(g, "shift", "past the end", shift, [randn(1, 2, 3)], steps=3)
    case(g, "shift", "one sequence", shift, [randn(4, 3)], steps=1)
    case(g, "concat", "features", lambda a, b: torch.cat([a, b], dim=-1), [randn(2, 3, 4), randn(2, 3, 2)])
    case(g, "sum", "everything", lambda x: x.sum().reshape(1), [randn(2, 3, 4)])  # the engine's scalars are [1]
    return g


def prims():
    g = {}
    for op, fn in BINARY.items():
        # DIV's divisors stay clear of 0; MAX's operands never tie
        divisor = (lambda *s: randn(*s).abs() + 0.5) if op == "DIV" else randn
        case(g, "binary", op + " rows", lambda a, b, op: fn(a, b), [randn(2, 3, 4), divisor(3, 1)], op=op)
        case(g, "binary", op + " both stretch", lambda a, b, op: fn(a, b), [randn(2, 1, 4), divisor(3, 1)], op=op)
        case(g, "binary", op + " vector first", lambda a, b, op: fn(a, b), [randn(4), divisor(2, 1, 4)], op=op)
        case(g, "binary", op + " constant", lambda a, b, op: fn(a, b), [randn(2, 3), divisor(1)], op=op)
    for op, fn in UNARY.items():
        # LOG and SQRT get positive inputs over several decades
        x = randn(3, 4, scale=3).exp() if op in ("LOG", "SQRT") else randn(3, 4, scale=4)
        case(g, "unary", op, lambda x, op: fn(x), [x], op=op)
    edges = torch.tensor([-100.0, -20, -10, 10, 20, 100])
    case(g, "unary", "TANH edges", lambda x, op: torch.tanh(x), [edges], op="TANH")
    case(g, "sum", "rows", lambda x: x.sum(-1, keepdim=True), [randn(2, 3, 4)])
    case(g, "amax", "rows", lambda x: x.amax(-1, keepdim=True), [randn(2, 3, 4)])
    case(g, "transpose", "batch", lambda x: x.transpose(-2, -1), [randn(2, 3, 4)])
    case(g, "transpose", "vector", lambda x: row(x).transpose(-2, -1), [randn(4)])
    case(g, "unsqueeze", "rows", lambda x: x.unsqueeze(-2), [randn(2, 3)])
    case(g, "squeeze", "column", lambda x: x.squeeze(-1), [randn(3, 4, 1)])
    case(g, "matmul", "vector", lambda a, b: row(a) @ b, [randn(3), randn(3, 2)])
    case(g, "matmul", "shared", lambda a, b: a @ b, [randn(2, 3, 4), randn(4, 2)])
    case(g, "matmul", "batched", lambda a, b: a @ b, [randn(2, 3, 4), randn(2, 4, 2)])
    case(g, "causal", "square", causal_mask, [randn(3, 3)])
    return g


def modern():
    """The modern block's operations, and the Bench's primitives for it."""
    g = {}
    case(g, "rotary", "pairs", rotary, [randn(5, 4)])
    case(g, "rotary", "heads", rotary, [randn(2, 3, 4, 8)])
    case(g, "rotary", "far", rotary, [randn(1, 64, 2)])
    case(g, "flash_attention", "short", flash, [randn(2, 2, 5, 4), randn(2, 2, 5, 4), randn(2, 2, 5, 4)], scale=0.5)
    # past one tile, and scores that keep rising along the row, so later tiles keep raising the running maximum
    climb = torch.arange(40.0)[:, None].expand(40, 2).clone() + randn(40, 2, scale=0.5)
    case(g, "flash_attention", "tiles", flash, [randn(1, 40, 2), climb, randn(1, 40, 2)], scale=1.0)
    case(g, "flash_attention", "far apart", flash, [randn(1, 20, 2, scale=8), randn(1, 20, 2, scale=8), randn(1, 20, 2)], scale=1.0)
    z_loss = lambda y: (torch.logsumexp(y, dim=-1) ** 2).mean().reshape(1)  # noqa: E731
    case(g, "z_loss", "ordinary", z_loss, [randn(2, 3, 7, scale=2)])
    case(g, "z_loss", "large", z_loss, [randn(1, 3, 5, scale=3, shift=1000)])
    case(g, "scaled", "temperature", lambda x, t: x * t, [randn(2, 3, 4), randn(1)])
    for op, fn in {"COS": torch.cos, "SIN": torch.sin, "ROUND": torch.round, "POW2": lambda x: 2.0 ** x,
                   "EXPONENT": lambda x: (torch.frexp(x).exponent - 1).to(x.dtype) + 0 * x}.items():
        case(g, "unary", op, lambda x, op: fn(x), [randn(3, 4, scale=4)], op=op)
    # a half goes to the even neighbour
    case(g, "unary", "ROUND ties", lambda x, op: torch.round(x), [torch.tensor([0.5, 1.5, 2.5, -0.5, -1.5, 3.25])], op="ROUND")
    case(g, "unary", "EXPONENT powers", lambda x, op: (torch.frexp(x).exponent - 1).to(x.dtype) + 0 * x,
         [torch.tensor([1.0, 2, 8, 0.5, 1024, 16777216, 16777215, -3])], op="EXPONENT")
    case(g, "turn", "pairs", turn, [randn(2, 3, 4)])
    case(g, "left", "even", lambda x: x[..., :half(x)], [randn(2, 6)])
    case(g, "left", "odd", lambda x: x[..., :half(x)], [randn(2, 5)])
    case(g, "right", "even", lambda x: x[..., half(x):], [randn(2, 6)])
    case(g, "right", "odd", lambda x: x[..., half(x):], [randn(2, 5)])
    return g


def as_format(x, dtype):
    return x.float().to(dtype).to(torch.float64)


def grid_values():
    """Ties, the edge of each format's subnormals, and numbers across its range."""
    ties = [257.0, 259.0, 261.0, 263.0, 8.5, 9.5, 17.0, 19.0, 1.125, 1.375, 0.0009765625, 0.0029296875, -1.125]
    rest = 10.0 ** (6 * torch.rand(200, generator=G) - 3) * torch.randn(200, generator=G).sign()
    return torch.tensor(ties + [0.0, 1.0, -1.0, 0.5, 448.0, 449.0, 460.0, 57344.0]).float().tolist() + rest.tolist()


def numerics():
    """Narrow floats as PyTorch's own casts have them, and the whole-tensor scale of fp8 as low-precision training uses it."""
    g = {}
    values = [v for v in grid_values()]
    x = torch.tensor(values)
    g["bf16"] = [{"name": "cast", "input": values, "output": [float(v) for v in as_format(x, torch.bfloat16).tolist()]}]
    g["e4m3"] = [{"name": "cast", "input": values, "output": [float(v) for v in as_format(x, torch.float8_e4m3fn).tolist()]}]
    within = [v for v in values if abs(v) <= 57344]  # past its top E5M2 gives infinity, where the engine stays at the top
    g["e5m2"] = [{"name": "cast", "input": within,
                  "output": [float(v) for v in as_format(torch.tensor(within), torch.float8_e5m2).tolist()]}]
    t = randn(8, 16, scale=3)
    t[2] *= 100
    s = t.abs().max().double() / 448
    g["fp8"] = [{"name": "scaled", "shape": list(t.shape), "input": t.flatten().tolist(),
                 "output": [float(v) for v in (as_format(t.double() / s, torch.float8_e4m3fn) * s).float().flatten().tolist()]}]
    return g


def newton_schulz(update, steps=5, a=3.4445, b=-4.7750, c=2.0315, eps=1e-7):
    """torch.optim.Muon's iteration, in float64 where it works in bf16."""
    x = update.T if update.shape[0] > update.shape[1] else update
    x = x / x.norm().clamp(min=eps)
    for _ in range(steps):
        gram = x @ x.T
        x = a * x + (b * gram + c * gram @ gram) @ x
    return x.T if update.shape[0] > update.shape[1] else x


def muon(name, lr, steps=25):
    """Muon on two matrices, tall and wide, as torch.optim.Muon does it with match_rms_adamw and weight decay 0.01."""
    params = [randn(5, 3), randn(3, 4)]
    run = {"name": name, "lr": lr, "params": [tensor(p) for p in params], "steps": []}
    exact = [p.double() for p in params]
    theirs = [p.double().clone().requires_grad_() for p in params]
    optimizer = torch.optim.Muon(theirs, lr=lr, momentum=0.95, nesterov=True, weight_decay=0.01, adjust_lr_fn="match_rms_adamw")
    buffers = [torch.zeros_like(p) for p in exact]
    for _ in range(steps):
        grads = [randn(*p.shape) for p in exact]
        for i, (p, grad) in enumerate(zip(exact, grads)):
            g = grad.double()
            buffers[i] = 0.95 * buffers[i] + 0.05 * g
            update = 0.05 * g + 0.95 * buffers[i]
            step = newton_schulz(update)
            size = lr * 0.2 * math.sqrt(max(p.shape))
            exact[i] = p * (1 - lr * 0.01) - size * step
            theirs[i].grad = g
        optimizer.step()
        # the iteration in bf16 rounds a little; the method itself has to match
        for mine, other in zip(exact, theirs):
            assert (mine - other).abs().max() < 2e-2 * lr * 25 + 1e-3, (name, (mine - other).abs().max())
        run["steps"].append({"grads": [values(g) for g in grads], "params": [values(p) for p in exact]})
    return run


def schedules():
    """Warmup then a cosine, by PyTorch's own schedulers: warmup ends at step W, the cosine reaches 0 at step T."""
    out = []
    for warmup, total in ((10, 100), (20, 2000), (1, 50)):
        opt = torch.optim.SGD([torch.zeros(1, requires_grad=True)], lr=1.0)
        rise = torch.optim.lr_scheduler.LinearLR(opt, start_factor=1 / warmup, end_factor=1.0, total_iters=max(warmup - 1, 0))
        fall = torch.optim.lr_scheduler.CosineAnnealingLR(opt, T_max=total - warmup, eta_min=0.0)
        both = torch.optim.lr_scheduler.SequentialLR(opt, [rise, fall], milestones=[warmup])
        rates = []
        for _ in range(total):
            rates.append(opt.param_groups[0]["lr"])
            opt.step()
            both.step()
        out.append({"name": f"warmup {warmup} of {total}", "warmup": warmup, "total": total, "rates": rates})
    return out


def adamw(name, lr, clip, steps=25):
    """Trainer's update on given gradients. Their size changes from step to step, so clipping bites on some only."""
    params = [randn(4, 3), randn(3)]
    run = {"name": name, "lr": lr, "clip": clip, "params": [tensor(p) for p in params], "steps": []}
    params = [p.double().requires_grad_() for p in params]
    # the engine's AdamW: PyTorch's defaults, with weight decay on matrices only
    optimizer = torch.optim.AdamW([{"params": params[:1]}, {"params": params[1:], "weight_decay": 0.0}], lr=lr,
                                  betas=(0.9, 0.999), eps=1e-8, weight_decay=0.01)
    schedule = torch.optim.lr_scheduler.LambdaLR(optimizer, lambda step: min(1.0, (step + 1) / WARMUP))
    for _ in range(steps):
        size = 10 ** (2 * torch.rand(1, generator=G).item() - 1.5)
        grads = [randn(*p.shape, scale=size) for p in params]
        for p, grad in zip(params, grads):
            p.grad = grad.double()
        if clip > 0:
            norm = torch.nn.utils.clip_grad_norm_(params, clip)
        else:  # 0 turns clipping off, as in Trainer
            norm = torch.linalg.vector_norm(torch.cat([p.grad.flatten() for p in params]))
        optimizer.step()
        schedule.step()
        run["steps"].append({"grads": [values(g) for g in grads], "norm": norm.item(),
                             "params": [values(p) for p in params]})
    return run


def write(name, groups):
    """One case per line, so a regenerated fixture diffs case by case."""
    body = ",\n".join(f'"{op}": [\n' + ",\n".join(json.dumps(c, separators=(",", ":")) for c in cases) + "\n]"
                      for op, cases in groups.items())
    (OUT / name).write_text("{\n" + body + "\n}\n")


if __name__ == "__main__":
    shutil.rmtree(OUT, ignore_errors=True)
    OUT.mkdir(parents=True)
    write("ops.json", ops())
    write("prims.json", prims())
    write("adamw.json", {"adamw": [adamw("clipped", 0.03, 1.0), adamw("unclipped", 0.3, 0.0)]})
    write("modern.json", modern())
    write("numerics.json", numerics())
    write("muon.json", {"muon": [muon("small", 0.003), muon("large", 0.03)]})
    write("schedules.json", {"cosine": schedules()})
