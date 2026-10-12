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
package com.dogsbay.dogsbayaieditor.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.AbstractAction;
import javax.swing.Action;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The parts of driving the editor that do not need an editor: which menu command is refused and why, and which panel name
 * means which tab. The rest of those commands is a side effect on a live window.
 */
class DriveTheUiTest {

    @AfterEach
    void restore() {
        UiDriving.allow(false);
    }

    private Action action(boolean enabled, AtomicInteger runs) {
        Action a = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runs.incrementAndGet();
            }
        };
        a.setEnabled(enabled);
        return a;
    }

    @Test
    void findsAnActionThatIsThereAndEnabled() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        Action validate = action(true, runs);
        Map<String, Action> menu = Map.of("XML:  Validate", validate);

        Action found = EditorExecutor.findAction("XML:  Validate", menu::get, () -> List.copyOf(menu.keySet()));

        assertThat(found).isSameAs(validate);
        assertThat(runs).hasValue(0); // finding it does not run it
        found.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "XML:  Validate"));
        assertThat(runs).hasValue(1);
    }

    @Test
    void refusesAnIdThatDoesNotExistAndSaysWhatDoes() {
        Map<String, Action> menu = Map.of("XML:  Validate", action(true, new AtomicInteger()));
        assertThatThrownBy(() -> EditorExecutor.findAction("Vlaidate", menu::get, () -> List.copyOf(menu.keySet())))
                .isInstanceOf(CommandException.class)
                .hasMessageContaining("Unknown action: Vlaidate")
                .hasMessageContaining("XML:  Validate"); // a typo is the likely cause, so the real ids are the answer
    }

    @Test
    void refusesADisabledActionRatherThanQuietlyDoingNothing() {
        AtomicInteger runs = new AtomicInteger();
        Map<String, Action> menu = Map.of("XML:  Validate", action(false, runs));

        assertThatThrownBy(() -> EditorExecutor.findAction("XML:  Validate", menu::get, () -> List.copyOf(menu.keySet())))
                .isInstanceOf(CommandException.class)
                .hasMessageContaining("not available now");
        assertThat(runs).hasValue(0);
    }

    @Test
    void refusesNoIdAtAll() {
        assertThatThrownBy(() -> EditorExecutor.findAction("  ", k -> null, List::of))
                .isInstanceOf(CommandException.class)
                .hasMessageContaining("action id is needed");
    }

    @Test
    void mapsEachPanelNameToTheTabTitleTheEditorUses() throws Exception {
        // these titles have to match the ones OutputPanel adds in its constructor, and nothing else links them
        assertThat(EditorExecutor.tabTitleFor("errors")).isEqualTo("Errors");
        assertThat(EditorExecutor.tabTitleFor("terminal")).isEqualTo("Terminal");
        assertThat(EditorExecutor.tabTitleFor("projectValidation")).isEqualTo("Project Validation");
    }

    @Test
    void refusesAPanelNameThatIsNotOneAndListsTheNames() {
        assertThatThrownBy(() -> EditorExecutor.tabTitleFor("parse"))
                .isInstanceOf(CommandException.class)
                .hasMessageContaining("Unknown panel: parse")
                .hasMessageContaining("projectValidation");
        assertThatThrownBy(() -> EditorExecutor.tabTitleFor(null)).isInstanceOf(CommandException.class);
    }

    @Test
    void everythingThatDrivesTheEditorIsRefusedUntilItIsAllowed() {
        // the ones that cannot be contained: a menu command, a shell line, typing, which goes wherever focus
        // is, and pressing a button, which does whatever that button does
        assertThat(UiDriving.isAllowed()).isFalse();
        for (String what : new String[] { "Running a menu command", "Running a command in the terminal", "Typing",
                "Pressing a button" }) {
            assertThat(UiDriving.refused(what)).hasMessageContaining(what).hasMessageContaining("--allow-driving");
        }
    }

    @Test
    void drivingIsOffUntilAskedFor() {
        assertThat(UiDriving.isAllowed()).isFalse();
        // and the refusal says how to turn it on, because a caller that meets it has no other way to find out
        assertThat(UiDriving.refused("Running a menu command")).hasMessageContaining("--allow-driving");
        UiDriving.allow(true);
        assertThat(UiDriving.isAllowed()).isTrue();
    }
}
