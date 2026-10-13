/*
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

package com.dogsbay.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * No tracked file is stored with CRLF line endings.
 *
 * <p>{@code .gitattributes} asks for LF in the repository throughout, and CRLF on checkout
 * only where Windows needs it ({@code *.bat}, {@code *.cmd}). A working tree applies those
 * rules on the way in, so an ordinary commit cannot break them.
 *
 * <p>Dependabot can. It writes blobs through the GitHub API, where there is no working tree
 * for the rules to apply to, so #7 — the gradle-wrapper bump — committed {@code gradlew.bat}
 * with CRLF in the blob. Nothing failed; the damage is that git then cleans the checked-out
 * file back to LF whenever it compares, so {@code gradlew.bat} reads as modified in every
 * clone, for ever, with nothing to show for it. It blocked a merge here before it was found.
 *
 * <p>The wrapper is bumped regularly, so this will be offered again. The check is the whole
 * class of fault rather than that one file: {@code git ls-files --eol} reports the index side
 * as {@code i/crlf} for exactly this, and nothing legitimate in this repository is stored
 * that way. Recovery is {@code git add --renormalize <file>}.
 */
class RepositoryLineEndingsTest {

    @Test
    void noTrackedFileIsStoredWithCrlf() throws Exception {
        Process git = new ProcessBuilder("git", "ls-files", "--eol").start();
        List<String> lines = new String(git.getInputStream().readAllBytes()).lines().toList();
        assumeTrue(git.waitFor() == 0, "no git repository here, so there is nothing to compare");

        // "i/crlf  w/crlf  attr/text eol=crlf  <path>" — the index side is the stored blob.
        List<String> stored = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith("i/crlf")) {
                stored.add(line.substring(line.lastIndexOf('\t') + 1));
            }
        }
        assertThat(stored)
                .as("these files are stored with CRLF, so git will report them as modified in "
                    + "every clone — run `git add --renormalize <file>` on each and commit")
                .isEmpty();
    }
}
