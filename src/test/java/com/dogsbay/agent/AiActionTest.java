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

package com.dogsbay.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiActionTest {

    @Test
    void everyActionHasLabelAndPrompt() {
        for (AiAction action : AiAction.values()) {
            assertThat(action.label()).as(action + " label").isNotBlank();
            assertThat(action.systemPrompt()).as(action + " prompt").isNotBlank();
            // prompts instruct "return only ..." so output is directly substitutable
            assertThat(action.systemPrompt().toLowerCase()).contains("return only");
        }
    }

    @Test
    void coversTheCoreEditingActions() {
        assertThat(java.util.Arrays.stream(AiAction.values()).map(AiAction::label).toList())
                .contains("Rewrite", "Simplify", "Fix issues", "Summarize", "Expand");
    }

    @Test
    void ditaActionsAreClassifiedAndGroupedLast() {
        assertThat(AiAction.SHORTDESC.isDita()).isTrue();
        assertThat(AiAction.WRAP_NOTE.isDita()).isTrue();
        assertThat(AiAction.REWRITE.isDita()).isFalse();
        assertThat(AiAction.FIX.isDita()).isFalse();

        // generic edits come before the DITA section so the menu groups cleanly
        AiAction[] all = AiAction.values();
        int firstDita = -1;
        for (int i = 0; i < all.length; i++) {
            if (all[i].isDita()) { firstDita = i; break; }
        }
        assertThat(firstDita).isGreaterThan(0);
        for (int i = firstDita; i < all.length; i++) {
            assertThat(all[i].isDita()).as(all[i] + " should be in the DITA group").isTrue();
        }
    }
}
