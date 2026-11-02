// GuideME's search labels are independent of the guide's exported language.
(function () {
    var chinese = document.documentElement.lang.startsWith("zh");
    var root = new URL(document.body.dataset.pathToRoot || "./", location.href);
    var other = new URL(chinese ? "../" : "zh-cn/", root);
    function updateLink() {
        var link = document.querySelector("[data-locale-link]");
        if (link) {
            var page = location.pathname.slice(root.pathname.length) || "index.html";
            link.href = new URL(page, other).href;
        }
    }
    var page = document.getElementById("page-content");
    if (page) {
        new MutationObserver(updateLink).observe(page.parentNode, {childList: true});
    }
    updateLink();
    // A locale change needs its own document shell, navigation and search index.
    window.addEventListener("click", function (event) {
        if (event.target instanceof Element && event.target.closest("[data-locale-link]")) {
            event.stopPropagation();
        }
    }, {capture: true});
    if (!chinese) {
        return;
    }
    document.querySelectorAll(".search-slot").forEach(function (slot) {
        function translate() {
            var input = slot.querySelector(".search-input");
            if (input) {
                input.placeholder = "搜索";
                input.setAttribute("aria-label", "搜索野外笔记");
            }
            slot.querySelectorAll(".no-results").forEach(function (row) {
                if (row.textContent !== "没有找到结果") {
                    row.textContent = "没有找到结果";
                }
            });
        }
        new MutationObserver(translate).observe(slot, {childList: true, subtree: true});
        translate();
    });
})();
