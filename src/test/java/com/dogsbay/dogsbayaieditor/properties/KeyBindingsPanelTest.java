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

package com.dogsbay.dogsbayaieditor.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

/**
 * The key settings page shows the product's commands, not a file's.
 */
class KeyBindingsPanelTest {

    private static final KeyStroke CTRL_S = KeyStroke.getKeyStroke("control S");

    private static KeyBindings bindings() {
        return new KeyBindings(List.of(
            new KeyBinding("File:  Save", "Save the document", "File", CTRL_S),
            new KeyBinding("File:  Open", "Open a document", "File", null),
            new KeyBinding("Edit:  Copy", "Copy the selection", "Edit", null)));
    }

    private static KeyBindingsPanel panel(KeyBindings keys) throws Exception {
        var ref = new java.util.concurrent.atomic.AtomicReference<KeyBindingsPanel>();
        SwingUtilities.invokeAndWait(() -> ref.set(new KeyBindingsPanel(keys)));
        return ref.get();
    }

    @Test
    void everyCommandIsListed() throws Exception {
        KeyBindingsPanel panel = panel(bindings());

        assertThat(panel.rows()).extracting(KeyBinding::id)
            .containsExactly("File:  Save", "File:  Open", "Edit:  Copy");
    }

    @Test
    void theFilterMatchesTheCommandItsGroupAndItsKey() throws Exception {
        KeyBindingsPanel panel = panel(bindings());

        SwingUtilities.invokeAndWait(() -> panel.filterField().setText("copy"));
        assertThat(panel.rows()).extracting(KeyBinding::id).containsExactly("Edit:  Copy");

        SwingUtilities.invokeAndWait(() -> panel.filterField().setText("file"));
        assertThat(panel.rows()).hasSize(2);

        // Searching by the key is how you find what has taken Ctrl+S.
        SwingUtilities.invokeAndWait(() -> panel.filterField().setText("ctrl+s"));
        assertThat(panel.rows()).extracting(KeyBinding::id).containsExactly("File:  Save");
    }

    @Test
    void aKeyAlreadyInUseIsReportedBeforeItIsTaken() throws Exception {
        KeyBindings keys = bindings();
        KeyBindingsPanel panel = panel(keys);

        assertThat(panel.commandUsing(CTRL_S, "File:  Open")).isEqualTo("Save the document");
        // The command that already holds it does not count as a clash with itself.
        assertThat(panel.commandUsing(CTRL_S, "File:  Save")).isNull();
    }

    @Test
    void aChangedCommandIsMarkedAsChanged() throws Exception {
        KeyBindings keys = bindings();
        KeyBindingsPanel panel = panel(keys);

        keys.bind("Edit:  Copy", KeyStroke.getKeyStroke("control C"));
        SwingUtilities.invokeAndWait(panel::refresh);

        assertThat(keys.isOverridden("Edit:  Copy")).isTrue();
        assertThat(keys.isOverridden("File:  Save")).isFalse();
    }

    @Test
    void aModifierOnItsOwnIsNotAShortcut() {
        // Holding Ctrl on the way to Ctrl+S must not be taken as the answer.
        assertThat(KeyStrokeCapture.isModifierOnly(KeyEvent.VK_CONTROL)).isTrue();
        assertThat(KeyStrokeCapture.isModifierOnly(KeyEvent.VK_SHIFT)).isTrue();
        assertThat(KeyStrokeCapture.isModifierOnly(KeyEvent.VK_S)).isFalse();
    }

    @Test
    void aKeyIsDescribedAsAReaderWouldSayIt() {
        assertThat(KeyStrokeCapture.describe(CTRL_S)).contains("S");
        assertThat(KeyStrokeCapture.describe(null)).isEmpty();
    }
}
