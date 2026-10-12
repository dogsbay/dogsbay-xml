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

import java.util.List;
import java.util.Map;

import javax.swing.KeyStroke;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The key settings used to be whatever a saved file contained, which failed
 * both ways: a command that had left the product stayed on offer, and one that
 * arrived could never be bound. The list now comes from the product and the
 * file holds only what the reader changed.
 */
class KeyBindingsTest {

    private static final KeyStroke CTRL_S = KeyStroke.getKeyStroke("control S");
    private static final KeyStroke CTRL_T = KeyStroke.getKeyStroke("control T");

    private static KeyBindings bindings() {
        return new KeyBindings(List.of(
            new KeyBinding("File:  Save", "Save the document", "File", CTRL_S),
            new KeyBinding("File:  Open", "Open a document", "File", null),
            new KeyBinding("Edit:  Copy", "Copy", "Edit", null)));
    }

    @Test
    void aCommandCarriesItsDefaultUntilTheReaderSaysOtherwise() {
        KeyBindings keys = bindings();

        assertThat(keys.strokeFor("File:  Save")).contains(CTRL_S);
        assertThat(keys.strokeFor("File:  Open")).isEmpty();
        assertThat(keys.isOverridden("File:  Save")).isFalse();
        assertThat(keys.toStore()).isEmpty();   // nothing changed, nothing stored
    }

    @Test
    void onlyTheChangesAreStored() {
        KeyBindings keys = bindings();

        keys.bind("File:  Open", CTRL_T);

        assertThat(keys.toStore()).containsExactly(Map.entry("File:  Open", CTRL_T.toString()));
        assertThat(keys.strokeFor("File:  Open")).contains(CTRL_T);
    }

    @Test
    void choosingTheDefaultIsNotAChoice() {
        KeyBindings keys = bindings();

        keys.bind("File:  Save", CTRL_S);

        // Storing it would freeze today's default: a reader who never chose
        // anything should follow the default when it improves.
        assertThat(keys.toStore()).isEmpty();
        assertThat(keys.isOverridden("File:  Save")).isFalse();
    }

    @Test
    void aCommandCanBeBoundToNothing() {
        KeyBindings keys = bindings();

        keys.bind("File:  Save", null);

        assertThat(keys.strokeFor("File:  Save")).isEmpty();
        assertThat(keys.toStore()).containsExactly(Map.entry("File:  Save", ""));
    }

    @Test
    void oneEntryPerCommand() {
        KeyBindings keys = bindings();

        keys.bind("File:  Open", CTRL_T);
        keys.bind("File:  Open", CTRL_S);
        keys.bind("File:  Open", CTRL_T);

        // The old store appended an element per write, which is how a file came
        // to hold the same command 325 times.
        assertThat(keys.toStore()).hasSize(1);
        assertThat(keys.strokeFor("File:  Open")).contains(CTRL_T);
    }

    @Test
    void aBindingForACommandThatIsGoneIsDropped() {
        KeyBindings keys = bindings();

        List<String> dropped = keys.load(Map.of(
            "File:  Open", CTRL_T.toString(),
            "Debugger:  StepInto", "control F7"));

        assertThat(dropped).containsExactly("Debugger:  StepInto");
        assertThat(keys.toStore()).containsOnlyKeys("File:  Open");
    }

    @Test
    void anUnreadableKeyIsDroppedRatherThanGuessed() {
        KeyBindings keys = bindings();

        List<String> dropped = keys.load(Map.of("File:  Open", "not a keystroke"));

        assertThat(dropped).containsExactly("File:  Open");
        assertThat(keys.strokeFor("File:  Open")).isEmpty();
    }

    @Test
    void bindingSomethingTheBuildDoesNotHaveIsIgnored() {
        KeyBindings keys = bindings();

        keys.bind("Debugger:  StepInto", CTRL_T);

        assertThat(keys.toStore()).isEmpty();
    }

    @Test
    void resettingRestoresTheDefault() {
        KeyBindings keys = bindings();
        keys.bind("File:  Save", CTRL_T);

        keys.reset("File:  Save");

        assertThat(keys.strokeFor("File:  Save")).contains(CTRL_S);
        assertThat(keys.toStore()).isEmpty();
    }

    @Test
    void theListIsGroupedTheWayTheSettingsShowIt() {
        KeyBindings keys = bindings();

        assertThat(keys.categories()).containsExactly("File", "Edit");
        assertThat(keys.inCategory("File")).extracting(KeyBinding::id)
            .containsExactly("File:  Save", "File:  Open");
    }

    @Test
    void twoCommandsWantingOneKeyAreReported() {
        KeyBindings keys = new KeyBindings(List.of(
            new KeyBinding("File:  Save", "Save", "File", CTRL_S),
            new KeyBinding("Edit:  Send", "Send", "Edit", CTRL_S),
            new KeyBinding("Edit:  Copy", "Copy", "Edit", CTRL_T)));

        assertThat(keys.conflicts()).containsOnlyKeys(CTRL_S);
        assertThat(keys.conflicts().get(CTRL_S)).containsExactly("File:  Save", "Edit:  Send");
    }


    // ── the commands that could never be bound ──────────────────────────

    @Test
    void theModernCommandsCanNowCarryKeys() {
        var catalogue = new KeyBindings();

        // Each of these arrived after the key settings stopped being able to
        // grow, and until now could not be bound to anything.
        assertThat(catalogue.strokeFor(KeyPreferences.SHOW_AUTHOR_VIEW_ACTION)).isPresent();
        assertThat(catalogue.strokeFor(KeyPreferences.SHOW_EDITOR_VIEW_ACTION)).isPresent();
        assertThat(catalogue.strokeFor(KeyPreferences.TOGGLE_PRIMARY_SIDEBAR_ACTION)).isPresent();
        assertThat(catalogue.strokeFor(KeyPreferences.TOGGLE_BOTTOM_PANEL_ACTION)).isPresent();
    }

    @Test
    @DisplayName("the Agent commands stay out until something answers to them")
    void theAgentCommandsAreNotOfferedYet() {
        var catalogue = new KeyBindings();

        // Listed with keys but implemented nowhere, they did nothing when
        // pressed and still reserved their keys against commands that work.
        assertThat(catalogue.categories()).doesNotContain("Agent");
        assertThat(catalogue.strokeFor(KeyPreferences.FOCUS_AGENT_ACTION)).isEmpty();
        assertThat(catalogue.strokeFor(KeyPreferences.SHOW_PROPOSALS_ACTION)).isEmpty();
    }

    @Test
    void noTwoCommandsWantTheSameKey() {
        // Ctrl+B was declared for Toggle bookmark and hard-coded onto the
        // primary sidebar toggle at the same time, so one of them quietly lost.
        // Nothing may ship in that state again.
        assertThat(new KeyBindings().conflicts()).isEmpty();
    }
    // ── the catalogue the product ships ─────────────────────────────────

    @Test
    void theCatalogueIsTheProductsNotAFiles() {
        var all = KeyBindingCatalogue.all();

        assertThat(all).isNotEmpty();
        assertThat(all).extracting(KeyBinding::id).doesNotHaveDuplicates();
        // The two largest groups of dead bindings, for features that do not
        // exist: an XSLT debugger and a grid.
        assertThat(all).extracting(KeyBinding::category).doesNotContain("Debugger", "Grid");
    }

    @Test
    void everyCatalogueEntryCanBeShownAndBound() {
        KeyBindings keys = new KeyBindings();

        for (KeyBinding binding : keys.all()) {
            assertThat(binding.label()).isNotBlank();
            assertThat(binding.category()).isNotBlank();
        }
        assertThat(keys.categories()).isNotEmpty();
    }

    @Test
    @DisplayName("a copy carries the plugin commands, not just the catalogue")
    void aCopyKeepsRegisteredCommands() {
        KeyBindings live = new KeyBindings();
        live.register(new KeyBinding("Plugin:  Frobnicate", "Frobnicate", "Plugins", null));
        live.bind("Plugin:  Frobnicate", javax.swing.KeyStroke.getKeyStroke("ctrl alt F"));

        KeyBindings editing = live.copy();

        // Building the settings page from the catalogue alone dropped these on OK.
        assertThat(editing.all()).anyMatch(b -> "Plugin:  Frobnicate".equals(b.id()));
        assertThat(editing.toStore()).containsKey("Plugin:  Frobnicate");
    }

    @Test
    @DisplayName("editing a copy leaves the original alone, which is what Cancel needs")
    void editingACopyDoesNotTouchTheOriginal() {
        KeyBindings live = new KeyBindings();
        String id = KeyPreferences.TOGGLE_BOTTOM_PANEL_ACTION;
        javax.swing.KeyStroke before = live.strokeFor(id).orElse(null);

        KeyBindings editing = live.copy();
        editing.bind(id, javax.swing.KeyStroke.getKeyStroke("ctrl alt shift Z"));

        assertThat(live.strokeFor(id).orElse(null)).isEqualTo(before);
        assertThat(live.isOverridden(id)).isFalse();
    }

    @Test
    @DisplayName("adopting a copy replaces the overrides, which is what OK needs")
    void adoptingTakesTheCopysChoices() {
        KeyBindings live = new KeyBindings();
        String id = KeyPreferences.TOGGLE_BOTTOM_PANEL_ACTION;
        live.bind(id, javax.swing.KeyStroke.getKeyStroke("ctrl alt 1"));

        KeyBindings editing = live.copy();
        editing.reset(id);
        editing.bind(KeyPreferences.TOGGLE_PRIMARY_SIDEBAR_ACTION,
                javax.swing.KeyStroke.getKeyStroke("ctrl alt 2"));
        live.adopt(editing);

        // Replaced wholesale: a reset in the dialog must not survive as a leftover.
        assertThat(live.isOverridden(id)).isFalse();
        assertThat(live.strokeFor(KeyPreferences.TOGGLE_PRIMARY_SIDEBAR_ACTION))
                .contains(javax.swing.KeyStroke.getKeyStroke("ctrl alt 2"));
    }
}
