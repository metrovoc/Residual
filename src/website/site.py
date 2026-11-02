"""Finish and check the website generated from one guide export.

    python3 src/website/site.py finish --root SITE --locale en_us|zh_cn --version VERSION --minecraft VERSION
    python3 src/website/site.py check --root BILINGUAL_SITE --version VERSION

English keeps the existing root; Simplified Chinese lives in zh-cn/. Guide page paths match
between languages. Clips, screenshots, search indexes and scene assets stay inside each locale.
"""
import argparse
from html import escape
from html.parser import HTMLParser
import posixpath
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit

ORIGIN = "https://metrovoc.github.io/Residual/"
RELEASES = "https://github.com/metrovoc/Residual/releases"


def finish(root, locale, version, minecraft):
    chinese = locale == "zh_cn"
    pages = sorted(root.rglob("*.html"))
    if not pages:
        raise ValueError(f"no generated pages in {root}")
    for path in pages:
        relative = path.relative_to(root).as_posix()
        current = ("zh-cn/" if chinese else "") + relative
        other = ("" if chinese else "zh-cn/") + relative
        href = posixpath.relpath(other, posixpath.dirname(current) or ".")
        text = path.read_text()
        # GuideME's standalone error page does not use the layout template.
        if 'data-locale-link' not in text:
            continue
        text = text.replace('<html lang="en">', '<html lang="zh-Hans">' if chinese else '<html lang="en">')
        text = re.sub(r'<a data-locale-link\b[^>]*>.*?</a>',
            f'<a data-locale-link href="{escape(href)}" lang="{"en" if chinese else "zh-Hans"}" hreflang="{"en" if chinese else "zh-Hans"}">{"English" if chinese else "简体中文"}</a>', text)
        text = text.replace(f'href="{RELEASES}"', f'href="{RELEASES}/tag/v{escape(version)}"')
        text = re.sub(r'<footer class="release-footer" data-release>.*?</footer>',
                      f'<footer class="release-footer" data-release>Residual {escape(version)} · Minecraft {escape(minecraft)}</footer>', text)
        text = re.sub(r'<link rel="alternate" hreflang="(?:en|zh-Hans|x-default)"[^>]*>\n?', '', text)
        alternates = ''.join(f'<link rel="alternate" hreflang="{lang}" href="{ORIGIN}{prefix}{escape(relative)}"/>\n'
                             for lang, prefix in [('en', ''), ('zh-Hans', 'zh-cn/'), ('x-default', '')])
        text = text.replace('</head>', alternates + '</head>')
        if chinese:
            text = text.replace('aria-label="menu"', 'aria-label="菜单"')
            text = text.replace('<strong>Experimental alpha.</strong> Expect breaking changes and major bugs. Your\n            <a href="https://github.com/metrovoc/Residual/issues">feedback</a> is much appreciated.',
                '<strong>实验性 Alpha 版本。</strong>可能有破坏性变更和严重问题。欢迎<a href="https://github.com/metrovoc/Residual/issues">反馈</a>。')
            text = text.replace('>Download</a>', '>下载</a>')
        path.write_text(text)
    print(f"Finished {locale}: {len(pages)} HTML files")


class Links(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links, self.clips, self.shots = [], [], []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        for key in ['href', 'src', 'data-scene-src']:
            if attrs.get(key):
                self.links.append(attrs[key])
        if attrs.get('data-clip'):
            self.clips.append(attrs['data-clip'])
        if attrs.get('data-shot'):
            self.shots.append(attrs['data-shot'])


def check(root, version):
    errors = []
    english = {p.relative_to(root).as_posix() for p in root.rglob('*.html') if 'zh-cn' not in p.relative_to(root).parts and p.name != '404.html'}
    chinese = {p.relative_to(root / 'zh-cn').as_posix() for p in (root / 'zh-cn').rglob('*.html') if p.name != '404.html'}
    if not english or english != chinese:
        errors.append(f"locale page sets differ: {sorted(english ^ chinese)}")
    for locale, prefix, lang in [('en_us', '', 'en'), ('zh_cn', 'zh-cn/', 'zh-Hans')]:
        base = root / prefix
        for name in sorted(english):
            path = base / name
            if not path.exists():
                continue
            text = path.read_text()
            if f'<html lang="{lang}">' not in text or f'{RELEASES}/tag/v{version}' not in text or 'data-locale-link' not in text:
                errors.append(f"{prefix}{name}: missing locale or release navigation")
            if locale == 'zh_cn' and ('Experimental alpha.' in text or '>Download</a>' in text):
                errors.append(f"{prefix}{name}: untranslated website labels")
            parsed = Links(); parsed.feed(text)
            for url in parsed.links:
                split = urlsplit(url)
                if split.scheme or split.netloc or not split.path:
                    continue
                if split.path.startswith('/Residual/'):
                    target = root / unquote(split.path.removeprefix('/Residual/'))
                elif split.path.startswith('/'):
                    errors.append(f"{prefix}{name}: link escapes project: {url}"); continue
                else:
                    target = path.parent / unquote(split.path)
                if not target.exists():
                    errors.append(f"{prefix}{name}: missing {url}")
            for clip in parsed.clips:
                if not (base / 'clips' / f'{clip}.clip').is_file():
                    errors.append(f"{prefix}{name}: missing clip {clip}")
            for shot in parsed.shots:
                for suffix in ['.png', '.json']:
                    if not (base / 'shots' / (shot + suffix)).is_file():
                        errors.append(f"{prefix}{name}: missing shot {shot}{suffix}")
    if errors:
        raise ValueError('\n'.join(errors))
    print(f"Checked {len(english)} matching pages per locale, links and all referenced clips/shots")


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('command', choices=['finish', 'check'])
    ap.add_argument('--root', type=Path, required=True)
    ap.add_argument('--version', required=True)
    ap.add_argument('--locale', choices=['en_us', 'zh_cn'])
    ap.add_argument('--minecraft')
    args = ap.parse_args()
    if args.command == 'finish':
        if not args.locale or not args.minecraft:
            ap.error('finish requires --locale and --minecraft')
        finish(args.root, args.locale, args.version, args.minecraft)
    else:
        check(args.root, args.version)


if __name__ == '__main__':
    main()
