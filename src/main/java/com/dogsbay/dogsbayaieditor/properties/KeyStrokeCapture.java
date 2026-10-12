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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

/**
 * Asks for a key by listening for one.
 *
 * <p>Typing the key you want is the only description of a shortcut nobody gets
 * wrong. The old dialog asked for a modifier from one list and a key from
 * another, which is a form to fill in about a gesture.
 */
public final class KeyStrokeCapture {

    private KeyStrokeCapture() {
    }

    /**
     * Show the capture and return what was pressed.
     *
     * @param parent  where to centre it
     * @param command the command being bound, so the reader knows what they
     *                are pressing a key for
     * @return the key, or null when the reader cancelled
     */
    public static KeyStroke ask(Component parent, String command) {
        JDialog dialog = new JDialog(JOptionPane.getFrameForComponent(parent),
                "Press a key", true);
        JLabel prompt = new JLabel("Press the key for " + command, SwingConstants.CENTER);
        JLabel pressed = new JLabel(" ", SwingConstants.CENTER);
        JLabel hint = new JLabel("Escape cancels", SwingConstants.CENTER);
        hint.setFont(hint.getFont().deriveFont(hint.getFont().getSize2D() - 1f));

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(16, 24, 12, 24));
        content.add(prompt, BorderLayout.NORTH);
        content.add(pressed, BorderLayout.CENTER);
        content.add(hint, BorderLayout.SOUTH);

        KeyStroke[] chosen = new KeyStroke[1];
        content.setFocusable(true);
        content.addKeyListener(new KeyListener() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE && e.getModifiersEx() == 0) {
                    // Bare Escape only: with a modifier held it is a keystroke
                    // somebody is trying to bind, and cancelling on it made
                    // Ctrl+Escape and Shift+Escape impossible to choose.
                    dialog.dispose();
                    return;
                }
                if (isModifierOnly(e.getKeyCode())) {
                    // Ctrl on its own is the reader on their way somewhere.
                    pressed.setText(KeyEvent.getModifiersExText(e.getModifiersEx()) + "…");
                    return;
                }
                chosen[0] = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiersEx());
                pressed.setText(describe(chosen[0]));
                // A beat on screen, so the reader sees what was taken.
                new javax.swing.Timer(220, a -> dialog.dispose()) {{
                    setRepeats(false);
                }}.start();
            }

            @Override public void keyReleased(KeyEvent e) {
            }

            @Override public void keyTyped(KeyEvent e) {
            }
        });

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowOpened(java.awt.event.WindowEvent e) {
                content.requestFocusInWindow();
            }
        });
        dialog.setVisible(true);
        return chosen[0];
    }

    /** Whether this key on its own is only ever part of a combination. */
    static boolean isModifierOnly(int keyCode) {
        return keyCode == KeyEvent.VK_CONTROL || keyCode == KeyEvent.VK_SHIFT
                || keyCode == KeyEvent.VK_ALT || keyCode == KeyEvent.VK_ALT_GRAPH
                || keyCode == KeyEvent.VK_META || keyCode == KeyEvent.VK_WINDOWS;
    }

    /** A keystroke as a reader would say it: "Ctrl+Shift+S". */
    public static String describe(KeyStroke stroke) {
        if (stroke == null) {
            return "";
        }
        String modifiers = KeyEvent.getModifiersExText(stroke.getModifiers());
        String key = KeyEvent.getKeyText(stroke.getKeyCode());
        return modifiers.isEmpty() ? key : modifiers + "+" + key;
    }
}
