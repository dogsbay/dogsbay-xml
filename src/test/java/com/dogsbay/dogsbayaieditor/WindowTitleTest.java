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
package com.dogsbay.dogsbayaieditor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** What the title bar shows, without needing a window to show it in. */
class WindowTitleTest {

    @AfterEach
    void restore() {
        DogsBayAIEditor.setTitleOverride(null);
    }

    @Test
    void usuallyNamesTheOpenDocument() {
        assertThat(DogsBayAIEditor.windowTitle(null, "topics/install.dita"))
                .isEqualTo("DogsBay XML - [topics/install.dita]");
        assertThat(DogsBayAIEditor.windowTitle(null, null)).isEqualTo("DogsBay XML");
    }

    @Test
    void aFixedTitleReplacesItEntirely() {
        // the document's name is a path on someone's disk, which is the thing a recording must not show
        assertThat(DogsBayAIEditor.windowTitle("Audacity User Guide", "topics/install.dita"))
                .isEqualTo("Audacity User Guide");
        assertThat(DogsBayAIEditor.windowTitle("Audacity User Guide", null)).isEqualTo("Audacity User Guide");
    }

    @Test
    void nothingAndBlankBothMeanTheUsualTitle() {
        DogsBayAIEditor.setTitleOverride("  ");
        assertThat(DogsBayAIEditor.titleOverride()).isNull();
        DogsBayAIEditor.setTitleOverride("Named");
        assertThat(DogsBayAIEditor.titleOverride()).isEqualTo("Named");
        DogsBayAIEditor.setTitleOverride(null);
        assertThat(DogsBayAIEditor.titleOverride()).isNull();
    }
}
