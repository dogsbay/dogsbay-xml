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

package com.dogsbay.dogsbayaieditor.dita.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.Timer;

import com.dogsbay.dogsbayaieditor.commands.BuildStepReport;
import com.dogsbay.xml.dita.BuildProgress;

/**
 * What the editor shows while DITA-OT works: the step running now and how long it has taken.
 *
 * <p>A build is minutes, and a window that looks idle for minutes is a window someone edits and saves into while the
 * build reads the files underneath. This is deliberately non-modal, like the one Build Deliverables puts up, so it
 * says what is happening without pretending the editor can be locked.
 */
public final class BuildProgressDialog {

    private final JDialog dialog;
    private final Timer stepTimer;
    private final long started = System.currentTimeMillis();
    private final String[] current = {null, null}; // deliverable, step
    private final long[] stepStarted = {System.currentTimeMillis()};

    private BuildProgressDialog(java.awt.Frame parent, String title, String message) {
        dialog = new JDialog(parent, title, false);
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.add(new JLabel(message), BorderLayout.NORTH);
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        panel.add(bar, BorderLayout.CENTER);
        JLabel stepLabel = new JLabel("Starting DITA-OT…");
        stepLabel.setPreferredSize(new Dimension(460, stepLabel.getPreferredSize().height));
        stepLabel.setFont(stepLabel.getFont().deriveFont(Font.PLAIN));
        panel.add(stepLabel, BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);

        stepTimer = new Timer(500, e -> {
            synchronized (current) {
                if (current[1] == null) {
                    return;
                }
                long now = System.currentTimeMillis();
                stepLabel.setText((current[0] != null ? current[0] + ": " : "") + current[1] + " — "
                        + BuildStepReport.duration(now - stepStarted[0])
                        + " (total " + BuildStepReport.duration(now - started) + ")");
            }
        });
        stepTimer.start();
    }

    /** Put one up. Call {@link #close()} from {@code done()}, whether the work succeeded or not. */
    public static BuildProgressDialog show(java.awt.Frame parent, String title, String message) {
        return new BuildProgressDialog(parent, title, message);
    }

    /** The listener to wrap the work in, so the dialog can name the step DITA-OT is on. */
    public BuildProgress.Listener listener() {
        return (deliverable, step) -> {
            synchronized (current) {
                if (!step.equals(current[1]) || !Objects.equals(deliverable, current[0])) {
                    current[0] = deliverable;
                    current[1] = step;
                    stepStarted[0] = System.currentTimeMillis();
                }
            }
        };
    }

    /** How long the work has taken so far, in milliseconds. */
    public long elapsed() {
        return System.currentTimeMillis() - started;
    }

    public void close() {
        stepTimer.stop();
        dialog.dispose();
    }
}
