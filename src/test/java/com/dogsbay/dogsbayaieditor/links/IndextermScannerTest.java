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

/** DITA 1.3 index-entry extraction: nesting, see/see-also, leaf-only. */
class IndextermScannerTest {

    @Test
    void extractsNestedLeafPathsNotIntermediateNodes() {
        var scan = IndextermScanner.scan("""
            <concept id="c"><title>T</title><prolog><metadata><keywords>
              <indexterm>recording<indexterm>levels</indexterm></indexterm>
              <indexterm>export</indexterm>
            </keywords></metadata></prolog></concept>
            """);
        // the primary "recording" alone is not a leaf — only "recording > levels" + "export"
        assertThat(scan.entries()).containsExactlyInAnyOrder("recording > levels", "export");
    }

    @Test
    void capturesSeeAndSeeAlsoRedirects() {
        var scan = IndextermScanner.scan("""
            <concept id="c"><title>T</title><conbody>
              <p><indexterm>audio<index-see>sound</index-see></indexterm></p>
              <p><indexterm>levels<index-see-also>volume</index-see-also></indexterm></p>
            </conbody></concept>
            """);
        assertThat(scan.sees()).hasSize(2);
        assertThat(scan.sees()).anySatisfy(s -> {
            assertThat(s.from()).isEqualTo("audio");
            assertThat(s.target()).isEqualTo("sound");
            assertThat(s.also()).isFalse();
        });
        assertThat(scan.sees()).anySatisfy(s -> {
            assertThat(s.target()).isEqualTo("volume");
            assertThat(s.also()).isTrue();
        });
    }

    @Test
    void pureIndexSeeIsARedirectNotALocatorEntry() {
        // <index-see> = pure redirect (no page locator); <index-see-also> = supplement.
        var scan = IndextermScanner.scan("""
            <concept id="c"><title>T</title><conbody>
              <p><indexterm>cats<index-see>felines</index-see></indexterm></p>
              <p><indexterm>dogs<index-see-also>canines</index-see-also></indexterm></p>
            </conbody></concept>
            """);
        // "cats" (pure see) is NOT an entry; "dogs" (see-also) keeps its locator entry
        assertThat(scan.entries()).containsExactly("dogs");
        assertThat(scan.sees()).hasSize(2);
    }

    @Test
    void aSortKeyIsNotPartOfTheEntry() {
        // The markup the DITA tutorial uses, from stage 13 onwards. Its sort key was collected as
        // character data inside the indexterm and ended up glued to the front of the display text,
        // so index-audit printed "clippingClipping (distortion)".
        var scan = IndextermScanner.scan("""
            <concept id="c"><title>T</title><prolog><metadata><keywords>
              <indexterm><index-sort-as>clipping</index-sort-as>Clipping (distortion)</indexterm>
            </keywords></metadata></prolog></concept>
            """);
        assertThat(scan.entries()).containsExactly("Clipping (distortion)");
    }

    @Test
    void aSortKeyIsIgnoredAtEveryLevelOfNesting() {
        var scan = IndextermScanner.scan("""
            <concept id="c"><title>T</title><prolog><metadata><keywords>
              <indexterm><index-sort-as>audio</index-sort-as>Audio formats<indexterm>
                <index-sort-as>lossless</index-sort-as>Lossless</indexterm></indexterm>
            </keywords></metadata></prolog></concept>
            """);
        assertThat(scan.entries()).containsExactly("Audio formats > Lossless");
    }

    @Test
    void aSortKeyDoesNotSwallowTheTextAroundIt() {
        // The sort key may sit after the text as well as before it, and closing it must put the
        // collecting back where it was rather than leaving it off.
        var scan = IndextermScanner.scan("""
            <concept id="c"><title>T</title><prolog><metadata><keywords>
              <indexterm>Clipping<index-sort-as>clipping</index-sort-as> (distortion)</indexterm>
            </keywords></metadata></prolog></concept>
            """);
        assertThat(scan.entries()).containsExactly("Clipping (distortion)");
    }

    @Test
    void aSortKeyOnARedirectLeavesTheRedirectIntact() {
        var scan = IndextermScanner.scan("""
            <concept id="c"><title>T</title><conbody>
              <p><indexterm><index-sort-as>compression</index-sort-as>Compression<index-see>codecs</index-see></indexterm></p>
            </conbody></concept>
            """);
        assertThat(scan.sees()).hasSize(1);
        assertThat(scan.sees().get(0).from()).isEqualTo("Compression");
        assertThat(scan.sees().get(0).target()).isEqualTo("codecs");
        assertThat(scan.entries()).as("a pure see is a redirect, not a locator entry").isEmpty();
    }

    @Test
    void noIndextermsYieldsEmptyScan() {
        var scan = IndextermScanner.scan("<concept id=\"c\"><title>T</title><conbody/></concept>");
        assertThat(scan.entries()).isEmpty();
        assertThat(scan.sees()).isEmpty();
    }
}
