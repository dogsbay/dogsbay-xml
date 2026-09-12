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

/** DITA 1.3 @chunk extraction. */
class ChunkScannerTest {

    @Test
    void collectsChunkUsagesWithElementAndValue() {
        var uses = ChunkScanner.scan("""
            <map><title>M</title>
              <topicref href="a.dita" chunk="to-content"/>
              <topicref href="b.dita" chunk="by-topic select-topic"/>
              <topicref href="c.dita"/>
            </map>
            """);
        assertThat(uses).hasSize(2);
        assertThat(uses).extracting(ChunkScanner.ChunkUse::element, ChunkScanner.ChunkUse::value)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("topicref", "to-content"),
                        org.assertj.core.groups.Tuple.tuple("topicref", "by-topic select-topic"));
    }

    @Test
    void blankChunkIsIgnoredAndEmptyMapYieldsNothing() {
        assertThat(ChunkScanner.scan("<map><topicref href=\"a.dita\" chunk=\"  \"/></map>"))
                .isEmpty();
        assertThat(ChunkScanner.scan("<map><title>M</title></map>")).isEmpty();
    }
}
