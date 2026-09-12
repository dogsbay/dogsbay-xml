# Contributing to DogsBay XML

Thanks for your interest in improving DogsBay XML.

## How this project takes contributions

**Detailed issues are the contribution we want most.** They are more useful to
this project than patches, and they are the fastest route to a fix.

We do **not** generally accept pull requests. That is not a comment on you: this
is a 20-year-old codebase with a lot of load-bearing detail, and changes are
implemented in-house so that they carry the tests, the surrounding cleanups, and
the conventions the rest of the tree relies on. A fix informed by a good issue
usually lands sooner than the same fix as a PR.

**What "detailed" means, concretely:**

- Exact steps to reproduce, and what you expected instead.
- The version (`Help ▸ About`), OS, and Java version.
- The document that triggers it, or a minimal cut of it, where you can share it.
- Any stack trace, in full — including the frames below the first.
- For rendering or editor issues, a screenshot. They have repeatedly pinned down
  bugs that a description alone did not.

**Contributors we come to trust get repository access**, at which point this
whole section stops applying to you. That path runs through issues, not PRs.

If you still want to send code, read *Submitting code* below first — there is a
licensing step, and it applies to code pasted into an issue as well as to pull
requests.

## Prerequisites

- **JDK 25** (the Gradle toolchain will auto-provision it via Foojay if you
  don't have it installed).
- Git.

The `lib/` directory of vendored jars is required to build — do not delete it.

## Build & test

```bash
./gradlew compileJava     # compile
./gradlew test            # run all tests (JUnit 5; excludes @Tag("ui"))
./gradlew uiTest          # display-driven UI tests (needs a display / xvfb-run)
./gradlew run             # run the editor
./gradlew shadowJar       # self-contained JAR
```

**All tests must pass before a change is merged.** Add tests alongside any
behavior change — see the existing tests under `src/test/java/com/dogsbay/` for
the conventions (JUnit 5 + AssertJ, `@TempDir` for filesystem work).

## Coding conventions

- Match the style of the file you're editing. The repo ships an `.editorconfig`
  (4-space indent for Java, LF line endings, final newline) — configure your
  editor to honor it.
- Line endings are LF for all source (enforced by `.gitattributes`); Windows
  scripts (`*.bat`) stay CRLF.
- Keep changes focused. Prefer small, reviewable PRs over large mixed ones.

## Submitting code

Two things to know before you do.

**1. Pull requests are usually declined**, for the reason above. Open an issue
first and ask — if we say a PR is welcome for that particular change, the build
and conventions sections below apply.

**2. Submitting code means signing the CLA.** DogsBay XML is Apache-2.0, and
DogsBay Ltd. may also offer it under commercial terms. That requires permission
from every contributor, which the [CLA](CLA.md) grants. Sign it by commenting on
your pull request or issue with the wording in that file.

**This applies to code pasted into an issue, not only to pull requests.** A
factual bug report needs no licence and no CLA — reproduce steps, observed
behaviour, and a stack trace are not works of authorship. But a patch or a
proposed implementation is, and a fix we write informed by it could be a
derivative of it. If you would rather not sign, describe the problem and leave
the code out; that is a perfectly good contribution and often the more useful
one.

## A note on the comments

Some code comments point at notes that are not in this repository: design notes
under `plans/…`, and developer documentation under `docs-dev/…`. This repository
carries what it takes to build, test and package the editor; the user
documentation lives at <https://dogsbay.ai>, and the rest is working material
kept in a private tree.

If one of those pointers is hiding something you need in order to understand a
piece of code, open an issue and ask. The answer belongs in the code anyway: a
comment that only makes sense with a document you cannot read is a comment that
needs rewriting, and saying so is a useful contribution.

## Continuous integration

PRs run build + tests on JDK 25. The end-to-end job additionally checks out a
demo corpus and asserts that planted issues still surface; while that corpus is
private it is skipped on forks, since GitHub does not give a fork access to
this repository's secrets. That is expected, and a maintainer will re-run it
internally if needed.

## Reporting bugs & security issues

- Functional bugs: open an issue using the bug-report template. This is the
  main way to contribute — see the top of this file for what makes a report
  actionable.
- Security vulnerabilities: **do not** open a public issue — see
  [SECURITY.md](SECURITY.md).
