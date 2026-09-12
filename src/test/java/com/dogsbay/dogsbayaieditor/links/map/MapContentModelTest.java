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

package com.dogsbay.dogsbayaieditor.links.map;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** P2: map content-model nesting rules. */
class MapContentModelTest {

    @Test
    void containersAcceptNestableChildren() {
        assertThat(MapContentModel.canNest("map", "topicref")).isTrue();
        assertThat(MapContentModel.canNest("topicref", "topicref")).isTrue();
        assertThat(MapContentModel.canNest("topichead", "topichead")).isTrue();
        assertThat(MapContentModel.canNest("chapter", "topicref")).isTrue();
        assertThat(MapContentModel.canNest("map", "mapref")).isTrue();
    }

    @Test
    void leavesRejectChildren() {
        assertThat(MapContentModel.canNest("mapref", "topicref")).isFalse();
        assertThat(MapContentModel.canNest("keydef", "topicref")).isFalse();
        assertThat(MapContentModel.canNest("glossref", "topicref")).isFalse();
    }

    @Test
    void bookmapDivisionsNestAtTheirProperPlace() {
        // book divisions go under the bookmap root…
        assertThat(MapContentModel.canNest("bookmap", "chapter")).isTrue();
        assertThat(MapContentModel.canNest("bookmap", "part")).isTrue();
        assertThat(MapContentModel.canNest("bookmap", "frontmatter")).isTrue();
        assertThat(MapContentModel.canNest("bookmap", "backmatter")).isTrue();
        // …a chapter also nests in a part; an appendix groups under appendices
        assertThat(MapContentModel.canNest("part", "chapter")).isTrue();
        assertThat(MapContentModel.canNest("appendices", "appendix")).isTrue();
        assertThat(MapContentModel.canNest("frontmatter", "preface")).isTrue();
        // topicrefs still nest inside a chapter
        assertThat(MapContentModel.canNest("chapter", "topicref")).isTrue();
    }

    @Test
    void bookmapDivisionsRejectedOutOfPlace() {
        // a chapter is not a topicref child, nor valid under a plain map/topichead
        assertThat(MapContentModel.canNest("topicref", "chapter")).isFalse();
        assertThat(MapContentModel.canNest("map", "chapter")).isFalse();
        assertThat(MapContentModel.canNest("topichead", "part")).isFalse();
        assertThat(MapContentModel.canNest("chapter", "chapter")).isFalse();
    }

    @Test
    void unknownAndNullTypesRejected() {
        assertThat(MapContentModel.canNest("reltable", "topicref")).isFalse();
        assertThat(MapContentModel.canNest("topicref", "title")).isFalse();
        assertThat(MapContentModel.canNest(null, "topicref")).isFalse();
        assertThat(MapContentModel.canNest("map", null)).isFalse();
    }
}
