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

package com.dogsbay.dogsbayaieditor.links.scheme;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** P2: the validation engine — ConditionChecker + DitavalConditions reader. */
class ConditionCheckerTest {

    @TempDir Path dir;

    /** platform ∈ {os,windows,mac,linux}; audience ∈ {novice,expert}. */
    private SubjectScheme scheme() {
        return SubjectScheme.of(List.of(
                new EnumerationBinding("platform", "os", Set.of("os", "windows", "mac", "linux")),
                new EnumerationBinding("audience", "aud", Set.of("novice", "expert"))),
            Set.of());
    }

    @Test
    void flagsUnknownValueWithNearMissSuggestion() {
        List<ConditionFinding> f = new ConditionChecker(scheme()).check("platform", "macos");
        assertThat(f).hasSize(1);
        assertThat(f.get(0).attribute()).isEqualTo("platform");
        assertThat(f.get(0).value()).isEqualTo("macos");
        assertThat(f.get(0).suggestion()).isEqualTo("mac");
        assertThat(f.get(0).message()).contains("macos").contains("mac");
    }

    @Test
    void allowedValueProducesNoFinding() {
        assertThat(new ConditionChecker(scheme()).check("platform", "mac")).isEmpty();
    }

    @Test
    void ungovernedAttributeProducesNoFinding() {
        assertThat(new ConditionChecker(scheme()).check("product", "anything")).isEmpty();
    }

    @Test
    void multiTokenValueFlagsOnlyTheBadToken() {
        List<ConditionFinding> f = new ConditionChecker(scheme()).check("platform", "mac windoze linux");
        assertThat(f).hasSize(1);
        assertThat(f.get(0).value()).isEqualTo("windoze");
        assertThat(f.get(0).suggestion()).isEqualTo("windows");
    }

    @Test
    void groupSyntaxIsNotFalselyFlagged() {
        // @otherprops group syntax — unchecked in v1, must not be reported as invalid
        assertThat(new ConditionChecker(scheme()).check("platform", "group(mac nonsense)")).isEmpty();
    }

    @Test
    void wildlyDifferentValueGetsNoSuggestion() {
        List<ConditionFinding> f = new ConditionChecker(scheme()).check("platform", "zzzzzzzz");
        assertThat(f).hasSize(1);
        assertThat(f.get(0).suggestion()).isNull();
    }

    @Test
    void shortUnrelatedTokenGetsNoSuggestion() {
        // "qt" is distance 2 from "os" — but that's a total rewrite (== length),
        // not a near-miss, so it must not be offered as a suggestion.
        List<ConditionFinding> f = new ConditionChecker(scheme()).check("platform", "qt");
        assertThat(f).hasSize(1);
        assertThat(f.get(0).suggestion()).isNull();
    }

    @Test
    void emptySchemeNeverFlags() {
        assertThat(new ConditionChecker(SubjectScheme.empty()).check("platform", "macos")).isEmpty();
        assertThat(new ConditionChecker(null).check("platform", "macos")).isEmpty();
    }

    @Test
    void ditavalReaderExtractsConditionsWithLines() throws Exception {
        Path d = dir.resolve("filter.ditaval");
        Files.writeString(d, """
            <?xml version="1.0" encoding="UTF-8"?>
            <val>
              <prop att="platform" val="mac" action="include"/>
              <prop att="platform" val="macos" action="exclude"/>
              <prop action="exclude"/>
            </val>
            """);
        List<DitavalConditions.Condition> conds = DitavalConditions.read(d.toFile());

        assertThat(conds).hasSize(2); // the att/val-less catch-all prop is skipped
        assertThat(conds.get(0).att()).isEqualTo("platform");
        assertThat(conds.get(0).val()).isEqualTo("mac");
        assertThat(conds.get(0).line()).isEqualTo(3);
        assertThat(conds.get(1).val()).isEqualTo("macos");
        assertThat(conds.get(1).line()).isEqualTo(4);

        // wiring DitavalConditions → ConditionChecker flags the bad ditaval value
        ConditionChecker checker = new ConditionChecker(scheme());
        var bad = conds.stream()
                .flatMap(c -> checker.check(c.att(), c.val()).stream())
                .toList();
        assertThat(bad).hasSize(1);
        assertThat(bad.get(0).value()).isEqualTo("macos");
    }
}
