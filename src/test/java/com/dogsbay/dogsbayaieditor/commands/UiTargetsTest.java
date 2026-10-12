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

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.commands.UiTargets.Kind;

/**
 * Finding what a recording named, by the text a reader can see on it.
 *
 * <p>About seven hundred clicks in the cue sheets were positions measured from a screenshot — a tree row, a
 * field beside its label, a tab, an option in a drop-down. Every one moved when a window manager drew a title
 * bar, and every one would move again on a change of font, theme or layout. These are the rules that replace
 * the measuring, checked over plain containers because a realised window is what a headless test cannot have.
 */
class UiTargetsTest {

    @Test
    void findsATabByItsTitleAndGivesTheTabsOwnBox() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Errors", new JPanel());
        tabs.addTab("Project Validation", new JPanel());
        tabs.setSize(400, 300);
        tabs.doLayout();

        var found = UiTargets.find(tabs, Kind.TAB, "Project Validation");

        assertThat(found).hasSize(1);
        assertThat(found.get(0).text()).isEqualTo("Project Validation");
        assertThat(found.get(0).within()).as("the tab's box, not the whole pane's").isNotNull();
    }

    @Test
    void findsAListRow() {
        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("Blank XML Document");
        model.addElement("Concept");
        JList<String> list = new JList<>(model);

        var found = UiTargets.find(list, Kind.ROW, "Concept");

        assertThat(found).hasSize(1);
        assertThat(found.get(0).text()).isEqualTo("Concept");
    }

    @Test
    void findsATreeRow() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("my-audacity-guide");
        root.add(new DefaultMutableTreeNode("topics"));
        JTree tree = new JTree(root);

        assertThat(UiTargets.find(tree, Kind.ROW, "topics")).hasSize(1);
        assertThat(UiTargets.find(tree, Kind.ROW, "my-audacity-guide")).hasSize(1);
    }

    @Test
    void findsATableCell() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][] { { "full", "html5" }, { "book-pdf", "pdf" } }, new Object[] { "Name", "Type" }));

        var found = UiTargets.find(table, Kind.ROW, "book-pdf");

        assertThat(found).hasSize(1);
        assertThat(found.get(0).within()).as("the cell's box").isNotNull();
    }

    @Test
    void findsAFieldByTheLabelThatNamesIt() {
        // getLabelFor is the only answer that is not a guess, so it is the one taken first
        JTextField value = new JTextField();
        JLabel label = new JLabel("Value");
        label.setLabelFor(value);
        JPanel dialog = new JPanel();
        dialog.add(label);
        dialog.add(new JTextField());   // a decoy earlier in the container
        dialog.add(value);

        var found = UiTargets.find(dialog, Kind.FIELD, "Value");

        assertThat(found).hasSize(1);
        assertThat(found.get(0).component()).isSameAs(value);
    }

    @Test
    void findsAFieldAfterItsLabelWhenNothingSaysWhichItIs() {
        // The convention every dialog here follows: the field comes after its label in the same container
        JPanel dialog = new JPanel();
        dialog.add(new JLabel("Tag name"));
        JTextField tag = new JTextField();
        dialog.add(tag);

        assertThat(UiTargets.find(dialog, Kind.FIELD, "Tag name").get(0).component()).isSameAs(tag);
    }

    @Test
    void aLabelsColonIsNotPartOfItsName() {
        // A cue sheet says "Value", and dialogs write "Value:"
        JPanel dialog = new JPanel();
        dialog.add(new JLabel("Value:"));
        dialog.add(new JTextField());

        assertThat(UiTargets.find(dialog, Kind.FIELD, "Value")).hasSize(1);
    }

    @Test
    void aLabelThatNamesNothingIsNotAField() {
        // Rather than pairing it with whatever happened to be nearby
        JPanel dialog = new JPanel();
        dialog.add(new JLabel("Nothing follows me"));

        assertThat(UiTargets.find(dialog, Kind.FIELD, "Nothing follows me")).isEmpty();
    }

    @Test
    void findsAMenuInTheMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.add(new JMenu("File"));
        JMenu refactor = new JMenu("Refactor");
        refactor.add(new JMenuItem("Set Attribute…"));
        bar.add(refactor);
        JPanel window = new JPanel();
        window.add(bar);

        var found = UiTargets.find(window, Kind.MENU, "Refactor");

        assertThat(found).hasSize(1);
        assertThat(found.get(0).component()).isSameAs(refactor);
    }

    @Test
    void findsAMenuItemAndForgivesItsEllipsis() {
        JMenuBar bar = new JMenuBar();
        JMenu refactor = new JMenu("Refactor");
        JMenuItem item = new JMenuItem("Set Attribute…");
        refactor.add(item);
        bar.add(refactor);

        java.util.List<UiTargets.Target> found = new java.util.ArrayList<>();
        UiTargets.items(bar, "Set Attribute", found, openPathFor(bar, refactor));

        assertThat(found.get(0).component()).isSameAs(item);
    }

    @Test
    void anItemOfAMenuNobodyHasOpenedIsNotSomewhereToPoint() {
        // find() means "what can be pointed at". The item is still known — findEverywhere has it — which is
        // what lets the refusal say "open its menu first" instead of "no such item".
        JMenuBar bar = new JMenuBar();
        JMenu refactor = new JMenu("Refactor");
        refactor.add(new JMenuItem("Set Attribute…"));
        bar.add(refactor);

        assertThat(UiTargets.find(bar, Kind.ITEM, "Set Attribute")).isEmpty();
        assertThat(UiTargets.findEverywhere(bar, Kind.ITEM, "Set Attribute")).hasSize(1);
    }

    @Test
    void aMenuIsNotAButtonAndAButtonIsNotAMenu() {
        // "Open" is both a menu item and a dialog button in the editor; naming the kind is what separates them
        JPanel window = new JPanel();
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(new JMenuItem("Open"));
        bar.add(file);
        window.add(bar);
        JButton button = new JButton("Open");
        window.add(button);

        assertThat(UiTargets.find(window, Kind.BUTTON, "Open")).hasSize(1);
        assertThat(UiTargets.find(window, Kind.BUTTON, "Open").get(0).component()).isSameAs(button);
        assertThat(UiTargets.findEverywhere(window, Kind.ITEM, "Open"))
                .as("the menu item, kept apart from the button of the same name")
                .hasSize(1);
    }

    @Test
    void findsADropDownByWhatIsShowingInIt() {
        JComboBox<String> combo = new JComboBox<>(new String[] { "en", "de" });
        JPanel dialog = new JPanel();
        dialog.add(combo);

        assertThat(UiTargets.find(dialog, Kind.COMBO, "en")).hasSize(1);
    }

    @Test
    void twoThingsOfTheSameNameAreBothReturnedSoTheCallerIsToldRatherThanGuessedFor() {
        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("Concept");
        model.addElement("Concept");

        assertThat(UiTargets.find(new JList<>(model), Kind.ROW, "Concept")).hasSize(2);
    }

    @Test
    void somethingHiddenIsNotFound() {
        // The same rule the buttons follow: a card the reader is not looking at is not a target
        JPanel hidden = new JPanel();
        hidden.add(new JLabel("Value"));
        hidden.add(new JTextField());
        hidden.setVisible(false);
        JPanel dialog = new JPanel();
        dialog.add(hidden);

        assertThat(UiTargets.find(dialog, Kind.FIELD, "Value")).isEmpty();
    }

    @Test
    void theNamesOfAKindAreListedForARefusal() {
        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("Blank XML Document");
        model.addElement("Concept");

        assertThat(UiTargets.namesOf(new JList<>(model), Kind.ROW))
                .containsExactly("Blank XML Document", "Concept");
    }

    @Test
    void theKindsAreNamedInWordsACueSheetCanUse() {
        assertThat(Kind.of("row")).isEqualTo(Kind.ROW);
        assertThat(Kind.of("FIELD")).isEqualTo(Kind.FIELD);
        assertThat(Kind.of("  tab ")).isEqualTo(Kind.TAB);
        assertThat(Kind.of("widget")).isNull();
        assertThat(Kind.of(null)).isNull();
        assertThat(Kind.names()).contains("row", "field", "tab", "menu", "item", "button", "combo", "option");
    }

    // ── An open menu ────────────────────────────────────────────────────

    /** Swing's path for an open menu-bar menu, which is what the live editor hands over. */
    private static java.util.List<javax.swing.MenuElement> openPathFor(JMenuBar bar, JMenu menu) {
        return java.util.List.of(bar, menu, menu.getPopupMenu());
    }

    @Test
    void anItemOfAnOpenMenuIsFoundOnceHoweverManyWaysItIsReached() {
        // The reported defect: [JMenuBar, JMenu, JPopupMenu] all lead to the same items, so every item of
        // every open menu was found three times and refused as ambiguous — which is the whole of what a
        // recording uses this for. Swing adds a fourth when the pointer is over the item.
        JMenuBar bar = new JMenuBar();
        JMenu refactor = new JMenu("Refactor");
        JMenuItem set = new JMenuItem("Set Attribute…");
        refactor.add(set);
        bar.add(refactor);

        java.util.List<UiTargets.Target> found = new java.util.ArrayList<>();
        UiTargets.items(bar, "Set Attribute", found, openPathFor(bar, refactor));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).component()).isSameAs(set);
    }

    @Test
    void theItemUnderThePointerIsStillOnlyOne() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenuItem save = new JMenuItem("Save");
        file.add(save);
        bar.add(file);
        java.util.List<javax.swing.MenuElement> hovered =
                java.util.List.of(bar, file, file.getPopupMenu(), save);

        java.util.List<UiTargets.Target> found = new java.util.ArrayList<>();
        UiTargets.items(bar, "Save", found, hovered);

        assertThat(found).hasSize(1);
    }

    @Test
    void aSubmenuIsAMenuAndNotAnItem() {
        // "a menu opens, an item acts" — so Project Tools has exactly one kind, and a caller writing
        // Project > Project Tools > Manage Deliverables never has to know the middle one is different
        JMenuBar bar = new JMenuBar();
        JMenu project = new JMenu("Project");
        JMenu tools = new JMenu("Project Tools");
        tools.add(new JMenuItem("Manage Deliverables…"));
        project.add(tools);
        bar.add(project);

        assertThat(UiTargets.find(bar, Kind.MENU, "Project Tools")).hasSize(1);
        assertThat(UiTargets.find(bar, Kind.ITEM, "Project Tools")).isEmpty();
        assertThat(UiTargets.namesOf(bar, Kind.MENU)).contains("Project Tools");
    }

    // ── A label names its own widget, or none ───────────────────────────

    @Test
    void aLabelWhoseWidgetIsNotATextBoxTakesNoOtherLabelsField() {
        // Save Remote Document: Protocol is a drop-down, and the scan ran past it to the Host box — so
        // locate field "Protocol" answered with Host, and the refusal list went on advertising Protocol.
        JPanel dialog = new JPanel();
        dialog.add(new JLabel("Protocol:"));
        dialog.add(new JComboBox<>(new String[] { "file", "ftp" }));
        dialog.add(new JLabel("Host:"));
        JTextField host = new JTextField();
        dialog.add(host);

        assertThat(UiTargets.find(dialog, Kind.FIELD, "Protocol")).isEmpty();
        assertThat(UiTargets.namesOf(dialog, Kind.FIELD)).doesNotContain("Protocol").contains("Host");
        assertThat(UiTargets.find(dialog, Kind.FIELD, "Host").get(0).component()).isSameAs(host);
    }

    @Test
    void aLabelThatNamesAComponentOutrightIsBelievedOrRefused() {
        // getLabelFor is the dialog saying which field this is; falling through to the guess when that
        // component is not a text box would answer with something the dialog explicitly did not mean
        JPanel dialog = new JPanel();
        JLabel label = new JLabel("Transtype");
        JComboBox<String> combo = new JComboBox<>(new String[] { "html5" });
        label.setLabelFor(combo);
        dialog.add(label);
        dialog.add(combo);
        dialog.add(new JTextField());

        assertThat(UiTargets.find(dialog, Kind.FIELD, "Transtype")).isEmpty();
    }

    // ── What is off the screen, and what is merely scrolled away ────────

    @Test
    void aBoxScrolledOutOfItsViewportIsNotSomewhereToPoint() {
        // A row forty down a scrolled table has bounds that are arithmetically right and point at a pixel
        // the viewport is not showing — the buttons below the table, or off the dialog entirely
        java.awt.Rectangle visible = new java.awt.Rectangle(0, 100, 200, 100);

        assertThat(UiTargets.onScreenPart(new java.awt.Rectangle(0, 900, 200, 18), visible)).isNull();
        assertThat(UiTargets.onScreenPart(new java.awt.Rectangle(0, 120, 200, 18), visible))
                .isEqualTo(new java.awt.Rectangle(0, 120, 200, 18));
    }

    @Test
    void aBoxHalfInViewIsClippedToTheHalfThatShows() {
        java.awt.Rectangle visible = new java.awt.Rectangle(0, 100, 200, 100);

        assertThat(UiTargets.onScreenPart(new java.awt.Rectangle(0, 190, 200, 20), visible))
                .isEqualTo(new java.awt.Rectangle(0, 190, 200, 10));
    }

    @Test
    void withNothingLaidOutYetThereIsNoViewportToBeOutsideOf() {
        java.awt.Rectangle box = new java.awt.Rectangle(0, 0, 10, 10);

        assertThat(UiTargets.onScreenPart(box, new java.awt.Rectangle())).isEqualTo(box);
        assertThat(UiTargets.onScreenPart(box, null)).isEqualTo(box);
    }

    // ── One row, whatever its columns say ───────────────────────────────

    @Test
    void aRowWhoseColumnsRepeatTheTextIsStillOneRow() {
        // An ordinary keydef whose keyword text equals its key name gave two hits for one clickable row,
        // and locate takes no column to tell them apart
        JTable table = new JTable(new DefaultTableModel(
                new Object[][] { { "product", "product" } }, new Object[] { "Key", "Text" }));

        assertThat(UiTargets.find(table, Kind.ROW, "product")).hasSize(1);
    }

    // ── The words a caller meets ────────────────────────────────────────

    @Test
    void anUnknownNameIsRefusedWithTheOnesThatExist() {
        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("Concept");
        model.addElement("Task");

        var outcome = UiTargets.resolve(new JList<>(model), "New File", Kind.ROW, "Reference", 0);

        assertThat(((UiTargets.Outcome.Refused) outcome).message())
                .contains("No row called 'Reference'").contains("'New File'")
                .contains("Concept").contains("Task");
    }

    @Test
    void aNameThatIsRightButHiddenIsNotReportedMissing() {
        // "No such item" about one the reader can see sends them hunting a typo that is not there
        JMenuBar bar = new JMenuBar();
        JMenu refactor = new JMenu("Refactor");
        refactor.add(new JMenuItem("Set Attribute…"));
        bar.add(refactor);

        var outcome = UiTargets.resolve(bar, "DogsBay XML", Kind.ITEM, "Set Attribute", 0);

        assertThat(((UiTargets.Outcome.Refused) outcome).message())
                .contains("is not on screen").contains("open its menu first")
                .doesNotContain("No item called");
    }

    @Test
    void namingAnOptionOfAClosedDropDownSaysToOpenIt() {
        // The name is right; the drop-down simply is not open. Listing the options as available and then
        // refusing every one of them is the most misleading answer available.
        JPanel dialog = new JPanel();
        dialog.add(new JComboBox<>(new String[] { "Blank XML Document", "Concept" }));

        var outcome = UiTargets.resolve(dialog, "New File", Kind.OPTION, "Concept", 0);

        assertThat(((UiTargets.Outcome.Refused) outcome).message())
                .contains("No drop-down is open").contains("Open one first")
                .doesNotContain("Available");
    }

    @Test
    void anAmbiguousNameSaysWhereEachOneIsAndHowToPickOne() {
        // Two rows of the same text is ordinary in a list, so a caller has to be able to get past it
        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("html5");
        model.addElement("html5");
        JList<String> list = new JList<>(model);

        var outcome = UiTargets.resolve(list, "Deliverables", Kind.ROW, "html5", 0);

        assertThat(((UiTargets.Outcome.Refused) outcome).message())
                .contains("2 things").contains("nth");
        assertThat(UiTargets.resolve(list, "Deliverables", Kind.ROW, "html5", 2))
                .isInstanceOf(UiTargets.Outcome.Found.class);
    }

    @Test
    void aRefusalDoesNotOfferProseAsAName() {
        // A dialog's explanatory paragraph is a JLabel too, and one of them ran to several hundred
        // characters of HTML — a refusal listing that is harder to read than one that lists nothing
        JPanel dialog = new JPanel();
        dialog.add(new JLabel("<html><body style='width:360px'>Lets external tools drive the editor.</body></html>"));
        dialog.add(new JTextField());
        dialog.add(new JLabel("Port:"));
        dialog.add(new JTextField());

        assertThat(UiTargets.namesOf(dialog, Kind.FIELD)).containsExactly("Port");
    }

    @Test
    void aHiddenThingIsNotOfferedAsAName() {
        // The editor's sidebars are cards of one CardLayout, and several have a Refresh button
        JPanel shown = new JPanel();
        shown.add(new JButton("Refresh"));
        JPanel hidden = new JPanel();
        hidden.add(new JButton("Rebuild"));
        hidden.setVisible(false);
        JPanel window = new JPanel();
        window.add(shown);
        window.add(hidden);

        assertThat(UiTargets.namesOf(window, Kind.BUTTON)).containsExactly("Refresh");
    }

    @Test
    void aTabOnAHiddenPaneIsNeitherFoundNorOffered() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Terminal", new JPanel());
        tabs.setVisible(false);
        JPanel window = new JPanel();
        window.add(tabs);

        assertThat(UiTargets.find(window, Kind.TAB, "Terminal")).isEmpty();
        assertThat(UiTargets.namesOf(window, Kind.TAB)).isEmpty();
    }

    @Test
    void theWholeComponentIsTheBoxWhenNothingNarrowsIt() {
        // A button, a menu and a field are pointed at whole; only a row, a tab and an option are part of
        // something bigger
        JButton button = new JButton("OK");
        button.setSize(80, 24);
        JPanel dialog = new JPanel();
        dialog.add(button);

        assertThat(UiTargets.find(dialog, Kind.BUTTON, "OK").get(0).box())
                .isEqualTo(new java.awt.Rectangle(0, 0, 80, 24));
    }

    // ── The text a row shows, not the text its value prints ─────────────

    /** A value whose toString is useless, as Manage Projects' rows were. */
    private record Project(String name) {
        @Override public String toString() {
            return "com.dogsbay.ProjectProperties@3f2a";
        }
    }

    @Test
    void aRowIsNamedByWhatItsRendererDraws() {
        // Manage Projects offered "com.dogsbay…ProjectProperties@3f2a", and a file chooser offered whole
        // paths where the row reads only the folder's name — neither is a name anyone could use
        DefaultListModel<Project> model = new DefaultListModel<>();
        model.addElement(new Project("audacity-guide"));
        JList<Project> list = new JList<>(model);
        list.setCellRenderer((l, value, i, sel, focus) -> new JLabel(value.name()));

        assertThat(UiTargets.find(list, Kind.ROW, "audacity-guide")).hasSize(1);
        assertThat(UiTargets.find(list, Kind.ROW, "com.dogsbay.ProjectProperties@3f2a")).isEmpty();
        assertThat(UiTargets.namesOf(list, Kind.ROW)).containsExactly("audacity-guide");
    }

    @Test
    void aTableCellIsNamedByWhatItsRendererDraws() {
        JTable table = new JTable(new DefaultTableModel(new Object[][] { { new Project("full") } },
                new Object[] { "Name" }));
        table.setDefaultRenderer(Object.class,
                (t, value, sel, focus, r, c) -> new JLabel(((Project) value).name()));

        assertThat(UiTargets.find(table, Kind.ROW, "full")).hasSize(1);
    }

    @Test
    void aValueThatDrawsItselfIsStillNamedByItsOwnWords() {
        // The ordinary case must keep working: a String row is named by the String
        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("Concept");

        assertThat(UiTargets.find(new JList<>(model), Kind.ROW, "Concept")).hasSize(1);
    }

    // ── A drop-down is named by its label ───────────────────────────────

    @Test
    void aDropDownIsFoundByTheLabelThatNamesIt() {
        // Found only by the value showing in it, "Default Root Map" was "(none)" — which repeats, so it was
        // ambiguous, and an empty editable one could not be found at all
        JPanel dialog = new JPanel();
        dialog.add(new JLabel("Default Root Map:"));
        JComboBox<String> combo = new JComboBox<>(new String[] { "(none)" });
        dialog.add(combo);
        dialog.add(new JLabel("Something else:"));
        dialog.add(new JComboBox<>(new String[] { "(none)" }));

        var found = UiTargets.find(dialog, Kind.COMBO, "Default Root Map");

        assertThat(found).hasSize(1);
        assertThat(found.get(0).component()).isSameAs(combo);
    }

    @Test
    void anEmptyDropDownIsStillFoundByItsLabel() {
        JPanel dialog = new JPanel();
        dialog.add(new JLabel("Input map"));
        JComboBox<String> combo = new JComboBox<>();
        combo.setEditable(true);
        dialog.add(combo);

        assertThat(UiTargets.find(dialog, Kind.COMBO, "Input map").get(0).component()).isSameAs(combo);
        assertThat(UiTargets.namesOf(dialog, Kind.COMBO)).contains("Input map");
    }

    @Test
    void aDropDownIsStillFoundByTheValueShowingInIt() {
        JPanel dialog = new JPanel();
        dialog.add(new JComboBox<>(new String[] { "html5", "pdf" }));

        assertThat(UiTargets.find(dialog, Kind.COMBO, "html5")).hasSize(1);
    }

    @Test
    void aLabelBesideADropDownIsNotAField() {
        // The kinds stay apart: a combo is a combo, whichever way it was named
        JPanel dialog = new JPanel();
        dialog.add(new JLabel("Transtype"));
        dialog.add(new JComboBox<>(new String[] { "html5" }));

        assertThat(UiTargets.find(dialog, Kind.FIELD, "Transtype")).isEmpty();
        assertThat(UiTargets.find(dialog, Kind.COMBO, "Transtype")).hasSize(1);
    }

    // ── A button with no words ──────────────────────────────────────────

    @Test
    void anIconOnlyButtonIsNamedByItsTooltip() {
        // The file chooser's Create New Folder has no text at all, so by text alone it could only be reached
        // by a measured position. Its tooltip is what a reader hovers to find out what it is.
        JPanel dialog = new JPanel();
        JButton icon = new JButton();
        icon.setToolTipText("Create New Folder");
        dialog.add(icon);

        assertThat(UiTargets.find(dialog, Kind.BUTTON, "Create New Folder").get(0).component())
                .isSameAs(icon);
        assertThat(DialogButtons.labelsOf(DialogButtons.buttonsIn(dialog)))
                .as("and a refusal names it, rather than saying a visible button is not there")
                .containsExactly("Create New Folder");
    }

    @Test
    void aButtonWithWordsIsStillNamedByThem() {
        JPanel dialog = new JPanel();
        JButton ok = new JButton("OK");
        ok.setToolTipText("Accept these settings");
        dialog.add(ok);

        assertThat(UiTargets.find(dialog, Kind.BUTTON, "OK")).hasSize(1);
        assertThat(UiTargets.find(dialog, Kind.BUTTON, "Accept these settings"))
                .as("and by its tooltip too, since that is also a name it is known by")
                .hasSize(1);
        assertThat(DialogButtons.labelsOf(DialogButtons.buttonsIn(dialog)))
                .as("but the words are what a refusal offers")
                .containsExactly("OK");
    }

    @Test
    void withNoMenuOpenTheRefusalSaysSoRatherThanListingEveryMenusItems() {
        // It listed forty items across File, Edit and View, of which none could be pointed at — a longer way
        // of saying nothing (found by asking a live editor, 2026-09-30)
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(new JMenuItem("Save"));
        JMenu edit = new JMenu("Edit");
        edit.add(new JMenuItem("Undo"));
        bar.add(file);
        bar.add(edit);

        assertThat(UiTargets.namesOf(bar, Kind.ITEM)).isEmpty();
        assertThat(((UiTargets.Outcome.Refused) UiTargets.resolve(bar, "DogsBay XML", Kind.ITEM, "zzz", 0))
                .message()).contains("No menu is open").doesNotContain("Undo");
    }

    // ── Decoration drawn into the row ───────────────────────────────────

    @Test
    void aRowIsNamedByItsWordsAndNotByTheIconsDrawnBesideThem() {
        // New XML Document draws "📄 Concept", and "⭐ 📄 Task" once a template has been used recently — so a
        // cue sheet naming the row could not survive its own recording
        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("📄 Concept");
        model.addElement("⭐ 📄 Task");
        JList<String> list = new JList<>(model);

        assertThat(UiTargets.find(list, Kind.ROW, "Concept")).hasSize(1);
        assertThat(UiTargets.find(list, Kind.ROW, "Task")).hasSize(1);
        assertThat(UiTargets.namesOf(list, Kind.ROW))
                .as("and the refusal offers the name a caller would type")
                .containsExactly("Concept", "Task");
    }

    @Test
    void aNameThatOpensWithPunctuationKeepsIt() {
        // A bracket is punctuation, not decoration: a drop-down reading "(none)" is still called that
        assertThat(DialogButtons.plain("(none)")).isEqualTo("(none)");
        assertThat(DialogButtons.plain("⭐ 📄 Task")).isEqualTo("Task");
        assertThat(DialogButtons.plain("  Concept  ")).isEqualTo("Concept");
        assertThat(DialogButtons.plain(null)).isEmpty();
    }

    // ── A renderer that is a panel of labels ────────────────────────────

    @Test
    void aRowDrawnByAPanelOfLabelsAnswersToAnyOfTheWordsItShows() {
        // Manage Projects' renderer extends JPanel, so reading only the top component gave
        // "com.dogsbay…ProjectProperties@1ca0f418" for a row that plainly reads "my-audacity-guide"
        DefaultListModel<Project> model = new DefaultListModel<>();
        model.addElement(new Project("my-audacity-guide"));
        JList<Project> list = new JList<>(model);
        list.setCellRenderer((l, value, i, sel, focus) -> {
            JPanel row = new JPanel();
            row.add(new JLabel());                          // an icon, with no words
            row.add(new JLabel(value.name()));              // the name
            row.add(new JLabel("/tmp/dogsbay-1/Documents"));// the detail underneath
            return row;
        });

        assertThat(UiTargets.find(list, Kind.ROW, "my-audacity-guide").get(0).text())
                .isEqualTo("my-audacity-guide");
        assertThat(UiTargets.find(list, Kind.ROW, "/tmp/dogsbay-1/Documents"))
                .as("which one of a row's labels is 'the name' is not something component order can say, so"
                        + " a row answers to any of them")
                .hasSize(1);
        assertThat(UiTargets.namesOf(list, Kind.ROW))
                .containsExactly("my-audacity-guide", "/tmp/dogsbay-1/Documents");
        assertThat(UiTargets.find(list, Kind.ROW, "com.dogsbay.ProjectProperties@3f2a")).isEmpty();
    }

    // ── Names that are nothing but a symbol ─────────────────────────────

    @Test
    void aButtonWhoseWholeNameIsASymbolKeepsIt() {
        // The Git panel's commit dropdown is a button called "▾" and nothing else — stripping its symbol left
        // it unnameable while the refusal went on listing it
        JPanel panel = new JPanel();
        JButton dropdown = new JButton("▾");
        panel.add(dropdown);

        assertThat(DialogButtons.plain("▾")).isEqualTo("▾");
        assertThat(UiTargets.find(panel, Kind.BUTTON, "▾").get(0).component()).isSameAs(dropdown);
        assertThat(DialogButtons.labelsOf(DialogButtons.buttonsIn(panel))).containsExactly("▾");
    }

    @Test
    void aDisabledButtonWithNoWordsIsRefusedRatherThanThrowing() {
        // An icon-only button's text is null, and those became reachable the moment tooltips started
        // matching — so the refusal used to throw a NullPointerException instead
        JPanel panel = new JPanel();
        JButton icon = new JButton();
        icon.setToolTipText("Split the Current Tab Horizontally.");
        icon.setEnabled(false);
        panel.add(icon);

        var outcome = DialogButtons.resolve(panel, "DogsBay XML", "Split the Current Tab Horizontally.");

        assertThat(((DialogButtons.Outcome.Refused) outcome).message())
                .contains("Split the Current Tab Horizontally.").contains("not available now");
    }

    @Test
    void aButtonsOwnWordsWinOverSomeoneElsesTooltip() {
        // Letting the two compete would make a toolbar icon tooltipped "Add" a rival for a dialog's Add
        // button, and clickButton has no way past an ambiguity
        JPanel dialog = new JPanel();
        JButton real = new JButton("Add");
        JButton icon = new JButton();
        icon.setToolTipText("Add");
        dialog.add(icon);
        dialog.add(real);

        assertThat(DialogButtons.matching(dialog, "Add", false)).containsExactly(real);
    }

    @Test
    void aNameWrittenAsHtmlGivesWayToTheTooltip() {
        // The Find bar's whole-word toggle is labelled <html>A<u>B</u></html> and known by its tooltip
        JPanel bar = new JPanel();
        JCheckBox whole = new JCheckBox("<html>A<u>B</u></html>");
        whole.setToolTipText("Match Whole Word");
        bar.add(whole);

        assertThat(DialogButtons.labelsOf(DialogButtons.buttonsIn(bar))).containsExactly("Match Whole Word");
        assertThat(UiTargets.find(bar, Kind.BUTTON, "Match Whole Word")).hasSize(1);
    }

    @Test
    void aButtonReportsTheNameItIsKnownBy() {
        // Create New Folder has no text, so the hit used to come back with an empty name while claiming to
        // report "the text as the thing itself spells it"
        JPanel dialog = new JPanel();
        JButton icon = new JButton();
        icon.setToolTipText("Create New Folder");
        dialog.add(icon);

        assertThat(UiTargets.find(dialog, Kind.BUTTON, "Create New Folder").get(0).text())
                .isEqualTo("Create New Folder");
    }

    @Test
    void twoButtonsOfOneNameAreListedOnce() {
        // Saying "Remove" twice in the list of what exists does not help anyone choose
        JPanel dialog = new JPanel();
        dialog.add(new JButton("Remove"));
        dialog.add(new JButton("Remove"));

        assertThat(DialogButtons.labelsOf(DialogButtons.buttonsIn(dialog))).containsExactly("Remove");
    }

    // ── A tree draws through a renderer like everything else ────────────

    @Test
    void aTreeRowIsNamedByWhatItsRendererDraws() {
        // convertValueToText was asked instead, which nothing in this editor overrides — so it was the node's
        // own toString under another name, and Where Used offered Reference[source=…, line=12, …] for a row
        // that reads "line 12 — xref"
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new Project("root"));
        root.add(new DefaultMutableTreeNode(new Project("line 12 — xref")));
        JTree tree = new JTree(root);
        tree.setCellRenderer((t, value, sel, exp, leaf, row, focus) ->
                new JLabel(((Project) ((DefaultMutableTreeNode) value).getUserObject()).name()));
        tree.expandRow(0);

        assertThat(UiTargets.find(tree, Kind.ROW, "line 12 — xref")).hasSize(1);
        assertThat(UiTargets.namesOf(tree, Kind.ROW)).contains("line 12 — xref")
                .doesNotContain("com.dogsbay.ProjectProperties@3f2a");
    }

    // ── A drop-down that is open ────────────────────────────────────────

    @Test
    void anOptionOfAnOpenDropDownIsFound() {
        // The whole of this kind was advertised in Kind.names() and had never been run by a test. A real
        // popup cannot be shown on a component that has never been on screen, so the popup is handed in —
        // the same way the open menu is.
        JComboBox<String> combo = new JComboBox<>(new String[] { "Blank XML Document", "Concept" });
        JPanel dialog = new JPanel();
        dialog.add(combo);
        DefaultListModel<String> shown = new DefaultListModel<>();
        shown.addElement("Blank XML Document");
        shown.addElement("Concept");
        JPanel popup = new JPanel();
        popup.add(new JList<>(shown));

        java.util.List<UiTargets.Target> found = new java.util.ArrayList<>();
        UiTargets.options(dialog, "Concept", found, c -> popup);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).text()).isEqualTo("Concept");
    }

    @Test
    void aDropDownThatIsNotShowingItsListOffersNoOptions() {
        JComboBox<String> combo = new JComboBox<>(new String[] { "Concept" });
        JPanel dialog = new JPanel();
        dialog.add(combo);

        assertThat(UiTargets.openPopupOf(combo)).as("nothing is open").isNull();
        java.util.List<UiTargets.Target> found = new java.util.ArrayList<>();
        UiTargets.options(dialog, "Concept", found, c -> null);
        assertThat(found).isEmpty();
    }

    // ── A text box inside a drop-down belongs to the drop-down ──────────

    @Test
    void anEditableDropDownIsNotAFieldInDisguise() {
        // The deliverable editor makes its combos editable, so the field kind answered for Transtype and the
        // refusal list advertised it as a field
        JPanel dialog = new JPanel();
        dialog.add(new JLabel("Transtype:"));
        JComboBox<String> combo = new JComboBox<>(new String[] { "html5" });
        combo.setEditable(true);
        dialog.add(combo);

        assertThat(UiTargets.find(dialog, Kind.FIELD, "Transtype")).isEmpty();
        assertThat(UiTargets.namesOf(dialog, Kind.FIELD)).doesNotContain("Transtype");
        assertThat(UiTargets.find(dialog, Kind.COMBO, "Transtype")).hasSize(1);
    }

    // ── nth that nobody can honour ──────────────────────────────────────

    @Test
    void anNthBeyondWhatIsThereIsRefusedNotRoundedDown() {
        // A cue sheet carrying nth=2 from a run where two rows matched must not quietly point at the first
        // one once the list has changed under it
        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("html5");
        JList<String> list = new JList<>(model);

        var outcome = UiTargets.resolve(list, "Deliverables", Kind.ROW, "html5", 2);

        assertThat(((UiTargets.Outcome.Refused) outcome).message())
                .contains("Asked for 2").contains("only 1");
    }

    @Test
    void aHiddenListOffersNoRows() {
        JPanel window = new JPanel();
        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("Concept");
        JList<String> list = new JList<>(model);
        list.setVisible(false);
        window.add(list);

        assertThat(UiTargets.find(window, Kind.ROW, "Concept")).isEmpty();
        assertThat(UiTargets.namesOf(window, Kind.ROW)).isEmpty();
    }

    // ── A cell, reached by where it is rather than what it says ─────────

    private static JTable deliverablesTable() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][] { { "full", "html5", "" }, { "book-pdf", "pdf", "" } },
                new Object[] { "Name", "Transtype", "Output" }));
        table.setSize(400, 60);
        return table;
    }

    @Test
    void findsACellByItsColumnAndItsRowNumber() {
        // The clicks this replaces are into cells that are empty — a row being filled in — which the row
        // kind cannot reach, because there is nothing written there to name
        var found = UiTargets.cells(deliverablesTable(), "Output", 2);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).within()).as("the cell's own box").isNotNull();
    }

    @Test
    void theLastRowIsReachableWithoutCountingIt() {
        JTable table = deliverablesTable();

        assertThat(UiTargets.cells(table, "Name", -1).get(0).within())
                .isEqualTo(table.getCellRect(1, 0, true));
    }

    @Test
    void aColumnNobodyHasFindsNothing() {
        assertThat(UiTargets.cells(deliverablesTable(), "Nope", 1)).isEmpty();
    }

    @Test
    void aRowBeyondTheTableFindsNothing() {
        assertThat(UiTargets.cells(deliverablesTable(), "Name", 9)).isEmpty();
        assertThat(UiTargets.cells(deliverablesTable(), "Name", 0)).isEmpty();
    }

    @Test
    void aHiddenTableHasNoCellsAndNoColumns() {
        JTable table = deliverablesTable();
        table.setVisible(false);
        JPanel dialog = new JPanel();
        dialog.add(table);

        assertThat(UiTargets.cells(dialog, "Name", 1)).isEmpty();
        assertThat(UiTargets.columnsOf(dialog)).isEmpty();
    }

    @Test
    void theColumnsAreListedForARefusal() {
        assertThat(UiTargets.columnsOf(deliverablesTable()))
                .containsExactly("Name", "Transtype", "Output");
    }

    // ── The status bar ──────────────────────────────────────────────────

    @Test
    void findsAStatusSegmentByTheTooltipItIsKnownBy() {
        // A StatusSegment is a panel, not a button, so no other kind could see it — and the deliverable chip
        // is the one clickable thing in the editor that is neither a button nor a menu
        com.dogsbay.dogsbayaieditor.StatusSegment chip =
                new com.dogsbay.dogsbayaieditor.StatusSegment("📦",
                        "Active deliverable (map · profile) — click to switch");
        JPanel statusBar = new JPanel();
        statusBar.add(chip);

        assertThat(UiTargets.find(statusBar, Kind.STATUS, "Active deliverable (map · profile) — click to switch"))
                .hasSize(1);
        assertThat(UiTargets.namesOf(statusBar, Kind.STATUS))
                .containsExactly("Active deliverable (map · profile) — click to switch");
    }

    @Test
    void aStatusSegmentIsAlsoFoundByWhatItShows() {
        com.dogsbay.dogsbayaieditor.StatusSegment chip =
                new com.dogsbay.dogsbayaieditor.StatusSegment("📦", "Active deliverable");
        chip.setValue("book-pdf", "No deliverable");
        JPanel statusBar = new JPanel();
        statusBar.add(chip);

        assertThat(UiTargets.find(statusBar, Kind.STATUS, "book-pdf")).hasSize(1);
    }

    @Test
    void everyKindACueSheetCanAskForIsNamed() {
        assertThat(Kind.names()).contains("text", "cell", "status");
        assertThat(Kind.of("cell")).isEqualTo(Kind.CELL);
        assertThat(Kind.of("TEXT")).isEqualTo(Kind.TEXT);
        assertThat(Kind.of("status")).isEqualTo(Kind.STATUS);
    }

    // ── What a panel scrolls, and where to ──────────────────────────────

    @Test
    void findsTheScrollPaneInsideAPanel() {
        // The Project Validation pane is a JList inside a ScrollableListPanel inside a JScrollPane
        JPanel inner = new JPanel();
        inner.add(new JList<>(new DefaultListModel<String>()));
        javax.swing.JScrollPane scroller = new javax.swing.JScrollPane(inner);
        JPanel content = new JPanel();
        content.add(scroller);

        assertThat(UiTargets.scrollerIn(content)).isSameAs(scroller);
    }

    @Test
    void aPanelWithNothingToScrollHasNoScrollPane() {
        assertThat(UiTargets.scrollerIn(new JPanel())).isNull();
    }

    @Test
    void theEndOfAPanelIsAsFarDownAsItGoes() {
        java.awt.Point at = new java.awt.Point(0, 0);
        java.awt.Dimension view = new java.awt.Dimension(200, 1000);
        java.awt.Dimension extent = new java.awt.Dimension(200, 300);

        assertThat(UiTargets.edgePosition(at, view, extent, true)).isEqualTo(new java.awt.Point(0, 700));
        assertThat(UiTargets.edgePosition(at, view, extent, false)).isEqualTo(new java.awt.Point(0, 0));
    }

    @Test
    void aPanelShorterThanItsWindowDoesNotScrollBackwards() {
        java.awt.Dimension view = new java.awt.Dimension(200, 100);
        java.awt.Dimension extent = new java.awt.Dimension(200, 300);

        assertThat(UiTargets.edgePosition(new java.awt.Point(0, 0), view, extent, true))
                .isEqualTo(new java.awt.Point(0, 0));
    }

    @Test
    void scrollingToTheEndLeavesSidewaysAlone() {
        // A reveal is asked to bring something into view vertically; shoving a horizontally scrolled panel
        // back to the left is a change nobody asked for
        assertThat(UiTargets.edgePosition(new java.awt.Point(120, 0),
                new java.awt.Dimension(900, 1000), new java.awt.Dimension(200, 300), true))
                .isEqualTo(new java.awt.Point(120, 700));
    }
}
