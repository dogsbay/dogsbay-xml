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

import java.util.List;

import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.commands.DialogButtons.WindowInfo;

/**
 * Finding the button a recording named.
 *
 * <p>Pressing a dialog's button used to mean clicking a screen position measured from a screenshot, which
 * moves when a window manager adds a title bar, when the theme or the font changes, or when the dialog gains
 * a line of text — and a recording that mis-clicks does not fail, it does something else and carries on. Every
 * decision that replaces that measurement is here, over plain containers, so it is checked without a display.
 */
class DialogButtonsTest {

    private static JPanel dialogWith(String... labels) {
        JPanel panel = new JPanel();
        for (String label : labels) {
            panel.add(new JButton(label));
        }
        return panel;
    }

    // ── Which button ────────────────────────────────────────────────────

    @Test
    void findsAButtonByItsLabel() {
        JPanel dialog = dialogWith("Add…", "Edit…", "Delete", "Set active", "Close");

        assertThat(DialogButtons.matching(dialog, "Set active", false)).hasSize(1);
        assertThat(DialogButtons.matching(dialog, "Set active", false).get(0).getText()).isEqualTo("Set active");
    }

    @Test
    void findsAButtonNestedInsideThePanelsThatLayItOut() {
        JPanel buttons = dialogWith("OK", "Cancel");
        JPanel content = new JPanel();
        content.add(new JPanel());
        content.add(buttons);
        JPanel dialog = new JPanel();
        dialog.add(content);

        assertThat(DialogButtons.matching(dialog, "OK", false)).hasSize(1);
    }

    @Test
    void anEllipsisIsPartOfTheDialogNotOfTheName() {
        // "Add…" says it opens something further, which is the dialog's business. Being told there is only
        // "Add…" when you asked for "Add" would be a riddle.
        JPanel dialog = dialogWith("Add…", "Close");

        assertThat(DialogButtons.matching(dialog, "Add", false)).hasSize(1);
        assertThat(DialogButtons.matching(dialog, "Add...", false)).hasSize(1);
        assertThat(DialogButtons.matching(dialog, "Add…", false)).hasSize(1);
    }

    @Test
    void caseAndSurroundingSpaceDoNotMatter() {
        JPanel dialog = dialogWith("OK");

        assertThat(DialogButtons.matching(dialog, "ok", false)).hasSize(1);
        assertThat(DialogButtons.matching(dialog, "  Ok  ", false)).hasSize(1);
    }

    @Test
    void aNameNothingAnswersToFindsNothing() {
        JPanel dialog = dialogWith("OK", "Cancel");

        assertThat(DialogButtons.matching(dialog, "Apply", false)).isEmpty();
    }

    @Test
    void twoButtonsOfTheSameNameAreBothReturnedSoTheCallerIsToldRatherThanGuessedFor() {
        JPanel dialog = dialogWith("Remove", "Remove");

        assertThat(DialogButtons.matching(dialog, "Remove", false)).hasSize(2);
    }

    @Test
    void aButtonWithNoTextIsNotAddressableByName() {
        // A toolbar's icon-only buttons would otherwise all answer to ""
        JPanel dialog = new JPanel();
        dialog.add(new JButton());
        dialog.add(new JButton("OK"));

        assertThat(DialogButtons.matching(dialog, "", false)).isEmpty();
        assertThat(DialogButtons.labelsOf(DialogButtons.buttonsIn(dialog))).containsExactly("OK");
    }

    @Test
    void aCheckBoxOrToggleIsAButtonTooAndCanBePressed() {
        // The Initialize Repository dialog's ".gitignore" checkbox is one of these, and a recording has to be
        // able to turn it off
        JPanel dialog = new JPanel();
        dialog.add(new JToggleButton("Also write a .gitignore"));

        assertThat(DialogButtons.matching(dialog, "Also write a .gitignore", false)).hasSize(1);
    }

    @Test
    void menuItemsAreNotButtonsHere() {
        // invokeAction drives the menus, by id and without opening one. Including menu items would make
        // "Open" ambiguous between a dialog's button and a menu nobody can see.
        JPanel dialog = new JPanel();
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(new JMenuItem("Open"));
        bar.add(file);
        dialog.add(bar);
        dialog.add(new JButton("Open"));

        assertThat(DialogButtons.matching(dialog, "Open", false)).hasSize(1);
        assertThat(DialogButtons.labelsOf(DialogButtons.buttonsIn(dialog))).containsExactly("Open");
    }

    @Test
    void theLabelsAreListedInTheOrderTheyAppearSoARefusalReadsLikeTheDialog() {
        JPanel dialog = dialogWith("Add…", "Edit…", "Delete", "Set active", "Close");

        assertThat(DialogButtons.labelsOf(DialogButtons.buttonsIn(dialog)))
                .containsExactly("Add…", "Edit…", "Delete", "Set active", "Close");
    }

    // ── What a hidden button counts for ─────────────────────────────────

    /** Two cards of one CardLayout, as the editor's sidebars are: both are children, one is on screen. */
    private static JPanel cardsWith(String shownLabel, String hiddenLabel) {
        JPanel shown = new JPanel();
        shown.add(new JButton(shownLabel));
        JPanel hidden = new JPanel();
        hidden.add(new JButton(hiddenLabel));
        JPanel cards = new JPanel(new java.awt.CardLayout());
        cards.add(shown, "shown");
        cards.add(hidden, "hidden");
        ((java.awt.CardLayout) cards.getLayout()).show(cards, "shown");
        return cards;
    }

    @Test
    void aButtonNobodyCanSeeDoesNotMakeAVisibleOneAmbiguous() {
        // The editor's sidebars are cards of one CardLayout, and several carry a Refresh button, so counting
        // the hidden ones reported "3 buttons are called 'Refresh'" about a window showing exactly one.
        JPanel cards = cardsWith("Refresh", "Refresh");

        assertThat(DialogButtons.matching(cards, "Refresh", true))
                .as("one of them is on screen, and that is the one to press")
                .hasSize(1);
        assertThat(DialogButtons.matching(cards, "Refresh", false))
                .as("both are in the window, which is what explains a button that cannot be pressed")
                .hasSize(2);
        assertThat(DialogButtons.resolve(cards, "DogsBay XML", "Refresh"))
                .as("so it resolves, instead of refusing as ambiguous")
                .isInstanceOf(DialogButtons.Outcome.Found.class);
    }

    @Test
    void aButtonInAnUnopenedCardIsExplainedAsHiddenNotAsMissing() {
        // "No such button" about one the reader can see in another tab sends them hunting for a typo
        JPanel cards = cardsWith("Refresh", "Normalize");

        DialogButtons.Outcome outcome = DialogButtons.resolve(cards, "Settings", "Normalize");

        assertThat(outcome).isInstanceOf(DialogButtons.Outcome.Refused.class);
        assertThat(((DialogButtons.Outcome.Refused) outcome).message())
                .contains("is not on screen").doesNotContain("No button called");
    }

    @Test
    void aButtonWhoseWholeNameIsAnEllipsisCanStillBeNamed() {
        // The folder chooser beside a path field is labelled "...". Stripping the ellipsis left nothing, which
        // matched no button while the refusal went on listing it as available.
        JPanel dialog = dialogWith("...", "OK");

        assertThat(DialogButtons.normalise("...")).isEqualTo("...");
        assertThat(DialogButtons.labelsOf(DialogButtons.buttonsIn(dialog))).contains("...");
    }

    // ── The words a caller actually meets ───────────────────────────────

    @Test
    void anUnknownNameIsRefusedWithTheNamesThatExist() {
        // Asking for the wrong one is how a recording author finds the right one
        DialogButtons.Outcome outcome = DialogButtons.resolve(
                dialogWith("Add…", "Delete", "Close"), "Manage DITA Deliverables", "Remove");

        assertThat(((DialogButtons.Outcome.Refused) outcome).message())
                .contains("No button called 'Remove'")
                .contains("'Manage DITA Deliverables'")
                .contains("Add…").contains("Delete").contains("Close");
    }

    @Test
    void anAmbiguousNameIsRefusedAndSaysHowMany() {
        DialogButtons.Outcome outcome = DialogButtons.resolve(dialogWith("Delete", "Delete"), "Settings", "Delete");

        assertThat(((DialogButtons.Outcome.Refused) outcome).message())
                .contains("2 buttons").contains("cannot say which one you meant");
    }

    @Test
    void aDisabledButtonIsRefusedRatherThanQuietlyDoingNothing() {
        JPanel dialog = dialogWith("Delete");
        DialogButtons.matching(dialog, "Delete", false).get(0).setEnabled(false);

        DialogButtons.Outcome outcome = DialogButtons.resolve(dialog, "Settings", "Delete");

        assertThat(((DialogButtons.Outcome.Refused) outcome).message()).contains("not available now");
    }

    @Test
    void theButtonItFindsIsTheOneToPress() {
        DialogButtons.Outcome outcome = DialogButtons.resolve(dialogWith("OK", "Cancel"), "Settings", "ok");

        assertThat(outcome).isInstanceOf(DialogButtons.Outcome.Found.class);
        assertThat(((DialogButtons.Outcome.Found) outcome).button().getText()).isEqualTo("OK");
    }

    @Test
    void aWindowWithNoTitleIsStillNamedInAMessage() {
        assertThat(DialogButtons.describe(null)).isEqualTo("the window with no title");
        assertThat(DialogButtons.describe("  ")).isEqualTo("the window with no title");
        assertThat(DialogButtons.describe("Settings")).isEqualTo("'Settings'");
    }

    @Test
    void theRefusalListsUntitledWindowsInsteadOfPretendingThereAreNone() {
        // Two untitled dialogs told the caller to "name one" and then listed nothing at all
        List<WindowInfo> windows = List.of(
                new WindowInfo("", true, false, true), new WindowInfo(null, true, false, true));

        assertThat(DialogButtons.noWindow(windows, null))
                .contains("Name one with 'window'").contains("(no title)");
    }

    // ── Which window ────────────────────────────────────────────────────

    private static WindowInfo frame(String title, boolean active) {
        return new WindowInfo(title, true, active, false);
    }

    private static WindowInfo dialog(String title, boolean active) {
        return new WindowInfo(title, true, active, true);
    }

    @Test
    void theActiveWindowIsTheOneBeingDriven() {
        // A modal dialog takes the focus when it opens, and that dialog is what the caller is driving
        List<WindowInfo> windows = List.of(frame("DogsBay XML", false), dialog("Set Attribute", true));

        assertThat(DialogButtons.chooseWindow(windows, null)).isEqualTo(1);
    }

    @Test
    void aNamedWindowWinsOverTheActiveOne() {
        List<WindowInfo> windows = List.of(frame("DogsBay XML", true), dialog("Set Attribute", false));

        assertThat(DialogButtons.chooseWindow(windows, "Set Attribute")).isEqualTo(1);
    }

    @Test
    void aWindowCanBeNamedByPartOfItsTitle() {
        List<WindowInfo> windows = List.of(frame("DogsBay XML", true), dialog("Manage DITA Deliverables", false));

        assertThat(DialogButtons.chooseWindow(windows, "Deliverables")).isEqualTo(1);
    }

    @Test
    void aWholeTitleIsPreferredToAFragmentOfAnother() {
        List<WindowInfo> windows = List.of(dialog("Deliverables and more", false), dialog("Deliverables", false));

        assertThat(DialogButtons.chooseWindow(windows, "Deliverables")).isEqualTo(1);
    }

    @Test
    void aNameNoWindowAnswersToChoosesNothing() {
        List<WindowInfo> windows = List.of(frame("DogsBay XML", true));

        assertThat(DialogButtons.chooseWindow(windows, "Set Attribute")).isEqualTo(-1);
    }

    @Test
    void withNothingActiveTheOneDialogOnScreenIsUnambiguous() {
        // A run driving the editor with no pointer or focus of its own is the usual reason nothing is active
        List<WindowInfo> windows = List.of(frame("DogsBay XML", false), dialog("Set Attribute", false));

        assertThat(DialogButtons.chooseWindow(windows, null)).isEqualTo(1);
    }

    @Test
    void withNothingActiveAndTwoDialogsItRefusesToGuess() {
        List<WindowInfo> windows = List.of(
                frame("DogsBay XML", false), dialog("Set Attribute", false), dialog("Confirm", false));

        assertThat(DialogButtons.chooseWindow(windows, null)).isEqualTo(-1);
    }

    @Test
    void aWindowThatIsNotOnScreenIsNeverChosen() {
        // Swing keeps disposed and not-yet-shown windows in Window.getWindows(), and this one even claims the
        // focus — a dialog that has just been disposed still can. It must not be picked either way.
        List<WindowInfo> windows = List.of(
                new WindowInfo("Set Attribute", false, true, true), frame("DogsBay XML", false));

        assertThat(DialogButtons.chooseWindow(windows, "Set Attribute")).isEqualTo(-1);
        assertThat(DialogButtons.chooseWindow(windows, null))
                .as("the editor, which is the only window on screen")
                .isEqualTo(1);
    }

    @Test
    void withNothingActiveAndNoDialogTheEditorWindowIsTheOnlyThingItCouldBe() {
        // The recording display has no window manager, so nothing is ever active there once the first dialog
        // has closed. Refusing to act on the only window on screen would have made the whole method useless
        // for exactly the runs it was built for (found by driving a real editor under Xvfb).
        List<WindowInfo> windows = List.of(frame("DogsBay XML", false));

        assertThat(DialogButtons.chooseWindow(windows, null)).isEqualTo(0);
    }

    @Test
    void aDialogIsPreferredToTheEditorBehindIt() {
        List<WindowInfo> windows = List.of(frame("DogsBay XML", false), dialog("Settings", false));

        assertThat(DialogButtons.chooseWindow(windows, null)).isEqualTo(1);
    }

    @Test
    void twoWindowsAndNoDialogIsStillAmbiguous() {
        List<WindowInfo> windows = List.of(frame("DogsBay XML", false), frame("Live Preview", false));

        assertThat(DialogButtons.chooseWindow(windows, null)).isEqualTo(-1);
    }

    @Test
    void theActiveOneWinsWhenTwoDialogsAreOpen() {
        // The arrangement that makes the active-window rule mean anything: with only one dialog showing, a
        // later fallback reaches the same answer, so deleting the rule broke nothing that was being checked.
        List<WindowInfo> windows = List.of(
                frame("DogsBay XML", false), dialog("Set Attribute", false), dialog("Confirm", true));

        assertThat(DialogButtons.chooseWindow(windows, null)).isEqualTo(2);
    }

    @Test
    void aNameThatFitsTwoWindowsIsRefusedRatherThanGuessedAt() {
        // The same rule the labels follow. Window.getWindows() is in creation order, so picking the first
        // match would press a button in whichever dialog happened to open first.
        List<WindowInfo> windows = List.of(
                dialog("Export Deliverable", false), dialog("Export Settings", false));

        assertThat(DialogButtons.chooseWindow(windows, "Export")).isEqualTo(-1);
    }

    @Test
    void twoWindowsOfTheSameTitleAreRefusedToo() {
        List<WindowInfo> windows = List.of(dialog("Settings", false), dialog("Settings", false));

        assertThat(DialogButtons.chooseWindow(windows, "Settings")).isEqualTo(-1);
    }
}
