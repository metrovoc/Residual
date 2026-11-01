"""Typesets the guide's formulas and figures as images, since guide pages have no math rendering of their own.

    uv run --with matplotlib python tools/formulas.py

The guide shows images at a quarter of their pixel size, so everything is drawn at four times the size it appears.
"""
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/residual/guides/residual/guide/formulas"

# the guide's body text, and the mod's teal and amber
TEXT = "#e4e4e4"
DIM = "#8a8a8a"
TEAL = "#3cd6c6"
AMBER = "#ffb547"

FORMULAS = {
    "cross_entropy": r"L = -\dfrac{1}{N}\sum_{t=1}^{N}\log p_\theta(x_{t+1} \mid x_1, \dots, x_t)",
    "chance": r"L_{\mathrm{chance}} = \log V \qquad \log 78 \approx 4.36",
    "softmax": r"\mathrm{softmax}(z)_i = \dfrac{e^{z_i}}{\sum_j e^{z_j}}",
    "temperature": r"p_i = \dfrac{e^{z_i/\tau}}{\sum_j e^{z_j/\tau}}",
    "linear": r"y = xW + b \qquad W \in \mathbb{R}^{C_{\mathrm{in}} \times C_{\mathrm{out}}}",
    "adamw": r"\theta \leftarrow \theta - \eta \left( \dfrac{\hat m}{\sqrt{\hat v} + \epsilon} + \lambda \theta \right)",
    "clip": r"g \leftarrow g \cdot \min\left(1, \dfrac{c}{\Vert g \Vert}\right)",
    "attention": r"\mathrm{Attention}(Q, K, V) = \mathrm{softmax}\left(\dfrac{Q K^\top}{\sqrt{d}} + M\right) V",
    "mask": r"M_{ts} = 0 \;\; \mathrm{if} \;\; s \leq t, \qquad M_{ts} = -\infty \;\; \mathrm{otherwise}",
    "scale": r"\mathrm{Var}(q \cdot k) = d \quad \Rightarrow \quad \mathrm{Var}\left(\dfrac{q \cdot k}{\sqrt{d}}\right) = 1",
    "heads": r"\mathrm{MultiHead}(X) = [\mathrm{head}_1;\, \dots;\, \mathrm{head}_H]\, W_O",
    "position": r"x_t = E[\mathrm{token}_t] + P[t]",
    "layernorm": r"\mathrm{LN}(x) = \gamma \odot \dfrac{x - \mu}{\sqrt{\sigma^2 + \epsilon}} + \beta",
    "rmsnorm": r"\mathrm{RMS}(x) = \gamma \odot \dfrac{x}{\sqrt{\frac{1}{C}\sum_i x_i^2 + \epsilon}}",
    "mlp": r"\mathrm{MLP}(x) = \phi(x W_1 + b_1)\, W_2 + b_2",
    "swiglu": r"\mathrm{SwiGLU}(x) = \left(\mathrm{SiLU}(x W_1) \odot x W_2\right) W_3",
    "rotary": r"\binom{a'}{b'}_{\!t,k} = \binom{a \cos\theta - b \sin\theta}{a \sin\theta + b \cos\theta} \qquad \theta = \dfrac{t}{10000^{2k/C}}",
    "qknorm": r"s = t \cdot \dfrac{\mathrm{rms}(q)\, \mathrm{rms}(k)^\top}{\sqrt{C}} = t \sqrt{C} \cos(q, k)",
    "zloss": r"L_z = 10^{-4} \cdot \dfrac{1}{N} \sum_t \left( \log \sum_j e^{y_{tj}} \right)^{\!2}",
    "online": r"m' = \max(m, \max_j s_j) \qquad l' = l\, e^{m - m'} + \sum_j e^{s_j - m'} \qquad o' = o\, e^{m - m'} + \sum_j e^{s_j - m'} v_j",
    "modern": r"x \leftarrow x + \mathrm{Attn}(\mathrm{RMS}_1(x)) \qquad x \leftarrow x + \mathrm{SwiGLU}(\mathrm{RMS}_2(x))",
    "schedule": r"\eta_t = \eta \cdot \min\left(1, \dfrac{t + 1}{W}\right) \cdot \dfrac{1}{2}\left(1 + \cos \pi\, \dfrac{t - S}{T - S}\right)",
    "float_grid": r"y = \mathrm{round}\left(\dfrac{x}{q}\right) q \qquad q = 2^{\max(\lfloor \log_2 |x| \rfloor,\, e) - m}",
    "newton": r"X \leftarrow X \left( a I + b X^\top X + c (X^\top X)^2 \right) \qquad a = 3.4445,\; b = -4.775,\; c = 2.0315",
    "muon": r"W \leftarrow (1 - \eta \lambda)\, W - 0.2\, \eta \sqrt{\max(r, c)}\; \mathrm{orth}((1 - \mu) g + \mu M)",
    "second_head": r"L = L_{\mathrm{next}} + 0.3 \, L_{\mathrm{after\ next}}",
    "residual": r"x_{l+1} = x_l + F_l(x_l)",
    "block": r"x \leftarrow x + \mathrm{Attn}(\mathrm{LN}_1(x)) \qquad x \leftarrow x + \mathrm{MLP}(\mathrm{LN}_2(x))",
    "residual_grad": r"\dfrac{\partial x_{l+1}}{\partial x_l} = I + \dfrac{\partial F_l}{\partial x_l}",
    "embedding": r"e_t = E[x_t] \qquad E \in \mathbb{R}^{V \times C}",
    "multiply": r"y = a \odot b \qquad y_i = a_i\, b_i",
    "matmul": r"Y = A B \qquad Y = A B^\top \;\; \mathrm{with\ Transpose}",
    "shift": r"y_t = x_{t-k} \;\; \mathrm{for} \;\; t \geq k, \qquad y_t = 0 \;\; \mathrm{for} \;\; t < k",
    "concat": r"y = [a;\, b] \qquad C_y = C_a + C_b",
    "calibration": r"r = \dfrac{\sum_t \mathbb{E}_\theta[a_{t+1} - a_t]}{\sum_t (a_{t+1} - a_t)}",
}


def formula(name, tex):
    fig = plt.figure(figsize=(0.01, 0.01))
    fig.text(0, 0, f"${tex}$", fontsize=26, color=TEXT)
    fig.savefig(OUT / f"{name}.png", dpi=100, transparent=True, bbox_inches="tight", pad_inches=0.12)
    plt.close(fig)


def activations():
    """The Activation machine's functions on one chart."""
    z = np.linspace(-4, 4, 400)
    sigmoid = 1 / (1 + np.exp(-z))
    curves = {
        "ReLU": (np.maximum(z, 0), TEAL),
        "GELU": (0.5 * z * (1 + np.tanh(np.sqrt(2 / np.pi) * (z + 0.044715 * z ** 3))), AMBER),
        "SiLU": (z * sigmoid, "#b48cff"),
        "tanh": (np.tanh(z), "#ff6f7d"),
        "sigmoid": (sigmoid, "#7cc8ff"),
    }
    fig, ax = plt.subplots(figsize=(9.6, 5.4), dpi=100)
    for side in ("top", "right"):
        ax.spines[side].set_visible(False)
    for side in ("left", "bottom"):
        ax.spines[side].set_color(DIM)
    ax.tick_params(colors=DIM, labelsize=20)
    ax.axhline(0, color=DIM, linewidth=1)
    ax.axvline(0, color=DIM, linewidth=1)
    for label, (y, color) in curves.items():
        ax.plot(z, y, color=color, linewidth=4, label=label)
    ax.set_xlim(-4, 4)
    ax.set_ylim(-1.5, 4)
    legend = ax.legend(fontsize=20, frameon=False, loc="upper left")
    for text in legend.get_texts():
        text.set_color(TEXT)
    fig.savefig(OUT / "activations.png", transparent=True, bbox_inches="tight", pad_inches=0.1)
    plt.close(fig)


def axes(figsize):
    fig, ax = plt.subplots(figsize=figsize, dpi=100)
    for side in ("top", "right"):
        ax.spines[side].set_visible(False)
    for side in ("left", "bottom"):
        ax.spines[side].set_color(DIM)
    ax.tick_params(colors=DIM, labelsize=20)
    return fig, ax


def schedules():
    """The three schedules of a run of 2,000 steps with 100 of warmup, as learn/Recipe.java has them: the page says which is which, in its language."""
    t = np.arange(2000)
    rise = np.minimum(1, (t + 1) / 100)

    def fall(start):
        return 0.5 * (1 + np.cos(np.pi * np.clip((t - start) / (2000 - start), 0, 1)))

    fig, ax = axes((9.6, 4.2))
    for y, color in ((rise, DIM), (rise * fall(100), TEAL), (rise * fall(1600), AMBER)):
        ax.plot(t, y, color=color, linewidth=4)
    ax.set_xlim(0, 2000)
    ax.set_ylim(0, 1.05)
    ax.set_xticks([0, 1000, 2000])
    ax.set_yticks([0, 0.5, 1])
    fig.savefig(OUT / "schedules.png", transparent=True, bbox_inches="tight", pad_inches=0.1)
    plt.close(fig)


def grids():
    """Every number fp8 and fp4 can hold from 0 to 8, as tensor/Numerics.java rounds to them: half as far apart in each power of 2 below."""
    def points(mantissa, lowest, top):
        out = {0.0}
        for e in range(lowest, 5):
            for m in range(2 ** mantissa):
                out.add(2.0 ** e * (1 + m / 2 ** mantissa))
        for m in range(2 ** mantissa):
            out.add(2.0 ** lowest * m / 2 ** mantissa)
        return sorted(v for v in out if v <= top)

    fig, ax = axes((9.6, 2.4))
    ax.spines["left"].set_visible(False)
    ax.set_yticks([])
    for y, (values, color, label) in enumerate(((points(1, 0, 6), AMBER, "fp4"), (points(3, -6, 8), TEAL, "fp8"))):
        ax.scatter(values, [y] * len(values), s=70, color=color, linewidths=0, clip_on=False)
        ax.text(8.25, y, label, color=TEXT, fontsize=20, va="center")
    ax.set_xlim(0, 8)
    ax.set_ylim(-0.6, 1.6)
    ax.set_xticks([0, 1, 2, 3, 4, 5, 6, 7, 8])
    fig.savefig(OUT / "grids.png", transparent=True, bbox_inches="tight", pad_inches=0.1)
    plt.close(fig)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    plt.rcParams["mathtext.fontset"] = "cm"
    for name, tex in FORMULAS.items():
        formula(name, tex)
    activations()
    schedules()
    grids()


if __name__ == "__main__":
    main()
