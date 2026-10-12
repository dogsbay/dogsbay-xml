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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.JMenuItem;
import javax.swing.text.JTextComponent;

/**
 * Extension point for the editor's right-click menu. Plugins register a
 * {@link Contributor}; each time the popup is about to show,
 * {@link Editor#showPopupMenu} collects contributed items (computed from the
 * click offset) and prepends them.
 *
 * <p>This is a static registry rather than a service because the editor
 * component ({@code xml.editor}) cannot depend on the plugin API. Plugins
 * should register through
 * {@code UIService.addEditorPopupContributor} rather than directly.
 */
public final class EditorPopupContributors {

    /** Contributes menu items for the editor popup at a given offset. */
    public interface Contributor {
        /**
         * @param editor the editor pane being right-clicked
         * @param offset document offset of the click
         * @return items to show (possibly empty, never null)
         */
        List<JMenuItem> contribute(JTextComponent editor, int offset);
    }

    private static final CopyOnWriteArrayList<Contributor> CONTRIBUTORS =
            new CopyOnWriteArrayList<>();

    private EditorPopupContributors() {
    }

    public static void register(Contributor contributor) {
        if (contributor != null) {
            CONTRIBUTORS.addIfAbsent(contributor);
        }
    }

    public static void unregister(Contributor contributor) {
        CONTRIBUTORS.remove(contributor);
    }

    /**
     * All contributed items for this popup, in registration order. A failing
     * contributor is isolated — the others still contribute.
     */
    public static List<JMenuItem> collect(JTextComponent editor, int offset) {
        List<JMenuItem> items = new ArrayList<>();
        for (Contributor contributor : CONTRIBUTORS) {
            try {
                List<JMenuItem> contributed = contributor.contribute(editor, offset);
                if (contributed != null) {
                    items.addAll(contributed);
                }
            } catch (Exception e) {
                // one broken contributor must not kill the popup
            }
        }
        return items;
    }

    /** Test hook. */
    static void clear() {
        CONTRIBUTORS.clear();
    }
}
