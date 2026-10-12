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

import javax.swing.text.PlainDocument;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the caret and element-selection commands.
 *
 * <p>These four were ported out of the legacy Rhino scripting API, which reached
 * them only from the in-editor JavaScript console. They were the one capability
 * the command engine lacked, so they broke the CLI/API parity it exists to
 * provide. See plans/remove-rhino-scripting.md.
 *
 * <p>The offset-to-line/column maths is tested against a plain Swing document;
 * the executor itself needs a running editor and is covered by the UI tests.
 */
class CaretCommandsTest {

    private static PlainDocument doc(String text) throws Exception {
        PlainDocument d = new PlainDocument();
        d.insertString(0, text, null);
        return d;
    }

    private static javax.swing.text.Element root(String text) throws Exception {
        return doc(text).getDefaultRootElement();
    }

    // ── Offset to line/column ───────────────────────────────────────────

    @Test
    void firstCharacterIsLineOneColumnOne() throws Exception {
        var pos = CaretPositions.at(root("hello\nworld"), 0);

        assertEquals(0, pos.offset());
        assertEquals(1, pos.line());
        assertEquals(1, pos.column(), "line and column are 1-based, like the status bar");
    }

    @Test
    void columnCountsFromTheStartOfItsOwnLine() throws Exception {
        // "hello\nworld" — offset 8 is the 'r' on line 2, third character.
        var pos = CaretPositions.at(root("hello\nworld"), 8);

        assertEquals(2, pos.line());
        assertEquals(3, pos.column());
    }

    @Test
    void startOfALaterLineIsColumnOne() throws Exception {
        // offset 6 is 'w', the first character after the newline.
        var pos = CaretPositions.at(root("hello\nworld"), 6);

        assertEquals(2, pos.line());
        assertEquals(1, pos.column());
    }

    @Test
    void offsetOfANewlineBelongsToTheLineItEnds() throws Exception {
        // offset 5 is the '\n' itself: still line 1, one past "hello".
        var pos = CaretPositions.at(root("hello\nworld"), 5);

        assertEquals(1, pos.line());
        assertEquals(6, pos.column());
    }

    @Test
    void handlesManyLines() throws Exception {
        var pos = CaretPositions.at(root("a\nb\nc\nd\ne"), 6);

        assertEquals(4, pos.line());
        assertEquals(1, pos.column());
    }

    @Test
    void emptyDocumentIsLineOneColumnOne() throws Exception {
        // Swing always reports at least one line, so this must not blow up.
        var pos = CaretPositions.at(root(""), 0);

        assertEquals(1, pos.line());
        assertEquals(1, pos.column());
    }

    @Test
    void blankLineBetweenContentIsItsOwnLine() throws Exception {
        // "a\n\nb" — offset 2 is the empty second line.
        var pos = CaretPositions.at(root("a\n\nb"), 2);

        assertEquals(2, pos.line());
        assertEquals(1, pos.column());
    }

    // ── Command shape ───────────────────────────────────────────────────

    @Test
    void setCursorCarriesTheRelativeFlag() {
        assertFalse(new SetCursorCommand(40, false).relative(), "absolute offset");
        assertTrue(new SetCursorCommand(-5, true).relative(),
                "a signed delta — this is what the legacy moveCursorPosition did");
        assertEquals(-5, new SetCursorCommand(-5, true).position(),
                "a negative delta is legitimate: move the caret backwards");
    }

    @Test
    void selectElementDistinguishesContentFromWholeElement() {
        assertFalse(new SelectElementCommand(false).contentOnly());
        assertTrue(new SelectElementCommand(true).contentOnly());
    }

    // ── Headless behaviour ──────────────────────────────────────────────

    @Test
    void caretCommandsRequireARunningEditor() {
        HeadlessExecutor executor = new HeadlessExecutor();

        for (Command<?> command : new Command<?>[] {
                new GotoLineCommand(1),
                new GetCursorCommand(),
                new SetCursorCommand(0, false),
                new SelectElementCommand(false)}) {
            CommandException thrown = assertThrows(CommandException.class,
                    () -> executor.execute(command),
                    command.getClass().getSimpleName() + " must not claim to work headless");
            assertEquals(CommandException.ErrorCode.EDITOR_NOT_RUNNING, thrown.getCode());
        }
    }

    // ── Registration ────────────────────────────────────────────────────

    @Test
    void caretCommandsAreDiscoverable() {
        CommandRegistry registry = new CommandRegistry();

        for (String name : new String[] {
                "goto-line", "get-cursor", "set-cursor", "select-element"}) {
            var meta = registry.getCommand(name);
            assertNotNull(meta, name + " must be registered for CLI/MCP discovery");
            assertTrue(meta.requiresEditor(), name + " operates on the live buffer");
        }
    }
}
