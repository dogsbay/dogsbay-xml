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

import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListModel;
import javax.swing.text.JTextComponent;
import javax.swing.tree.TreePath;

/**
 * Finding the thing a recording named, by the text a reader can see on it.
 *
 * <p>A position measured from a screenshot is a guess about a pixel; it moves when a window manager draws a
 * title bar, when the theme or the font changes, and when a dialog gains a line. The text is what the lesson
 * already names and what the reader is looking for, so it is what a cue sheet should say.
 *
 * <p>Every decision lives here, over a plain {@link Container}, and the boxes come back <em>relative to the
 * component that holds them</em> rather than in screen coordinates. That is deliberate: it is what lets all of
 * this be checked without a display, since a realised window is the one thing a headless test cannot have.
 */
public final class UiTargets {

    private UiTargets() {
    }

    /** What kind of thing to look for. Named by the caller, because the text alone is ambiguous. */
    public enum Kind {
        /** A button, found as {@link DialogButtons} finds one. */
        BUTTON,
        /** A menu in the window's menu bar. */
        MENU,
        /** An item of whatever menu is open now, a context menu included. */
        ITEM,
        /** A tab of a tabbed pane. */
        TAB,
        /** A row of a list, a table or a tree. */
        ROW,
        /** The text component a label names. */
        FIELD,
        /** A drop-down, by the option showing in it. */
        COMBO,
        /** An item of the open drop-down. */
        OPTION,
        /** A place in the open document, by the words there. */
        TEXT,
        /** A cell of a table, by its column and its row number — including an empty one. */
        CELL,
        /** A segment of the status bar, by the tooltip it is known by. */
        STATUS;

        /** The kind called {@code name}, or null when there is no such kind. */
        public static Kind of(String name) {
            if (name == null) {
                return null;
            }
            for (Kind kind : values()) {
                if (kind.name().equalsIgnoreCase(name.trim())) {
                    return kind;
                }
            }
            return null;
        }

        /** The names a caller may use, for a refusal that has to say what there is. */
        public static List<String> names() {
            return java.util.Arrays.stream(values())
                    .map(k -> k.name().toLowerCase(java.util.Locale.ROOT)).toList();
        }
    }

    /**
     * Something found on screen: the component to measure from, and the box within it.
     *
     * @param component what to take screen coordinates from
     * @param within    the box inside {@code component}, or null for the whole of it. A row and a tab are part
     *                  of a bigger component, so they are the reason this is here.
     * @param text      the text as the thing itself spells it, which may differ in case from what was asked
     */
    public record Target(Component component, Rectangle within, String text) {

        /** The box to point at, relative to {@code component}. */
        public Rectangle box() {
            return within != null ? new Rectangle(within)
                    : new Rectangle(0, 0, component.getWidth(), component.getHeight());
        }
    }

    /**
     * Everything of this kind under {@code root} whose text is {@code text}.
     *
     * <p>More than one is the caller's to disambiguate, not this method's to guess at — the same rule the
     * buttons follow, and for the same reason: picking the first in component order is the kind of mistake a
     * recording carries on past.
     */
    public static List<Target> find(Container root, Kind kind, String text) {
        List<Target> found = new ArrayList<>();
        switch (kind) {
            case BUTTON -> DialogButtons.matching(root, text, true)
                    .forEach(b -> addOnce(found, new Target(b, null, DialogButtons.displayNameOf(b))));
            case MENU -> menus(root, text, found);
            case ITEM -> items(root, text, found,
                    List.of(javax.swing.MenuSelectionManager.defaultManager().getSelectedPath()));
            case TAB -> tabs(root, text, found);
            case ROW -> rows(root, text, found);
            case FIELD -> fields(root, text, found);
            case COMBO -> combos(root, text, found);
            case OPTION -> options(root, text, found);
            case STATUS -> statuses(root, text, found);
            // Neither is found in a container: a document's text needs the editor's own pane and its offsets,
            // and a cell is asked for by position rather than by anything written on it.
            case TEXT, CELL -> { }
        }
        return found;
    }

    /**
     * The box of {@code box} that is actually on screen, or null when none of it is.
     *
     * <p>Visibility up the parent chain says nothing about a viewport. A row forty down a scrolled table, or a
     * document tab scrolled off the left, has bounds that are arithmetically right and point at a pixel the
     * viewport is not showing — a recording aiming there clicks the buttons below the table, or the sidebar
     * (reported 2026-09-30). Pure, and given the visible rectangle rather than asking for it, so the arithmetic
     * is checked rather than only read.
     */
    public static Rectangle onScreenPart(Rectangle box, Rectangle visible) {
        if (visible == null || visible.isEmpty()) {
            return box;     // nothing has laid this out yet; there is no viewport to be outside of
        }
        Rectangle shown = box.intersection(visible);
        return shown.isEmpty() ? null : shown;
    }

    /** What was found, or why nothing was. */
    public sealed interface Outcome {

        /** The one thing of that kind with that name. */
        record Found(Target target) implements Outcome {
        }

        /** Nothing to point at, and what to tell the caller. */
        record Refused(String message) implements Outcome {
        }
    }

    /**
     * The one thing called {@code text} in {@code root}, or why there is not one.
     *
     * <p>Every refusal {@code locate} can give, in one place and over a plain container, so the words a caller
     * meets are checked by a test rather than only read. That split is what {@code clickButton} does, and
     * leaving it out here is how a menu item that could never be found shipped with sixteen green tests.
     *
     * @param nth which one, when a name fits more than one thing; 1-based, 0 for "there should only be one"
     */
    public static Outcome resolve(Container root, String windowName, Kind kind, String text, int nth) {
        List<Target> hits = find(root, kind, text);
        if (hits.isEmpty()) {
            return new Outcome.Refused(nothingFound(root, windowName, kind, text));
        }
        if (nth >= 1 && nth != 1 && nth > hits.size()) {
            // A cue sheet carrying nth=2 from a run where two rows matched must not quietly point at the
            // first one once the list has changed under it.
            return new Outcome.Refused("Asked for " + nth + " of '" + text + "' in "
                    + DialogButtons.describe(windowName) + ", but there " + (hits.size() == 1 ? "is only 1"
                            : "are only " + hits.size()) + ".");
        }
        if (hits.size() > 1) {
            if (nth >= 1 && nth <= hits.size()) {
                return new Outcome.Found(hits.get(nth - 1));
            }
            // Where each of them is, because a caller told only how many has nothing to act on — and for a
            // list or a table two rows of the same text is ordinary rather than a mistake.
            StringBuilder where = new StringBuilder();
            for (int i = 0; i < hits.size(); i++) {
                Rectangle box = hits.get(i).box();
                where.append(i == 0 ? "" : ", ").append(i + 1).append(": ")
                        .append(box.x).append(',').append(box.y);
            }
            return new Outcome.Refused(hits.size() + " things in " + DialogButtons.describe(windowName)
                    + " are called '" + text + "' (" + where + "). Ask for one of them with 'nth'.");
        }
        return new Outcome.Found(hits.get(0));
    }

    /**
     * Why nothing of this kind is called that.
     *
     * <p>Asked a second time without the visibility rule, so something that is there but cannot be pointed at
     * is told apart from something that is not there at all. A refusal that says "no such row" and then lists
     * the row in its suggestions sends the caller hunting a typo that does not exist.
     */
    private static String nothingFound(Container root, String windowName, Kind kind, String text) {
        String what = kind.name().toLowerCase(java.util.Locale.ROOT);
        if (kind == Kind.OPTION && !anyPopupOpen(root)) {
            return "No drop-down is open in " + DialogButtons.describe(windowName)
                    + ", so it has no options to point at. Open one first.";
        }
        if (!findEverywhere(root, kind, text).isEmpty()) {
            return "'" + text + "' is in " + DialogButtons.describe(windowName)
                    + " but is not on screen"
                    + (kind == Kind.ITEM ? " — open its menu first." : ".");
        }
        List<String> available = namesOf(root, kind);
        if (kind == Kind.ITEM && available.isEmpty()) {
            // Asked after the name itself, so an item that is right but hidden still hears "open its menu"
            // about its own menu rather than this.
            return "No menu is open in " + DialogButtons.describe(windowName)
                    + ", so it has no items to point at. Open one first.";
        }
        return "No " + what + " called '" + text + "' in " + DialogButtons.describe(windowName)
                + ". Available: " + available;
    }

    /** Whether any drop-down in {@code root} is showing its list. */
    private static boolean anyPopupOpen(Container root) {
        boolean[] open = { false };
        forEach(root, JComboBox.class, combo -> open[0] |= combo.isPopupVisible());
        return open[0];
    }

    /** The same search, without asking whether the thing can be seen. */
    static List<Target> findEverywhere(Container root, Kind kind, String text) {
        List<Target> found = new ArrayList<>();
        switch (kind) {
            case BUTTON -> DialogButtons.matching(root, text, false)
                    .forEach(b -> addOnce(found, new Target(b, null, DialogButtons.displayNameOf(b))));
            case ITEM -> forEach(root, JMenuItem.class, item -> {
                if (!(item instanceof JMenu) && matches(item.getText(), text)) {
                    addOnce(found, new Target(item, null, item.getText()));
                }
            });
            case OPTION -> forEach(root, JComboBox.class, combo -> {
                for (int i = 0; i < combo.getItemCount(); i++) {
                    if (matches(String.valueOf(combo.getItemAt(i)), text)) {
                        addOnce(found, new Target(combo, null, String.valueOf(combo.getItemAt(i))));
                    }
                }
            });
            default -> found.addAll(find(root, kind, text));
        }
        return found;
    }

    /** The texts of this kind under {@code root}, for a refusal that has to say what does exist. */
    public static List<String> namesOf(Container root, Kind kind) {
        List<String> names = new ArrayList<>();
        switch (kind) {
            // Only what can be seen, for every kind. Listing the rest is how a refusal comes to name the very
            // thing it has just said is not there — the editor's sidebars are cards of one CardLayout, and
            // three of them have a Refresh button.
            case BUTTON -> names.addAll(DialogButtons.labelsOf(DialogButtons.buttonsIn(root).stream()
                    .filter(b -> DialogButtons.visibleWithin(b, root)).toList()));
            case MENU -> forEach(root, JMenu.class, m -> add(names, m.getText()));   // submenus too
            // Only the menu that is open. Listing every item of every menu offered forty names of which none
            // could be pointed at, which is a longer way of saying nothing.
            case ITEM -> forEach(root, JMenuItem.class, m -> {
                if (!(m instanceof JMenu) && visible(m, root)) {
                    add(names, m.getText());
                }
            });
            case TAB -> forEach(root, JTabbedPane.class, pane -> {
                if (!visible(pane, root)) {
                    return;
                }
                for (int i = 0; i < pane.getTabCount(); i++) {
                    add(names, pane.getTitleAt(i));
                }
            });
            case ROW -> rowTexts(root, names);
            // Only the labels that name something. Listing every label put whole HTML paragraphs into the
            // refusal — a dialog's explanatory text is a JLabel too — which is less use than saying nothing.
            case FIELD -> fieldNames(root, names);
            // A drop-down answers to what is showing in it; its options answer only while it is open. Sharing
            // one list offered every option as available and then refused every one of them.
            case COMBO -> {
                forEach(root, JLabel.class, label -> {
                    if (visible(label, root) && widgetNamedBy(label, root, JComboBox.class) != null) {
                        add(names, stripColon(label.getText()));
                    }
                });
                forEach(root, JComboBox.class, combo -> {
                    if (visible(combo, root)) {
                        add(names, String.valueOf(combo.getSelectedItem()));
                    }
                });
            }
            case OPTION -> forEach(root, JComboBox.class, combo -> {
                if (combo.isPopupVisible()) {
                    for (int i = 0; i < combo.getItemCount(); i++) {
                        add(names, String.valueOf(combo.getItemAt(i)));
                    }
                }
            });
            case STATUS -> forEach(root, com.dogsbay.dogsbayaieditor.StatusSegment.class, segment -> {
                if (visible(segment, root)) {
                    add(names, segment.getToolTipText());
                }
            });
            case TEXT, CELL -> { }
        }
        return names;
    }

    // ── The kinds ───────────────────────────────────────────────────────

    /**
     * A menu, at any depth.
     *
     * <p>A submenu is a menu: {@code Project Tools} is something you open, exactly as {@code Project} is, and
     * a caller writing {@code Project > Project Tools > Manage Deliverables} should not have to know that the
     * middle one is spelled differently. Only the menu bar's own were searched, which left every submenu
     * findable as no kind at all (reported 2026-09-30).
     */
    private static void menus(Container root, String text, List<Target> found) {
        forEach(root, JMenu.class, menu -> {
            if (matches(menu.getText(), text)) {
                addOnce(found, new Target(menu, null, menu.getText()));
            }
        });
    }

    /**
     * An item of an open menu.
     *
     * <p>A menu that is open is not part of the window's own tree when the toolkit gives it a window of its
     * own, which it does as soon as the menu would not fit inside the frame. So the open menu is asked for by
     * name — {@code MenuSelectionManager} is what Swing itself uses to know which menu is showing — and the
     * window it was found in does not come into it.
     */
    static void items(Container root, String text, List<Target> found,
            List<javax.swing.MenuElement> openMenu) {
        for (javax.swing.MenuElement element : openMenu) {
            if (element.getComponent() instanceof Container open) {
                forEach(open, JMenuItem.class, item -> {
                    if (!(item instanceof JMenu) && matches(item.getText(), text)) {
                        // Once, however many ways it was reached. Swing's path for an open menu-bar menu is
                        // [JMenuBar, JMenu, JPopupMenu], and all three lead to the same items — so every item
                        // of every menu was found three times and refused as ambiguous, which is the whole of
                        // what a recording uses this for (reported 2026-09-30). A fourth arrives when the
                        // pointer is over the item, since Swing appends it to the path.
                        addOnce(found, new Target(item, null, item.getText()));
                    }
                });
            }
        }
        if (found.isEmpty()) {
            // No menu open, or one whose popup lives in the window's own layered pane. What can be pointed at,
            // as everywhere else: an item of a menu nobody has opened is found by findEverywhere instead, and
            // told "open its menu first" rather than "no such item", which would send a caller looking for a
            // typo that is not there.
            forEach(root, JMenuItem.class, item -> {
                if (!(item instanceof JMenu) && matches(item.getText(), text) && visible(item, root)) {
                    addOnce(found, new Target(item, null, item.getText()));
                }
            });
        }
    }

    /** Add a target unless that very component is already there. */
    private static void addOnce(List<Target> found, Target target) {
        for (Target already : found) {
            if (already.component() == target.component()
                    && java.util.Objects.equals(already.within(), target.within())) {
                return;
            }
        }
        found.add(target);
    }

    private static void tabs(Container root, String text, List<Target> found) {
        forEach(root, JTabbedPane.class, pane -> {
            for (int i = 0; i < pane.getTabCount(); i++) {
                if (matches(pane.getTitleAt(i), text) && visible(pane, root)) {
                    Rectangle bounds = pane.getBoundsAt(i);
                    if (bounds != null) {
                        found.add(new Target(pane, bounds, pane.getTitleAt(i)));
                    }
                }
            }
        });
    }

    private static void rows(Container root, String text, List<Target> found) {
        forEach(root, JList.class, list -> {
            ListModel<?> model = list.getModel();
            for (int i = 0; i < model.getSize(); i++) {
                String shown = matching(rowNames(list, model.getElementAt(i), i), text);
                if (shown != null && visible(list, root)) {
                    Rectangle bounds = list.getCellBounds(i, i);
                    if (bounds != null) {
                        addOnce(found, new Target(list, bounds, shown));
                    }
                }
            }
        });
        forEach(root, JTable.class, table -> {
            for (int r = 0; r < table.getRowCount(); r++) {
                // One hit per row, at the first cell that matches. Per cell, an ordinary keydef whose keyword
                // text equals its key name gave two hits for the one row a caller can click, and there is no
                // column to disambiguate them with.
                for (int c = 0; c < table.getColumnCount(); c++) {
                    String shown = matching(cellNames(table, r, c), text);
                    if (shown != null && visible(table, root)) {
                        addOnce(found, new Target(table, table.getCellRect(r, c, true), shown));
                        break;
                    }
                }
            }
        });
        forEach(root, JTree.class, tree -> {
            for (int r = 0; r < tree.getRowCount(); r++) {
                TreePath path = tree.getPathForRow(r);
                if (path == null) {
                    continue;
                }
                String shown = matching(treeRowNames(tree, path, r), text);
                if (shown != null && visible(tree, root)) {
                    Rectangle bounds = tree.getRowBounds(r);
                    if (bounds != null) {
                        addOnce(found, new Target(tree, bounds, shown));
                    }
                }
            }
        });
    }

    /**
     * The text component a label names.
     *
     * <p>{@code getLabelFor()} when the dialog sets it, which is the only answer that is not a guess. When it
     * does not, the convention every dialog here follows is that the field comes after its label in the same
     * container, so the next text component is taken — and a label followed by nothing is refused rather than
     * paired with whatever happened to be nearby.
     */
    private static void fields(Container root, String text, List<Target> found) {
        labelled(root, text, JTextComponent.class, found);
    }

    /**
     * The widget of {@code type} that a label naming {@code text} names.
     *
     * <p>Shared by fields and drop-downs, because "the thing beside this label" is one rule and a caller
     * should not have to know which sort of widget a dialog used. A drop-down found only by the value showing
     * in it was unfindable when it was empty, and ambiguous when several read {@code (none)} — the Default
     * Root Map chooser is both (reported 2026-09-30).
     */
    private static <T extends Component> void labelled(Container root, String text, Class<T> type,
            List<Target> found) {
        forEach(root, JLabel.class, label -> {
            if (!matches(stripColon(label.getText()), text) || !visible(label, root)) {
                return;
            }
            T widget = widgetNamedBy(label, root, type);
            if (widget != null) {
                addOnce(found, new Target(widget, null, stripColon(label.getText())));
            }
        });
    }

    /**
     * The widget a label names, or null.
     *
     * <p>{@code getLabelFor()} when the dialog sets it — believed, or refused: falling through to the guess
     * when the named component is hidden, or is not the sort of widget asked for, would answer with something
     * the dialog explicitly did not mean. Otherwise the convention every dialog here follows, that the widget
     * comes after its label and before the next one.
     */
    private static <T extends Component> T widgetNamedBy(JLabel label, Container root, Class<T> type) {
        if (label.getLabelFor() != null) {
            return type.isInstance(label.getLabelFor()) && visible(label.getLabelFor(), root)
                    ? type.cast(label.getLabelFor()) : null;
        }
        return nextWidgetBeforeTheNextLabel(label, root, type);
    }

    /** A drop-down: by the label that names it, or by the value showing in it. */
    /**
     * A segment of the status bar, by the tooltip it is known by.
     *
     * <p>A {@code StatusSegment} is a panel, not a button, so no other kind can see it — and the deliverable
     * chip, which a recording clicks to switch deliverable, was the one clickable thing in the editor that is
     * neither a button nor a menu. It already carries the tooltip a reader hovers to find out what it is.
     */
    private static void statuses(Container root, String text, List<Target> found) {
        forEach(root, com.dogsbay.dogsbayaieditor.StatusSegment.class, segment -> {
            if (visible(segment, root)
                    && (matches(segment.getToolTipText(), text) || matches(segment.displayedText(), text))) {
                addOnce(found, new Target(segment, null,
                        DialogButtons.plain(segment.getToolTipText())));
            }
        });
    }

    /**
     * A cell of a table, by its column name and its row number.
     *
     * <p>By position rather than by anything written on it, because the clicks this replaces are into cells
     * that are <em>empty</em> — a row being filled in. The {@code row} kind stays for finding a row you can
     * name; this is for reaching a cell you cannot.
     *
     * @param column the column's heading
     * @param row    1-based, or -1 for the last row
     */
    public static List<Target> cells(Container root, String column, int row) {
        List<Target> found = new ArrayList<>();
        forEach(root, JTable.class, table -> {
            if (!visible(table, root) || table.getRowCount() == 0) {
                return;
            }
            for (int c = 0; c < table.getColumnCount(); c++) {
                if (!matches(table.getColumnName(c), column)) {
                    continue;
                }
                int index = row < 0 ? table.getRowCount() - 1 : row - 1;
                if (index >= 0 && index < table.getRowCount()) {
                    addOnce(found, new Target(table, table.getCellRect(index, c, true),
                            table.getColumnName(c) + " " + (index + 1)));
                }
            }
        });
        return found;
    }

    /**
     * The scroll pane inside {@code container}, or null when it holds none.
     *
     * <p>A pure walk, so what a panel scrolls can be checked without a window.
     */
    public static javax.swing.JScrollPane scrollerIn(Container container) {
        if (container instanceof javax.swing.JScrollPane scroller) {
            return scroller;
        }
        for (Component child : container.getComponents()) {
            if (child instanceof Container inner) {
                javax.swing.JScrollPane found = scrollerIn(inner);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Where a viewport sits when scrolled to one end.
     *
     * <p>Sideways is left alone: a reveal is asked to bring something into view vertically, and shoving a
     * horizontally scrolled panel back to the left is a change nobody asked for.
     *
     * @param at     where it is now
     * @param toEnd  the bottom when true, the top when false
     */
    public static java.awt.Point edgePosition(java.awt.Point at, java.awt.Dimension view,
            java.awt.Dimension extent, boolean toEnd) {
        return new java.awt.Point(at.x, toEnd ? Math.max(0, view.height - extent.height) : 0);
    }

    /** The column headings of the tables in {@code root}, for a refusal that has to say what there is. */
    public static List<String> columnsOf(Container root) {
        List<String> names = new ArrayList<>();
        forEach(root, JTable.class, table -> {
            if (visible(table, root)) {
                for (int c = 0; c < table.getColumnCount(); c++) {
                    add(names, table.getColumnName(c));
                }
            }
        });
        return names;
    }

    private static void combos(Container root, String text, List<Target> found) {
        labelled(root, text, JComboBox.class, found);
        forEach(root, JComboBox.class, combo -> {
            if (matches(String.valueOf(combo.getSelectedItem()), text) && visible(combo, root)) {
                addOnce(found, new Target(combo, null, String.valueOf(combo.getSelectedItem())));
            }
        });
    }

    /** An option of a drop-down that is open: its list lives in a popup, which Swing hands out on request. */
    private static void options(Container root, String text, List<Target> found) {
        options(root, text, found, UiTargets::openPopupOf);
    }

    /**
     * The list a drop-down is showing, or null when it is not showing one.
     *
     * <p>{@code getAccessibleChild} is the documented way to the popup, and the accessibility layer is the
     * one part of a look-and-feel that can be asked about a component without painting it. Passed in rather
     * than called directly so the rest of this can be checked without a window: a real popup cannot be shown
     * on a component that has never been on screen.
     */
    static Container openPopupOf(JComboBox<?> combo) {
        if (!combo.isPopupVisible()) {
            return null;
        }
        javax.accessibility.Accessible popup = combo.getUI().getAccessibleChild(combo, 0);
        return popup instanceof Container open ? open : null;
    }

    static void options(Container root, String text, List<Target> found,
            java.util.function.Function<JComboBox<?>, Container> popupOf) {
        forEach(root, JComboBox.class, combo -> {
            Container open = popupOf.apply(combo);
            if (open != null) {
                forEach(open, JList.class, list -> {
                    ListModel<?> model = list.getModel();
                    for (int i = 0; i < model.getSize(); i++) {
                        String shown = matching(rowNames(list, model.getElementAt(i), i), text);
                        if (shown != null) {
                            Rectangle bounds = list.getCellBounds(i, i);
                            if (bounds != null) {
                                addOnce(found, new Target(list, bounds, shown));
                            }
                        }
                    }
                });
            }
        });
    }

    // ── Shared ──────────────────────────────────────────────────────────

    /**
     * Every piece of text a rendered row draws, in the order it draws them.
     *
     * <p><b>All</b> of them, not the first. A renderer is often a panel of several labels and which one is
     * "the name" is not something component order can be trusted to say: the Search results renderer puts the
     * line and column first, so taking the first label named every match {@code [15,8]} — a position that
     * moves the moment the file is edited, which is the brittleness this exists to remove. Manage Projects
     * works the other way round only because its name panel happens to be added before its menu button.
     *
     * <p>So a row answers to any of the words it shows, and reports back the one that was asked for. The whole
     * set is what a refusal lists, which is also how a caller finds out what a row can be called.
     */
    private static List<String> wordsIn(Component component, List<String> into) {
        if (component instanceof JLabel label) {
            addWords(into, label.getText());
        } else if (component instanceof JTextComponent text) {
            addWords(into, text.getText());
        }
        if (component instanceof Container container && !(component instanceof JTextComponent)) {
            for (Component child : container.getComponents()) {
                wordsIn(child, into);
            }
        }
        return into;
    }

    private static void addWords(List<String> into, String text) {
        String plain = DialogButtons.plain(text);
        if (!plain.isEmpty() && !plain.toLowerCase(java.util.Locale.ROOT).startsWith("<html")
                && !into.contains(plain)) {
            into.add(plain);
        }
    }

    @SuppressWarnings("unchecked")
    private static javax.swing.ListCellRenderer<Object> asObjectRenderer(JList<?> list) {
        return (javax.swing.ListCellRenderer<Object>) list.getCellRenderer();
    }

    /** The names a list row answers to. */
    static List<String> rowNames(JList<?> list, Object value, int index) {
        try {
            javax.swing.ListCellRenderer<Object> renderer = asObjectRenderer(list);
            if (renderer != null) {
                List<String> words = wordsIn(
                        renderer.getListCellRendererComponent(list, value, index, false, false),
                        new ArrayList<>());
                if (!words.isEmpty()) {
                    return words;
                }
            }
        } catch (RuntimeException rendererObjected) {
            // fall through to the value's own text
        }
        return fallbackName(value);
    }

    /** The names a table cell answers to. */
    static List<String> cellNames(JTable table, int row, int column) {
        try {
            List<String> words = wordsIn(
                    table.prepareRenderer(table.getCellRenderer(row, column), row, column),
                    new ArrayList<>());
            if (!words.isEmpty()) {
                return words;
            }
        } catch (RuntimeException rendererObjected) {
            // fall through
        }
        return fallbackName(table.getValueAt(row, column));
    }

    /**
     * The names a tree row answers to.
     *
     * <p>Through the tree's renderer, like the others. {@code convertValueToText} was asked instead, which
     * nothing in this editor overrides — so it was the node's own {@code toString()} under another name, and
     * Where Used offered {@code Reference[source=/abs/path, line=12, …]} for a row that reads
     * {@code line 12 — xref} (reported 2026-10-01). That is the very symptom this was written to remove.
     */
    static List<String> treeRowNames(JTree tree, TreePath path, int row) {
        Object node = path.getLastPathComponent();
        try {
            javax.swing.tree.TreeCellRenderer renderer = tree.getCellRenderer();
            if (renderer != null) {
                List<String> words = wordsIn(renderer.getTreeCellRendererComponent(tree, node, false,
                        tree.isExpanded(row), tree.getModel().isLeaf(node), row, false), new ArrayList<>());
                if (!words.isEmpty()) {
                    return words;
                }
            }
            String converted = tree.convertValueToText(node, false, tree.isExpanded(row),
                    tree.getModel().isLeaf(node), row, false);
            if (converted != null && !converted.isBlank()) {
                return List.of(DialogButtons.plain(converted));
            }
        } catch (RuntimeException rendererObjected) {
            // fall through
        }
        return fallbackName(node);
    }

    private static List<String> fallbackName(Object value) {
        String plain = DialogButtons.plain(String.valueOf(value));
        return plain.isEmpty() ? List.of() : List.of(plain);
    }

    /** The name among {@code names} that {@code wanted} asked for, or null. */
    private static String matching(List<String> names, String wanted) {
        for (String name : names) {
            if (matches(name, wanted)) {
                return name;
            }
        }
        return null;
    }


    /** A label's text without the colon dialogs put after it, so a cue sheet says {@code Value}. */
    static String stripColon(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.endsWith(":") ? trimmed.substring(0, trimmed.length() - 1).trim() : trimmed;
    }

    /** The same forgiveness the buttons get: trimmed, ignoring case and a trailing ellipsis. */
    static boolean matches(String actual, String wanted) {
        String left = DialogButtons.normalise(actual);
        return !left.isEmpty() && left.equals(DialogButtons.normalise(wanted));
    }

    private static boolean visible(Component component, Container root) {
        return DialogButtons.visibleWithin(component, root);
    }


    /**
     * Add a name worth offering.
     *
     * <p>Not the prose. A dialog's explanatory paragraph is a {@code JLabel} like any other, and one of them
     * ran to several hundred characters of HTML — a refusal listing that is harder to read than one that lists
     * nothing. Anything written as HTML is skipped.
     */
    private static void add(List<String> names, String text) {
        String trimmed = DialogButtons.plain(text);
        if (trimmed.isEmpty() || trimmed.toLowerCase(java.util.Locale.ROOT).startsWith("<html")) {
            return;
        }
        // Whole, not shortened. A name cut short used to end in an ellipsis, and the matching strips a
        // trailing ellipsis — so every shortened suggestion was one that could never match what it named.
        if (!names.contains(trimmed)) {
            names.add(trimmed);
        }
    }

    /** The labels that name a field, as a caller would ask for them — without the dialog's colon. */
    private static void fieldNames(Container root, List<String> names) {
        forEach(root, JLabel.class, label -> {
            if (!visible(label, root)) {
                return;
            }
            if (widgetNamedBy(label, root, JTextComponent.class) != null) {
                add(names, stripColon(label.getText()));
            }
        });
    }

    private static void rowTexts(Container root, List<String> names) {
        forEach(root, JList.class, list -> {
            if (!visible(list, root)) {
                return;
            }
            for (int i = 0; i < list.getModel().getSize(); i++) {
                rowNames(list, list.getModel().getElementAt(i), i).forEach(n -> add(names, n));
            }
        });
        forEach(root, JTable.class, table -> {
            if (!visible(table, root)) {
                return;
            }
            for (int r = 0; r < table.getRowCount(); r++) {
                for (int c = 0; c < table.getColumnCount(); c++) {
                    cellNames(table, r, c).forEach(n -> add(names, n));
                }
            }
        });
        forEach(root, JTree.class, tree -> {
            if (!visible(tree, root)) {
                return;
            }
            for (int r = 0; r < tree.getRowCount(); r++) {
                TreePath path = tree.getPathForRow(r);
                if (path != null) {
                    treeRowNames(tree, path, r).forEach(n -> add(names, n));
                }
            }
        });
    }

    /**
     * The text component between {@code label} and the next label in the same container.
     *
     * <p>Stopping at the next label is what makes this a convention rather than a guess. Without it the scan
     * ran on until it found any text box at all, so a label whose own widget is not one — a drop-down that is
     * not editable, a checkbox, a radio group — quietly took the field belonging to a label further down:
     * Save Remote Document's <b>Protocol</b>, whose widget is a combo, answered with the <b>Host</b> box, and
     * the refusal list went on advertising Protocol as a field (reported 2026-09-30). A label with no text box
     * of its own now has none, which is what its own documentation always said.
     */
    private static <T extends Component> T nextWidgetBeforeTheNextLabel(JLabel label, Container root,
            Class<T> type) {
        Container parent = label.getParent();
        if (parent == null) {
            return null;
        }
        Component[] children = parent.getComponents();
        for (int i = 0; i < children.length; i++) {
            if (children[i] != label) {
                continue;
            }
            for (int j = i + 1; j < children.length; j++) {
                if (children[j] instanceof JLabel) {
                    return null;        // the next row has begun; this label names nothing of its own
                }
                T found = firstOfType(children[j], root, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T extends Component> T firstOfType(Component component, Container root, Class<T> type) {
        if (type.isInstance(component)) {
            return DialogButtons.visibleWithin(component, root) ? type.cast(component) : null;
        }
        if (component instanceof JComboBox && !JComboBox.class.isAssignableFrom(type)) {
            // An editable drop-down has a text box inside it, and that box is the drop-down's, not the next
            // thing a label could be naming. Descending into one made the field kind answer for Transtype and
            // the refusal list advertise it as a field (reported 2026-10-01).
            return null;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = firstOfType(child, root, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Every {@code type} under {@code container}, each visited once, in the order they appear.
     *
     * <p>A menu's items are reached through {@code getMenuComponents()}: they live in the menu's popup, which
     * is not among its children, so a walk over {@code getComponents()} alone never sees a single menu item.
     */
    private static <T> void forEach(Container container, Class<T> type, java.util.function.Consumer<T> visit) {
        visitOnce(container, type, visit);
    }

    private static <T> void visitOnce(Component component, Class<T> type,
            java.util.function.Consumer<T> visit) {
        if (type.isInstance(component)) {
            visit.accept(type.cast(component));
        }
        if (component instanceof JMenu menu) {
            for (Component item : menu.getMenuComponents()) {
                visitOnce(item, type, visit);
            }
            return;     // a JMenu's own getComponents() holds nothing else
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                visitOnce(child, type, visit);
            }
        }
    }
}
