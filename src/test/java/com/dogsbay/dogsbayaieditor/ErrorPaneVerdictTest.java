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

import javax.swing.JViewport;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.dogsbay.xml.XMLError;

/**
 * The verdict is the last line in the Project Validation pane, and has to be the line you can see.
 *
 * <p>It is last on purpose — every warning printed above it is counted in it — but a clean project's dozens of
 * DITA-OT notes put it below the fold, and a reader who had to scroll for "Ready:" read the notes as the result
 * (reported 2026-09-30).
 */
class ErrorPaneVerdictTest {

    private static final int ROWS = 200;

    /**
     * A Project Validation pane laid out small enough that 200 rows cannot fit, so scrolling is the only way
     * to the bottom. {@link ProjectErrorPane} rather than the base pane: it is the one this is about, and its
     * renderer does not ask the editor which document is active.
     */
    private static ProjectErrorPane laidOutPane() {
        ProjectErrorPane pane = new ProjectErrorPane(null);
        pane.setSize(500, 80);
        layOut(pane);
        return pane;
    }

    /** Lay the tree out by hand: {@code validate()} is a no-op for a component with no peer. */
    private static void layOut(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof java.awt.Container inner) {
                layOut(inner);
            }
        }
    }

    private static JViewport viewportOf(ErrorPane pane) {
        JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, pane.getList());
        assertThat(viewport).as("the list is inside a scroll pane").isNotNull();
        return viewport;
    }

    private static void fill(ErrorPane pane) {
        pane.startCheck("Check Project — 200 notes");
        for (int i = 0; i < ROWS; i++) {
            pane.addError(new XMLError("file:/p/topics/t.dita", i + 1, 1, XMLError.WARNING,
                    "No rule for 'audience=podcaster' was found in the DITAVAL file"));
        }
    }

    @Test
    void theVerdictIsScrolledIntoView() throws Exception {
        ProjectErrorPane pane = laidOutPane();
        fill(pane);
        layOut(pane);
        JViewport viewport = viewportOf(pane);
        viewport.setViewPosition(new java.awt.Point(0, 0));

        // Both on the event dispatch thread, and in this order. endCheck posts the scroll to the back of the
        // queue; laying out here — before that runnable is dispatched — is what the repaint manager does for a
        // component that is on screen, and a test component never is. Doing it from the test thread instead
        // was a race against the scroll, which passed alone and failed about once a suite.
        SwingUtilities.invokeAndWait(() -> {
            pane.endCheck("Ready: the project is healthy, every deliverable built.");
            layOut(pane);
        });
        flushEventQueue();

        int lastRow = pane.getList().getModel().getSize() - 1;
        java.awt.Rectangle verdict = pane.getList().getCellBounds(lastRow, lastRow);
        assertThat(verdict).as("the list has laid its rows out").isNotNull();
        assertThat(pane.getList().getVisibleRect().contains(verdict.x, verdict.y))
                .as("the verdict's row is inside the visible area, not below it")
                .isTrue();
    }

    /** Run everything already queued on the event dispatch thread. */
    private static void flushEventQueue() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
