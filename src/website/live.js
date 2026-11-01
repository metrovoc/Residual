// The field notes' tags of their own on the website: step-throughs, shots with their parts marked, and the widgets that run
// the real math. The export rewrites each tag to an element with data- attributes (client/NotesWeb.java); the arithmetic is in
// live-math.js, held to the game's numbers by a test.
(function () {
    var root = document.body.dataset.pathToRoot || "";
    var SCREEN = "#11161b", DARK = "#0b0f13", GRID = "#1d252d", TEXT = "#e4ebf1", DIM = "#7f8d99", TEAL = "#3cd6c6", AMBER = "#ffb547";

    function make(tag, parent, className, text) {
        var el = document.createElement(tag);
        if (className) {
            el.className = className;
        }
        if (text !== undefined) {
            el.textContent = text;
        }
        if (parent) {
            parent.appendChild(el);
        }
        return el;
    }

    function numbers(text) {
        return text.trim().split(/\s+/).map(Number);
    }

    function words(text) {
        return text.trim().split(/\s+/);
    }

    // ---------------------------------------------------------------- steps

    function steps(el) {
        var pages = Array.prototype.slice.call(el.children).filter(function (c) { return c.hasAttribute("data-step"); });
        var stack = make("div", null, "steps-stack");
        pages.forEach(function (page) { stack.appendChild(page); });
        var bar = make("div", el, "steps-bar");
        el.appendChild(stack);
        var back = make("button", bar, "", "‹");
        var where = make("span", bar);
        var marks = make("span", bar, "steps-marks");
        var ticks = pages.map(function () { return make("i", marks); });
        var on = make("button", bar, "", "›");
        back.type = on.type = "button";
        var current = 0;
        function show() {
            pages.forEach(function (page, i) { page.classList.toggle("shown", i === current); });
            ticks.forEach(function (tick, i) { tick.classList.toggle("lit", i <= current); });
            where.textContent = (current + 1) + " / " + pages.length;
            back.disabled = current === 0;
            on.disabled = current === pages.length - 1;
        }
        back.addEventListener("click", function () { current--; show(); });
        on.addEventListener("click", function () { current++; show(); });
        show();
    }

    // ---------------------------------------------------------------- shots

    function shot(el) {
        var name = el.dataset.shot, list = el.querySelector("ol");
        var parts = Array.prototype.slice.call(list.children);
        fetch(root + "shots/" + name + ".json").then(function (r) {
            return r.ok ? r.json() : null;
        }).then(function (where) {
            if (!where) {
                return;
            }
            var picture = make("div", null, "shot-picture");
            el.insertBefore(picture, list);
            picture.style.maxWidth = "calc(" + where.width + "px * var(--gui-scale, 2))";
            var img = make("img", picture);
            img.alt = el.getAttribute("aria-label") || "";
            img.src = root + "shots/" + name + ".png";
            img.addEventListener("error", function () { picture.remove(); });
            parts.forEach(function (li, i) {
                var rect = where.parts[li.dataset.part];
                if (!rect) {
                    return;
                }
                var mark = make("span", picture, "shot-part");
                mark.style.left = 100 * rect[0] / where.width + "%";
                mark.style.top = 100 * rect[1] / where.height + "%";
                mark.style.width = 100 * rect[2] / where.width + "%";
                mark.style.height = 100 * rect[3] / where.height + "%";
                mark.title = li.textContent.trim();
                make("b", mark, "", String(i + 1));
                function light(on) {
                    mark.classList.toggle("lit", on);
                    li.classList.toggle("lit", on);
                }
                [mark, li].forEach(function (one) {
                    one.addEventListener("mouseenter", function () { light(true); });
                    one.addEventListener("mouseleave", function () { light(false); });
                });
            });
        });
    }

    // ---------------------------------------------------------------- softmax

    function softmax(el) {
        var labels = words(el.dataset.labels), scores = numbers(el.dataset.scores), temperature = 1;
        el.className = "live live-softmax";
        var head = make("div", el, "live-row");
        make("span", head);
        make("span", head, "live-dim", el.dataset.score);
        make("span", head);
        make("span", head, "live-dim", el.dataset.chance);
        var rows = labels.map(function (label, i) {
            var row = make("div", el, "live-row");
            make("span", row, "", label);
            var slider = make("input", row);
            slider.type = "range";
            slider.min = -6;
            slider.max = 6;
            slider.step = 0.1;
            slider.value = scores[i];
            var value = make("span", row, "live-input");
            var cell = make("span", row, "live-bar");
            var bar = make("i", cell);
            var percent = make("span", row, "live-output");
            slider.addEventListener("input", function () {
                scores[i] = Number(slider.value);
                update();
            });
            return {value: value, bar: bar, percent: percent};
        });
        var last = make("div", el, "live-row live-rule");
        make("span", last, "live-dim", el.dataset.temperature);
        var hot = make("input", last);
        hot.type = "range";
        hot.min = 0.1;
        hot.max = 3;
        hot.step = 0.1;
        hot.value = temperature;
        var hotValue = make("span", last, "live-input");
        hot.addEventListener("input", function () {
            temperature = Number(hot.value);
            update();
        });
        function update() {
            var chances = LiveMath.softmax(scores, temperature);
            rows.forEach(function (row, i) {
                row.value.textContent = scores[i].toFixed(1);
                row.bar.style.width = 100 * chances[i] + "%";
                row.percent.textContent = Math.round(100 * chances[i]) + "%";
            });
            hotValue.textContent = temperature.toFixed(1);
        }
        update();
    }

    // ---------------------------------------------------------------- attention

    function heat(v) {
        // the Ui's amber, a gentle curve keeping small values visible
        var k = Math.sqrt(Math.max(0, Math.min(1, v)));
        function lerp(a, b) {
            return Math.round(a + (b - a) * k);
        }
        return "rgb(" + lerp(0x15, 0xff) + "," + lerp(0x17, 0xa8) + "," + lerp(0x1b, 0x3a) + ")";
    }

    function attention(el) {
        var tokens = words(el.dataset.tokens), points = numbers(el.dataset.points), t = tokens.length;
        var SIZE = 208, REACH = 2.5, chosen = t - 1, causal = true, dragging = -1, result;
        el.className = "live live-attention";
        var canvas = make("canvas", el);
        canvas.width = canvas.height = SIZE;
        var context = canvas.getContext("2d");
        var side = make("div", el);
        var table = make("table", side);
        var head = make("tr", table);
        make("th", head);
        tokens.forEach(function (token) { make("th", head, "", token); });
        var cells = tokens.map(function (token, i) {
            var row = make("tr", table);
            make("th", row, "", token);
            row.addEventListener("click", function () {
                chosen = i;
                draw();
            });
            return tokens.map(function () { return make("td", row); });
        });
        var label = make("label", side, "live-dim");
        var box = make("input", label);
        box.type = "checkbox";
        box.checked = true;
        label.appendChild(document.createTextNode(" " + el.dataset.mask));
        box.addEventListener("change", function () {
            causal = box.checked;
            draw();
        });
        function px(v) {
            return (v + REACH) / (2 * REACH) * (SIZE - 1);
        }
        function py(v) {
            return (REACH - v) / (2 * REACH) * (SIZE - 1);
        }
        function draw() {
            result = LiveMath.attention(points, 2, causal);
            context.fillStyle = DARK;
            context.fillRect(0, 0, SIZE, SIZE);
            context.fillStyle = GRID;
            context.fillRect(SIZE / 2, 0, 1, SIZE);
            context.fillRect(0, SIZE / 2, SIZE, 1);
            context.font = "14px sans-serif";
            tokens.forEach(function (token, i) {
                var x = px(points[i * 2]), y = py(points[i * 2 + 1]);
                context.fillStyle = i === chosen ? AMBER : TEXT;
                context.fillRect(x - 4, y - 4, 9, 9);
                context.fillStyle = i === chosen ? AMBER : DIM;
                context.fillText(token, Math.min(x + 7, SIZE - context.measureText(token).width), Math.max(14, y - 2));
            });
            context.strokeStyle = TEAL;
            context.lineWidth = 2;
            context.strokeRect(px(result.out[chosen * 2]) - 6, py(result.out[chosen * 2 + 1]) - 6, 13, 13);
            cells.forEach(function (row, i) {
                row[0].parentNode.classList.toggle("chosen", i === chosen);
                row.forEach(function (cell, j) {
                    var masked = causal && j > i, w = result.weights[i * t + j];
                    cell.textContent = masked ? "" : w.toFixed(2);
                    cell.style.background = masked ? DARK : heat(w);
                    cell.style.color = w > 0.45 && !masked ? SCREEN : TEXT;
                });
            });
        }
        function place(event) {
            var rect = canvas.getBoundingClientRect();
            return [(event.clientX - rect.left) * SIZE / rect.width, (event.clientY - rect.top) * SIZE / rect.height];
        }
        canvas.addEventListener("pointerdown", function (event) {
            var at = place(event);
            for (var i = t - 1; i >= 0; i--) {
                if (Math.abs(at[0] - px(points[i * 2])) <= 8 && Math.abs(at[1] - py(points[i * 2 + 1])) <= 8) {
                    chosen = dragging = i;
                    canvas.setPointerCapture(event.pointerId);
                    draw();
                    return;
                }
            }
        });
        canvas.addEventListener("pointermove", function (event) {
            if (dragging >= 0) {
                var at = place(event);
                points[dragging * 2] = Math.round(Math.max(-REACH, Math.min(REACH, at[0] / (SIZE - 1) * 2 * REACH - REACH)) * 10) / 10;
                points[dragging * 2 + 1] = Math.round(Math.max(-REACH, Math.min(REACH, REACH - at[1] / (SIZE - 1) * 2 * REACH)) * 10) / 10;
                draw();
            }
        });
        canvas.addEventListener("pointerup", function () { dragging = -1; });
        draw();
    }

    // ---------------------------------------------------------------- the training curve

    function curve(el) {
        var RATES = [1e-3, 1e-2, 0.1, 1.5], STEPS = 400, W = 600, H = 192;
        var rate = 1, running = false, started = false, seen = false, run, train, tested;
        el.className = "live live-curve";
        var canvas = make("canvas", el);
        canvas.width = W;
        canvas.height = H;
        var context = canvas.getContext("2d");
        var legend = make("div", el, "live-legend");
        var trainText = make("span", legend), testText = make("span", legend);
        trainText.style.color = TEAL;
        testText.style.color = AMBER;
        var controls = make("div", el, "live-controls");
        var play = make("button", controls), reset = make("button", controls, "", "↺"), rateButton = make("button", controls);
        function restart() {
            run = new LiveMath.Curve(el.dataset.practice, el.dataset.test, RATES[rate]);
            train = [];
            tested = [];
            rateButton.textContent = el.dataset.rateWord + ": " + (RATES[rate] >= 0.1 ? RATES[rate].toFixed(1) : RATES[rate].toFixed(3));
        }
        function line(values, hi, color) {
            context.strokeStyle = color;
            context.lineWidth = 2;
            context.beginPath();
            values.forEach(function (v, i) {
                // the scale's label gets a row above the curves
                var x = i * (W - 1) / (STEPS - 1), y = 24 + (hi - Math.max(0, Math.min(hi, v))) / hi * (H - 25);
                if (i === 0) {
                    context.moveTo(x, y);
                } else {
                    context.lineTo(x, y);
                }
            });
            context.stroke();
        }
        function draw() {
            var hi = 1;
            train.concat(tested).forEach(function (v) { hi = Math.max(hi, v); });
            context.fillStyle = DARK;
            context.fillRect(0, 0, W, H);
            context.fillStyle = GRID;
            for (var i = 0; i <= 4; i++) {
                context.fillRect(0, i * (H - 1) / 4, W, 1);
            }
            line(tested, hi, AMBER);
            line(train, hi, TEAL);
            context.fillStyle = DIM;
            context.font = "14px sans-serif";
            context.fillText(hi.toFixed(3), 4, 16);
            var steps = el.dataset.stepsWord.replace("%s", String(train.length));
            context.fillText(steps, W - 4 - context.measureText(steps).width, H - 6);
            var n = train.length;
            trainText.textContent = el.dataset.trainWord + " " + (n ? train[n - 1].toFixed(3) : "–");
            testText.textContent = el.dataset.testWord + " " + (n ? tested[n - 1].toFixed(3) : "–");
            play.textContent = running ? "❚❚" : "▶";
        }
        play.addEventListener("click", function () {
            if (train.length >= STEPS) {
                restart();
            }
            running = !running;
            draw();
        });
        reset.addEventListener("click", function () {
            restart();
            running = false;
            draw();
        });
        rateButton.addEventListener("click", function () {
            rate = (rate + 1) % RATES.length;
            restart();
            running = true;
            draw();
        });
        // two steps every twentieth of a second, as in the game, and only while it is in view
        setInterval(function () {
            if (!running || !seen) {
                return;
            }
            for (var i = 0; i < 2 && train.length < STEPS; i++) {
                var losses = run.next();
                train.push(losses[0]);
                tested.push(losses[1]);
            }
            running = train.length < STEPS;
            draw();
        }, 50);
        new IntersectionObserver(function (entries) {
            seen = entries[0].isIntersecting;
            if (seen && !started) {
                started = running = true;
            }
        }).observe(canvas);
        restart();
        draw();
    }

    document.querySelectorAll("[data-steps]").forEach(steps);
    document.querySelectorAll("[data-shot]").forEach(shot);
    document.querySelectorAll("[data-live=softmax]").forEach(softmax);
    document.querySelectorAll("[data-live=attention]").forEach(attention);
    document.querySelectorAll("[data-live=curve]").forEach(curve);
})();
