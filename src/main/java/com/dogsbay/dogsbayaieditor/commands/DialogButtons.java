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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.AbstractButton;
import javax.swing.JMenuItem;

/**
 * Finding the button a caller named, and saying why when there isn't one.
 *
 * <p>Everything about {@link ClickButtonCommand} that can be got wrong lives here, over a plain
 * {@link Container} and a list of window descriptions rather than live {@code Window}s — so it can be tested
 * without a display, and so the part that needs a running editor is small enough to see through.
 */
public final class DialogButtons {

    private DialogButtons() {
    }

    /** A window as the chooser needs to see it. */
    public record WindowInfo(String title, boolean showing, boolean active, boolean dialog) {
    }

    /**
     * Which window to look in: the one whose title was asked for, else the active one, else the only dialog
     * on screen, else the only window on screen, else nothing.
     *
     * <p>The active window is right for the ordinary case, because a modal dialog takes the focus when it
     * opens and that dialog is what the caller is driving. {@code wanted} is for the case it is not — a
     * caller pressing a button on a window it did not just open, or a run where something else has stolen
     * the focus. A title is matched whole first and then as a fragment, so
     * {@code "Manage DITA Deliverables"} and {@code "Deliverables"} both find the same window; a name that
     * fits more than one window is refused rather than guessed at, as an ambiguous button label is.
     *
     * @return the index into {@code windows}, or -1 when none of them fits, or more than one does
     */
    public static int chooseWindow(List<WindowInfo> windows, String wanted) {
        List<Integer> showing = new ArrayList<>();
        for (int i = 0; i < windows.size(); i++) {
            if (windows.get(i).showing()) {
                showing.add(i);
            }
        }
        if (wanted != null && !wanted.isBlank()) {
            String asked = wanted.trim();
            List<Integer> whole = showing.stream()
                    .filter(i -> asked.equalsIgnoreCase(title(windows.get(i)))).toList();
            if (!whole.isEmpty()) {
                return whole.size() == 1 ? whole.get(0) : -1;
            }
            List<Integer> fragment = showing.stream()
                    .filter(i -> title(windows.get(i)).toLowerCase(Locale.ROOT)
                            .contains(asked.toLowerCase(Locale.ROOT)))
                    .toList();
            return fragment.size() == 1 ? fragment.get(0) : -1;
        }
        for (int i : showing) {
            if (windows.get(i).active()) {
                return i;
            }
        }
        // Nothing is active. On the recording display this is the normal state, not an edge case: there is no
        // window manager there to give any window the focus, so after the first dialog closes nothing is
        // active again for the rest of the run (found by driving a real editor under Xvfb, 2026-09-30).
        //
        // One dialog on screen is what was meant — a dialog sits on top of the editor and is what a caller is
        // working. With no dialog at all, the only window showing is the editor itself and there is nothing
        // else it could be. Two dialogs are genuinely ambiguous, and guessing would press a button on
        // whichever happened to come first.
        List<Integer> dialogs = showing.stream().filter(i -> windows.get(i).dialog()).toList();
        if (!dialogs.isEmpty()) {
            return dialogs.size() == 1 ? dialogs.get(0) : -1;
        }
        return showing.size() == 1 ? showing.get(0) : -1;
    }

    private static String title(WindowInfo window) {
        return window.title() == null ? "" : window.title();
    }

    /** A window named for a message: its title, or that it has none. */
    public static String describe(String title) {
        return title == null || title.isBlank() ? "the window with no title" : "'" + title + "'";
    }

    /** The titles of the windows a caller could name, with untitled ones still counted. */
    public static List<String> nameable(List<WindowInfo> windows) {
        return windows.stream().filter(WindowInfo::showing)
                .map(w -> w.title() == null || w.title().isBlank() ? "(no title)" : w.title()).toList();
    }

    /**
     * Why no window could be chosen, in the words the caller needs.
     *
     * <p>Untitled windows are counted, not filtered out. Leaving them out let the refusal say "name one" and
     * then list nothing, about a screen with two untitled dialogs on it.
     */
    public static String noWindow(List<WindowInfo> windows, String wanted) {
        List<String> open = nameable(windows);
        if (wanted != null && !wanted.isBlank()) {
            return "No open window is called '" + wanted + "', or more than one is. Open: " + open;
        }
        return "No window is active, and there is not exactly one dialog open. Name one with 'window'."
                + " Open: " + open;
    }

    /** The button to press, or why there isn't one. */
    public sealed interface Outcome {

        /** The one showing, enabled button with that name. */
        record Found(AbstractButton button) implements Outcome {
        }

        /** No button to press, and what to tell the caller. */
        record Refused(String message) implements Outcome {
        }
    }

    /**
     * The button called {@code label} in {@code root}, or why there is none.
     *
     * <p>All of {@link ClickButtonCommand}'s refusals, in one place and over a plain container, so the words a
     * caller actually meets are checked by a test rather than only read. The order matters: a name that fits
     * nothing on screen is explained by listing what is there, and a name that fits something the reader
     * cannot press says which of those two it is, because "no such button" about a button they can see would
     * send them looking for a typo that is not there.
     *
     * @param root       the chosen window
     * @param windowName that window's title, for the message
     * @param label      the name the caller asked for
     */
    public static Outcome resolve(Container root, String windowName, String label) {
        List<AbstractButton> hits = matching(root, label, true);
        if (hits.isEmpty()) {
            // Ask again including what is not on screen, so a button in a tab the reader has not opened is
            // explained as hidden rather than reported missing.
            List<AbstractButton> hidden = matching(root, label, false);
            if (!hidden.isEmpty()) {
                return new Outcome.Refused("'" + label + "' is in " + describe(windowName)
                        + " but is not on screen.");
            }
            return new Outcome.Refused("No button called '" + label + "' in " + describe(windowName)
                    + ". Available: " + labelsOf(buttonsIn(root)));
        }
        if (hits.size() > 1) {
            return new Outcome.Refused(hits.size() + " buttons in " + describe(windowName) + " are called '"
                    + label + "'. This cannot say which one you meant.");
        }
        AbstractButton button = hits.get(0);
        if (!button.isEnabled()) {
            // displayNameOf, not getText(): an icon-only button's text is null, and those became reachable
            // the moment tooltips started matching — so this line used to throw instead of refusing.

            // Refused rather than quietly doing nothing, as an unavailable menu command is: a caller pressing
            // Delete with nothing selected needs to know that, not a success it can do nothing with.
            return new Outcome.Refused("'" + displayNameOf(button) + "' is not available now.");
        }
        return new Outcome.Found(button);
    }

    /** Every button under {@code root} that a person could press, in the order they appear. */
    public static List<AbstractButton> buttonsIn(Container root) {
        List<AbstractButton> found = new ArrayList<>();
        collect(root, found);
        return found;
    }

    private static void collect(Container container, List<AbstractButton> found) {
        for (Component child : container.getComponents()) {
            // A menu item is a button too, and invokeAction is how menus are driven — including one here
            // would make "Open" ambiguous between a dialog's button and a menu nobody can see.
            if (child instanceof AbstractButton button && !(child instanceof JMenuItem)) {
                found.add(button);
            }
            if (child instanceof Container inner) {
                collect(inner, found);
            }
        }
    }

    /**
     * Whether {@code component} is visible all the way up to {@code root}.
     *
     * <p>Not {@code isShowing()}, which also asks whether the window is on a display. The window was
     * established as on screen before we got here, so what is left to ask is the part that distinguishes a
     * card the reader is looking at from its siblings — {@code CardLayout} and {@code JTabbedPane} hide the
     * others by making them invisible. Asking only this also means the question can be put to a container in
     * a test, which a realised window cannot be.
     */
    static boolean visibleWithin(Component component, Container root) {
        for (Component at = component; at != null; at = at.getParent()) {
            if (!at.isVisible()) {
                return false;
            }
            if (at == root) {
                return true;
            }
        }
        return true;    // detached from the root we were given; nothing above it can hide it
    }

    /**
     * Everything a button answers to, normalised: its text, its tooltip, and the name it gives the
     * accessibility layer.
     *
     * <p>An icon-only button has no text at all — the file chooser's <b>Create New Folder</b> is one — so by
     * text alone it could not be named, and the only way to press it was a measured position (reported
     * 2026-09-30). Its tooltip is what the reader hovers to find out what it is, which makes it the name they
     * already know it by.
     */
    static List<String> namesOf(AbstractButton button) {
        List<String> names = new ArrayList<>();
        for (String candidate : new String[] { button.getText(), button.getToolTipText(), accessibleName(button) }) {
            String name = normalise(candidate);
            if (!name.isEmpty() && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private static String accessibleName(AbstractButton button) {
        try {
            return button.getAccessibleContext() == null ? null
                    : button.getAccessibleContext().getAccessibleName();
        } catch (RuntimeException noName) {
            return null;
        }
    }

    /**
     * What to call {@code button} in a message, and in the answer a caller gets back.
     *
     * <p>Its own words when it has any that a person could type; otherwise the tooltip it is known by. Words
     * written as HTML are not words anyone can ask for — the Find bar's whole-word toggle is labelled
     * {@code <html>A<u>B</u></html>} and known by its tooltip, "Match Whole Word" — so they give way to the
     * tooltip too.
     */
    public static String displayNameOf(AbstractButton button) {
        String text = button.getText();
        if (text != null && !text.isBlank() && !text.trim().toLowerCase(Locale.ROOT).startsWith("<html")) {
            return text.trim();
        }
        String tooltip = button.getToolTipText();
        if (tooltip != null && !tooltip.isBlank()) {
            return tooltip.trim();
        }
        String accessible = accessibleName(button);
        if (accessible != null && !accessible.isBlank()) {
            return accessible.trim();
        }
        return text == null ? "" : text.trim();
    }

    /**
     * The names of {@code buttons}, for an error that has to say what does exist.
     *
     * <p>One entry per name rather than per button: a dialog with two <b>Remove</b> buttons is ambiguous, and
     * saying so twice in the list of what exists does not help anyone choose.
     */
    public static List<String> labelsOf(List<AbstractButton> buttons) {
        List<String> names = new ArrayList<>();
        for (AbstractButton button : buttons) {
            String shown = displayNameOf(button);
            if (!shown.isBlank() && !names.contains(shown)) {
                names.add(shown);
            }
        }
        return names;
    }

    /**
     * The buttons under {@code root} whose label is {@code label}.
     *
     * <p>More than one is the caller's problem to disambiguate, not this method's to guess at: a dialog with
     * two buttons reading the same thing is unusual, and pressing the first of them because it was first in
     * the component tree is the kind of thing that goes unnoticed in a recording.
     *
     * <p>{@code onScreenOnly} decides whether a button the reader cannot see counts. It has to: the editor's
     * sidebars are cards of one {@code CardLayout}, so every panel is a child of the frame at once and only
     * the chosen one is showing. Several of them have a <b>Refresh</b> button, so counting the hidden ones
     * made the visible one unreachable — "3 buttons are called 'Refresh'" about a window showing exactly one.
     * The caller asks with {@code true} to decide what to press, and with {@code false} to explain why a
     * button that is in the window cannot be pressed.
     */
    public static List<AbstractButton> matching(Container root, String label, boolean onScreenOnly) {
        String wanted = normalise(label);
        List<AbstractButton> byWords = new ArrayList<>();
        List<AbstractButton> byTooltip = new ArrayList<>();
        for (AbstractButton button : buttonsIn(root)) {
            if (onScreenOnly && !visibleWithin(button, root)) {
                continue;
            }
            if (wanted.isEmpty()) {
                continue;
            }
            if (normalise(button.getText()).equals(wanted)) {
                byWords.add(button);
            } else if (namesOf(button).contains(wanted)) {
                byTooltip.add(button);
            }
        }
        // A button's own words win. A tooltip is a name of last resort, for a button that has none — letting
        // the two compete would turn a toolbar icon tooltipped "Add" into a rival for a dialog's Add button,
        // and clickButton has no way past an ambiguity.
        return byWords.isEmpty() ? byTooltip : byWords;
    }

    /**
     * A label reduced to what a caller can reasonably be expected to type: trimmed, without a trailing
     * ellipsis, lower case.
     *
     * <p>The ellipsis says the button opens something further, which is the dialog's business and not the
     * name of the button — asking for {@code Add} and being told there is only {@code Add…} would be a
     * riddle. Case, because {@code OK} is also written {@code Ok}.
     */
    public static String normalise(String label) {
        return plain(label).toLowerCase(Locale.ROOT);
    }

    /**
     * A name with its decoration removed, but its own case and words kept.
     *
     * <p>A list draws icons into the row's text — the New XML Document list offers {@code 📄 Concept}, and
     * {@code ⭐ 📄 Task} once a template has been used recently. The star comes and goes with what the reader
     * did last, so a cue sheet naming the row could not survive its own recording (reported 2026-09-30). Only
     * leading <em>symbols</em> go: a bracket is punctuation and stays, so a drop-down reading {@code (none)}
     * is still called that.
     */
    public static String plain(String label) {
        if (label == null) {
            return "";
        }
        String text = label.trim();
        int at = 0;
        while (at < text.length()) {
            int codePoint = text.codePointAt(at);
            int type = Character.getType(codePoint);
            boolean decoration = type == Character.OTHER_SYMBOL || type == Character.MODIFIER_SYMBOL
                    || type == Character.FORMAT || Character.isWhitespace(codePoint);
            if (!decoration) {
                break;
            }
            at += Character.charCount(codePoint);
        }
        String stripped = text.substring(at).trim();
        // Unless that was the whole name. The Git panel's commit dropdown is a button called "▾" and nothing
        // else, so stripping its symbol left it with no name at all — unnameable, while the refusal went on
        // listing it. A symbol is decoration only when there are words behind it.
        if (!stripped.isEmpty()) {
            text = stripped;
        }
        while (text.endsWith("…") || text.endsWith("...")) {
            String shorter = text.substring(0, text.length() - (text.endsWith("…") ? 1 : 3)).trim();
            if (shorter.isEmpty()) {
                // A button whose whole name is "..." — the folder chooser beside a path field is one. Strip
                // that and the name becomes nothing, which matches no button while the refusal goes on
                // listing it. An ellipsis is a suffix only when there is something in front of it.
                break;
            }
            text = shorter;
        }
        return text;
    }
}
