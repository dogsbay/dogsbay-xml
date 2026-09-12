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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** P1: subjectScheme parsing, closure discovery, and value flattening. */
class SubjectSchemeTest {

    @TempDir Path dir;

    private void write(String name, String content) throws Exception {
        Files.writeString(dir.resolve(name), content);
    }

    private void writeRootMapReferencing(String scheme) throws Exception {
        write("root.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE map PUBLIC "-//OASIS//DTD DITA Map//EN" "map.dtd">
            <map><title>Root</title><mapref href="%s"/></map>
            """.formatted(scheme));
    }

    private void writeScheme(String name) throws Exception {
        write(name, """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE subjectScheme PUBLIC "-//OASIS//DTD DITA Subject Scheme Map//EN" "subjectScheme.dtd">
            <subjectScheme>
              <subjectdef keys="os">
                <subjectdef keys="windows"/>
                <subjectdef keys="mac"/>
                <subjectdef keys="linux"/>
              </subjectdef>
              <subjectdef keys="audiences">
                <subjectdef keys="beginner"/>
                <subjectdef keys="expert"/>
              </subjectdef>
              <enumerationdef>
                <attributedef name="platform"/>
                <subjectdef keyref="os"/>
              </enumerationdef>
              <enumerationdef>
                <attributedef name="audience"/>
                <subjectdef keyref="audiences"/>
              </enumerationdef>
            </subjectScheme>
            """);
    }

    @Test
    void discoversSchemeInClosureAndGovernsBoundAttributes() throws Exception {
        writeScheme("conditions.ditamap");
        writeRootMapReferencing("conditions.ditamap");

        SubjectScheme s = SubjectScheme.fromRootMap(dir.resolve("root.ditamap").toFile());

        assertThat(s.isEmpty()).isFalse();
        assertThat(s.schemeMaps()).hasSize(1);
        assertThat(s.governs("platform")).isTrue();
        assertThat(s.governs("audience")).isTrue();
        assertThat(s.governs("product")).isFalse();
    }

    @Test
    void allowedValuesFlattenTheSubjectSubtree() throws Exception {
        writeScheme("conditions.ditamap");
        writeRootMapReferencing("conditions.ditamap");

        SubjectScheme s = SubjectScheme.fromRootMap(dir.resolve("root.ditamap").toFile());

        // the bound subject's key + all descendants are legal
        assertThat(s.allowedValues("platform"))
            .contains("os", "windows", "mac", "linux")
            .doesNotContain("beginner");
        assertThat(s.isAllowed("platform", "mac")).isTrue();
        assertThat(s.isAllowed("platform", "macos")).isFalse(); // the macos/mac near-miss
        assertThat(s.allowedValues("audience")).contains("beginner", "expert");
    }

    @Test
    void ungovernedAttributeAllowsAnything() throws Exception {
        writeScheme("conditions.ditamap");
        writeRootMapReferencing("conditions.ditamap");
        SubjectScheme s = SubjectScheme.fromRootMap(dir.resolve("root.ditamap").toFile());

        assertThat(s.governs("otherprops")).isFalse();
        assertThat(s.isAllowed("otherprops", "anything")).isTrue(); // ungoverned → no-op
    }

    @Test
    void noSchemeInClosureYieldsEmpty() throws Exception {
        write("root.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE map PUBLIC "-//OASIS//DTD DITA Map//EN" "map.dtd">
            <map><title>Root</title><topicref href="t.dita"/></map>
            """);
        SubjectScheme s = SubjectScheme.fromRootMap(dir.resolve("root.ditamap").toFile());

        assertThat(s.isEmpty()).isTrue();
        assertThat(s.governs("platform")).isFalse();
        assertThat(s.isAllowed("platform", "whatever")).isTrue();
    }

    @Test
    void malformedSchemeIsSkippedNotThrown() throws Exception {
        write("conditions.ditamap", "<subjectScheme><enumerationdef>"); // unclosed
        writeRootMapReferencing("conditions.ditamap");

        SubjectScheme s = SubjectScheme.fromRootMap(dir.resolve("root.ditamap").toFile());
        assertThat(s.isEmpty()).isTrue(); // no throw
    }

    @Test
    void inlineEnumerationdefWithNestedChildrenGoverns() throws Exception {
        // The bound subject is inline (no keyref) and carries its values on nested
        // <subjectdef> children, with no @keys on the wrapper.
        write("conditions.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE subjectScheme PUBLIC "-//OASIS//DTD DITA Subject Scheme Map//EN" "subjectScheme.dtd">
            <subjectScheme>
              <enumerationdef>
                <attributedef name="platform"/>
                <subjectdef>
                  <subjectdef keys="windows"/>
                  <subjectdef keys="mac"/>
                </subjectdef>
              </enumerationdef>
            </subjectScheme>
            """);
        writeRootMapReferencing("conditions.ditamap");

        SubjectScheme s = SubjectScheme.fromRootMap(dir.resolve("root.ditamap").toFile());
        assertThat(s.governs("platform")).isTrue();
        assertThat(s.allowedValues("platform")).containsExactlyInAnyOrder("windows", "mac");
        assertThat(s.isAllowed("platform", "macos")).isFalse();
    }

    @Test
    void flattenKeysHandlesHierarchyAndMultiTokenKeys() {
        SubjectDef leaf = new SubjectDef("mac macos", null, List.of());
        SubjectDef root = new SubjectDef("os", null, List.of(leaf,
            new SubjectDef("linux", null, List.of())));
        assertThat(root.flattenKeys()).containsExactlyInAnyOrder("os", "mac", "macos", "linux");
        assertThat(leaf.keyTokens()).containsExactly("mac", "macos");
    }
}
