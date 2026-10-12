#!/usr/bin/env python3
"""Replace source licence headers with the Apache-2.0 SPDX header.

Implements the Apache-2.0 header rollout. Kept in the repo so new
files can be normalised as they are added, rather than letting header debt regrow.

Usage:
    scripts/relicense-headers.py --mode proprietary   # DogsBay proprietary headers
    scripts/relicense-headers.py --mode none          # files with no licence header
    scripts/relicense-headers.py --mode mpl           # DogsBay's own MPL 1.1 files
    scripts/relicense-headers.py --mode <m> --check   # report only, write nothing

The modes are separate because they carry different provenance, and the MPL batch
is meant to land as its own reviewed commit.

SAFETY: every rewrite is verified byte-for-byte from the `package` statement
onward. If a single file would change below its header, the script writes nothing
and exits non-zero. Past mass edits in this repo silently deleted large spans of
code; the whole point of that check is that it cannot happen unnoticed.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SOURCE_ROOTS = ["src/main/java", "src/test/java"]

HEADER = """/*
 * Copyright (C) 2002-2026 DogsBay Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
"""

# Third-party code we have no right to relicense. A file matching any of these is
# never touched, in any mode. See plan 008 for why attribution does not cure the
# Sun files.
THIRD_PARTY_MARKERS = (
    "Sun Microsystems",
    "SUN PROPRIETARY",
    "University of Wisconsin",
    "NIAGARA",
)

# The GPLv2 Classpath Exception, matched on the OpenJDK phrasing rather than the
# bare word: "classpath" appears in ordinary code and comments throughout this
# tree, and a loose match wrongly excluded DogsBay's own files from relicensing.
CLASSPATH_EXCEPTION = re.compile(r"classpath[\"'\s]+exception", re.IGNORECASE)

# Paths excluded from relicensing regardless of their header text, for code whose
# provenance no text marker reveals. Empty since the Rhino shell fork was removed
# (the Rhino removal); keep the mechanism for the next such case.
THIRD_PARTY_PATHS = ()

MPL_MARKER = "Mozilla Public License"
PROPRIETARY_MARKER = "proprietary information of DogsBay"
SPDX_MARKER = "SPDX-License-Identifier"

LICENCE_WORDS = re.compile(r"copyright|licen[sc]e|all rights reserved", re.IGNORECASE)


class Skip(Exception):
    """A file that must not be rewritten, with the reason why."""


def classify(text: str, rel: str) -> str:
    """Return the batch a file belongs to: third-party, done, mpl, proprietary or none."""
    if any(p in rel for p in THIRD_PARTY_PATHS):
        return "third-party"
    if any(m in text for m in THIRD_PARTY_MARKERS):
        return "third-party"
    if CLASSPATH_EXCEPTION.search(text):
        return "third-party"
    if SPDX_MARKER in text:
        return "done"
    if MPL_MARKER in text:
        return "mpl"
    if PROPRIETARY_MARKER in text:
        return "proprietary"
    return "none"


def split_at_package(text: str) -> tuple[str, str]:
    """Split into (preamble, body) at the `package` statement.

    The body is what must survive the rewrite untouched.
    """
    match = re.search(r"^package\s", text, re.MULTILINE)
    if not match:
        raise Skip("no package statement — cannot locate the header boundary")
    return text[: match.start()], text[match.start() :]


def rewrite(text: str) -> str:
    """Return `text` with its licence header replaced by the Apache-2.0 header."""
    preamble, body = split_at_package(text)

    stripped = preamble.strip()
    if not stripped:
        # No leading comment at all — prepend.
        return HEADER + "\n" + body

    # Only ever consider a comment block that opens at the very top of the file.
    if not stripped.startswith("/*"):
        raise Skip("preamble does not start with a block comment")
    end = stripped.find("*/")
    if end == -1:
        raise Skip("unterminated leading block comment")

    leading_comment = stripped[: end + 2]
    remainder = stripped[end + 2 :].strip()
    if remainder:
        # Something else sits between the comment and `package` (a second comment,
        # an annotation). Too unusual to handle blind.
        raise Skip("unexpected content between the leading comment and `package`")

    if LICENCE_WORDS.search(leading_comment):
        # It is a licence/copyright header — replace it.
        return HEADER + "\n" + body
    # A genuine code comment (e.g. an IDE-generated note). Keep it, and put the
    # licence header above it rather than destroying authored content.
    return HEADER + "\n" + leading_comment + "\n\n" + body


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--mode",
        required=True,
        choices=["proprietary", "none", "mpl"],
        help="which batch of files to rewrite",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="report what would change without writing",
    )
    args = parser.parse_args()

    files = sorted(
        p
        for root in SOURCE_ROOTS
        for p in (REPO_ROOT / root).rglob("*.java")
    )

    planned: list[tuple[Path, str]] = []
    skipped: list[tuple[Path, str]] = []
    counts: dict[str, int] = {}

    for path in files:
        rel = str(path.relative_to(REPO_ROOT))
        text = path.read_text(encoding="utf-8")
        group = classify(text, rel)
        counts[group] = counts.get(group, 0) + 1
        if group != args.mode:
            continue
        try:
            updated = rewrite(text)
        except Skip as exc:
            skipped.append((path, str(exc)))
            continue

        # The invariant: nothing below the header may change.
        _, before_body = split_at_package(text)
        _, after_body = split_at_package(updated)
        if before_body != after_body:
            print(f"FATAL: body would change in {rel}", file=sys.stderr)
            return 2
        planned.append((path, updated))

    print(f"scanned {len(files)} files")
    for group in sorted(counts):
        print(f"  {group:12} {counts[group]}")
    print(f"mode={args.mode}: {len(planned)} to rewrite, {len(skipped)} skipped")
    for path, reason in skipped:
        print(f"  SKIP {path.relative_to(REPO_ROOT)}: {reason}")

    if args.check:
        print("--check: nothing written")
        return 0

    for path, updated in planned:
        # newline="\n" keeps LF endings regardless of platform (.gitattributes
        # enforces LF, and a CRLF slip would show up as a whole-file diff).
        with open(path, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(updated)
    print(f"rewrote {len(planned)} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
