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
package com.dogsbay.xml.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.dogsbay.xml.review.Splices.Splice;

class SplicesTest {

    @Test
    void appliesFromTheEndAndReplacementsBeforeInsertionsAtTheSameOffset() {
        String s = "abcdef";
        assertThat(Splices.apply(s, List.of(new Splice(1, 2, "X"), new Splice(4, 0, "-"))))
                .isEqualTo("aXd-ef");
        assertThat(Splices.apply(s, List.of(new Splice(2, 0, "[old]"), new Splice(2, 1, "C"))))
                .as("the insertion lands in front of the replaced text").isEqualTo("ab[old]Cdef");
    }

    @Test
    void aSpliceInsideAnotherSplicesDeletionIsDropped() {
        String s = "<a><b>x</b></a>";
        List<Splice> both = List.of(new Splice(3, 8, ""), new Splice(6, 1, "y"));
        assertThat(Splices.apply(s, both)).isEqualTo("<a></a>");
    }
}
