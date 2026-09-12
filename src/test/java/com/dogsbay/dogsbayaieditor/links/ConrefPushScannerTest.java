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

/** DITA 1.3 conref-push extraction with sibling-pairing checks. */
class ConrefPushScannerTest {

    @Test
    void wellFormedPushBeforeWithMarkHasNoPairingIssue() {
        var pushes = ConrefPushScanner.scan("""
            <concept id="c"><title>T</title><conbody>
              <section>
                <p conaction="mark" conref="other.dita#o/p1"/>
                <p conaction="pushbefore">Inserted before.</p>
              </section>
            </conbody></concept>
            """);
        assertThat(pushes).hasSize(2);
        assertThat(pushes).allSatisfy(p -> assertThat(p.pairing()).isNull());
        assertThat(pushes).anySatisfy(p -> {
            assertThat(p.action()).isEqualTo("mark");
            assertThat(p.carriesTarget()).isTrue();
            assertThat(p.conref()).isEqualTo("other.dita#o/p1");
        });
    }

    @Test
    void pushBeforeWithoutMarkSiblingIsFlagged() {
        var pushes = ConrefPushScanner.scan("""
            <concept id="c"><title>T</title><conbody>
              <section><p conaction="pushafter">Orphan push.</p></section>
            </conbody></concept>
            """);
        assertThat(pushes).hasSize(1);
        assertThat(pushes.get(0).pairing()).contains("no sibling conaction=\"mark\"");
    }

    @Test
    void pushReplaceWithoutTargetIsFlagged() {
        var pushes = ConrefPushScanner.scan("""
            <concept id="c"><title>T</title><conbody>
              <section><p conaction="pushreplace">No target.</p></section>
            </conbody></concept>
            """);
        assertThat(pushes.get(0).pairing()).contains("no @conref");
    }
}
