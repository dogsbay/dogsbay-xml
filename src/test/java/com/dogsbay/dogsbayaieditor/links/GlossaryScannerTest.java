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

import org.junit.jupiter.api.Test;

/** DITA 1.3 glossary extraction: glossentry detection, surface forms, term refs. */
class GlossaryScannerTest {

    @Test
    void detectsGlossentryWithTermAndSurfaceForms() {
        var scan = GlossaryScanner.scan("""
            <glossentry id="waveform">
              <glossterm>waveform</glossterm>
              <glossBody><glossAlt>
                <glossAcronym>WF</glossAcronym>
              </glossAlt></glossBody>
            </glossentry>
            """);
        assertThat(scan.isGlossentry()).isTrue();
        assertThat(scan.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.id()).isEqualTo("waveform");
            assertThat(entry.term()).isEqualTo("waveform");
            assertThat(entry.surfaceForms()).containsExactly("WF");
        });
    }

    @Test
    void collectsAbbreviatedFormAndTermKeyrefs() {
        var scan = GlossaryScanner.scan("""
            <concept id="c"><title>T</title><conbody>
              <p>See <abbreviated-form keyref="waveform"/> and
                 <term keyref="clipping">clipping</term>.</p>
            </conbody></concept>
            """);
        assertThat(scan.isGlossentry()).isFalse();
        assertThat(scan.refs()).extracting(GlossaryScanner.TermRef::element,
                GlossaryScanner.TermRef::keyref)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("abbreviated-form", "waveform"),
                        org.assertj.core.groups.Tuple.tuple("term", "clipping"));
    }

    @Test
    void plainTopicIsNotAGlossentry() {
        var scan = GlossaryScanner.scan("<concept id=\"c\"><title>T</title><conbody/></concept>");
        assertThat(scan.isGlossentry()).isFalse();
        assertThat(scan.entries()).isEmpty();
    }

    @Test
    void readsEveryEntryOfAGlossgroup() {
        // The shape the Audacity tutorial uses for units, and the shape that was invisible: four
        // references into a group came back "not a glossary term" because only a root glossentry
        // counted (CI, 2026-10-12).
        var scan = GlossaryScanner.scan("""
            <glossgroup id="audio-units">
              <title>Units</title>
              <prolog><author>The team</author></prolog>
              <glossentry id="gl-decibel">
                <glossterm><sort-as value="decibel"/>dB (decibel)</glossterm>
                <glossdef>A logarithmic unit.</glossdef>
                <glossBody>
                  <glossSurfaceForm>decibel (dB)</glossSurfaceForm>
                  <glossAlt><glossAbbreviation>dB</glossAbbreviation></glossAlt>
                </glossBody>
              </glossentry>
              <glossentry id="gl-hertz">
                <glossterm>hertz</glossterm>
                <glossdef>Cycles per second.</glossdef>
              </glossentry>
            </glossgroup>
            """);

        assertThat(scan.isGlossentry()).isTrue();
        assertThat(scan.entries()).extracting(GlossaryScanner.GlossEntry::id,
                GlossaryScanner.GlossEntry::term)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("gl-decibel", "dB (decibel)"),
                        org.assertj.core.groups.Tuple.tuple("gl-hertz", "hertz"));
        // Each entry keeps its own forms: the group's first one has an abbreviation, the second none.
        assertThat(scan.entries().get(0).surfaceForms()).containsExactly("dB");
        assertThat(scan.entries().get(1).surfaceForms()).isEmpty();
    }

    @Test
    void followsGroupsDownAsFarAsTheyNest() {
        // glossgroup's content model allows glossgroup, so a glossary can be filed in sections.
        var scan = GlossaryScanner.scan("""
            <glossgroup id="outer"><title>All</title>
              <glossgroup id="inner"><title>Units</title>
                <glossentry id="gl-hertz"><glossterm>hertz</glossterm></glossentry>
              </glossgroup>
              <glossentry id="gl-clipping"><glossterm>clipping</glossterm></glossentry>
            </glossgroup>
            """);
        assertThat(scan.entries()).extracting(GlossaryScanner.GlossEntry::id)
                .containsExactly("gl-hertz", "gl-clipping");
    }

    @Test
    void anEntryInAGroupKeepsTheRefsWrittenInsideIt() {
        // A definition may mention another term, and that reference belongs to the file either way.
        var scan = GlossaryScanner.scan("""
            <glossgroup id="g"><title>All</title>
              <glossentry id="gl-decibel"><glossterm>decibel</glossterm>
                <glossdef>Measured against <abbreviated-form keyref="gl-hertz"/>.</glossdef>
              </glossentry>
            </glossgroup>
            """);
        assertThat(scan.entries()).hasSize(1);
        assertThat(scan.refs()).extracting(GlossaryScanner.TermRef::keyref)
                .containsExactly("gl-hertz");
    }
}
