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

import java.awt.Toolkit;
import java.awt.event.InputEvent;

import javax.swing.KeyStroke;

/**
 * One bindable command: what it is called, where it sits in the settings list,
 * and the key it carries unless the reader says otherwise.
 *
 * @param id           the command's identity, stable across releases
 * @param label        what the settings list calls it
 * @param category     the group it appears under
 * @param defaultStroke the key it carries out of the box, or null for none
 */
public record KeyBinding(String id, String label, String category, KeyStroke defaultStroke) {

    public KeyBinding {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("a binding needs an id");
        }
    }

    /**
     * A keystroke from the catalogue's shorthand, or null when the command
     * carries no key by default.
     *
     * <p>{@code CTRL} means the platform's menu key, so a Mac gets Command
     * where Linux and Windows get Control. The old tables carried two parallel
     * copies of every default for exactly this, one guarded by a check for
     * {@code mrj.version} — a property that has not existed since Apple's MRJ.
     */
    public static KeyStroke stroke(String mask, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (mask == null) {
            // A key with no modifier — Tab, Escape, F5. Refusing these lost
            // Tab-to-indent its default while Shift+Tab kept unindent, leaving
            // the pair lopsided, and no bare key could ever be a default.
            return KeyStroke.getKeyStroke(key);
        }
        int modifiers = 0;
        for (String part : mask.split("_")) {
            modifiers |= switch (part) {
                case "CTRL" -> menuMask();
                case "SHIFT" -> InputEvent.SHIFT_DOWN_MASK;
                case "ALT" -> InputEvent.ALT_DOWN_MASK;
                default -> 0;
            };
        }
        KeyStroke named = KeyStroke.getKeyStroke(key.length() == 1
                ? "pressed " + key.toUpperCase(java.util.Locale.ROOT)
                : "pressed " + key.toUpperCase(java.util.Locale.ROOT));
        if (named == null) {
            return null;
        }
        return KeyStroke.getKeyStroke(named.getKeyCode(), modifiers);
    }

    /** The platform's menu modifier: Command on a Mac, Control elsewhere. */
    static int menuMask() {
        try {
            return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (Throwable headless) {
            return InputEvent.CTRL_DOWN_MASK;
        }
    }
}
