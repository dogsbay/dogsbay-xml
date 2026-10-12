# DITA-OT tools

## run_testsuite.sh

Publishes DITA-OT's integration test cases with `html5` and `dogsbay-site` and compares each pair with `compare_outputs.py`. The 32 cases and their parameters mirror DITA-OT's `ITFormatHtml5`, `ITFormatXhtml`, `ITContentUplevels` and `ITPreprocess*` tests, reading inputs from a DITA-OT source checkout.

```bash
DITA_OT=/path/to/dita-ot-4.3.5 \
DITA_OT_SRC=/path/to/dita-ot-source \
PYTHON=/path/to/venv/bin/python \
OUT=/tmp/testsuite \
./run_testsuite.sh              # every case
./run_testsuite.sh html5 lang   # some cases
```

- `DITA_OT` must have `com.dogsbay.publish` installed, with its bundled `ssg/` site builder. `DOGSBAY_SSG` points at a working copy's `dist/build/build.mjs` instead.
- Each case writes `OUT/cases/<name>/` (`ref/`, `pub/`, the build logs, `compare.txt`, `compare.json`) and a line in `OUT/summary.txt`: the comparison result, or which step failed.
- `dogsbay-site` builds the site itself, with no package install and no network, so cases need no shared `node_modules` and a full run is minutes faster than it was.
- Cases are compared with the plugin's defaults, which leave `dogsbay.family.links` off while `html5` always writes parent and sibling links. Cases whose only differences are "Parent topic:" links are equivalent; confirm one with `--dogsbay.family.links=yes`.
- A failed publish shows only the Ant error in `publish.log`; rerun that case's `dita` command with `-v` for the underlying message.

## compare_project.sh

Builds one map with `html5` into `<out-dir>/ref` and with `dogsbay-site` into `<out-dir>/pub` (the built site is `<out-dir>/pub/site`) and compares them. The out-dir is deleted first, so the script refuses one that contains the map, the home folder or `/`.

```bash
DITA_OT=/path/to/dita-ot-4.3.5 ./compare_project.sh testdata/gaps/gaps.ditamap /tmp/gaps-out
```

`testdata/gaps` holds small topics for features neither sample project uses: spanning cells and a `colspec` without `colnum`, properties tables, element anchors (untitled sections, list items of blocks, element ids repeated in a nested topic), highlighting, cross-references to tables and figures, images (inline, sized, scaled, aligned, linked), and footnotes (with markup, in a list item).

## compare_outputs.py

Semantically compares a DITA-OT `html5` build with a dogsbay site built by the `com.dogsbay.publish` plugin, page by page, and exits 0 only when they are equivalent.

It compares topic content, not site chrome: the page set, titles, text in reading order (code blocks with their lines and indentation), block structure (heading levels, ordered or unordered list items and depth, table header or data cells, code blocks), inline code, bold and italic text, note types, links (each link's text and target page with its fragment, counted, including links within the page), links on the site that do not reach a page (unless the same link is broken in DITA-OT's output), table shapes (header or data cells and their spans), and the navigation (DITA-OT's table of contents against the site's `_dogsbay/nav.js`).

It ignores presentation: generated note labels ("Tip:"), callout colors, the page table of contents, DITA-OT's duplicate related links, file extensions in link text, a code block's language header (a code figure's caption in that header is kept), where a figure caption sits, footnote numbering markup, line breaks between words, inline markup inside code (Markdown code is plain text), and emphasis wrapping exactly one code span.

Other normalizations: `x/index.html` and `x.html` are both page `x` (the Astro build wrote a page as a folder, dogsbay-ssg writes it as a file); several terms of one definition are one term line; adjacent code elements are one code span; `<q>` is text in quotation marks; DITA note types dogsbay has no callout for compare as the converter maps them (attention and trouble as warning, notice and restriction as important, fastpath as tip, remember and other as note); DITA-OT's element fragments (`#topicid__elementid`) compare as the element id; DITA-OT's footnote back links are dropped. Generated taxonomy pages (`/tags/`) are not compared; `--site-ignore` lists the folders to leave out.

```bash
pip install beautifulsoup4

dita -i audacity-guide.ditamap -f html5 -o ref-html5
dita -i audacity-guide.ditamap -f dogsbay-site -o pub

python compare_outputs.py ref-html5 pub/site --site-nav pub/site/_dogsbay/nav.js --json report.json
```

Images are compared by file name, whether the file exists on the site, alt text, and width, height and alignment where DITA-OT sets them (alignment from the image or its enclosing `div` or `figure`, not page layout classes); missing files are `broken-images`. Subscript, superscript, underline, strikethrough and overline are compared as inline kinds, `tt` as code. Link fragments must land on an element with that id on the target page (fragments into other files, such as a PDF page, are not checked); a fragment compares as its element id on both sides (`topicid__elementid` → `elementid`). A site link to a file it serves as it is (a copied PDF) resolves. Broken images are counted per name. Empty anchors (`<a id>`) are ignored in text; a footnote's own number is not an inline kind.

DITA-OT's generated table of contents page (an `index.html` with no `<main>`) is not compared as a page.

Table and figure numbers ("Table 1. ") are compared, since the plugin generates them too; for a site published with `dogsbay.numbering=no`, pass `--no-numbering`.

When the reference build has no table of contents page (`--html5.toc.generate=no`, as the DITA-OT documentation builds), build once more with `--nav-toc=full` and pass one of its pages with `--ref-nav ref-nav/index.html`.

Exit codes: 0 equivalent, 1 not equivalent, 2 error. `--site-nav` without a reference table of contents (no `index.html` and no `--ref-nav`) is not equivalent: the navigation was not compared.

The comparison was checked by planting differences in a built site (a deleted paragraph, a changed link target, a changed note type, removed italic, an ordered list made unordered, a dropped navigation entry); each was reported.
