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

package com.dogsbay.dogsbayaieditor;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards two {@link DogsBayView} methods against being silently emptied.
 *
 * <p>This is a source-level check, which is unusual, and it exists because the
 * same accident has now happened three times in one commit — {@code reload()},
 * {@code updateModel()} and {@code setSchemaInternal()} each lost their entire
 * body when a removal deleted one branch of an if/else chain and took the rest
 * with it. Every occurrence compiled, and every one passed the full suite:
 *
 * <ul>
 *   <li>{@code reload()} — the editor stopped showing external changes, and the
 *       next save wrote the stale buffer back over them.</li>
 *   <li>{@code updateModel()} — worse: the editor's text never reached the
 *       document model, so <em>every save wrote stale text</em> and discarded
 *       whatever had been typed.</li>
 * </ul>
 *
 * <p>Both are view-dispatch methods on a class that needs a full application
 * frame to instantiate, so neither is reachable from the unit tier and neither
 * has behavioural coverage. Until the editor-level UI harness exists, asserting
 * that the dispatch is still present is worth more than nothing — an emptied
 * body is exactly the failure mode, and it is trivially detectable.
 *
 * <p>If these methods are legitimately restructured, update the expectations
 * here rather than deleting the test.
 */
class DogsBayViewDispatchTest {

    private static String source;

    @BeforeAll
    static void readSource() throws Exception {
        Path file = Path.of("src/main/java/com/dogsbay/dogsbayaieditor/DogsBayView.java");
        assertTrue(Files.isRegularFile(file), "cannot locate DogsBayView source at " + file);
        source = Files.readString(file);
    }

    /** The body of a method, by brace matching from its signature. */
    private static String bodyOf(String methodSignature) {
        // Match on a whitespace-insensitive form: an exact-text search would break
        // spuriously the first time anyone reformats the file, and report it as a
        // missing method. Brace counting below ignores string literals, which is
        // safe only while these method bodies contain no braces in literals.
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(methodSignature.replaceAll("\\s+", "\\\\s*")
                        .replace("(", "\\(").replace(")", "\\)"))
                .matcher(source);
        assertTrue(m.find(), "method not found: " + methodSignature);
        int start = m.start();
        int open = source.indexOf('{', start);
        int depth = 0;
        int i = open;
        while (i < source.length()) {
            if (source.charAt(i) == '{') {
                depth++;
            } else if (source.charAt(i) == '}') {
                depth--;
                if (depth == 0) {
                    break;
                }
            }
            i++;
        }
        return source.substring(open + 1, i);
    }

    /**
     * {@code save()} writes the document model, and this is the only thing that
     * puts the editor's text into it. Emptied, every save silently discards the
     * user's edits.
     */
    @Test
    void updateModelStillPushesTheEditorBufferIntoTheModel() {
        String body = bodyOf("public void updateModel()");

        assertTrue(body.contains("editor.parse()"),
                "updateModel() must call Editor.parse() — without it save() writes "
                        + "stale text and loses everything typed since the last parse");
        assertTrue(body.contains("instanceof Editor"),
                "updateModel() must dispatch on the active view");
    }

    /**
     * Reloading refreshes the model; this is what puts the model on screen. Emptied,
     * external changes (agent edits, Git operations, File > Reload) are invisible.
     */
    @Test
    void reloadStillRefreshesTheVisibleView() {
        String body = bodyOf("public void reload( boolean showError)");

        assertTrue(body.contains("refreshCurrentView"),
                "reload() must refresh the on-screen view, or a reloaded document "
                        + "keeps displaying its previous content");
    }

    /** Both methods dispatch over the views that actually exist. */
    @Test
    void refreshCoversEveryDocumentView() {
        String body = bodyOf("private void refreshCurrentView()");

        for (String view : new String[] {"Editor", "Viewer", "Browser", "AuthorView",
                "AuthorSplitView", "PluginViewPanel"}) {
            assertTrue(body.contains("instanceof " + view),
                    "refreshCurrentView() no longer handles " + view
                            + " — that view would show stale content after a reload");
        }
    }

    /**
     * Undo is one history across the Author and XML views, which only works
     * while the view that did not make the change is re-synced afterwards.
     * Emptied, undoing in one view leaves the other showing the old content
     * and the next save writes whichever of the two the model happens to hold.
     *
     * <p>This only proves the dispatch is still present, the same as the
     * checks above and for the same reason: {@code DogsBayView} needs a full
     * application frame. The behaviour of the sync itself — one shared stack,
     * pending text flushed before the undo, edits made by the sync not
     * recorded, a stale Author edit stopping rather than corrupting — is
     * covered by {@code ChangeManagerSharedUndoTest}.
     */
    @Test
    void undoStillSynchronisesTheOtherView() {
        String body = bodyOf("private void synchroniseAfterUndo( javax.swing.undo.UndoableEdit edit)");

        assertTrue(body.contains("UndoableAuthorEdit"),
                "the sync must tell an Author-view edit from a text edit");
        assertTrue(body.contains("author.applyToDocument()"),
                "undoing an Author edit must export the block model, or the XML side keeps the old text");
        assertTrue(body.contains("editor.parse()"),
                "undoing a text edit must parse the buffer, or the Author side keeps the old model");
    }

    /**
     * Ctrl+Z in the Author view must undo there. The global action switches to
     * the XML editor before undoing, which is right for a text edit and wrong
     * for an Author edit: it would throw the writer out of the view they are
     * working in on every undo.
     */
    @Test
    void undoStaysInTheAuthorViewForAnAuthorEdit() throws Exception {
        for (String action : new String[] {"UndoAction", "RedoAction"}) {
            String text = Files.readString(
                    Path.of("src/main/java/com/dogsbay/dogsbayaieditor/actions/" + action + ".java"));
            assertTrue(text.contains("AuthorView") && text.contains("AuthorSplitView"),
                    action + " must recognise the Author views");
            assertTrue(text.contains("isAuthor"),
                    action + " must ask the change manager whether the next edit is an Author edit");
        }
    }
}
