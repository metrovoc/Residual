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
    "chance": r"L_{\mathrm{chance}} = \log V \qquad \log 27 \approx 3.30",
    "softmax": r"\mathrm{softmax}(z)_i = \dfrac{e^{z_i}}{\sum_j e^{z_j}}",
    "temperature": r"p_i = \dfrac{e^{z_i/\tau}}{\sum_j e^{z_j/\tau}}",
    "bigram": r"p(x_{t+1} = j \mid x_t = i) = \mathrm{softmax}(E_i)_j",
    "linear": r"y = xW + b \qquad W \in \mathbb{R}^{C_{\mathrm{in}} \times C_{\mathrm{out}}}",
    "context": r"h = \tanh([e_{t-2};\, e_{t-1};\, e_t]\, W_1 + b_1) \qquad z = h W_2 + b_2",
    "adamw": r"\theta \leftarrow \theta - \eta \left( \dfrac{\hat m}{\sqrt{\hat v} + \epsilon} + \lambda \theta \right)",
    "clip": r"g \leftarrow g \cdot \min\left(1, \dfrac{c}{\Vert g \Vert}\right)",
    "qkv": r"Q = X W_Q \qquad K = X W_K \qquad V = X W_V",
    "attention": r"\mathrm{Attention}(Q, K, V) = \mathrm{softmax}\left(\dfrac{Q K^\top}{\sqrt{d}} + M\right) V",
    "mask": r"M_{ts} = 0 \;\; \mathrm{if} \;\; s \leq t, \qquad M_{ts} = -\infty \;\; \mathrm{otherwise}",
    "scale": r"\mathrm{Var}(q \cdot k) = d \quad \Rightarrow \quad \mathrm{Var}\left(\dfrac{q \cdot k}{\sqrt{d}}\right) = 1",
    "heads": r"\mathrm{MultiHead}(X) = [\mathrm{head}_1;\, \dots;\, \mathrm{head}_H]\, W_O",
    "position": r"x_t = E[\mathrm{token}_t] + P[t]",
    "layernorm": r"\mathrm{LN}(x) = \gamma \odot \dfrac{x - \mu}{\sqrt{\sigma^2 + \epsilon}} + \beta",
    "rmsnorm": r"\mathrm{RMS}(x) = \gamma \odot \dfrac{x}{\sqrt{\frac{1}{C}\sum_i x_i^2 + \epsilon}}",
    "mlp": r"\mathrm{MLP}(x) = \phi(x W_1 + b_1)\, W_2 + b_2",
    "residual": r"x_{l+1} = x_l + F_l(x_l)",
    "block": r"x \leftarrow x + \mathrm{Attn}(\mathrm{LN}_1(x)) \qquad x \leftarrow x + \mathrm{MLP}(\mathrm{LN}_2(x))",
    "chain": r"\dfrac{\partial L}{\partial x_0} = \dfrac{\partial L}{\partial x_L} \prod_{l=1}^{L} \dfrac{\partial x_l}{\partial x_{l-1}}",
    "residual_grad": r"\dfrac{\partial x_{l+1}}{\partial x_l} = I + \dfrac{\partial F_l}{\partial x_l}",
    "sigmoid_slope": r"\sigma'(z) = \sigma(z)\,(1 - \sigma(z)) \leq \dfrac{1}{4}",
    "gpt": r"h_0 = E[x] + P \qquad h_l = \mathrm{Block}_l(h_{l-1}) \qquad z = \mathrm{LN}(h_L)\, W_U",
    "embedding": r"e_t = E[x_t] \qquad E \in \mathbb{R}^{V \times C}",
    "multiply": r"y = a \odot b \qquad y_i = a_i\, b_i",
    "matmul": r"Y = A B \qquad Y = A B^\top \;\; \mathrm{with\ Transpose}",
    "shift": r"y_t = x_{t-k} \;\; \mathrm{for} \;\; t \geq k, \qquad y_t = 0 \;\; \mathrm{for} \;\; t < k",
    "concat": r"y = [a;\, b] \qquad C_y = C_a + C_b",
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


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    plt.rcParams["mathtext.fontset"] = "cm"
    for name, tex in FORMULAS.items():
        formula(name, tex)
    activations()


if __name__ == "__main__":
    main()
