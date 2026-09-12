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
        assertThat(scan.entry().id()).isEqualTo("waveform");
        assertThat(scan.entry().term()).isEqualTo("waveform");
        assertThat(scan.entry().surfaceForms()).containsExactly("WF");
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
        assertThat(scan.entry()).isNull();
    }
}
