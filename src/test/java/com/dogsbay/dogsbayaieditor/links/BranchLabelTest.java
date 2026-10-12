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

package com.dogsbay.dogsbayaieditor.links;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.Test;

/**
 * What a branch is called in the status-bar switcher, the Branch selector and the Map Explorer.
 *
 * <p>A branch names itself in {@code <ditavalmeta>}, with a resource prefix or a resource suffix. Only the
 * prefix was read, so a map whose Windows and macOS branches use {@code win-} and {@code mac-} and whose Linux
 * branch uses {@code -linux} listed the third as {@code platform-linux.ditaval} — the odd one out, and the only
 * one not saying what it does (reported 2026-09-30).
 */
class BranchLabelTest {

    private static Branch branch(String ditavalHref, String resourcePrefix, String resourceSuffix) {
        return new Branch("topics/installing.dita", ditavalHref,
                ditavalHref == null ? null : new File(ditavalHref),
                null, null, resourcePrefix, resourceSuffix);
    }

    @Test
    void aPrefixedBranchIsCalledByItsPrefix() {
        assertThat(branch("filters/platform-windows.ditaval", "win-", null).label()).isEqualTo("win-");
    }

    @Test
    void aSuffixedBranchIsCalledByItsSuffix() {
        assertThat(branch("filters/platform-linux.ditaval", null, "-linux").label()).isEqualTo("-linux");
    }

    @Test
    void aPrefixWinsWhenABranchHasBoth() {
        // DITA-OT applies both; the prefix is the one that reads as a name at the front of a file name
        assertThat(branch("filters/p.ditaval", "win-", "-x86").label()).isEqualTo("win-");
    }

    @Test
    void aBranchThatNamesNothingFallsBackToItsDitavalFile() {
        assertThat(branch("filters/review.ditaval", null, null).label()).isEqualTo("review.ditaval");
        assertThat(branch("filters/review.ditaval", "  ", "  ").label()).isEqualTo("review.ditaval");
    }

    @Test
    void aBranchWithNoDitavalAtAllStillHasALabel() {
        assertThat(branch(null, null, null).label()).isEqualTo("(branch)");
    }
}
