# com.dogsbay.publish

A DITA-OT plugin that publishes a DITA deliverable as a dogsbay site. DITA stays the source; each publish regenerates the site's `content/` folder (pages of topics removed from the map do not survive), and keeps an existing `dogsbay.config.yml`.

| Transtype | Produces |
| --- | --- |
| `dogsbay` | A dogsbay site project: `content/*.md`, `content/nav.yml`, `content/index.md`, `dogsbay.config.yml` (when missing), and `publish-report.md` |
| `dogsbay-site` | `dogsbay`, then the built site in `site/`: HTML pages, `.md` copies, `llms.txt` and a search index, built by `dogsbay-ssg` in one pass |

Conditions are resolved by DITA-OT with `--args.filter`, as for any output; each run is one deliverable.

DogsBay XML ships this plugin in its built-in DITA-OT; see the editor's `docs/dita-publishing.md`.

## Requirements

- DITA-OT 4 (tested with 4.3.5)
- Node.js 20 or later
Nothing else: `dogsbay-site` needs no dogsbay CLI, no package manager and no network access. It used to scaffold an Astro project, install its packages and run the Astro build; the site builder is now bundled in the plugin as `ssg/`, so the whole publish is two local Node.js processes.

The converter itself is in the plugin: `converter/publish-dita.mjs` is the dogsbay CLI's `publish-dita` command bundled into one file, so `dogsbay` needs no dogsbay CLI install. `converter/VERSION` records the dogsbay version and commit it was built from.

The site builder is in the plugin too: `ssg/build.mjs` is `dogsbay-ssg`'s build-only entry, with `ssg/VERSION` recording the commit. Unlike the converter it is a folder rather than one file — the entry code-splits, so `build.mjs` loads `chunks/` from beside itself and spawns `highlight-worker.mjs`. Copying `build.mjs` on its own will not work.

## Install

With Node.js (from npm, the same package that builds this plugin):

```bash
npx -y @dogsbay/dita-ot-plugin@beta install <dita-ot>
```

With DITA-OT's installer, from the plugin zip:

```bash
<dita-ot>/bin/dita install https://cdn.jsdelivr.net/npm/@dogsbay/dita-ot-plugin@<version>/dist/com.dogsbay.publish-<version>.zip
```

The plugin's own version in `plugin.xml` is DITA-OT's form of the dogsbay version: `0.2.0-beta.116` becomes `0.2.0.beta-116`, since DITA-OT allows no dots in a version qualifier.

## Use

```bash
dita -i guide.ditamap -f dogsbay -o site
dita -i guide.ditamap -f dogsbay --dogsbay.node=/usr/local/bin/node -o site
dita -i guide.ditamap -f dogsbay-site -o site
dita -i guide.ditamap -f dogsbay-site --dogsbay.ssg=/path/to/dogsbay-ssg/dist/build/build.mjs -o site
```

| Parameter | Default | Meaning |
| --- | --- | --- |
| `dogsbay.node` | `node` | The Node.js executable that runs the converter and the site builder when either is a script: a command on the PATH or a path |
| `dogsbay.cli` | none | The dogsbay CLI: a command on the PATH (`dogsbay.cmd` on Windows), or an absolute path to a `.js`, `.mjs` or `.cjs` bin script (run with `dogsbay.node`). When given, it does the conversion instead of the plugin's converter |
| `dogsbay.ssg` | the plugin's `ssg/build.mjs` | `dogsbay-site`: the site builder, as an absolute path to a `build.mjs` or a `dogsbay-ssg` command on the PATH. It must be `build.mjs`, not the CLI's bin script: `build.mjs` *is* the build command and takes no subcommand, while the PATH command is the whole CLI and is given one. A `build.mjs` copied away from its `chunks/` folder will not run |
| `dogsbay.family.links` | `no` | `yes` adds the parent, previous and next topic links after each topic, as `html5` has them (a dogsbay site has previous/next buttons and a sidebar instead) |
| `dogsbay.numbering` | `yes` | Number titled tables and figures ("Table 1. ", "Figure 1. ") per topic, as `html5` does, with DITA-OT's localized words |
| `args.gen.task.lbl` | `NO` | DITA-OT's parameter; `YES` adds task section headings, as in `html5` |
| `args.rellinks` | all | DITA-OT's parameter; which related links preprocessing keeps |

## How it works

`build_dogsbay.xml` adds the target `dita2dogsbay`, which depends on DITA-OT's `build-init` and `preprocess2` (the same preprocessing as `html5`), then runs the converter, `publish-dita ${dita.temp.dir} -o ${output.dir}`. The converter reads the preprocessed temp files before DITA-OT cleans them up.

`dita2dogsbay-site` adds one more target, `dogsbay.site`, which runs the builder over what the converter just wrote: `build.mjs ${output.dir} -o ${output.dir}/site`. Nothing has to scaffold a project first, because the converter already writes `dogsbay.config.yml` — its output *is* a site project.

The site goes in a subfolder because the builder refuses to write into a folder holding anything but its own output, and `${output.dir}` holds the content being read. It leaves its own output folder out of that content, so building inside the project is safe.

A page is written as `<path>.html` (the Astro build wrote `<path>/index.html`), and its links are relative, so the built site can be browsed from the file system as well as served.

The plugin is built and released from the dogsbay repo, `packages/dita-ot-plugin` (`@dogsbay/dita-ot-plugin` on npm). The converter's source is `@dogsbay/format-dita` (`publishDitaTemp`), bundled from `packages/cli` (`bundle:publish-dita`). Developer notes: `docs-dev/dita-ot-plugin.md` and `docs-dev/format-dita-publish.md` in the dogsbay repo.

## Checking the output

`packages/dita-ot-plugin/tools/compare_outputs.py` in the dogsbay repo compares a DITA-OT `html5` build with the built dogsbay site. See the `tools/README.md` next to it.
