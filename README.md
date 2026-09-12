# DogsBay XML

An XML editor for DITA technical writers — schema-aware editing, live styled
preview, project-wide reuse analysis, safe refactoring, and full automation
through a command-line tool and an MCP server for AI assistants.

Java 25 · Swing · Apache-2.0

## What it does

- **Edit** — every document opens in tabbed views: **Editor** (syntax
  highlighting, folding, error margin), **Author** (WYSIWYG blocks),
  **Viewer** (XML tree), **Designer** (schema-aware graphical editing) and
  **Browser** (rendered preview). XML and DITA first; Markdown and AsciiDoc get
  highlighting, outline and preview.
- **Validate** — XSD, DTD (with XML catalogs) and RelaxNG, plus ISO Schematron
  for business rules. DITA topics validate against bundled DTDs out of the box.
- **Understand a project** — map explorer, where-used, key and conref audits,
  link checking, subject schemes, and a project health report.
- **Publish** — DITA-OT deliverables with DITAVAL filtering.
- **Automate** — the same operations run headless from the `dogsbay-xml` CLI, or
  against the running editor over MCP / JSON-RPC.

The [documentation](https://dogsbay.ai) catalogues everything, with a page per
area.

## Install

Download an installer from [Releases](../../releases) — RPM, DEB, DMG or MSI,
each with a bundled Java runtime. No separate JDK needed.

## Build from source

Requires **Java 25**.

```bash
./gradlew run            # run the editor
./gradlew test           # run the test suite
./gradlew shadowJar      # self-contained JAR
./gradlew jpackage       # native installer for the current OS
./gradlew syncLib        # required once before using bin/dogsbay-xml
bin/dogsbay-xml --help       # the CLI
```

The build resolves most dependencies from Maven Central; a set of long-vendored
jars lives in `lib/` and is committed, so a fresh clone builds without hunting
for artefacts that are no longer published.

## Command line

```bash
bin/dogsbay-xml validate file.dita --catalog catalog.xml
bin/dogsbay-xml query "docs/**/*.xml" "//xref[@href]"
bin/dogsbay-xml transform input.xml style.xslt -o out.html
```

The command-line tools are documented at <https://dogsbay.ai>.

## AI assistants

The editor can expose an MCP server on localhost so Claude Code, Claude Desktop
or Cursor can drive it — open documents, validate, query, edit. **It is off by
default** and enabled in Preferences → Server. See the MCP integration guide at
<https://dogsbay.ai>.

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) first: **detailed issues are the
contribution this project wants, and pull requests are usually declined.**
Submitting code — including code pasted into an issue — requires the
[CLA](CLA.md).

## Licence

Apache Licence 2.0 — see [LICENSE](LICENSE). Third-party components bundled in
this source tree are listed in [NOTICE](NOTICE), which must be retained in
redistributions.

**Cryptography notice.** This software does not implement or bundle its own
cryptography, but it does *use* it: HTTPS connections rely on the Java runtime's
TLS, and API credentials are stored through the operating system's secret
service. Because some countries restrict the import, possession, use or
re-export of software that uses encryption, check your local laws before
downloading or re-exporting it. See the
[Apache Software Foundation's export notice](https://www.apache.org/licenses/exports/)
for the general shape of these rules.

## Security

Report vulnerabilities privately as described in [SECURITY.md](SECURITY.md) —
not in a public issue.
