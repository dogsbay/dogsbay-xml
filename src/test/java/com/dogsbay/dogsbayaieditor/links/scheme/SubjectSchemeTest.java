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
    void bindingsAreOrderedByAttributeName() throws Exception {
        // byAttribute is a Map.copyOf, and Java's immutable maps iterate in an order randomised per process —
        // so the Controlled Values view listed @audience first for one reader and @platform first for the next.
        // Six attributes, declared in reverse, because two of them could come out alphabetical by luck and a
        // test that passes by luck is how this got here.
        List<String> alphabetical = List.of("alpha", "bravo", "charlie", "delta", "echo", "foxtrot");
        StringBuilder scheme = new StringBuilder(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE subjectScheme PUBLIC \"-//OASIS//DTD DITA Subject Scheme Map//EN\""
                + " \"subjectScheme.dtd\">\n"
                + "<subjectScheme>\n"
                + "  <subjectdef keys=\"vals\"><subjectdef keys=\"yes\"/></subjectdef>\n");
        for (String attribute : alphabetical.reversed()) {
            scheme.append("  <enumerationdef><attributedef name=\"").append(attribute)
                    .append("\"/><subjectdef keyref=\"vals\"/></enumerationdef>\n");
        }
        write("many.ditamap", scheme.append("</subjectScheme>\n").toString());
        writeRootMapReferencing("many.ditamap");

        SubjectScheme s = SubjectScheme.fromRootMap(dir.resolve("root.ditamap").toFile());

        assertThat(s.bindings()).extracting(EnumerationBinding::attribute)
                .containsExactlyElementsOf(alphabetical);
    }

    @Test
    void allowedValuesFlattenTheSubjectSubtree() throws Exception {
        writeScheme("conditions.ditamap");
        writeRootMapReferencing("conditions.ditamap");

        SubjectScheme s = SubjectScheme.fromRootMap(dir.resolve("root.ditamap").toFile());

        // the bound subject's descendants are legal; the subject itself names the dimension and is not one of its
        // own values — platform="os" is rejected by a real build, so it is rejected here
        assertThat(s.allowedValues("platform"))
            .contains("windows", "mac", "linux")
            .doesNotContain("os", "beginner");
        assertThat(s.isAllowed("platform", "os")).isFalse();
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
    void anAnonymousWrapperInsideAnEnumerationdefBindsNothing() throws Exception {
        // The wrapper has neither @keyref nor @keys, so it names no subject. DITA-OT resolves an enumerationdef's
        // child by name against the subjects the scheme defines, and a child with no name resolves to nothing — so
        // the attribute stays ungoverned. Binding it here would have us reject values a real build accepts, which is
        // the worse of the two mistakes.
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
        assertThat(s.governs("platform")).isFalse();
        assertThat(s.isAllowed("platform", "anything")).isTrue();
    }

    @Test
    void theInlineShorthandBindsTheSubtreeItNames() throws Exception {
        // The everyday form: the subject is written inside the enumerationdef with @keys and its values nested under
        // it. DITA-OT registers it like any other subject and then resolves the binding against that registry, so it
        // governs — with the wrapper's own key being the category rather than a value.
        write("conditions.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE subjectScheme PUBLIC "-//OASIS//DTD DITA Subject Scheme Map//EN" "subjectScheme.dtd">
            <subjectScheme>
              <enumerationdef>
                <attributedef name="platform"/>
                <subjectdef keys="os">
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
        assertThat(s.isAllowed("platform", "os")).isFalse();
    }

    @Test
    void theBareShorthandBindsTheSubtreeDefinedAbove() throws Exception {
        // <subjectdef keys="os"/> inside the enumerationdef is a reference to the subject defined above, not a
        // definition of a one-value subject called "os". Read the other way it binds the single value "os" — which
        // is close to the opposite answer, and the one value a real build rejects.
        write("conditions.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE subjectScheme PUBLIC "-//OASIS//DTD DITA Subject Scheme Map//EN" "subjectScheme.dtd">
            <subjectScheme>
              <subjectdef keys="os">
                <subjectdef keys="windows"/>
                <subjectdef keys="linux"/>
              </subjectdef>
              <enumerationdef>
                <attributedef name="platform"/>
                <subjectdef keys="os"/>
              </enumerationdef>
            </subjectScheme>
            """);
        writeRootMapReferencing("conditions.ditamap");

        SubjectScheme s = SubjectScheme.fromRootMap(dir.resolve("root.ditamap").toFile());
        assertThat(s.allowedValues("platform")).containsExactlyInAnyOrder("windows", "linux");
        assertThat(s.isAllowed("platform", "os")).isFalse();
    }

    @Test
    void anEnumerationdefNamingASubjectTheSchemeNeverDefinesGovernsNothing() throws Exception {
        write("conditions.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE subjectScheme PUBLIC "-//OASIS//DTD DITA Subject Scheme Map//EN" "subjectScheme.dtd">
            <subjectScheme>
              <enumerationdef>
                <attributedef name="platform"/>
                <subjectdef keyref="nowhere"/>
              </enumerationdef>
            </subjectScheme>
            """);
        writeRootMapReferencing("conditions.ditamap");

        SubjectScheme s = SubjectScheme.fromRootMap(dir.resolve("root.ditamap").toFile());
        assertThat(s.governs("platform")).isFalse();
    }

    @Test
    void flattenKeysHandlesHierarchyAndMultiTokenKeys() {
        SubjectDef leaf = new SubjectDef("mac macos", null, List.of());
        SubjectDef root = new SubjectDef("os", null, List.of(leaf,
            new SubjectDef("linux", null, List.of())));
        assertThat(root.flattenKeys()).containsExactlyInAnyOrder("os", "mac", "macos", "linux");
        assertThat(leaf.keyTokens()).containsExactly("mac", "macos");
    }

    /** A scheme file whose body is whatever is passed in. */
    private SubjectScheme scheme(String body) throws Exception {
        write("conditions.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE subjectScheme PUBLIC "-//OASIS//DTD DITA Subject Scheme Map//EN" "subjectScheme.dtd">
            <subjectScheme>
            %s
            </subjectScheme>
            """.formatted(body));
        writeRootMapReferencing("conditions.ditamap");
        return SubjectScheme.fromRootMap(dir.resolve("root.ditamap").toFile());
    }

    @Test
    void twoBindingsForOneAttributeBothCount() throws Exception {
        // A scheme that governs one attribute from several places writes exactly that — DITA-OT accumulates them into
        // one value set. Keeping only the last would reject every value but the ones in it, which is how the scheme
        // shipped with DITA-OT's own documentation (nine bindings for outputclass) would have had eight discarded.
        SubjectScheme s = scheme("""
              <subjectdef keys="os"><subjectdef keys="linux"/></subjectdef>
              <subjectdef keys="db"><subjectdef keys="postgres"/></subjectdef>
              <enumerationdef><attributedef name="platform"/><subjectdef keyref="os"/></enumerationdef>
              <enumerationdef><attributedef name="platform"/><subjectdef keyref="db"/></enumerationdef>
            """);

        assertThat(s.allowedValues("platform")).containsExactlyInAnyOrder("linux", "postgres");
    }

    @Test
    void aSubtreeNamedTwiceDoesNotLetItsOtherNameBackInAsAValue() throws Exception {
        // Both names are the category under different spellings. Binding the subtree a second time under its alias
        // would add that alias as a value — the exact fault the category exclusion exists to prevent, by the back
        // door. DITA-OT skips a subtree it has already bound for the attribute.
        SubjectScheme s = scheme("""
              <subjectdef keys="os operating-system"><subjectdef keys="linux"/></subjectdef>
              <enumerationdef><attributedef name="platform"/>
                <subjectdef keyref="os"/><subjectdef keyref="operating-system"/></enumerationdef>
            """);

        assertThat(s.isAllowed("platform", "os")).isFalse();
        assertThat(s.allowedValues("platform")).contains("linux");
    }

    @Test
    void aSubjectsOtherNamesAreValuesWhenItIsNotTheCategory() throws Exception {
        // mac and macos are two names for one subject, and both are legal values; only the bound key is excluded
        SubjectScheme s = scheme("""
              <subjectdef keys="os"><subjectdef keys="mac macos"/></subjectdef>
              <enumerationdef><attributedef name="platform"/><subjectdef keyref="os"/></enumerationdef>
            """);

        assertThat(s.allowedValues("platform")).containsExactlyInAnyOrder("mac", "macos");
    }

    @Test
    void aKeyrefIntoAnotherSchemeFileBindsNothing() throws Exception {
        // DITA-OT resolves a binding against the subjects in its own scheme document. Reaching across files would
        // govern an attribute a real build leaves alone, and every value an author used would then be reported as a
        // violation no build agrees with.
        write("subjects.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE subjectScheme PUBLIC "-//OASIS//DTD DITA Subject Scheme Map//EN" "subjectScheme.dtd">
            <subjectScheme>
              <subjectdef keys="os"><subjectdef keys="linux"/></subjectdef>
            </subjectScheme>
            """);
        write("bindings.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE subjectScheme PUBLIC "-//OASIS//DTD DITA Subject Scheme Map//EN" "subjectScheme.dtd">
            <subjectScheme>
              <enumerationdef><attributedef name="platform"/><subjectdef keyref="os"/></enumerationdef>
            </subjectScheme>
            """);
        write("root.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE map PUBLIC "-//OASIS//DTD DITA Map//EN" "map.dtd">
            <map><title>Root</title><mapref href="subjects.ditamap"/><mapref href="bindings.ditamap"/></map>
            """);

        SubjectScheme s = SubjectScheme.fromRootMap(dir.resolve("root.ditamap").toFile());

        assertThat(s.governs("platform")).isFalse();
    }

    @Test
    void theFirstAttributedefIsTheOneBound() throws Exception {
        SubjectScheme s = scheme("""
              <subjectdef keys="os"><subjectdef keys="linux"/></subjectdef>
              <enumerationdef>
                <attributedef name="platform"/><attributedef name="product"/>
                <subjectdef keyref="os"/></enumerationdef>
            """);

        assertThat(s.governs("platform")).isTrue();
        assertThat(s.governs("product")).isFalse();
    }

    @Test
    void aSubjectGroupedUnderAHeadingStillBelongsToItsParent() throws Exception {
        SubjectScheme s = scheme("""
              <subjectdef keys="os">
                <subjectHead><subjectdef keys="linux"/></subjectHead>
              </subjectdef>
              <enumerationdef><attributedef name="platform"/><subjectdef keyref="os"/></enumerationdef>
            """);

        assertThat(s.allowedValues("platform")).containsExactly("linux");
    }

    @Test
    void aCategoryWithNothingUnderItGovernsNothing() throws Exception {
        // Its only key is the category, which is not one of its own values, so the vocabulary would be empty. DITA-OT
        // would then reject every value of the attribute; a vocabulary of nothing is not what a scheme author meant,
        // and the cost of guessing wrong there falls on every document in the project.
        SubjectScheme s = scheme("""
              <subjectdef keys="os"/>
              <enumerationdef><attributedef name="platform"/><subjectdef keyref="os"/></enumerationdef>
            """);

        assertThat(s.governs("platform")).isFalse();
        assertThat(s.isAllowed("platform", "anything")).isTrue();
    }

}
