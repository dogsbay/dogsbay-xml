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

package com.dogsbay.xml.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import javax.swing.JMenuItem;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Tests for the editor popup contributor registry. */
class EditorPopupContributorsTest {

    @AfterEach
    void cleanup() {
        EditorPopupContributors.clear();
    }

    @Test
    void collect_returnsItemsInRegistrationOrder() {
        EditorPopupContributors.register((editor, offset) ->
                List.of(new JMenuItem("first")));
        EditorPopupContributors.register((editor, offset) ->
                List.of(new JMenuItem("second")));

        List<JMenuItem> items = EditorPopupContributors.collect(null, 0);
        assertEquals(2, items.size());
        assertEquals("first", items.get(0).getText());
        assertEquals("second", items.get(1).getText());
    }

    @Test
    void unregister_removesContributor() {
        EditorPopupContributors.Contributor c = (editor, offset) ->
                List.of(new JMenuItem("x"));
        EditorPopupContributors.register(c);
        assertEquals(1, EditorPopupContributors.collect(null, 0).size());

        EditorPopupContributors.unregister(c);
        assertTrue(EditorPopupContributors.collect(null, 0).isEmpty());
    }

    @Test
    void throwingContributor_isIsolated() {
        EditorPopupContributors.register((editor, offset) -> {
            throw new IllegalStateException("boom");
        });
        EditorPopupContributors.register((editor, offset) ->
                List.of(new JMenuItem("survivor")));

        List<JMenuItem> items = EditorPopupContributors.collect(null, 0);
        assertEquals(1, items.size());
        assertEquals("survivor", items.get(0).getText());
    }

    @Test
    void nullContribution_treatedAsEmpty() {
        EditorPopupContributors.register((editor, offset) -> null);
        assertTrue(EditorPopupContributors.collect(null, 0).isEmpty());
    }

    @Test
    void doubleRegister_addedOnce() {
        EditorPopupContributors.Contributor c = (editor, offset) ->
                List.of(new JMenuItem("once"));
        EditorPopupContributors.register(c);
        EditorPopupContributors.register(c);
        assertEquals(1, EditorPopupContributors.collect(null, 0).size());
    }
}
