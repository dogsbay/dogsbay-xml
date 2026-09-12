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

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Parses the <em>real</em> DITA-OT subjectScheme maps (vendored under
 * test/resources, Apache-licensed) — the ones with a real DITA DOCTYPE, no
 * authored {@code @class} (DTD-defaulted), {@code subjectHead}/{@code topicmeta}
 * containers, and {@code <schemeref>} cross-map composition. This is the test that
 * actually validates external-DTD-off parsing against genuine DITA, not a toy.
 */
class RealSubjectSchemeTest {

    private Path resource(String name) throws Exception {
        return Path.of(getClass()
                .getResource("/dita/subjectscheme/" + name).toURI());
    }

    @Test
    void parsesGenuineDitaOtSubjectScheme() throws Exception {
        SubjectScheme s = SubjectScheme.fromRootMap(resource("subjectscheme.ditamap").toFile());

        assertThat(s.isEmpty()).isFalse();
        // bindings defined in the root scheme file
        assertThat(s.governs("audience")).isTrue();
        assertThat(s.governs("platform")).isTrue();
        // deep hierarchy: mac/linux are under os > unix
        assertThat(s.allowedValues("platform"))
            .contains("os", "windows", "unix", "mac", "linux");
        assertThat(s.allowedValues("audience"))
            .contains("audience", "novice", "expert", "xslt-customizer");
        assertThat(s.isAllowed("platform", "mac")).isTrue();
        assertThat(s.isAllowed("platform", "macos")).isFalse();
    }

    @Test
    void followsSchemerefCompositionAcrossMaps() throws Exception {
        // deliveryTarget is defined in subjectscheme-deliverytarget.ditamap, pulled in
        // via <schemeref> from the root — exercises real multi-file scheme composition.
        SubjectScheme s = SubjectScheme.fromRootMap(resource("subjectscheme.ditamap").toFile());
        assertThat(s.schemeMaps().size()).isGreaterThan(1);
        assertThat(s.governs("deliveryTarget")).isTrue();
    }
}
