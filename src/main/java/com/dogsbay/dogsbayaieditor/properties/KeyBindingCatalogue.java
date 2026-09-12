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

import java.util.List;

/**
 * The commands that can carry a key, and the key each one carries out of the
 * box.
 *
 * <p>This list is the product's, not a file's. The key settings used to be
 * whatever a saved file happened to contain, which failed in both directions:
 * a command that no longer existed stayed on offer forever, and a command
 * added to the product never appeared at all. Fifty-one per cent of the
 * declared bindings named things that had been gone for years — thirty-eight
 * for an XSLT debugger the product does not ship, thirty for a grid it does
 * not have — while nothing from the last two years could be bound to anything.
 *
 * <p>What a reader changes is kept as an override against these ids, so a
 * command that leaves the product takes its binding with it, and one that
 * arrives is bindable the day it lands.
 */
public final class KeyBindingCatalogue {

    private KeyBindingCatalogue() {
    }

    /** Every bindable command, in the order the settings list shows them. */
    public static List<KeyBinding> all() {
        return ALL;
    }

    private static final List<KeyBinding> ALL = List.of(
        // The commands that arrived while the key settings could not grow.
        // Ctrl+Alt switches what you are looking at; Ctrl+Shift acts on the
        // project. The three panel toggles keep the keys they already carried
        // as hard-coded accelerators, now changeable like everything else.
        binding(KeyPreferences.SHOW_EDITOR_VIEW_ACTION, "Show the Editor view", "View", "CTRL_ALT", "E"),
        binding(KeyPreferences.SHOW_AUTHOR_VIEW_ACTION, "Show the Author view", "View", "CTRL_ALT", "A"),
        binding(KeyPreferences.TOGGLE_AUTHOR_SPLIT_ACTION, "Show the Author view beside the source", "View", "CTRL_ALT", "S"),
        binding(KeyPreferences.TOGGLE_PRIMARY_SIDEBAR_ACTION, "Show or hide the primary sidebar", "View", "CTRL", "B"),
        binding(KeyPreferences.TOGGLE_BOTTOM_PANEL_ACTION, "Show or hide the bottom panel", "View", "CTRL", "J"),
        binding(KeyPreferences.TOGGLE_SECONDARY_SIDEBAR_ACTION, "Show or hide the secondary sidebar", "View", "CTRL_ALT", "B"),
        // The Agent commands are not here yet. Nothing in the product answers to
        // these ids — no menu item, no mode action — so a binding for one would
        // list a key on the Bindings page, do nothing when pressed, and still
        // reserve that key against every command that does work. They come back
        // with the actions behind them. CatalogueReachabilityTest is what keeps
        // that promise.
        binding(KeyPreferences.ADD_CDATA_ACTION, "Adds a CDATA section", "Edit", null, null),
        binding(KeyPreferences.BEGIN_ACTION, "Moves to beginning of data", "Editor", "CTRL", "HOME"),
        binding(KeyPreferences.BEGIN_LINE_ACTION, "Moves to beginning of line", "Editor", null, null),
        binding(KeyPreferences.CLOSE_ACTION, "Close current file", "File", "CTRL", "W"),
        binding(KeyPreferences.CLOSE_ALL_ACTION, "Close all files", "File", "CTRL_SHIFT", "W"),
        binding(KeyPreferences.COMMENT_ACTION, "Add a comment", "Edit", "CTRL", "K"),
        binding(KeyPreferences.CONVERT_CHARACTERS_ACTION, "Convert Characters to Entities", "Edit", null, null),
        binding(KeyPreferences.CONVERT_ENTITIES_ACTION, "Convert Entities to Characters", "Edit", null, null),
        binding(KeyPreferences.COPY_ACTION, "Copy", "Editor", "CTRL", "C"),
        binding(KeyPreferences.CREATE_TYPE_ACTION, "Create a new Type", "Schema", null, null),
        binding(KeyPreferences.CUT_ACTION, "Cut", "Editor", "CTRL", "X"),
        binding(KeyPreferences.DELETE_NEXT_CHAR_ACTION, "Deletes the next character", "Editor", null, null),
        binding(KeyPreferences.DELETE_PREV_CHAR_ACTION, "Deletes the previous character", "Editor", null, null),
        binding(KeyPreferences.DOWN_ACTION, "Moves insertion point down one line", "Editor", null, null),
        binding(KeyPreferences.END_ACTION, "Moves to end of data", "Editor", "CTRL", "END"),
        binding(KeyPreferences.END_LINE_ACTION, "Moves to end of line", "Editor", null, null),
        binding(KeyPreferences.EXECUTE_ADVANCED_XSLT_ACTION, "Execute Advanced XSLT", "Transform", null, null),
        binding(KeyPreferences.EXECUTE_FO_ACTION, "Execute FO", "Transform", null, null),
        binding(KeyPreferences.EXECUTE_PREVIOUS_FO_ACTION, "Execute Previous FO", "Transform", null, null),
        binding(KeyPreferences.EXECUTE_PREVIOUS_SCENARIO_ACTION, "Execute Previous Scenario", "Transform", null, null),
        binding(KeyPreferences.EXECUTE_PREVIOUS_XSLT_ACTION, "Execute Previous XSLT", "Transform", null, null),
        binding(KeyPreferences.EXECUTE_SCENARIO_ACTION, "Execute Scenario", "Transform", null, null),
        binding(KeyPreferences.EXECUTE_SIMPLE_XSLT_ACTION, "Execute Simple XSLT", "Transform", null, null),
        binding(KeyPreferences.FIND_ACTION, "Find in current document", "Edit", "CTRL", "F"),
        binding(KeyPreferences.FIND_IN_FILES_ACTION, "Find in Files", "File", null, null),
        binding(KeyPreferences.FORMAT_ACTION, "Format the XML", "Edit", null, null),
        binding(KeyPreferences.GOTO_ACTION, "Goto line number", "Edit", "CTRL", "G"),
        binding(KeyPreferences.GOTO_END_TAG_ACTION, "Goto end tag", "Edit", "CTRL", "DOWN"),
        binding(KeyPreferences.GOTO_NEXT_ATTRIBUTE_VALUE_ACTION, "Goto next Attribute value", "Edit", "CTRL_SHIFT", "DOWN"),
        binding(KeyPreferences.GOTO_PREVIOUS_ATTRIBUTE_VALUE_ACTION, "Goto previous Attribute value", "Edit", "CTRL_SHIFT", "UP"),
        binding(KeyPreferences.GOTO_START_TAG_ACTION, "Goto start tag", "Edit", "CTRL", "UP"),
        binding(KeyPreferences.HIGHLIGHT_ACTION, "Highlight", "View", null, null),
        binding(KeyPreferences.INSERT_SPECIAL_CHAR_ACTION, "Insert Special Character", "Edit", "CTRL", "I"),
        binding(KeyPreferences.LEFT_ACTION, "Moves insertion point to the left one", "Editor", null, null),
        binding(KeyPreferences.LOCK_ACTION, "Lock elements and attributes", "Edit", null, null),
        binding(KeyPreferences.MANAGE_SCENARIOS_ACTION, "Manage Scenarios", "Transform", null, null),
        binding(KeyPreferences.MANAGE_TEMPLATE_ACTION, "Manage Templates", "File", null, null),
        binding(KeyPreferences.MANAGE_TYPES_ACTION, "Manage Types", "Schema", null, null),
        binding(KeyPreferences.NEW_DOCUMENT_ACTION, "New Document", "File", "CTRL", "N"),
        binding(KeyPreferences.NEXT_WORD_ACTION, "Moves to beginning of next word", "Editor", "CTRL", "RIGHT"),
        binding(KeyPreferences.OPEN_ACTION, "Open a file", "File", "CTRL", "O"),
        binding(KeyPreferences.PAGE_DOWN_ACTION, "Moves down one information pane", "Editor", null, null),
        binding(KeyPreferences.PAGE_UP_ACTION, "Moves up one information pane", "Editor", null, null),
        binding(KeyPreferences.PASTE_ACTION, "Paste", "Editor", "CTRL", "V"),
        binding(KeyPreferences.PREFERENCES_ACTION, "Show Preferences Settings", "File", null, null),
        // Ctrl+Shift+V is what VS Code puts preview on, and readers arrive
        // with that in their fingers. Its preview-to-the-side is the chord
        // Ctrl+K V, which a single keystroke cannot express, so the split
        // takes the Ctrl+Alt key its neighbours in the View family use.
        binding(KeyPreferences.PREVIEW_IN_SPLIT_ACTION, "Preview in Split", "Tools", "CTRL_ALT", "P"),
        binding(KeyPreferences.PREVIEW_IN_TAB_ACTION, "Preview in Tab", "Tools", "CTRL_SHIFT", "V"),
        binding(KeyPreferences.PREVIOUS_WORD_ACTION, "Moves to beginning of previous word", "Editor", "CTRL", "LEFT"),
        binding(KeyPreferences.REDO_ACTION, "Redo previous undo", "Edit", "CTRL", "Y"),
        binding(KeyPreferences.RELOAD_ACTION, "Reload the document", "File", null, null),
        binding(KeyPreferences.RENAME_ELEMENT_ACTION, "Rename an Element", "Edit", "CTRL", "R"),
        binding(KeyPreferences.REPEAT_TAG_ACTION, "Repeat last Tag", "Edit", "CTRL_SHIFT", "T"),
        binding(KeyPreferences.REPLACE_ACTION, "Replace in current document", "Edit", "CTRL", "H"),
        binding(KeyPreferences.RESOLVE_XINCLUDES_ACTION, "Resolve XIncludes", "XML", null, null),
        binding(KeyPreferences.RIGHT_ACTION, "Moves insertion point to the right one", "Editor", null, null),
        binding(KeyPreferences.SAVE_ACTION, "Save current file", "File", "CTRL", "S"),
        binding(KeyPreferences.SAVE_ALL_ACTION, "Save all files", "File", "CTRL_SHIFT", "S"),
        binding(KeyPreferences.SAVE_AS_ACTION, "Save file as", "File", null, null),
        binding(KeyPreferences.SAVE_AS_TEMPLATE_ACTION, "Save the current document as a template", "File", null, null),
        binding(KeyPreferences.SELECT_ALL_ACTION, "Select All", "Editor", "CTRL", "A"),
        binding(KeyPreferences.SELECT_BOOKMARK_ACTION, "Select Bookmark", "Edit", "CTRL_SHIFT", "B"),
        binding(KeyPreferences.SELECT_DOCUMENT_ACTION, "Select Document", "View", "ALT", "1"),
        binding(KeyPreferences.SELECT_ELEMENT_ACTION, "Select Element", "Edit", "CTRL", "E"),
        binding(KeyPreferences.SELECT_ELEMENT_CONTENT_ACTION, "Select Element Content", "Edit", "CTRL_SHIFT", "E"),
        binding(KeyPreferences.SELECT_FRAGMENT_ACTION, "Select Fragment", "Edit", "CTRL_SHIFT", "SPACE"),
        binding(KeyPreferences.SET_TYPE_ACTION, "Set the Type", "Schema", null, null),
        binding(KeyPreferences.SPLIT_ELEMENT_ACTION, "Splits the element", "Edit", null, null),
        binding(KeyPreferences.START_BROWSER_ACTION, "Start Browser", "Tools", null, null),
        binding(KeyPreferences.STRIP_TAG_ACTION, "Strips out the tags", "Edit", null, null),
        binding(KeyPreferences.TAB_ACTION, "Indent", "Edit", null, "TAB"),
        binding(KeyPreferences.TAG_ACTION, "Tag", "Edit", "CTRL", "T"),
        // Ctrl+B was declared here and also hard-coded onto the primary sidebar
        // toggle, so the sidebar has been quietly winning it. The sidebar keeps
        // it, being what every other editor does with that key; bookmarks move
        // to a free one rather than to a fight nobody could see.
        binding(KeyPreferences.TOGGLE_BOOKMARK_ACTION, "Toggle bookmark", "Edit", "CTRL", "M"),
        binding(KeyPreferences.TOGGLE_EMPTY_ELEMENT_ACTION, "Expand an empty Element", "Edit", "CTRL", "L"),
        binding(KeyPreferences.TOGGLE_FULL_ACTION, "Toggle Full Screen Editing", "View", null, null),
        binding(KeyPreferences.TOOLS_ADD_NODE_ACTION, "Add a Node", "XML", null, null),
        binding(KeyPreferences.TOOLS_ADD_NODE_TO_NS_ACTION, "Add a Node To a Namespace", "XML", null, null),
        binding(KeyPreferences.TOOLS_CAPITALIZE_ACTION, "Capitalize", "XML", null, null),
        binding(KeyPreferences.TOOLS_CHANGE_NS_PREFIX_ACTION, "Change A Namespace Prefix", "XML", null, null),
        binding(KeyPreferences.TOOLS_CONVERT_NODE_ACTION, "Convert a Node Type", "XML", null, null),
        binding(KeyPreferences.TOOLS_DECAPITALIZE_ACTION, "DeCapitalize", "XML", null, null),
        binding(KeyPreferences.TOOLS_EMPTY_DOCUMENT_ACTION, "Empty Document", "XML", null, null),
        binding(KeyPreferences.TOOLS_LOWERCASE_ACTION, "Lowercase", "XML", null, null),
        binding(KeyPreferences.TOOLS_MOVE_NS_TO_FIRST_USED_ACTION, "Move All Namespace Declarations To Where They Are First Used", "XML", null, null),
        binding(KeyPreferences.TOOLS_MOVE_NS_TO_ROOT_ACTION, "Move All Namespace Declarations To The Root", "XML", null, null),
        binding(KeyPreferences.TOOLS_REMOVE_NODE_ACTION, "Remove a Node", "XML", null, null),
        binding(KeyPreferences.TOOLS_REMOVE_UNUSED_NS_ACTION, "Remove Any Usused Namespaces", "XML", null, null),
        binding(KeyPreferences.TOOLS_RENAME_NODE_ACTION, "Rename a Node", "XML", null, null),
        binding(KeyPreferences.TOOLS_SET_NODE_VALUE_ACTION, "Set Node Value", "XML", null, null),
        binding(KeyPreferences.TOOLS_SORT_NODE_ACTION, "Sort a Node Type", "XML", null, null),
        binding(KeyPreferences.TOOLS_UPPERCASE_ACTION, "Uppercase", "XML", null, null),
        binding(KeyPreferences.TYPE_PROPERTIES_ACTION, "Type Properties", "Schema", null, null),
        binding(KeyPreferences.UNDO_ACTION, "Undo last edit", "Edit", "CTRL", "Z"),
        binding(KeyPreferences.UNINDENT_ACTION, "Unindent", "Edit", "SHIFT", "TAB"),
        binding(KeyPreferences.UP_ACTION, "Moves insertion point up one line", "Editor", null, null),
        binding(KeyPreferences.VALIDATE_ACTION, "Validate against a schema", "XML", null, null),
        binding(KeyPreferences.VIEWER_INLINE_MIXED_CONTENT_ACTION, "Inline mixed content in the Viewer", "View", null, null),
        binding(KeyPreferences.VIEWER_SHOW_ATTRIBUTES_ACTION, "Show attributes in the Viewer", "View", null, null),
        binding(KeyPreferences.VIEWER_SHOW_COMMENTS_ACTION, "Show comments in the Viewer", "View", null, null),
        binding(KeyPreferences.VIEWER_SHOW_NAMESPACES_ACTION, "Show namespace in the Viewer", "View", null, null),
        binding(KeyPreferences.VIEWER_SHOW_PROCESSING_INSTRUCTIONS_ACTION, "Show processing instructions in the Viewer", "View", null, null),
        binding(KeyPreferences.VIEWER_SHOW_TEXT_CONTENT_ACTION, "Show text content in the Viewer", "View", null, null),
        binding(KeyPreferences.VIEW_EDITOR_BUTTONS_ACTION, "View Editor buttons", "View", null, null),
        binding(KeyPreferences.VIEW_EDITOR_END_TAG_COMPLETION_ACTION, "Use the Editor's end tag completion", "View", null, null),
        binding(KeyPreferences.VIEW_EDITOR_ERROR_HIGHLIGHTING_ACTION, "Highlight errors in the Editor", "View", null, null),
        binding(KeyPreferences.VIEW_EDITOR_SHOW_ANNOTATION_ACTION, "Show the Editor's annotation margin", "View", null, null),
        binding(KeyPreferences.VIEW_EDITOR_SHOW_FOLDING_ACTION, "Show the Editor's folding margin", "View", null, null),
        binding(KeyPreferences.VIEW_EDITOR_SHOW_LINE_NUMBER_ACTION, "Show the Editor's line number margin", "View", null, null),
        binding(KeyPreferences.VIEW_EDITOR_SHOW_OVERVIEW_ACTION, "Show the Editor's overview margin", "View", null, null),
        binding(KeyPreferences.VIEW_EDITOR_SMART_INDENTATION_ACTION, "Use the Editor's smart indentation", "View", null, null),
        binding(KeyPreferences.VIEW_EDITOR_SOFT_WRAPPING_ACTION, "Use soft wrapping for the Editor", "View", null, null),
        binding(KeyPreferences.VIEW_EDITOR_TAG_COMPLETION_ACTION, "Use the Editor's tag completion", "View", null, null),
        binding(KeyPreferences.VIEW_FRAGMENT_BUTTONS_ACTION, "View fragment buttons", "View", null, null),
        binding(KeyPreferences.VIEW_SPLIT_HORIZONTALLY_ACTION, "Split horizontally", "View", null, null),
        binding(KeyPreferences.VIEW_SPLIT_VERTICALLY_ACTION, "Split vertically", "View", null, null),
        binding(KeyPreferences.VIEW_STANDARD_BUTTONS_ACTION, "View standard buttons", "View", null, null),
        binding(KeyPreferences.VIEW_SYNCHRONIZE_SPLITS_ACTION, "Synchronise the splits", "View", null, null),
        binding(KeyPreferences.VIEW_UNSPLIT_ACTION, "Unsplit", "View", null, null),
        binding(KeyPreferences.WELL_FORMEDNESS_ACTION, "Check Well-formedness", "XML", null, null),
        binding(KeyPreferences.XDIFF_ACTION, "XML Diff and Merge functionality", "Tools", null, null)
    );

    private static KeyBinding binding(String id, String label, String category,
            String mask, String key) {
        return new KeyBinding(id, label, category, KeyBinding.stroke(mask, key));
    }
}
