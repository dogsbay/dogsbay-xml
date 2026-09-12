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

package com.dogsbay.xml.author.app;

import java.awt.BorderLayout;
import java.io.File;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.dom4j.Document;
import org.dom4j.DocumentType;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import com.dogsbay.xml.author.adapter.BlockXmlFormatter;
import com.dogsbay.xml.author.adapter.DitaBlockAdapter;
import com.dogsbay.xml.author.adapter.GenericXmlAdapter;
import com.dogsbay.xml.author.spi.DefaultReferenceResolver;
import com.dogsbay.xml.author.ui.AuthorEditorPanel;

/**
 * DogsBay Author — the standalone WYSIWYG editor for DITA topics and XML.
 * One window, one document: open, edit, save. Built entirely from the
 * standalone-clean {@code xml/author} engine (the standalone offering from
 * plans/wysiwyg-author-view.md).
 *
 * <p>Also the visual test harness: {@code --screenshot out.png} renders the
 * opened document offscreen (display-server independent) and exits.
 */
public final class AuthorApp {

    private final JFrame frame;
    private final AuthorEditorPanel panel;
    private File currentFile;
    private File rootMap;
    private DocumentType doctype;

    private final javax.swing.undo.UndoManager undo = new javax.swing.undo.UndoManager();

    private AuthorApp(File initialMap) {
        rootMap = initialMap;
        panel = new AuthorEditorPanel();
        undo.setLimit(500);
        panel.addUndoableEditListener(e -> undo.addEdit(e.getEdit()));
        frame = new JFrame("DogsBay Author");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setJMenuBar(buildMenuBar());
        frame.setContentPane(panel);
        frame.setSize(960, 800);
        frame.setLocationByPlatform(true);
    }

    private JMenuBar buildMenuBar() {
        int menuMask = frame.getToolkit().getMenuShortcutKeyMaskEx();
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");

        JMenuItem open = new JMenuItem("Open…");
        open.setAccelerator(KeyStroke.getKeyStroke('O', menuMask));
        open.addActionListener(e -> openViaChooser());
        file.add(open);

        JMenuItem setMap = new JMenuItem("Set Root Map…");
        setMap.addActionListener(e -> chooseRootMap());
        file.add(setMap);
        file.addSeparator();

        JMenuItem save = new JMenuItem("Save");
        save.setAccelerator(KeyStroke.getKeyStroke('S', menuMask));
        save.addActionListener(e -> save(currentFile));
        file.add(save);

        JMenuItem saveAs = new JMenuItem("Save As…");
        saveAs.addActionListener(e -> saveAs());
        file.add(saveAs);
        file.addSeparator();

        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> frame.dispose());
        file.add(exit);

        bar.add(file);

        JMenu edit = new JMenu("Edit");
        JMenuItem undoItem = new JMenuItem("Undo");
        undoItem.setAccelerator(KeyStroke.getKeyStroke('Z', menuMask));
        undoItem.addActionListener(e -> {
            panel.commitPendingEdits();
            if (undo.canUndo()) {
                undo.undo();
            }
        });
        edit.add(undoItem);
        JMenuItem redoItem = new JMenuItem("Redo");
        redoItem.setAccelerator(KeyStroke.getKeyStroke('Y', menuMask));
        redoItem.addActionListener(e -> {
            panel.commitPendingEdits();   // typed text that has not reached the model yet would be lost
            if (undo.canRedo()) {
                undo.redo();
            }
        });
        edit.add(redoItem);
        bar.add(edit);

        JMenu viewMenu = new JMenu("View");
        JMenuItem zoomIn = new JMenuItem("Zoom In");
        zoomIn.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_EQUALS, menuMask));
        zoomIn.addActionListener(e -> panel.zoom(+1));
        viewMenu.add(zoomIn);
        JMenuItem zoomOut = new JMenuItem("Zoom Out");
        zoomOut.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_MINUS, menuMask));
        zoomOut.addActionListener(e -> panel.zoom(-1));
        viewMenu.add(zoomOut);
        JMenuItem zoomReset = new JMenuItem("Normal Size");
        zoomReset.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_0, menuMask));
        zoomReset.addActionListener(e -> panel.zoom(0));
        viewMenu.add(zoomReset);
        bar.add(viewMenu);
        return bar;
    }

    // ------------------------------------------------------------------
    // Open / save
    // ------------------------------------------------------------------

    private void openViaChooser() {
        JFileChooser chooser = new JFileChooser(
                currentFile != null ? currentFile.getParentFile() : new File("."));
        chooser.setFileFilter(new FileNameExtensionFilter("XML / DITA", "xml", "dita", "ditamap"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            open(chooser.getSelectedFile());
        }
    }

    private void chooseRootMap() {
        JFileChooser chooser = new JFileChooser(
                rootMap != null ? rootMap.getParentFile()
                        : currentFile != null ? currentFile.getParentFile() : new File("."));
        chooser.setFileFilter(new FileNameExtensionFilter("DITA map", "ditamap"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            rootMap = chooser.getSelectedFile();
            if (currentFile != null) {
                open(currentFile); // re-resolve keys
            }
        }
    }

    void open(File file) {
        try {
            Document document = read(file);
            Element root = document.getRootElement();
            doctype = document.getDocType();
            currentFile = file;

            panel.setAdapter(DitaBlockAdapter.isDitaTopic(root.getName())
                    ? new DitaBlockAdapter() : new GenericXmlAdapter());
            panel.setReferenceResolver(new DefaultReferenceResolver(file.toURI(), rootMap));
            panel.setContent(root);
            undo.discardAllEdits();   // a newly opened document starts a fresh history
            frame.setTitle("DogsBay Author — " + file.getName());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame,
                    "Cannot open " + file + ":\n" + e.getMessage(),
                    "Open failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveAs() {
        JFileChooser chooser = new JFileChooser(
                currentFile != null ? currentFile.getParentFile() : new File("."));
        if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            save(chooser.getSelectedFile());
        }
    }

    private void save(File target) {
        if (target == null || panel.getAuthorDocument() == null) {
            return;
        }
        try {
            Element exported = panel.exportContent();
            BlockXmlFormatter.indent(exported);
            try (Writer out = Files.newBufferedWriter(target.toPath(), StandardCharsets.UTF_8)) {
                out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                if (doctype != null) {
                    out.write(doctype.asXML());
                    out.write("\n");
                }
                out.write(exported.asXML());
                out.write("\n");
            }
            currentFile = target;
            frame.setTitle("DogsBay Author — " + target.getName());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame,
                    "Cannot save " + target + ":\n" + e.getMessage(),
                    "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static Document read(File file) throws Exception {
        SAXReader reader = new SAXReader();
        reader.setValidation(false);
        reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        reader.setIncludeExternalDTDDeclarations(false);
        return reader.read(file);
    }

    // ------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        File topicFile = null;
        String screenshot = null;
        File map = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--screenshot" -> screenshot = args[++i];
                case "--map" -> map = new File(args[++i]);
                default -> topicFile = new File(args[i]);
            }
        }

        if (screenshot != null) {
            renderScreenshot(topicFile, map, screenshot);
            return;
        }

        File openFile = topicFile;
        File rootMap = map;
        SwingUtilities.invokeLater(() -> {
            AuthorApp app = new AuthorApp(rootMap);
            if (openFile != null) {
                app.open(openFile);
            }
            app.frame.setVisible(true);
        });
    }

    /**
     * Offscreen visual-test mode: lay out at a fixed size, paint to a PNG,
     * exit. Works headless and under Wayland/CI — no window is created.
     */
    private static void renderScreenshot(File topicFile, File map, String outPath) throws Exception {
        if (topicFile == null) {
            System.err.println("usage: AuthorApp <file.dita> [--map root.ditamap] [--screenshot out.png]");
            System.exit(2);
        }
        Element root = read(topicFile).getRootElement();
        File mapFile = map;
        String out = outPath;
        SwingUtilities.invokeLater(() -> {
            try {
                AuthorEditorPanel panel = new AuthorEditorPanel();
                if (!DitaBlockAdapter.isDitaTopic(root.getName())) {
                    panel.setAdapter(new GenericXmlAdapter());
                }
                panel.setReferenceResolver(new DefaultReferenceResolver(topicFile.toURI(), mapFile));
                panel.setContent(root);
                panel.setSize(900, 1100);
                // multiple layout passes: text panes report wrap-aware heights
                // only once sized, and cached layout sizes must be invalidated
                // in between (validate() is a no-op without a peer)
                for (int pass = 0; pass < 6; pass++) {
                    layOut(panel);
                    invalidateAll(panel);
                }
                layOut(panel);
                java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                        900, 1100, java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = image.createGraphics();
                g.setColor(panel.getBackground());
                g.fillRect(0, 0, 900, 1100);
                panel.paint(g);
                g.dispose();
                javax.imageio.ImageIO.write(image, "png", new File(out));
                System.out.println("screenshot written: " + out);
                System.exit(0);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        });
    }

    private static void layOut(java.awt.Component c) {
        c.doLayout();
        if (c instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                layOut(child);
            }
        }
    }

    private static void invalidateAll(java.awt.Component c) {
        c.invalidate();
        if (c instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                invalidateAll(child);
            }
        }
    }
}
