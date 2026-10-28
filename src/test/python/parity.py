"""Replays the models exported by PyTorchParityTest in PyTorch and compares every number.

    RESIDUAL_PARITY_DIR=build/parity ./gradlew test --tests '*PyTorchParityTest*'
    uv run --no-project --with torch --with numpy python src/test/python/parity.py build/parity

Per model it checks the logits, loss and every gradient of the first step, then every parameter after a few clipped
AdamW steps. Adam divides each gradient by its own running magnitude, so float32 rounding in near-zero gradients
grows into visible parameter differences; the parameters are therefore judged against a float64 run, and must be
about as close to it as PyTorch's own float32 run is. The test uses a large weight decay so that a mistake there
would stand well clear of that noise.
"""

import importlib.util
import json
import sys
from pathlib import Path

import torch
import torch.nn.functional as F


def load_module(path):
    spec = importlib.util.spec_from_file_location(path.parent.name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def train(module, case, dtype):
    """Runs the case's steps; returns the first step's logits, loss and gradients, and the final parameters."""
    model = module.load().to(dtype)
    params = dict(model.named_parameters())
    optimizer = torch.optim.AdamW(
        [
            {"params": [p for p in params.values() if p.dim() >= 2], "weight_decay": case["weight_decay"]},
            {"params": [p for p in params.values() if p.dim() < 2], "weight_decay": 0.0},
        ],
        lr=case["lr"],
    )
    tokens = torch.tensor(case["tokens"]).view(case["batch"], case["time"])
    targets = torch.tensor(case["targets"])
    weights = torch.tensor(case["weights"], dtype=dtype)
    first = None
    for step in range(case["steps"]):
        logits = model(tokens)
        losses = F.cross_entropy(logits.view(-1, logits.shape[-1]), targets, reduction="none")
        loss = (losses * weights).sum() / weights.sum()
        optimizer.zero_grad()
        loss.backward()
        if step == 0:
            first = logits.detach(), loss.item(), {n: p.grad.clone() for n, p in params.items()}
        torch.nn.utils.clip_grad_norm_(model.parameters(), case["clip"])
        optimizer.step()
    module.generate(model.float(), module.VOCAB[:3], count=8)
    return first, {n: p.detach().double() for n, p in params.items()}


def worst(ours, reference):
    """Largest absolute difference, relative to the reference's largest magnitude."""
    scale = max(reference.abs().max().item(), 1e-6)
    return (torch.tensor(ours).view_as(reference) - reference).abs().max().item() / scale


def check(case_dir):
    module = load_module(case_dir / "model.py")
    case = json.loads((case_dir / "case.json").read_text())
    (logits, loss, grads), after32 = train(module, case, torch.float32)
    _, after64 = train(module, case, torch.float64)
    java = {n: torch.tensor(v, dtype=torch.float64).view_as(after64[n]) for n, v in case["after"].items()}

    def drift(params):
        # in units of one full learning-rate step
        return max((params[n] - after64[n]).abs().max().item() for n in after64) / case["lr"]

    result = {
        "logits": worst(case["logits"], logits),
        "loss": abs(case["loss"] - loss) / loss,
        "grads": max(worst(case["grads"][n], g) for n, g in grads.items()),
        "drift": drift(java),
        "torch drift": drift(after32),
    }
    bad = [k for k, limit in (("logits", 1e-4), ("loss", 1e-5), ("grads", 1e-4)) if not result[k] <= limit]
    if not result["drift"] <= 4 * result["torch drift"] + 5e-3:
        bad.append("drift")
    return result, bad


def main():
    root = Path(sys.argv[1])
    columns = ("logits", "loss", "grads", "drift", "torch drift")
    print(f"{'model':<12}" + "".join(f"{c:>13}" for c in columns))
    failed = False
    for case_dir in sorted(p for p in root.iterdir() if (p / "case.json").exists()):
        result, bad = check(case_dir)
        failed |= bool(bad)
        print(f"{case_dir.name:<12}" + "".join(f"{result[c]:>13.2e}" for c in columns) + ("  FAIL " + ",".join(bad) if bad else ""))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
