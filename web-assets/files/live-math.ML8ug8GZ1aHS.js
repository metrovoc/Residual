// What the field notes' live widgets compute, on the website. In the game the same widgets run on the engine models run
// on (notes/LiveMath.java); this is the same arithmetic in single precision, order for order, and LiveMathTest holds the
// two to the same numbers.
var LiveMath = (function () {
    var f = Math.fround;

    // F.softmax over one row, with what the sampler does first: the scores divided by the temperature
    function softmax(scores, temperature) {
        var t = Math.max(f(temperature), f(1e-3));
        var x = [];
        for (var i = 0; i < scores.length; i++) {
            x.push(f(f(scores[i]) / t));
        }
        return softmaxRow(x);
    }

    function softmaxRow(x) {
        var max = -Infinity, sum = 0, o = [], j;
        for (j = 0; j < x.length; j++) {
            max = Math.max(max, x[j]);
        }
        if (max === -Infinity) {
            return x.map(function () { return 0; });
        }
        for (j = 0; j < x.length; j++) {
            var e = f(Math.exp(f(x[j] - max)));
            o.push(e);
            sum = f(sum + e);
        }
        var inv = f(1 / sum);
        return o.map(function (e) { return f(e * inv); });
    }

    // One head of attention over the vectors x [T, d], flat by rows, with queries, keys and values the vectors themselves
    function attention(x, d, causal) {
        var t = x.length / d, scale = f(1 / Math.sqrt(d)), scores = [], weights = [], out = [], i, j, p;
        x = x.map(f);
        for (i = 0; i < t; i++) {
            var row = [];
            for (j = 0; j < t; j++) {
                var s = 0;
                for (p = 0; p < d; p++) {
                    s = f(s + f(x[i * d + p] * x[j * d + p]));
                }
                s = f(s * scale);
                scores.push(s);
                row.push(causal && j > i ? -Infinity : s);
            }
            weights = weights.concat(softmaxRow(row));
        }
        for (i = 0; i < t; i++) {
            var read = [];
            for (p = 0; p < d; p++) {
                read.push(0);
            }
            for (j = 0; j < t; j++) {
                var w = weights[i * t + j];
                if (w !== 0) {
                    for (p = 0; p < d; p++) {
                        read[p] = f(read[p] + f(w * x[j * d + p]));
                    }
                }
            }
            out = out.concat(read);
        }
        return {scores: scores, weights: weights, out: out};
    }

    // A table of what comes after each character, learning from one short text and tested on another, as a Core trains:
    // cross-entropy, AdamW with its usual settings, the first twenty steps warming up
    function Curve(practice, test, learningRate) {
        var seen = {}, all = practice + test, i;
        for (i = 0; i < all.length; i++) {
            seen[all.charAt(i)] = true;
        }
        var alphabet = Object.keys(seen).sort();
        var index = {};
        alphabet.forEach(function (c, n) { index[c] = n; });
        function ids(text) {
            var out = [];
            for (var k = 0; k < text.length; k++) {
                out.push(index[text.charAt(k)]);
            }
            return out;
        }
        this.v = alphabet.length;
        this.practice = ids(practice);
        this.test = ids(test);
        this.lr = f(learningRate);
        this.step = 0;
        var cells = this.v * this.v;
        this.table = [];
        this.m = [];
        this.s = [];
        for (i = 0; i < cells; i++) {
            this.table.push(0);
            this.m.push(0);
            this.s.push(0);
        }
    }

    // Mean cross-entropy of the table on a text, in the engine's mix of precisions; with grad, what it adds to each weight
    Curve.prototype.loss = function (ids, grad) {
        var v = this.v, rows = ids.length - 1, total = 0, count = 0;
        var probs = [], r, j;
        for (r = 0; r < rows; r++) {
            var off = ids[r] * v, max = -Infinity, sum = 0, p = [];
            for (j = 0; j < v; j++) {
                max = Math.max(max, this.table[off + j]);
            }
            for (j = 0; j < v; j++) {
                var e = f(Math.exp(f(this.table[off + j] - max)));
                p.push(e);
                sum += e;
            }
            var inv = f(1.0 / sum);
            for (j = 0; j < v; j++) {
                p[j] = f(p[j] * inv);
            }
            probs.push(p);
            total += Math.log(sum) + max - this.table[off + ids[r + 1]];
            count += 1;
        }
        if (grad) {
            var c = f(1 / count);
            // the logits' gradient row by row, then each row's into the table's row, as embedding's backward does
            for (r = 0; r < rows; r++) {
                var row = ids[r] * v;
                for (j = 0; j < v; j++) {
                    var g = f(c * probs[r][j]);
                    if (j === ids[r + 1]) {
                        g = f(g - c);
                    }
                    grad[row + j] = f(grad[row + j] + g);
                }
            }
        }
        return f(total / count);
    };

    // One step of training: returns the loss on the practice text and on the test before the weights move
    Curve.prototype.next = function () {
        var tested = this.loss(this.test, null), grad = [], i;
        for (i = 0; i < this.table.length; i++) {
            grad.push(0);
        }
        var loss = this.loss(this.practice, grad);
        var rate = f(this.lr * Math.min(1, f(f(this.step + 1) / 20)));
        var beta1 = f(0.9), beta2 = f(0.999), eps = f(1e-8), decayRate = f(0.01);
        this.step++;
        var bc1 = 1 - Math.pow(beta1, this.step), bc2 = 1 - Math.pow(beta2, this.step);
        var stepSize = f(rate / bc1), bc2Sqrt = f(Math.sqrt(bc2)), decay = f(1 - f(rate * decayRate));
        var keep1 = f(1 - beta1), keep2 = f(1 - beta2);
        for (i = 0; i < this.table.length; i++) {
            var gi = grad[i];
            this.m[i] = f(f(beta1 * this.m[i]) + f(keep1 * gi));
            this.s[i] = f(f(beta2 * this.s[i]) + f(f(keep2 * gi) * gi));
            var denom = f(f(f(Math.sqrt(this.s[i])) / bc2Sqrt) + eps);
            this.table[i] = f(f(this.table[i] * decay) - f(f(stepSize * this.m[i]) / denom));
        }
        return [loss, tested];
    };

    return {softmax: softmax, attention: attention, Curve: Curve};
})();
