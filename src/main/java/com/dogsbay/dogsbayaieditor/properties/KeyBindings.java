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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.swing.KeyStroke;

/**
 * The keys as they stand: the catalogue's defaults, with whatever the reader
 * has changed on top.
 *
 * <p>Only the changes are kept. A reader who has changed nothing stores
 * nothing, so the defaults can be improved between releases and reach them.
 * The old design stored every binding, which is how a file came to hold 854
 * entries for 206 commands, two of them repeated 325 times each: writing a
 * binding appended an element rather than replacing one, and reading kept the
 * last, so the duplicates were invisible while the file grew.
 *
 * <p>An override for a command that no longer exists is dropped when it is
 * read. That is what stops a binding outliving the thing it binds.
 */
public final class KeyBindings {

    private final Map<String, KeyBinding> catalogue = new LinkedHashMap<>();
    private final Map<String, KeyStroke> overrides = new LinkedHashMap<>();

    public KeyBindings() {
        this(KeyBindingCatalogue.all());
    }

    public KeyBindings(List<KeyBinding> bindings) {
        for (KeyBinding binding : bindings) {
            catalogue.put(binding.id(), binding);
        }
    }

    /**
     * Add a command a plugin brings with it, so it appears in the key settings
     * beside the built-in ones and can be rebound like them.
     *
     * <p>Registering the same id twice keeps the first, so a plugin reloaded
     * during a session does not double up.
     */
    public void register(KeyBinding binding) {
        catalogue.putIfAbsent(binding.id(), binding);
    }

    /**
     * An editable copy: the same commands, including any a plugin registered,
     * with the same overrides. The settings page works on one of these so that
     * Cancel means cancel — edits made against the live instance survived a
     * Cancel and were committed by the next OK on any page.
     */
    public KeyBindings copy() {
        KeyBindings copy = new KeyBindings(List.of());
        copy.catalogue.putAll(catalogue);
        copy.overrides.putAll(overrides);
        return copy;
    }

    /**
     * Take {@code other}'s overrides as our own, discarding what we held. Used
     * to commit a settings page's copy back onto the instance the menus and the
     * editor read, which otherwise went on serving the keys from startup until
     * a restart.
     */
    public void adopt(KeyBindings other) {
        overrides.clear();
        overrides.putAll(other.overrides);
    }

    /** Every bindable command, defaults and overrides applied. */
    public List<KeyBinding> all() {
        List<KeyBinding> out = new ArrayList<>(catalogue.size());
        for (KeyBinding binding : catalogue.values()) {
            out.add(overrides.containsKey(binding.id())
                    ? new KeyBinding(binding.id(), binding.label(), binding.category(),
                            overrides.get(binding.id()))
                    : binding);
        }
        return out;
    }

    /** The commands in one category, in catalogue order. */
    public List<KeyBinding> inCategory(String category) {
        return all().stream().filter(b -> b.category().equals(category)).toList();
    }

    /** The categories, in the order they first appear. */
    public List<String> categories() {
        return catalogue.values().stream().map(KeyBinding::category).distinct().toList();
    }

    /** The key a command carries now, or empty when it carries none. */
    public Optional<KeyStroke> strokeFor(String id) {
        if (overrides.containsKey(id)) {
            return Optional.ofNullable(overrides.get(id));
        }
        return Optional.ofNullable(catalogue.get(id)).map(KeyBinding::defaultStroke);
    }

    /**
     * Give a command a key, or none at all.
     *
     * <p>Setting the default back is recorded as no override rather than as an
     * override that happens to match: a reader who has not chosen anything
     * should follow the default when it changes.
     *
     * @param stroke the key, or null to bind nothing to this command
     */
    public void bind(String id, KeyStroke stroke) {
        KeyBinding binding = catalogue.get(id);
        if (binding == null) {
            return;   // not a command this build has
        }
        if (java.util.Objects.equals(binding.defaultStroke(), stroke)) {
            overrides.remove(id);
        } else {
            overrides.put(id, stroke);
        }
    }

    /** Forget a reader's choice for one command, restoring its default. */
    public void reset(String id) {
        overrides.remove(id);
    }

    /** Forget every choice. */
    public void resetAll() {
        overrides.clear();
    }

    /** Whether the reader has chosen this command's key themselves. */
    public boolean isOverridden(String id) {
        return overrides.containsKey(id);
    }

    /**
     * What needs storing: one entry per command the reader has changed, keyed
     * by id, with an empty value meaning "bound to nothing".
     */
    public Map<String, String> toStore() {
        Map<String, String> out = new LinkedHashMap<>();
        overrides.forEach((id, stroke) -> out.put(id, stroke == null ? "" : stroke.toString()));
        return out;
    }

    /**
     * Adopt stored choices, ignoring any that name a command this build does
     * not have or a key it cannot parse.
     *
     * @return the ids that were dropped, for the log
     */
    public List<String> load(Map<String, String> stored) {
        overrides.clear();
        List<String> dropped = new ArrayList<>();
        if (stored == null) {
            return dropped;
        }
        stored.forEach((id, value) -> {
            if (!catalogue.containsKey(id)) {
                dropped.add(id);
                return;
            }
            if (value == null || value.isBlank()) {
                overrides.put(id, null);   // deliberately bound to nothing
                return;
            }
            KeyStroke stroke = KeyStroke.getKeyStroke(value);
            if (stroke == null) {
                dropped.add(id);
            } else {
                bind(id, stroke);
            }
        });
        return dropped;
    }

    /** Whether any command currently carries this key. */
    public boolean isTaken(javax.swing.KeyStroke stroke) {
        if (stroke == null) {
            return false;
        }
        return all().stream().anyMatch(b -> stroke.equals(b.defaultStroke()));
    }

    /**
     * Put these keys on the menu items that run the commands.
     *
     * <p>Four lines, where the old install path was a chain of one
     * {@code if (actionName.equals(...))} block per command, each fetching the
     * same menu item and calling the same setter — around fifteen hundred
     * lines that had to be edited by hand whenever a command was added, and
     * usually was not.
     *
     * @param editor the window whose menus carry the shortcuts
     */
    public void applyTo(com.dogsbay.dogsbayaieditor.DogsBayAIEditor editor) {
        if (editor == null) {
            return;
        }
        for (KeyBinding binding : all()) {
            editor.bindAccelerator(binding.id(), binding.defaultStroke());
        }
    }

    /**
     * Commands sharing a key, which is worth showing before it is saved rather
     * than discovering when the wrong one runs.
     *
     * @return each contested key, with the commands that want it
     */
    public Map<KeyStroke, List<String>> conflicts() {
        Map<KeyStroke, List<String>> byStroke = new LinkedHashMap<>();
        for (KeyBinding binding : all()) {
            if (binding.defaultStroke() != null) {
                byStroke.computeIfAbsent(binding.defaultStroke(), k -> new ArrayList<>())
                        .add(binding.id());
            }
        }
        byStroke.values().removeIf(ids -> ids.size() < 2);
        return byStroke;
    }
}
