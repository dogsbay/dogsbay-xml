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

package com.dogsbay.dogsbayaieditor.update;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.net.URI;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A thin notification banner shown at the top of the main window
 * when a new version is available.
 */
public class UpdateNotificationPanel extends JPanel {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateNotificationPanel.class);

    private final String releaseUrl;
    private Runnable onDismiss;

    public UpdateNotificationPanel(String newVersion, String releaseUrl) {
        this.releaseUrl = releaseUrl;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0,
                UIManager.getColor("Separator.foreground")),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        setBackground(UIManager.getColor("EditorPane.background"));

        JLabel label = new JLabel("Version " + newVersion + " is available.");
        add(label, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);

        JButton downloadBtn = new JButton("Download");
        downloadBtn.addActionListener(e -> openReleasePage());
        buttons.add(downloadBtn);

        JButton dismissBtn = new JButton("Dismiss");
        dismissBtn.addActionListener(e -> dismiss());
        buttons.add(dismissBtn);

        add(buttons, BorderLayout.EAST);
    }

    public void setOnDismiss(Runnable onDismiss) {
        this.onDismiss = onDismiss;
    }

    private void openReleasePage() {
        try {
            Desktop.getDesktop().browse(new URI(releaseUrl));
        } catch (Exception e) {
            LOG.warn("Failed to open release page: {}", e.getMessage());
        }
        dismiss();
    }

    private void dismiss() {
        SwingUtilities.invokeLater(() -> {
            setVisible(false);
            if (getParent() != null) {
                getParent().remove(this);
                getParent().revalidate();
            }
            if (onDismiss != null) {
                onDismiss.run();
            }
        });
    }
}
