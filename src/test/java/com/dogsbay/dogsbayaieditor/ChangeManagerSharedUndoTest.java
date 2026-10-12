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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.UndoableEdit;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.author.UndoableAuthorEdit;

/** One undo history spans the Author and XML views, and re-syncs the other side. */
class ChangeManagerSharedUndoTest {

    /** A named edit that records the calls made to it. */
    private static final class NamedEdit extends AbstractUndoableEdit {
        private final String name;
        private final List<String> log;

        NamedEdit(String name, List<String> log) {
            this.name = name;
            this.log = log;
        }

        @Override
        public void undo() {
            super.undo();
            log.add("undo " + name);
        }

        @Override
        public void redo() {
            super.redo();
            log.add("redo " + name);
        }
    }

    @Test
    void aTextEditAndAnAuthorEditBothStayOnTheStack() {
        List<String> log = new ArrayList<>();
        ChangeManager manager = new ChangeManager();
        manager.addEdit(new NamedEdit("text", log));
        manager.addEdit(new UndoableAuthorEdit(new NamedEdit("author", log)));

        manager.undo();
        manager.undo();
        assertThat(log).containsExactly("undo author", "undo text");
        assertThat(manager.canUndo()).isFalse();

        manager.redo();
        manager.redo();
        assertThat(log).containsExactly("undo author", "undo text", "redo text", "redo author");
    }

    @Test
    void theOtherViewIsSynchronisedAfterEachUndoAndRedo() {
        List<String> log = new ArrayList<>();
        ChangeManager manager = new ChangeManager();
        List<String> synced = new ArrayList<>();
        manager.setViewSynchroniser(edit -> synced.add(
                edit instanceof UndoableAuthorEdit ? "author" : "text"));

        manager.addEdit(new NamedEdit("text", log));
        manager.addEdit(new UndoableAuthorEdit(new NamedEdit("author", log)));
        manager.undo();
        manager.undo();
        manager.redo();
        assertThat(synced).containsExactly("author", "text", "text");
    }

    @Test
    void aViewRefreshDuringTheSyncIsNotRecordedSoRedoSurvives() {
        List<String> log = new ArrayList<>();
        ChangeManager manager = new ChangeManager();
        // the sync reloads a pane, which would otherwise add an edit and drop the redo stack
        manager.setViewSynchroniser(edit -> {
            manager.startCompound(false);
            manager.addEdit(new NamedEdit("reload", log));
            manager.endCompound();
        });

        manager.addEdit(new NamedEdit("one", log));
        manager.undo();
        assertThat(manager.canRedo()).as("the reload did not clear the redo stack").isTrue();
        manager.redo();
        assertThat(log).containsExactly("undo one", "redo one");
    }

    @Test
    void textStillPendingWhenUndoIsPressedBecomesItsOwnUndoableEdit() {
        List<String> log = new ArrayList<>();
        ChangeManager manager = new ChangeManager();
        // the Author view commits typed text on a timer; pressing undo must flush it first
        boolean[] pending = {true};
        manager.setPendingEditCommitter(() -> {
            if (pending[0]) {
                pending[0] = false;
                manager.addEdit(new NamedEdit("typed", log));
            }
        });
        manager.setViewSynchroniser(edit -> { });
        manager.addEdit(new NamedEdit("structural", log));

        manager.undo();
        assertThat(log).as("the flushed text is undone first, not lost").containsExactly("undo typed");
        manager.undo();
        assertThat(log).containsExactly("undo typed", "undo structural");
    }

    @Test
    void theCommitterDoesNotRunAgainWhileTheSyncIsInFlight() {
        List<String> log = new ArrayList<>();
        ChangeManager manager = new ChangeManager();
        int[] commits = {0};
        manager.setPendingEditCommitter(() -> commits[0]++);
        // a sync that undoes something itself must not re-enter the committer
        manager.setViewSynchroniser(edit -> manager.undo());
        manager.addEdit(new NamedEdit("one", log));
        manager.undo();
        assertThat(commits[0]).isEqualTo(1);
    }

    @Test
    void aStaleAuthorEditStopsTheUndoInsteadOfCorruptingTheModel() {
        var adapter = new com.dogsbay.xml.author.adapter.DitaBlockAdapter();
        var model = adapter.importDocument(com.dogsbay.xml.author.adapter.AdapterTestSupport.parse(
                "<concept id='c'><title>T</title><conbody><p>x</p></conbody></concept>"));
        var panel = new com.dogsbay.xml.author.ui.AuthorEditorPanel(adapter);
        panel.setAuthorDocument(model);

        List<String> log = new ArrayList<>();
        UndoableAuthorEdit edit = new UndoableAuthorEdit(
                new NamedEdit("author", log), panel::getAuthorDocument, model);
        assertThat(edit.isStale()).isFalse();
        assertThat(edit.canUndo()).isTrue();

        // the XML side changed and the Author view re-imported: the old model is gone
        panel.setAuthorDocument(adapter.importDocument(com.dogsbay.xml.author.adapter.AdapterTestSupport.parse(
                "<concept id='c'><title>T</title><conbody><p>y</p></conbody></concept>")));
        assertThat(edit.isStale()).isTrue();
        assertThat(edit.canUndo()).isFalse();

        ChangeManager manager = new ChangeManager();
        manager.addEdit(edit);
        manager.undo();
        assertThat(log).as("a stale edit is not applied").isEmpty();
    }
}
