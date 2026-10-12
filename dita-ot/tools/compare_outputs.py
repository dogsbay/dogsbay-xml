"""Semantically compare a DITA-OT HTML5 build with a dogsbay site built by
the com.dogsbay.publish plugin (dogsbay-site).

Compares, page by page, only the topic content (not site chrome):
  - which pages exist on each side
  - the title
  - text units in reading order (paragraphs, list items, table cells,
    headings, code), ignoring generated labels ("Tip:", "Table 1.",
    callout titles, the page table of contents)
  - structure: for text that matches, its block kind (heading level,
    ordered or unordered list item and depth, table header or data cell,
    code block, paragraph)
  - inline meaning: code, bold (UI controls) and italic (terms) text
  - note types
  - links: text and target page, plus links that do not resolve to a page
    on their own side
  - table shapes
and the navigation: DITA-OT's table of contents against the site's
generated navigation (dogsbay renders the sidebar from it in the browser).

Normalizations, so presentation choices do not count as content:
  - DITA-OT's duplicate related links (the same target listed twice in one
    group) count once
  - link text that is a file name (`x.html`, `x.dita`, `x.md`) compares
    without its extension
  - a callout's type is its title ("Caution"), not its color variant
  - loose text and paragraphs are the same block kind
  - markup Markdown cannot carry is not compared: inline markup inside code,
    a code block's language header, where a figure caption sits, footnote
    numbering markup (see README.md for the full list)

Usage: python compare_outputs.py <dita-ot html5 dir> <dogsbay site dist dir>
                                 [--site-nav _dogsbay/nav.js] [--ref-nav toc-page.html] [--json out.json]
Exit code 0 when the outputs are equivalent, 1 otherwise.

Requires beautifulsoup4.
"""
import argparse
import collections
import difflib
import json
import os
import posixpath
import re
import sys
from urllib.parse import unquote, urlparse

from bs4 import BeautifulSoup, Comment, Doctype, NavigableString, Tag

BLOCK_TAGS = {"p", "li", "dt", "dd", "pre", "h1", "h2", "h3", "h4", "h5", "h6",
              "td", "th", "figcaption", "caption", "blockquote"}
CONTAINER_TAGS = {"div", "section", "article", "nav", "ul", "ol", "dl", "table", "tbody", "thead",
                  "tr", "figure", "main", "details", "aside"}
EXTERNAL = re.compile(r"^[a-zA-Z][a-zA-Z0-9+.-]*:")
FILE_TEXT = re.compile(r"^([\w./-]+)\.(html|dita|md)$")

# Inline meaning: DITA-OT marks it by element or class; dogsbay by element.
REF_INLINE = [
    ("code", lambda t: (t.name in ("code", "kbd", "samp", "tt") and t.find_parent("pre") is None)
     or bool({"codeph", "filepath", "cmdname", "apiname", "option", "parmname", "systemoutput", "userinput",
              "msgph", "msgnum", "varname", "tt"} & set(t.get("class", [])))),
    ("sub", lambda t: t.name == "sub"),
    ("sup", lambda t: t.name == "sup"),
    ("u", lambda t: t.name == "u"),
    ("strike", lambda t: t.name in ("s", "del", "strike") or "line-through" in t.get("class", [])),
    ("overline", lambda t: "overline" in t.get("class", [])),
    ("strong", lambda t: t.name in ("strong", "b") or bool({"uicontrol", "wintitle"} & set(t.get("class", [])))),
    ("em", lambda t: t.name in ("em", "i", "dfn", "cite", "var") or "term" in t.get("class", [])),
]
SITE_INLINE = [
    ("code", lambda t: t.name in ("code", "kbd", "samp") and t.parent is not None and t.parent.name != "pre"),
    ("strong", lambda t: t.name in ("strong", "b")),
    ("em", lambda t: t.name in ("em", "i", "dfn", "cite")),
    ("sub", lambda t: t.name == "sub"),
    ("sup", lambda t: t.name == "sup"),
    ("u", lambda t: t.name == "u"),
    ("strike", lambda t: t.name in ("s", "del", "strike")),
    ("overline", lambda t: "overline" in t.get("class", []) or "overline" in t.get("style", "")),
]


def squash(text):
    return re.sub(r"\s+", " ", text).strip()


def link_text(text):
    m = FILE_TEXT.match(text)
    return m.group(1) if m else text


# ── pages ─────────────────────────────────────────────────────────────────

def ref_pages(root):
    """DITA-OT: topics/x.html → key topics/x; index.html → index; topics/index.html → topics (as dogsbay serves it)."""
    pages = {}
    for dirpath, _, files in os.walk(root):
        for f in files:
            if f.endswith(".html"):
                full = os.path.join(dirpath, f)
                rel = os.path.relpath(full, root).replace(os.sep, "/")
                key = rel[:-5]
                key = key[: -len("/index")] if key.endswith("/index") else key
                # DITA-OT's generated table of contents (no <main>) is navigation, compared separately
                if key == "index" or rel.endswith("/index.html"):
                    with open(full, encoding="utf-8") as handle:
                        if "<main" not in handle.read():
                            continue
                pages[key] = full
    return pages


def site_pages(root, ignore=("tags",)):
    """
    dogsbay: `topics/x.html` → key `topics/x`, and `topics/x/index.html` → the same
    key (dogsbay-ssg writes a page as a file, the Astro build wrote it as a folder).
    `index.html` is `index`; 404, the site's own asset folders, generated taxonomy
    pages and redirect stubs are not pages.
    """
    pages = {}
    skip_dirs = {"pagefind", "_dogsbay", "_astro"} | set(ignore)
    for dirpath, dirnames, files in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in skip_dirs]
        for f in files:
            if not f.endswith(".html"):
                continue
            full = os.path.join(dirpath, f)
            rel = os.path.relpath(full, root).replace(os.sep, "/")
            if rel == "404.html":
                continue
            # A redirect is not a page, wherever it sits: the builder writes one for every
            # `redirect:`, every `aliases:` entry and the locale and version stubs, not
            # only for a site without a home page
            with open(full, encoding="utf-8") as handle:
                if 'http-equiv="refresh"' in handle.read(2000):
                    continue
            if rel == "index.html":
                pages["index"] = full
                continue
            key = rel[: -len("/index.html")] if rel.endswith("/index.html") else rel[:-5]
            # Both spellings of one page in one folder: say so rather than drop one of them
            # silently, which would hide every difference that page has
            if key in pages:
                kept = os.path.relpath(pages[key], root).replace(os.sep, "/")
                print(f"warning: page {key} is both {rel} and {kept}; comparing {kept}", file=sys.stderr)
                continue
            pages[key] = full
    return pages


# ── content extraction ────────────────────────────────────────────────────

def merge_emphasis_runs(root):
    """Adjacent runs of the same emphasis (<em>a</em><em>b</em>) read as one."""
    for tag in root.select("em + em, strong + strong, i + i, b + b"):
        prev = tag.previous_sibling
        if isinstance(prev, Tag) and prev.name == tag.name:
            for child in list(tag.children):
                prev.append(child.extract())
            tag.decompose()


def ref_content(soup, numbering=True):
    article = soup.select_one("main article") or soup.select_one("main") or soup.body or soup
    # Table and figure numbers are compared unless the site was published without them
    labels = ".note__title" if numbering else ".note__title, .table--title-label, .figure--title-label, .fig--title-label"
    for gen in article.select(labels):
        gen.decompose()
    # A footnote reference in a title: the site's title is text (the note is listed with the others)
    for ref in article.select("h1 a[href^='#fntarg_']"):
        (ref.find_parent("sup") or ref).decompose()
    # A browser shows <q> in quotation marks
    for q in article.select("q"):
        q.insert(0, "\u201C")
        q.append("\u201D")
        q.unwrap()
    # Several terms for one definition are one term line in Markdown (separated by line breaks)
    for dt in article.select("dt + dt"):
        prev = dt.find_previous_sibling("dt")
        prev.append(soup.new_tag("br"))
        for child in list(dt.children):
            prev.append(child.extract())
        dt.decompose()
    # dogsbay places a figure's caption below its content; DITA-OT above. Placement is presentation
    for caption in article.select("figure > figcaption"):
        caption.parent.append(caption.extract())
    # The same target listed twice in one related-links group counts once
    for group in article.select(".related-links .linklist"):
        seen = set()
        for li in group.find_all("li", recursive=True):
            a = li.find("a", href=True)
            if not a:
                continue
            key = (squash(a.get_text()), a["href"])
            if key in seen:
                li.decompose()
            else:
                seen.add(key)
    merge_emphasis_runs(article)
    return article.find("h1"), article


def site_content(soup):
    # dogsbay-ssg marks the page's content main#db-content; the Astro build marked it
    # main[data-pagefind-body]. Falling back to <body> would compare the site's whole
    # chrome (header, sidebar, search box, skip link) against DITA-OT's page.
    mains = soup.select("main#db-content") or soup.select("main[data-pagefind-body]") or soup.select("main")
    main = mains[-1] if mains else (soup.body or soup)
    # Footnotes: the reference is its number, and each note is its number and text
    # (DITA-OT: a superscript number, and a numbered block at the end)
    for ref in main.select("sup > a[href^='#fn-']"):
        ref.string = squash(ref.get_text()).strip("[]")
    for gen in main.select("[data-footnotes-separator], #footnote-label, a[href^='#fnref-']"):
        gen.decompose()
    for li in main.select("[data-footnote-list] > li"):
        m = re.search(r"fn-?(\w+)$", li.get("id", ""))
        number = (m.group(1) if m else "") + " "
        first = next((c for c in li.children if isinstance(c, Tag) or squash(str(c))), None)
        # The number leads the note's first paragraph (block content) or its text
        (first if isinstance(first, Tag) and first.name == "p" else li).insert(0, number)
        li.name = "div"
    for ol in main.select("[data-footnote-list]"):
        ol.name = "div"
    merge_emphasis_runs(main)
    # A callout's type is its title ("Caution"); data-variant is only its color (caution is styled "danger")
    for note in main.select("[data-variant][role=note]"):
        title = note.select_one("[data-part=title]")
        note["data-compare-type"] = squash(title.get_text()).lower() if title else note.get("data-variant", "note")
    # A heading's own permalink (aria-hidden, shown on hover) is navigation, not text. It
    # does carry a character to click ("\u00b6"), so it cannot be recognised by being empty;
    # restricting the rule to headings is what keeps it from swallowing real words in prose.
    for anchor in main.select("a[aria-hidden=true][href^='#']"):
        if anchor.find_parent(["h1", "h2", "h3", "h4", "h5", "h6"]):
            anchor.decompose()
    for gen in main.select("svg, [data-part=title], [data-pagefind-ignore], [data-toc-top], footer, nav,"
                           " [data-page-meta-strip], [data-copy-markdown], [data-llms-link]"):
        gen.decompose()
    # A code block's header (its language name and copy button) is chrome
    for header in main.select("[data-component=code-block] > div:not(:has(pre)), [data-component=code-rich] > div:not(:has(pre))"):
        header.decompose()
    # A code figure's caption is its header bar (a figcaption): keep the caption, drop the
    # language label and copy button, and put the caption last, as for DITA-OT's figures
    for caption in main.select("figure[data-component] > figcaption"):
        for gen in caption.select("[data-part=language], [data-part=copy]"):
            gen.decompose()
        caption.parent.append(caption.extract())
    return main.find("h1"), main


def is_empty_anchor(tag):
    """An anchor with no link and no content: a link target only (DITA-OT's and the site's id anchors)."""
    return tag.name == "a" and not tag.get("href") and not tag.get_text(strip=True) and not tag.find(True)


def leads(tag):
    """Whether a tag is its parent's first element (empty anchors aside), with no text of the parent's own before or around it."""
    parent = tag.parent
    return next((c for c in parent.children if isinstance(c, Tag) and not is_empty_anchor(c)), None) is tag and not squash(
        "".join(str(c) for c in parent.children if isinstance(c, NavigableString) and not isinstance(c, Comment)))


def block_kind(tag):
    """The block kind of a unit's element: heading level, list item type and depth, cell, code, paragraph."""
    if tag is not None and tag.name in ("p", "div"):
        # A leading paragraph inside a list item, definition, cell or quote is how Markdown renders
        # that block's own text; a wrapping div (DITA-OT's <div>, <bodydiv>) does not count
        node = tag
        while node.parent is not None and leads(node) and node.parent.name == "div":
            node = node.parent
        if node.parent is not None and node.parent.name in ("li", "dd", "dt", "td", "th", "blockquote") and leads(node):
            return block_kind(node.parent)
    if tag is None or tag.name in CONTAINER_TAGS or tag.name == "p":
        return "para"
    name = tag.name
    if re.fullmatch(r"h[1-6]", name):
        return name
    if name == "li":
        lists = [p.name for p in tag.parents if p.name in ("ol", "ul")]
        return f"li:{lists[0] if lists else '?'}:{len(lists)}"
    if name in ("td", "th", "pre", "dt", "dd", "caption", "figcaption", "blockquote"):
        return name
    return "para"


def text_units(root, skip):
    """(text, kind) in reading order: one unit per block's own text; nested blocks and loose text are their own units."""
    units = []

    def flush(buffer, owner):
        raw = "".join(buffer)
        if owner is not None and owner.name == "pre":
            # Code keeps its lines and indentation; only trailing space and blank edges are ignored
            text = "\n".join(line.rstrip() for line in raw.split("\n")).strip("\n")
        else:
            text = squash(raw)
        if text.strip():
            units.append((text, block_kind(owner)))
        buffer.clear()

    def visit(node, buffer, owner):
        for child in node.children:
            if isinstance(child, (Comment, Doctype)):
                continue
            if isinstance(child, NavigableString):
                buffer.append(str(child))
            elif isinstance(child, Tag):
                if id(child) in skip or child.name in ("script", "style"):
                    continue
                if child.name == "br":
                    buffer.append(" ")  # a line break separates words
                    continue
                if child.name == "a" and FILE_TEXT.match(squash(child.get_text())):
                    buffer.append(link_text(squash(child.get_text())))  # a file name as link text, without extension
                    continue
                if child.name in BLOCK_TAGS or child.name in CONTAINER_TAGS:
                    flush(buffer, owner)
                    inner = []
                    visit(child, inner, child)
                    flush(inner, child)
                else:
                    visit(child, buffer, owner)

    top = []
    visit(root, top, root)
    flush(top, root)
    return units


def inline_meaning(root, rules, skip):
    found = collections.Counter()
    marked = {}
    for tag in root.find_all(True):
        if id(tag) in skip or any(id(p) in skip for p in tag.parents):
            continue
        if tag.name == "pre" or tag.find_parent("pre") is not None:
            continue  # a code block is plain text; markup inside it is not inline meaning
        if tag.name == "sup" and (str((tag.parent or {}).get("name", "")).startswith("fntarg_") or str(tag.get("id", "")).startswith("fn-")):
            continue  # a footnote's own number in the list of notes (DITA-OT's a[name=fntarg_N], the site's sup#fn-N)
        if any(marked.get(id(p)) == "code" for p in tag.parents):
            continue  # nor is markup inside inline code (Markdown code spans are plain text)
        elements = [c for c in tag.children if isinstance(c, Tag)]
        if tag.name in ("strong", "b", "em", "i") and len(elements) == 1 and elements[0].name == "code" \
                and not squash("".join(str(c) for c in tag.children if isinstance(c, NavigableString))):
            continue  # emphasis around exactly one code span is the code's formatting, as markup inside it is
        for kind, test in rules:
            if test(tag):
                marked[id(tag)] = kind
                # Code inside code (DITA-OT's <filepath><varname>) is one code span
                if any(marked.get(id(p)) == kind for p in tag.parents):
                    break
                if kind == "code":
                    # Adjacent code elements with nothing between them read as one code span
                    prev = tag.previous_sibling
                    if isinstance(prev, Tag) and marked.get(id(prev)) == "code":
                        break
                    parts = [tag.get_text()]
                    nxt = tag.next_sibling
                    while isinstance(nxt, Tag) and any(k == "code" and t(nxt) for k, t in rules):
                        parts.append(nxt.get_text())
                        nxt = nxt.next_sibling
                    text = squash("".join(parts))
                else:
                    text = squash(tag.get_text())
                if text:
                    found[(kind, text)] += 1
                break
    return found


_ID_CACHE = {}


def page_ids(file):
    """Every id (and a[name]) on a page, so a link's fragment can be checked."""
    if file not in _ID_CACHE:
        try:
            soup = BeautifulSoup(open(file, encoding="utf-8").read(), "html.parser")
            _ID_CACHE[file] = {t["id"] for t in soup.find_all(id=True)} | {t["name"] for t in soup.find_all("a", attrs={"name": True})}
        except (OSError, UnicodeDecodeError):
            _ID_CACHE[file] = set()
    return _ID_CACHE[file]


def target_file(side_root, joined, side):
    """The file a resolved link path serves: DITA-OT's x.html, the site's x/index.html (or a file served as it is)."""
    if side == "ref" or (joined not in ("", ".") and os.path.isfile(os.path.join(side_root, joined))):
        return os.path.join(side_root, joined)
    return os.path.join(side_root, joined, "index.html") if joined not in ("", ".") else os.path.join(side_root, "index.html")


def resolve_link(href, side_root, base, side):
    """The page key a relative link reaches the way a browser would, and whether a page exists there.

    `base` is the folder the page is served from. DITA-OT pages are files
    (topics/x.html, parameters/index.html), so it is the file's folder. dogsbay
    pages are served without a trailing slash (topics/x, parameters), so it is
    the parent of the page's path.
    """
    parsed = urlparse(href)
    path = unquote(parsed.path)
    joined = posixpath.normpath(posixpath.join("/" + base, path)).lstrip("/") if path else base
    key = re.sub(r"(^|/)index$", "", re.sub(r"\.(html|md)$", "", joined)).rstrip("/") or "index"
    raw_fragment = parsed.fragment
    if not path:
        return key, True
    if side == "ref":
        exists = os.path.isfile(os.path.join(side_root, joined))
    else:
        # A page (x/index.html), or a file the site serves as it is (a copied PDF or image)
        exists = (not joined.endswith(".md")) and (
            os.path.isfile(os.path.join(side_root, joined, "index.html"))
            or (joined in ("", ".") and os.path.isfile(os.path.join(side_root, "index.html")))
            or (joined not in ("", ".") and os.path.isfile(os.path.join(side_root, joined))))
    # A fragment must name an element on the target page (a fragment into another file, such as
    # a PDF page, is not checked)
    target = target_file(side_root, joined, side)
    if exists and raw_fragment and target.endswith(".html") and unquote(raw_fragment) not in page_ids(target):
        exists = False
    return key, exists


def fragment(href, side):
    """A link's fragment as an element id: DITA-OT writes `topicid__elementid`, dogsbay `elementid`
    (and DITA-OT's form for an id an earlier topic in the file already has)."""
    frag = urlparse(href).fragment
    return frag.split("__", 1)[1] if "__" in frag else frag


def links(root, side_root, base, page_key, side, page_file=None):
    out = []
    own_ids = page_ids(page_file) if page_file else None
    for a in root.find_all("a", href=True):
        href = a["href"]
        img = a.find("img")
        text = link_text(squash(a.get_text())) or (f"[image {img.get('alt', '')}]" if img else "")
        if not text:
            continue
        if EXTERNAL.match(href):
            out.append({"text": text, "target": href, "resolves": True})
            continue
        if href.startswith("#"):
            frag = fragment(href, side)
            # Footnotes: DITA-OT links a note back to its reference (#fnsrc_N), which dogsbay's
            # comparison view drops; its reference link #fntarg_N is dogsbay's #fn-N
            if side == "ref" and frag.startswith("fnsrc_"):
                continue
            frag = re.sub(r"^fntarg_", "fn-", frag) if side == "ref" else frag
            resolves = own_ids is None or unquote(href[1:]) in own_ids
            out.append({"text": text, "target": f"{page_key}#{frag}", "resolves": resolves})
            continue
        target, resolves = resolve_link(href, side_root, base, side)
        frag = fragment(href, side)
        out.append({"text": text, "target": f"{target}#{frag}" if frag else target, "resolves": resolves})
    return out


# DITA note types dogsbay Markdown has no callout for, as the converter maps them
NOTE_TYPES = {"attention": "warning", "notice": "important", "fastpath": "tip", "remember": "note",
              "restriction": "important", "trouble": "warning", "other": "note"}


def notes(root, side):
    if side == "ref":
        found = []
        for n in root.select("div.note"):
            types = [c[len("note_"):] for c in n.get("class", []) if c.startswith("note_")]
            kind = types[0] if types else "note"
            found.append(NOTE_TYPES.get(kind, kind))
        return found
    return [n.get("data-compare-type", "note") for n in root.select("[data-variant][role=note]")]


def images(root, side_root, base, side, skip):
    """Each image: file name, whether the file exists, alt text, explicit size, alignment.

    The file name is compared without extension or build hash (the site may serve an
    optimized copy); the size only where DITA-OT sets one (the site may add the
    image's own size); alignment as DITA-OT's image* classes or the site's float and
    centering classes.
    """
    out = []
    for img in root.find_all("img"):
        if any(id(p) in skip for p in img.parents):
            continue
        src = img.get("src", "")
        path = unquote(urlparse(src).path)
        if EXTERNAL.match(src) or src.startswith("data:"):
            exists = True
        elif path.startswith("/"):
            exists = os.path.isfile(os.path.join(side_root, path.lstrip("/")))
        else:
            exists = os.path.isfile(os.path.join(side_root, posixpath.normpath(posixpath.join(base, path))))
        # The file name without its extension, and without the build hash of an optimized copy (img.HASH.webp)
        name = re.sub(r"\.[A-Za-z0-9_-]{6,}$", "", posixpath.splitext(posixpath.basename(path))[0]) if side == "site" else posixpath.splitext(posixpath.basename(path))[0]
        # Alignment is on the image or its own wrapper (DITA-OT's div.imagecenter, the site's figure)
        classes = set(img.get("class", []))
        if img.parent is not None and img.parent.name in ("div", "figure"):
            classes |= set(img.parent.get("class", []))
        if side == "ref":
            align = next((a for a in ("left", "right", "center") if f"image{a}" in classes), "")
        else:
            align = "center" if "mx-auto" in classes else "left" if "float-left" in classes else "right" if "float-right" in classes else ""
        out.append({"name": name, "exists": exists, "alt": squash(img.get("alt", "")),
                    "width": img.get("width", ""), "height": img.get("height", ""), "align": align})
    return out


def tables(root):
    """Each table's shape: its own rows (not a nested table's), each cell's type and spans."""
    def cell(c):
        spans = "".join(f"/{a}={c[a]}" for a in ("colspan", "rowspan") if c.get(a) not in (None, "1"))
        return c.name + spans

    return [[[cell(c) for c in tr.find_all(["td", "th"], recursive=False)]
             for tr in t.find_all("tr") if tr.find_parent("table") is t]
            for t in root.find_all("table")]


def extract(path, side, side_root, key, numbering=True):
    soup = BeautifulSoup(open(path, encoding="utf-8").read(), "html.parser")
    title_el, root = ref_content(soup, numbering) if side == "ref" else site_content(soup)
    title = squash(title_el.get_text()) if title_el else ""
    skip = {id(title_el)} if title_el is not None else set()
    rel = os.path.relpath(path, side_root).replace(os.sep, "/")
    base = posixpath.dirname(rel) if side == "ref" else (posixpath.dirname(key) if key != "index" else "")
    return {
        "title": title,
        "units": text_units(root, skip),
        "inline": inline_meaning(root, REF_INLINE if side == "ref" else SITE_INLINE, skip),
        "notes": notes(root, side),
        "links": links(root, side_root, base, key, side, path),
        "tables": tables(root),
        "images": images(root, side_root, base, side, skip),
    }


# ── navigation ────────────────────────────────────────────────────────────

def nav_target(href):
    """A navigation link as a page key, with its fragment: topics/x.html#s → topics/x#s, x/index → x."""
    path, _, fragment = href.partition("#")
    path = re.sub(r"(^|/)index$", "", posixpath.normpath(path) if path else "") or "index"
    return f"{path}#{fragment}" if fragment else path


def ref_nav(index_file, ref_root):
    soup = BeautifulSoup(open(index_file, encoding="utf-8").read(), "html.parser")
    # Links resolve from the TOC page's own folder, when it is a page of the reference build;
    # a TOC page from another build (--ref-nav) is taken to be at that build's root
    rel = os.path.relpath(os.path.abspath(index_file), os.path.abspath(ref_root)).replace(os.sep, "/")
    base = "" if rel.startswith("..") else posixpath.dirname(rel)
    toc = soup.select_one("nav > ul.map") or soup.select_one("nav.toc > ul")

    def items(ul):
        out = []
        for li in ul.find_all("li", recursive=False):
            a = li.find("a", recursive=False)
            if a is not None:
                label = squash(a.get_text())
            else:
                # A topic group: its own text, before the nested list
                label = squash("".join(str(c) for c in li.children if isinstance(c, NavigableString)
                                       and not isinstance(c, Comment)))
                span = li.find("span", recursive=False)
                if not label and span is not None:
                    label = squash(span.get_text())
            target = nav_target(re.sub(r"\.html(?=#|$)", "", posixpath.join(base, a["href"]) if base else a["href"])) if a else None
            sub = li.find("ul", recursive=False)
            out.append({"label": label, "target": target, "children": items(sub) if sub else []})
        return out

    return items(toc) if toc else []


def load_nav(nav_file):
    """
    The site's navigation as a list of {label, href, children} dicts.

    Two formats: dogsbay-ssg writes `_dogsbay/nav.js`, a window assignment whose
    entries are [label, href, children?] tuples; the Astro build wrote
    `src/data/nav.json` as objects. Both are read, so a site built either way can
    be compared against DITA-OT's TOC.
    """
    text = open(nav_file, encoding="utf-8").read()
    try:
        data = json.loads(text)
    except ValueError:
        # A window assignment: take the array, not everything after the first "=", so a
        # banner comment or an "=" in a label cannot shift the slice
        match = re.search(r"=\s*(\[.*\])\s*;?\s*$", text, re.S)
        if not match:
            raise SystemExit(f"{nav_file}: not JSON and not a window assignment of an array")
        data = json.loads(match.group(1))
    if not data or isinstance(data[0], dict):
        return data

    # Tuples: [label, href, children, flag]; the 4th element marks an alias or fragment
    # entry and is not needed here
    def objects(nodes):
        return [{"label": n[0], "href": n[1] if len(n) > 1 else None,
                 "children": objects(n[2] if len(n) > 2 else [])} for n in nodes]

    return objects(data)


def site_nav(nav_file, drop_home=True):
    def items(nodes):
        out = []
        for n in nodes:
            href = n.get("href")
            if href is not None:
                href = re.sub(r"\.html(?=#|$)", "", href.strip("/"))
            target = nav_target(href) if href else ("" if href is not None else None)
            # "Home": a generated index page, which DITA-OT's TOC does not list. Its href
            # is "index.html" from dogsbay-ssg and was "/" from the Astro build. Only when
            # the home page IS generated: a real topic mapped to index.md is a page DITA-OT
            # lists too, and dropping it would report the navigation as differing.
            if drop_home and target in ("", "index") and not n.get("children"):
                continue
            if target == "":
                target = "index"
            out.append({"label": squash(n.get("label", "")), "target": target, "children": items(n.get("children", []))})
        return out

    return items(load_nav(nav_file))


def nav_lines(items, depth=0):
    lines = []
    for item in items:
        lines.append(f"{'  ' * depth}{item['label']} -> {item['target']}")
        lines.extend(nav_lines(item["children"], depth + 1))
    return lines


# ── comparison ────────────────────────────────────────────────────────────

def compare_page(ref, out):
    diffs = []
    if ref["title"] != out["title"]:
        diffs.append({"kind": "title", "ref": ref["title"], "site": out["title"]})
    ref_text = [u[0] for u in ref["units"]]
    out_text = [u[0] for u in out["units"]]
    matcher = difflib.SequenceMatcher(a=ref_text, b=out_text, autojunk=False)
    for op, i1, i2, j1, j2 in matcher.get_opcodes():
        if op != "equal":
            diffs.append({"kind": f"text-{op}", "ref": ref_text[i1:i2], "site": out_text[j1:j2]})
            continue
        for (text, ref_kind), (_, out_kind) in zip(ref["units"][i1:i2], out["units"][j1:j2]):
            if ref_kind != out_kind:
                diffs.append({"kind": "structure", "text": text, "ref": ref_kind, "site": out_kind})
    if ref["inline"] != out["inline"]:
        only_ref = sorted((ref["inline"] - out["inline"]).elements())
        only_site = sorted((out["inline"] - ref["inline"]).elements())
        diffs.append({"kind": "inline", "only_ref": only_ref, "only_site": only_site})
    if ref["notes"] != out["notes"]:
        diffs.append({"kind": "notes", "ref": ref["notes"], "site": out["notes"]})
    ref_links = collections.Counter((l["text"], l["target"]) for l in ref["links"])
    out_links = collections.Counter((l["text"], l["target"]) for l in out["links"])
    if ref_links != out_links:
        diffs.append({"kind": "links",
                      "only_ref": sorted((ref_links - out_links).elements()),
                      "only_site": sorted((out_links - ref_links).elements())})
    # A site link that does not reach a page is a difference unless the same link is broken in DITA-OT's output too
    broken_ref = {(l["text"], l["target"]) for l in ref["links"] if not l["resolves"]}
    broken_site = sorted({(l["text"], l["target"]) for l in out["links"] if not l["resolves"]} - broken_ref)
    if broken_site:
        diffs.append({"kind": "broken-links", "ref": sorted(broken_ref), "site": broken_site})
    # Images: the site's size counts only where DITA-OT sets one
    def image_key(image, ref_image=None):
        sized = ref_image is None or ref_image["width"] or ref_image["height"]
        return (image["name"], image["alt"], image["width"] if sized else "", image["height"] if sized else "", image["align"])
    ref_images = [image_key(i) for i in ref["images"]]
    out_images = [image_key(o, r) for o, r in zip(out["images"], ref["images"])] + [image_key(o) for o in out["images"][len(ref["images"]):]]
    if ref_images != out_images:
        diffs.append({"kind": "images", "ref": ref_images, "site": out_images})
    # Broken images counted, so more broken copies of an image than DITA-OT has are reported
    missing = sorted((collections.Counter(i["name"] for i in out["images"] if not i["exists"])
                      - collections.Counter(i["name"] for i in ref["images"] if not i["exists"])).elements())
    if missing:
        diffs.append({"kind": "broken-images", "ref": [], "site": missing})
    if ref["tables"] != out["tables"]:
        diffs.append({"kind": "tables", "ref": ref["tables"], "site": out["tables"]})
    return diffs


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("ref", help="DITA-OT html5 output directory")
    parser.add_argument("site", help="dogsbay site dist directory")
    parser.add_argument("--site-nav", help="the site's generated navigation (_dogsbay/nav.js, or astro/src/data/nav.json)")
    parser.add_argument("--ref-nav", help="a DITA-OT page with the full table of contents, when the reference build has none "
                                          "(html5.toc.generate=no); default: the reference index.html")
    parser.add_argument("--json", help="write the full report as JSON")
    parser.add_argument("--no-numbering", action="store_true",
                        help="the site was published without table and figure numbers (dogsbay.numbering=no): ignore DITA-OT's")
    parser.add_argument("--site-ignore", default="tags",
                        help="comma-separated top-level folders of generated site pages to leave out (default: tags)")
    args = parser.parse_args()
    numbering = not args.no_numbering

    ref = ref_pages(args.ref)
    site = site_pages(args.site, tuple(x for x in args.site_ignore.split(",") if x))
    # With no topic page at DITA-OT's index (its index.html is a generated table of contents, or
    # there is none), the site's home page is generated too: navigation, not a topic page
    if "index" not in ref:
        site.pop("index", None)
    report = {"only_ref": sorted(set(ref) - set(site)), "only_site": sorted(set(site) - set(ref)), "pages": {}}
    for key in sorted(set(ref) & set(site)):
        diffs = compare_page(extract(ref[key], "ref", args.ref, key, numbering), extract(site[key], "site", args.site, key))
        if diffs:
            report["pages"][key] = diffs

    nav_diff = []
    nav_compared = False
    root_index = os.path.join(args.ref, "index.html")
    ref_nav_file = args.ref_nav or (root_index if os.path.isfile(root_index) else None)
    if args.site_nav and ref_nav_file:
        nav_compared = True
        ref_lines = nav_lines(ref_nav(ref_nav_file, args.ref))
        site_lines = nav_lines(site_nav(args.site_nav, drop_home="index" not in ref))
        nav_diff = [line for line in difflib.unified_diff(ref_lines, site_lines, "DITA-OT toc", "dogsbay nav", lineterm="")]
        report["navigation"] = nav_diff

    total = sum(len(d) for d in report["pages"].values())
    print(f"pages: {len(ref)} DITA-OT, {len(site)} dogsbay, {len(set(ref) & set(site))} in both")
    if report["only_ref"]:
        print(f"only in DITA-OT: {', '.join(report['only_ref'])}")
    if report["only_site"]:
        print(f"only in dogsbay: {', '.join(report['only_site'])}")
    if args.site_nav and not nav_compared:
        print("navigation: not compared (the DITA-OT build has no index.html; pass --ref-nav)")
    elif args.site_nav:
        print("navigation: " + ("same" if not nav_diff else "differs"))
        for line in nav_diff:
            print(f"  {line}")
    for key, diffs in report["pages"].items():
        print(f"\n== {key}")
        for d in diffs:
            kind = d["kind"]
            if kind.startswith("text-"):
                print(f"  {kind}:")
                for u in d["ref"]:
                    print(f"    - DITA-OT: {u[:200]}")
                for u in d["site"]:
                    print(f"    + dogsbay: {u[:200]}")
            elif kind in ("links", "inline"):
                for l in d["only_ref"]:
                    print(f"  {kind} only in DITA-OT: {l}")
                for l in d["only_site"]:
                    print(f"  {kind} only in dogsbay: {l}")
            elif kind == "structure":
                print(f"  structure: {d['text'][:80]!r} DITA-OT={d['ref']} dogsbay={d['site']}")
            else:
                print(f"  {kind}: DITA-OT={d.get('ref')} dogsbay={d.get('site')}")
    print(f"\n{len(report['pages'])} pages differ, {total} differences")
    if args.json:
        report["pages"] = {k: [{**d} for d in v] for k, v in report["pages"].items()}
        json.dump(report, open(args.json, "w"), indent=2, default=list)
    equivalent = (not report["pages"] and not report["only_ref"] and not report["only_site"] and not nav_diff
                  and (nav_compared or not args.site_nav))
    print("EQUIVALENT" if equivalent else "NOT EQUIVALENT")
    return 0 if equivalent else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:  # a crash is not "not equivalent" (exit 1)
        print(f"error: {error}", file=sys.stderr)
        sys.exit(2)
