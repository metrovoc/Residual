// The field notes' shots on the website, their parts marked. The export rewrites each tag to an element with data-
// attributes (client/NotesWeb.java).
(function () {
    var root = new URL(document.body.dataset.pathToRoot || "./", location.href).href;
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

    // ---------------------------------------------------------------- shots

    function shot(el) {
        var name = el.dataset.shot, list = el.querySelector("ol");
        var parts = Array.prototype.slice.call(list.children);
        var request = new AbortController();
        fetch(root + "shots/" + name + ".json", {signal: request.signal}).then(function (r) {
            return r.ok ? r.json() : null;
        }).then(function (where) {
            if (!where || request.signal.aborted) {
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
        }).catch(function (error) {
            if (!request.signal.aborted) {
                console.error("Failed to load marked screenshot", name, error);
            }
        });
        return function () { request.abort(); };
    }

    var mounted, cleanups = [];
    function mount() {
        var page = document.getElementById("page-content");
        if (page === mounted) {
            return;
        }
        cleanups.forEach(function (cleanup) { cleanup(); });
        cleanups = [];
        mounted = page;
        if (!page) {
            return;
        }
        page.querySelectorAll("[data-shot]").forEach(function (el) {
            var cleanup = shot(el);
            if (cleanup) {
                cleanups.push(cleanup);
            }
        });
    }
    var page = document.getElementById("page-content");
    if (page) {
        // GuideME replaces this node during navigation, including back and forward.
        new MutationObserver(mount).observe(page.parentNode, {childList: true});
        mount();
    }
})();
