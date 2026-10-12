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

package com.dogsbay.dogsbayaieditor.ditaproject;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class BatchResultTest {

    @Test
    void cleanWhenNothingFailed() {
        BatchResult<String> r = BatchResult.capped(10, 10, 0, List.of(), 200);
        assertThat(r.isClean()).isTrue();
        assertThat(r.truncated()).isZero();
        assertThat(r.findings()).isEmpty();
    }

    @Test
    void keepsAllFindingsUnderCap() {
        BatchResult<String> r = BatchResult.capped(3, 1, 2, List.of("a", "b"), 200);
        assertThat(r.isClean()).isFalse();
        assertThat(r.findings()).containsExactly("a", "b");
        assertThat(r.truncated()).isZero();
    }

    @Test
    void capsFindingsAndRecordsOverflow() {
        List<Integer> many = java.util.stream.IntStream.range(0, 250).boxed().toList();
        BatchResult<Integer> r = BatchResult.capped(250, 0, 250, many, 200);
        assertThat(r.findings()).hasSize(200);
        assertThat(r.findings().get(0)).isZero();
        assertThat(r.findings().get(199)).isEqualTo(199);
        assertThat(r.truncated()).isEqualTo(50);
    }

    @Test
    void findingsAreDefensivelyCopiedAndImmutable() {
        var src = new java.util.ArrayList<>(List.of("x"));
        BatchResult<String> r = BatchResult.capped(1, 0, 1, src, 200);
        src.add("y"); // must not affect the result
        assertThat(r.findings()).containsExactly("x");
    }
}
