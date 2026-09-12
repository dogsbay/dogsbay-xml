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

package com.dogsbay.xml.author.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.dogsbay.xml.author.undo.BlockEdit;

/**
 * Property-style undo/redo test: apply long sequences of random valid
 * transactions, then verify that undoing everything restores the exact
 * starting structure and redoing everything restores the exact final
 * structure. Seeds are fixed so failures are reproducible.
 */
class UndoRedoPropertyTest {

    private static final int SEQUENCES = 25;
    private static final int TRANSACTIONS_PER_SEQUENCE = 60;

    @Test
    void undoAllRestoresInitialTreeAndRedoAllRestoresFinalTree() {
        for (long seed = 0; seed < SEQUENCES; seed++) {
            runSequence(seed);
        }
    }

    private void runSequence(long seed) {
        Random random = new Random(seed);
        AuthorDocument doc = new AuthorDocument(BlockTypeRegistry.ditaProfile());
        AuthorBlock task = doc.createBlock("task");
        doc.setRoot(task);
        AuthorBlock taskbody = doc.createBlock("taskbody");
        doc.attach(task, 0, taskbody);

        String initial = task.toStructureString();
        List<BlockEdit> edits = new ArrayList<>();

        for (int i = 0; i < TRANSACTIONS_PER_SEQUENCE; i++) {
            BlockEdit edit = randomTransaction(doc, random, i);
            if (edit != null) {
                edits.add(edit);
            }
        }
        String fin = doc.getRoot().toStructureString();

        for (int i = edits.size() - 1; i >= 0; i--) {
            edits.get(i).undo();
        }
        assertThat(doc.getRoot().toStructureString())
                .as("undo-all, seed %d", seed)
                .isEqualTo(initial);

        for (BlockEdit edit : edits) {
            edit.redo();
        }
        assertThat(doc.getRoot().toStructureString())
                .as("redo-all, seed %d", seed)
                .isEqualTo(fin);
    }

    /** Applies one random valid transaction; returns null if no op was possible. */
    private BlockEdit randomTransaction(AuthorDocument doc, Random random, int step) {
        List<AuthorBlock> all = collect(doc.getRoot());
        Transaction tx = doc.begin("step " + step);
        try {
            switch (random.nextInt(5)) {
                case 0 -> insertSomewhere(doc, tx, all, random);
                case 1 -> removeSomething(tx, all, random);
                case 2 -> moveSomething(doc, tx, all, random);
                case 3 -> {
                    AuthorBlock b = all.get(random.nextInt(all.size()));
                    tx.setAttribute(b, "a" + random.nextInt(3),
                            random.nextBoolean() ? "v" + random.nextInt(100) : null);
                }
                case 4 -> setTextSomewhere(tx, all, random);
                default -> throw new IllegalStateException();
            }
        } catch (AuthorStructureException e) {
            // the random pick happened to be invalid and the tx rolled back — fine
            return null;
        }
        if (tx.isEmpty()) {
            tx.rollback();
            return null;
        }
        return tx.commit();
    }

    private void insertSomewhere(AuthorDocument doc, Transaction tx, List<AuthorBlock> all, Random random) {
        AuthorBlock parent = all.get(random.nextInt(all.size()));
        List<BlockType> allowed = doc.getRegistry().allowedChildren(parent.getType());
        if (allowed.isEmpty() || parent.getType().getCategory() == BlockType.Category.VOID) {
            return;
        }
        BlockType childType = allowed.get(random.nextInt(allowed.size()));
        int index = random.nextInt(parent.getChildren().size() + 1);
        AuthorBlock child = tx.insertBlock(childType.getName(), parent, index);
        if (childType.hasText() && random.nextBoolean()) {
            tx.setText(child, List.of(InlineRun.of("txt" + random.nextInt(10))));
        }
    }

    private void removeSomething(Transaction tx, List<AuthorBlock> all, Random random) {
        List<AuthorBlock> removable = all.stream()
                .filter(b -> b.getParent() != null && b.getParent().getParent() != null)
                .toList();
        if (removable.isEmpty()) {
            return;
        }
        tx.removeBlock(removable.get(random.nextInt(removable.size())));
    }

    private void moveSomething(AuthorDocument doc, Transaction tx, List<AuthorBlock> all, Random random) {
        List<AuthorBlock> movable = all.stream()
                .filter(b -> b.getParent() != null && b.getParent().getParent() != null)
                .toList();
        if (movable.isEmpty()) {
            return;
        }
        AuthorBlock block = movable.get(random.nextInt(movable.size()));
        List<AuthorBlock> targets = all.stream()
                .filter(p -> !block.isAncestorOf(p))
                .filter(p -> doc.getRegistry().isValidChild(p.getType(), block.getType()))
                .toList();
        if (targets.isEmpty()) {
            return;
        }
        AuthorBlock target = targets.get(random.nextInt(targets.size()));
        tx.moveBlock(block, target, random.nextInt(target.getChildren().size() + 1));
    }

    private void setTextSomewhere(Transaction tx, List<AuthorBlock> all, Random random) {
        List<AuthorBlock> textBlocks = all.stream()
                .filter(b -> b.getType().hasText())
                .toList();
        if (textBlocks.isEmpty()) {
            return;
        }
        AuthorBlock block = textBlocks.get(random.nextInt(textBlocks.size()));
        List<InlineRun> runs = new ArrayList<>();
        int n = random.nextInt(4);
        for (int i = 0; i < n; i++) {
            InlineRun run = InlineRun.of("w" + random.nextInt(50) + " ");
            if (random.nextBoolean()) {
                run = run.with(InlineStyle.BOLD, InlineStyle.TRUE);
            }
            if (random.nextInt(4) == 0) {
                run = run.with(InlineStyle.DITA_INLINE, "codeph");
            }
            runs.add(run);
        }
        tx.setText(block, runs);
    }

    private List<AuthorBlock> collect(AuthorBlock root) {
        List<AuthorBlock> out = new ArrayList<>();
        addRecursive(root, out);
        return out;
    }

    private void addRecursive(AuthorBlock block, List<AuthorBlock> out) {
        out.add(block);
        for (AuthorBlock child : block.getChildren()) {
            addRecursive(child, out);
        }
    }
}
