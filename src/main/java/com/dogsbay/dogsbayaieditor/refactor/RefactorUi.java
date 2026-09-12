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

package com.dogsbay.dogsbayaieditor.refactor;

import java.io.File;
import java.net.URL;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.commands.CreateKeydefCommand;
import com.dogsbay.dogsbayaieditor.commands.DeleteFileCommand;
import com.dogsbay.dogsbayaieditor.commands.ExtractConrefCommand;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.HealthCommand;
import com.dogsbay.dogsbayaieditor.commands.InlineConrefCommand;
import com.dogsbay.dogsbayaieditor.commands.InlineKeyCommand;
import com.dogsbay.dogsbayaieditor.commands.KeyifyCommand;
import com.dogsbay.dogsbayaieditor.commands.MergeKeydefsCommand;
import com.dogsbay.dogsbayaieditor.commands.RenameElementIdCommand;
import com.dogsbay.dogsbayaieditor.commands.RenameFileCommand;
import com.dogsbay.dogsbayaieditor.commands.RenameProfileValueCommand;
import com.dogsbay.dogsbayaieditor.commands.RenameKeyCommand;
import com.dogsbay.dogsbayaieditor.commands.SplitTopicCommand;
import com.dogsbay.dogsbayaieditor.commands.RetargetCommand;
import com.dogsbay.dogsbayaieditor.commands.results.HealthReport;
import com.dogsbay.dogsbayaieditor.commands.results.RefactorResult;

/**
 * GUI entry points for the refactoring commands. Each flow is:
 * prompt → dry-run (background) → plan dialog → apply (background).
 * The same commands back the CLI and MCP — the GUI adds confirmation,
 * nothing else.
 */
public class RefactorUi {

    private final DogsBayAIEditor editor;
    private final HeadlessExecutor executor = new HeadlessExecutor();

    public RefactorUi(DogsBayAIEditor editor) {
        this.editor = editor;
    }

    private static boolean popupContributorInstalled = false;

    /**
     * Registers the editor right-click item "Extract to Conref ('x')…",
     * shown when the caret sits inside an extractable element. Called once
     * during menu construction; safe to call again.
     */
    public static synchronized void installEditorPopupContributor(DogsBayAIEditor editor) {
        if (popupContributorInstalled) {
            return;
        }
        popupContributorInstalled = true;
        com.dogsbay.xml.editor.EditorPopupContributors.register((pane, offset) -> {
            try {
                java.util.List<javax.swing.JMenuItem> items = new java.util.ArrayList<>();

                String selected = pane.getSelectedText();
                if (selected != null && !selected.trim().isEmpty()
                        && !selected.contains("<")) {
                    javax.swing.JMenuItem createKey = new javax.swing.JMenuItem(
                            "Create Key from Selection…");
                    createKey.setToolTipText(
                            "Define a keydef with the selected text and replace "
                            + "the selection with a keyref");
                    createKey.addActionListener(e ->
                            new RefactorUi(editor).createKeyFromSelection(pane));
                    items.add(createKey);
                }

                javax.swing.text.Document doc = pane.getDocument();
                String text = doc.getText(0, doc.getLength());

                com.dogsbay.dogsbayaieditor.links.ProfilingValueAtCaret.Located profiling =
                        com.dogsbay.dogsbayaieditor.links.ProfilingValueAtCaret
                                .locate(text, offset);
                if (profiling != null && profiling.token() != null) {
                    javax.swing.JMenuItem item = new javax.swing.JMenuItem(
                            "Rename Profiling Value '" + profiling.token() + "'…");
                    item.setToolTipText(profiling.attribute() != null
                            ? "Rename this " + profiling.attribute()
                                    + " value project-wide, including ditaval rules"
                            : "Rule has no @att — you'll pick the attribute");
                    item.addActionListener(e -> new RefactorUi(editor)
                            .renameProfileValue(profiling.attribute(), profiling.token()));
                    items.add(item);
                }

                java.util.List<com.dogsbay.dogsbayaieditor.links.ElementAtCaret.Located> chain =
                        com.dogsbay.dogsbayaieditor.links.ElementAtCaret.locateChain(text, offset);
                if (chain.isEmpty()) {
                    return items;
                }
                if (chain.size() == 1) {
                    com.dogsbay.dogsbayaieditor.links.ElementAtCaret.Located located =
                            chain.get(0);
                    javax.swing.JMenuItem item = new javax.swing.JMenuItem(
                            "Extract to Conref (<" + located.element() + ">)…");
                    item.setToolTipText("Move this <" + located.element()
                            + "> into a reuse topic and conref it back");
                    item.addActionListener(e ->
                            new RefactorUi(editor).extractConrefAt(text, located));
                    items.add(item);
                } else {
                    // Nested elements: let the user pick the extraction level
                    javax.swing.JMenu submenu = new javax.swing.JMenu("Extract to Conref");
                    for (com.dogsbay.dogsbayaieditor.links.ElementAtCaret.Located located : chain) {
                        javax.swing.JMenuItem item = new javax.swing.JMenuItem(
                                "<" + located.element() + ">  (line " + located.line() + ")");
                        item.addActionListener(e ->
                                new RefactorUi(editor).extractConrefAt(text, located));
                        submenu.add(item);
                    }
                    items.add(submenu);
                }
                return items;
            } catch (Exception e) {
                return java.util.List.of();
            }
        });
    }

    /**
     * Rename/move {@code file}, rewriting every reference to it.
     * {@code afterApply} runs on the EDT after a successful apply
     * (e.g. to refresh an explorer tree); may be null.
     */
    public void renameFileWithReferences(File file, Runnable afterApply) {
        if (file == null || !file.isFile()) {
            MessageHandler.showError(editor, "Select a saved file to rename.", "Rename with References");
            return;
        }
        File root = resolveRoot(file);
        String currentRelative = relativize(root, file);

        String input = (String) JOptionPane.showInputDialog(editor,
            "New path (relative to " + root.getAbsolutePath() + "):",
            "Rename/Move File with References",
            JOptionPane.PLAIN_MESSAGE, null, null, currentRelative);
        if (input == null || input.trim().isEmpty()) {
            return;
        }
        File destination = new File(input.trim());
        if (!destination.isAbsolute()) {
            destination = new File(root, input.trim());
        }
        if (destination.getAbsolutePath().equals(file.getAbsolutePath())) {
            return;
        }

        final File target = destination;
        runRefactor(
            new RenameFileCommand(file.getAbsolutePath(), target.getAbsolutePath(), root.getAbsolutePath(), false),
            new RenameFileCommand(file.getAbsolutePath(), target.getAbsolutePath(), root.getAbsolutePath(), true),
            root,
            plan -> "Move " + relativize(root, file) + " → " + relativize(root, target)
                + "  (" + plan.edits().size() + " reference update" + (plan.edits().size() == 1 ? "" : "s")
                + ", scanned " + root.getAbsolutePath() + ")",
            afterApply);
    }

    /**
     * Rename a key across the project: all keydef definitions plus every
     * keyref/conkeyref. {@code oldKeyPrefill} pre-fills the prompt; may be
     * null. {@code afterApply} runs on the EDT after apply; may be null.
     */
    public void renameKey(String oldKeyPrefill, Runnable afterApply) {
        File root = resolveRoot(activeDocumentFile());

        String oldKey = (String) JOptionPane.showInputDialog(editor,
            "Key to rename:", "Rename Key",
            JOptionPane.PLAIN_MESSAGE, null, null, oldKeyPrefill);
        if (oldKey == null || oldKey.trim().isEmpty()) {
            return;
        }
        oldKey = oldKey.trim();

        String newKey = (String) JOptionPane.showInputDialog(editor,
            "New name for key '" + oldKey + "':", "Rename Key",
            JOptionPane.PLAIN_MESSAGE, null, null, oldKey);
        if (newKey == null || newKey.trim().isEmpty() || newKey.trim().equals(oldKey)) {
            return;
        }
        newKey = newKey.trim();

        final String from = oldKey;
        final String to = newKey;
        runRefactor(
            new RenameKeyCommand(from, to, root.getAbsolutePath(), false),
            new RenameKeyCommand(from, to, root.getAbsolutePath(), true),
            root,
            plan -> "Rename key '" + from + "' → '" + to
                + "'  (" + plan.edits().size() + " edit" + (plan.edits().size() == 1 ? "" : "s")
                + ", scanned " + root.getAbsolutePath() + ")",
            afterApply);
    }

    /**
     * Retarget: every reference to {@code from} is rewritten to point at a
     * replacement file the user picks. Neither file moves.
     * {@code afterApply} runs on the EDT after apply; may be null.
     */
    public void retarget(File from, Runnable afterApply) {
        if (from == null || !from.isFile()) {
            MessageHandler.showError(editor,
                    "Select a saved file whose references should be retargeted.",
                    "Retarget References");
            return;
        }
        File root = resolveRoot(from);

        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(from.getParentFile());
        chooser.setDialogTitle("Retarget references of " + from.getName() + " to…");
        if (chooser.showOpenDialog(editor) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        File to = chooser.getSelectedFile();
        if (to == null || to.getAbsolutePath().equals(from.getAbsolutePath())) {
            return;
        }

        runRefactor(
                new RetargetCommand(from.getAbsolutePath(), to.getAbsolutePath(),
                        root.getAbsolutePath(), false),
                new RetargetCommand(from.getAbsolutePath(), to.getAbsolutePath(),
                        root.getAbsolutePath(), true),
                root,
                plan -> "Retarget " + relativize(root, from) + " → " + relativize(root, to)
                        + "  (" + plan.edits().size() + " reference update"
                        + (plan.edits().size() == 1 ? "" : "s")
                        + ", scanned " + root.getAbsolutePath() + ")",
                afterApply);
    }

    /**
     * Keyify: convert direct references to {@code target} into key
     * references and add a keydef to a map the user picks — keys often
     * live in a dedicated keys submap, so the chooser always shows, with
     * {@code contextMap} (the default root map) pre-selected as a starting
     * point. Prompts for the key name, suggesting one from the file name.
     */
    public void keyifyFile(File target, File contextMap, Runnable afterApply) {
        if (target == null || !target.isFile()) {
            MessageHandler.showError(editor, "Target file not found.", "Convert to Key");
            return;
        }
        File root = resolveRoot(target);
        File map = chooseKeydefMap(contextMap, root, "Convert to Key");
        if (map == null) {
            return;
        }

        String name = target.getName();
        int dot = name.lastIndexOf('.');
        String suggested = (dot > 0 ? name.substring(0, dot) : name)
                .replaceAll("[^A-Za-z0-9_.-]", "-");
        String key = (String) JOptionPane.showInputDialog(editor,
                "Key name for " + target.getName() + ":", "Convert References to Key",
                JOptionPane.PLAIN_MESSAGE, null, null, suggested);
        if (key == null || key.trim().isEmpty()) {
            return;
        }

        final String keyName = key.trim();
        final File mapFile = map;
        final String rootMapPath = contextMap != null && contextMap.isFile()
                ? contextMap.getAbsolutePath() : null;
        runRefactor(
                new KeyifyCommand(target.getAbsolutePath(), keyName,
                        mapFile.getAbsolutePath(), root.getAbsolutePath(),
                        rootMapPath, false),
                new KeyifyCommand(target.getAbsolutePath(), keyName,
                        mapFile.getAbsolutePath(), root.getAbsolutePath(),
                        rootMapPath, true),
                root,
                plan -> "Key " + relativize(root, target) + " as '" + keyName
                        + "' (keydef added to " + mapFile.getName()
                        + ", scanned " + root.getAbsolutePath() + ")",
                afterApply);
    }

    /**
     * Create a text key from the editor selection ("extract variable"):
     * prompts for a key name and the keydef map, adds
     * {@code <keydef><topicmeta><keywords><keyword>selection...} there,
     * and replaces the selection with {@code <ph keyref="key"/>}.
     */
    public void createKeyFromSelection(javax.swing.text.JTextComponent pane) {
        String selected = pane != null ? pane.getSelectedText() : null;
        if (selected == null || selected.trim().isEmpty()) {
            MessageHandler.showError(editor,
                    "Select the text the key should resolve to first.",
                    "Create Key from Selection");
            return;
        }
        if (selected.contains("<") || selected.contains(">")) {
            MessageHandler.showError(editor,
                    "Select plain text only — the selection contains markup.",
                    "Create Key from Selection");
            return;
        }
        final String text = selected.trim();

        String suggested = text.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (suggested.length() > 40) {
            suggested = suggested.substring(0, 40).replaceAll("-+$", "");
        }
        if (suggested.isEmpty() || !Character.isLetter(suggested.charAt(0))) {
            suggested = "key-" + suggested;
        }
        String key = (String) JOptionPane.showInputDialog(editor,
                "Key name for \"" + abbreviate(text) + "\":",
                "Create Key from Selection",
                JOptionPane.PLAIN_MESSAGE, null, null, suggested);
        if (key == null || key.trim().isEmpty()) {
            return;
        }
        key = key.trim();
        if (!key.matches("[A-Za-z][A-Za-z0-9._-]*")) {
            MessageHandler.showError(editor,
                    "Invalid key name: must start with a letter and contain only "
                            + "letters, digits, '.', '-', '_'.",
                    "Create Key from Selection");
            return;
        }

        File rootMap = editor.getDefaultRootMapFile();
        File root = resolveRoot(activeDocumentFile());
        File map = chooseKeydefMap(rootMap, root, "Create Key from Selection");
        if (map == null) {
            return;
        }

        final String keyName = key;
        final String rootMapPath = rootMap != null && rootMap.isFile()
                ? rootMap.getAbsolutePath() : null;
        final String stub = "<ph keyref=\"" + keyName + "\"/>";
        final File mapFile = map;

        // Replace All edits files on disk, so the active document must be
        // saved; otherwise only define-the-key (buffer replacement) is
        // offered.
        File activeFile = activeDocumentFile();
        boolean savedState = false;
        if (activeFile != null && activeFile.isFile()) {
            try {
                String disk = java.nio.file.Files.readString(activeFile.toPath(),
                        java.nio.charset.StandardCharsets.UTF_8);
                javax.swing.text.Document doc = pane.getDocument();
                savedState = disk.equals(doc.getText(0, doc.getLength()));
            } catch (Exception ignored) {
                // treated as unsaved
            }
        }
        final boolean saved = savedState;
        final String replaceRootPath = saved ? root.getAbsolutePath() : null;

        editor.setWait(true);
        editor.setStatus("Scanning for occurrences...");
        new Thread(() -> {
            RefactorResult plan;
            try {
                plan = executor.execute(new CreateKeydefCommand(keyName, text,
                        mapFile.getAbsolutePath(), rootMapPath, replaceRootPath, false));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    editor.setStatus("Done");
                    editor.setWait(false);
                    MessageHandler.showError(editor,
                            "Create key failed: " + ex.getMessage(),
                            "Create Key from Selection");
                });
                return;
            }
            SwingUtilities.invokeLater(() -> {
                editor.setStatus("Done");
                editor.setWait(false);

                int occurrences = (int) plan.edits().stream()
                        .filter(e -> "text".equals(e.attribute())).count();
                java.util.List<String> warnings = new java.util.ArrayList<>(plan.warnings());
                if (!saved) {
                    warnings.add("Document has unsaved changes — Replace All is "
                            + "available after saving");
                }

                RefactorPlanDialog dialog = new RefactorPlanDialog(editor,
                        "Create Key from Selection",
                        "Define key '" + keyName + "' = \"" + abbreviate(text)
                                + "\" in " + mapFile.getName() + "\n"
                                + (replaceRootPath != null
                                        ? occurrences + " whole-word occurrence"
                                                + (occurrences == 1 ? "" : "s")
                                                + " in text content under "
                                                + root.getAbsolutePath()
                                                + " (attributes and code excluded)"
                                        : "Only the selection will become " + stub),
                        warnings,
                        new RefactorPlanTableModel(plan.edits(), root),
                        replaceRootPath != null && occurrences > 0
                                ? "Define Key and Replace All" : "Define Key",
                        replaceRootPath != null && occurrences > 0
                                ? "Define Key Only" : null);
                RefactorPlanDialog.Outcome outcome = dialog.showAndChoose();
                if (outcome == RefactorPlanDialog.Outcome.CANCEL) {
                    return;
                }
                boolean replaceAll = outcome == RefactorPlanDialog.Outcome.APPLY
                        && replaceRootPath != null && occurrences > 0;

                editor.setWait(true);
                editor.setStatus("Creating key...");
                new Thread(() -> {
                    RefactorResult result;
                    try {
                        result = executor.execute(new CreateKeydefCommand(keyName, text,
                                mapFile.getAbsolutePath(), rootMapPath,
                                replaceAll ? replaceRootPath : null, true));
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            editor.setStatus("Done");
                            editor.setWait(false);
                            MessageHandler.showError(editor,
                                    "Create key failed: " + ex.getMessage(),
                                    "Create Key from Selection");
                        });
                        return;
                    }
                    final boolean replacedAll = replaceAll;
                    SwingUtilities.invokeLater(() -> {
                        editor.setWait(false);
                        if (!result.failures().isEmpty()) {
                            editor.setStatus("Done");
                            MessageHandler.showError(editor,
                                    "Completed with failures:\n"
                                            + String.join("\n", result.failures()),
                                    "Create Key from Selection");
                            return;
                        }
                        if (replacedAll) {
                            // Disk replacement covered the selection too;
                            // the buffer is stale now.
                            editor.setStatus("Key '" + keyName + "' defined; "
                                    + result.editsApplied() + " edit(s) in "
                                    + result.filesChanged() + " file(s) — reopen "
                                    + "the document to see the changes");
                        } else {
                            pane.replaceSelection(stub);
                            editor.setStatus("Key '" + keyName + "' defined in "
                                    + mapFile.getName() + " — save the document");
                        }
                    });
                }).start();
            });
        }).start();
    }

    private static String abbreviate(String text) {
        return text.length() > 60 ? text.substring(0, 57) + "..." : text;
    }

    /**
     * Chooser for the map that receives a keydef — keys often live in a
     * dedicated keys submap; {@code contextMap} (usually the default root
     * map) is pre-selected as the starting point. Null when cancelled.
     */
    private File chooseKeydefMap(File contextMap, File root, String errorTitle) {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(
                contextMap != null ? contextMap.getParentFile() : root);
        chooser.setDialogTitle("Map that receives the keydef (often a dedicated keys submap)");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "DITA maps (*.ditamap)", "ditamap"));
        if (contextMap != null && contextMap.isFile()) {
            chooser.setSelectedFile(contextMap);
        }
        if (chooser.showOpenDialog(editor) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File map = chooser.getSelectedFile();
        if (map == null || !map.isFile()) {
            MessageHandler.showError(editor,
                    "The keydef map must be an existing .ditamap file.", errorTitle);
            return null;
        }
        return map;
    }

    /**
     * Inline a conref: replace conrefs to {@code target#…/elementId} with
     * the content itself. The plan shows every instance; the user picks
     * all instances or just the ones in the active document.
     */
    public void inlineConref(File target, String elementId, Runnable afterApply) {
        if (target == null || !target.isFile() || elementId == null) {
            MessageHandler.showError(editor,
                    "The conref target could not be resolved.", "Inline Conref");
            return;
        }
        File root = resolveRoot(target);
        File activeFile = activeDocumentFile();

        editor.setWait(true);
        editor.setStatus("Scanning for conref instances...");
        new Thread(() -> {
            RefactorResult plan;
            try {
                plan = executor.execute(new InlineConrefCommand(
                        target.getAbsolutePath(), elementId,
                        root.getAbsolutePath(), null, false));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    editor.setStatus("Done");
                    editor.setWait(false);
                    MessageHandler.showError(editor,
                            "Inline failed: " + ex.getMessage(), "Inline Conref");
                });
                return;
            }
            SwingUtilities.invokeLater(() -> {
                editor.setStatus("Done");
                editor.setWait(false);

                int total = plan.edits().size();
                long inActive = activeFile == null ? 0 : plan.edits().stream()
                        .filter(e -> e.file().equals(activeFile.getAbsolutePath()))
                        .count();
                String alternative = activeFile != null && inActive > 0 && inActive < total
                        ? "Inline Only in " + activeFile.getName()
                                + " (" + inActive + ")" : null;
                RefactorPlanDialog dialog = new RefactorPlanDialog(editor,
                        "Inline Conref",
                        "Inline " + relativize(root, target) + "#…/" + elementId
                                + " — " + total + " instance" + (total == 1 ? "" : "s")
                                + " (scanned " + root.getAbsolutePath() + ")",
                        plan.warnings(),
                        new RefactorPlanTableModel(plan.edits(), root),
                        total == 1 ? "Inline" : "Inline All (" + total + ")",
                        alternative);
                RefactorPlanDialog.Outcome outcome = dialog.showAndChoose();
                if (outcome == RefactorPlanDialog.Outcome.CANCEL || total == 0) {
                    return;
                }
                String onlyFile = outcome == RefactorPlanDialog.Outcome.ALTERNATIVE
                        && activeFile != null ? activeFile.getAbsolutePath() : null;
                applyRefactor(new InlineConrefCommand(target.getAbsolutePath(),
                        elementId, root.getAbsolutePath(), onlyFile, true), () -> {
                    editor.setStatus("Inlined — reopen affected documents "
                            + "to see the content");
                    if (afterApply != null) {
                        afterApply.run();
                    }
                });
            });
        }).start();
    }

    /**
     * Rename an element id: prompts for old (pre-filled from the caret
     * element when it has one) and new id; rewrites every referencing
     * fragment project-wide.
     */
    public void renameElementId() {
        File file = activeDocumentFile();
        if (file == null || !file.isFile()) {
            MessageHandler.showError(editor,
                    "Save the document first — Rename Element Id works on the saved file.",
                    "Rename Element Id");
            return;
        }
        String prefill = null;
        try {
            javax.swing.text.JTextComponent pane = activeEditorPane();
            if (pane != null) {
                javax.swing.text.Document doc = pane.getDocument();
                com.dogsbay.dogsbayaieditor.links.ElementAtCaret.Located located =
                        com.dogsbay.dogsbayaieditor.links.ElementAtCaret.locate(
                                doc.getText(0, doc.getLength()), pane.getCaretPosition());
                if (located != null) {
                    prefill = located.id();
                }
            }
        } catch (Exception ignored) {
            // no prefill
        }

        String oldId = (String) JOptionPane.showInputDialog(editor,
                "Id to rename (in " + file.getName() + "):", "Rename Element Id",
                JOptionPane.PLAIN_MESSAGE, null, null, prefill);
        if (oldId == null || oldId.trim().isEmpty()) {
            return;
        }
        oldId = oldId.trim();
        String newId = (String) JOptionPane.showInputDialog(editor,
                "New id for '" + oldId + "':", "Rename Element Id",
                JOptionPane.PLAIN_MESSAGE, null, null, oldId);
        if (newId == null || newId.trim().isEmpty() || newId.trim().equals(oldId)) {
            return;
        }
        newId = newId.trim();

        File root = resolveRoot(file);
        File rootMap = editor.getDefaultRootMapFile();
        String rootMapPath = rootMap != null && rootMap.isFile()
                ? rootMap.getAbsolutePath() : null;
        final String from = oldId;
        final String to = newId;
        runRefactor(
                new RenameElementIdCommand(file.getAbsolutePath(), from, to,
                        root.getAbsolutePath(), rootMapPath, false),
                new RenameElementIdCommand(file.getAbsolutePath(), from, to,
                        root.getAbsolutePath(), rootMapPath, true),
                root,
                plan -> "Rename id '" + from + "' → '" + to + "' in "
                        + relativize(root, file) + "  (" + plan.edits().size()
                        + " edit" + (plan.edits().size() == 1 ? "" : "s")
                        + ", scanned " + root.getAbsolutePath() + ")",
                () -> editor.setStatus("Renamed id '" + from + "' → '" + to
                        + "' — reopen affected documents"));
    }

    /**
     * Rename a profiling attribute value project-wide (product, audience,
     * platform, otherprops token), including matching ditaval rules.
     */
    public void renameProfileValue() {
        renameProfileValue(null, null);
    }

    /**
     * Prefilled variant for the editor right-click: with both prefills
     * known (caret on a profiling attribute) only the new value is
     * prompted; {@code attributePrefill} null (a ditaval rule without
     * {@code @att}) shows the attribute picker.
     */
    public void renameProfileValue(String attributePrefill, String tokenPrefill) {
        String[] attributes = {"product", "audience", "platform", "otherprops", "props"};
        String attribute = attributePrefill;
        if (attribute == null) {
            String initial = attributes[0];
            attribute = (String) JOptionPane.showInputDialog(editor,
                    "Profiling attribute:", "Rename Profiling Value",
                    JOptionPane.PLAIN_MESSAGE, null, attributes, initial);
            if (attribute == null) {
                return;
            }
        }
        String oldValue = tokenPrefill;
        if (oldValue == null) {
            oldValue = (String) JOptionPane.showInputDialog(editor,
                    "Value to rename (a single token, e.g. widget_v1):",
                    "Rename Profiling Value", JOptionPane.PLAIN_MESSAGE, null, null, null);
            if (oldValue == null || oldValue.trim().isEmpty()) {
                return;
            }
        }
        oldValue = oldValue.trim();
        String newValue = (String) JOptionPane.showInputDialog(editor,
                "New value for " + attribute + "=\"" + oldValue + "\":",
                "Rename Profiling Value", JOptionPane.PLAIN_MESSAGE, null, null, oldValue);
        if (newValue == null || newValue.trim().isEmpty()
                || newValue.trim().equals(oldValue)) {
            return;
        }
        newValue = newValue.trim();
        if (oldValue.matches(".*\\s.*") || newValue.matches(".*\\s.*")) {
            MessageHandler.showError(editor,
                    "Profiling values are single tokens (no whitespace).",
                    "Rename Profiling Value");
            return;
        }

        File root = resolveRoot(activeDocumentFile());
        final String attr = attribute;
        final String from = oldValue;
        final String to = newValue;
        runRefactor(
                new RenameProfileValueCommand(attr, from, to,
                        root.getAbsolutePath(), false),
                new RenameProfileValueCommand(attr, from, to,
                        root.getAbsolutePath(), true),
                root,
                plan -> "Rename " + attr + "=\"" + from + "\" → \"" + to + "\"  ("
                        + plan.edits().size() + " edit"
                        + (plan.edits().size() == 1 ? "" : "s")
                        + " incl. ditaval rules, scanned " + root.getAbsolutePath() + ")",
                () -> editor.setStatus("Renamed " + attr + " value '" + from
                        + "' → '" + to + "' — reopen affected documents"));
    }

    /**
     * Split the active topic: every top-level section becomes a
     * standalone topic, wired into the Default Root Map when set.
     * Works on the saved file; reopen after applying.
     */
    public void splitTopic() {
        File file = activeDocumentFile();
        if (file == null || !file.isFile()) {
            MessageHandler.showError(editor,
                    "Save the document first — Split Topic works on the saved file.",
                    "Split Topic");
            return;
        }
        File root = resolveRoot(file);
        File rootMap = editor.getDefaultRootMapFile();
        String mapPath = rootMap != null && rootMap.isFile()
                ? rootMap.getAbsolutePath() : null;

        runRefactor(
                new SplitTopicCommand(file.getAbsolutePath(),
                        root.getAbsolutePath(), mapPath, false),
                new SplitTopicCommand(file.getAbsolutePath(),
                        root.getAbsolutePath(), mapPath, true),
                root,
                plan -> "Split " + relativize(root, file) + " — "
                        + plan.edits().stream()
                                .filter(e -> "split".equals(e.attribute())).count()
                        + " section(s) become standalone topics"
                        + (mapPath != null ? ", wired into " + rootMap.getName() : "")
                        + " (scanned " + root.getAbsolutePath() + ")",
                () -> editor.setStatus("Topic split — reopen "
                        + file.getName() + " and the new topics"));
    }

    /**
     * Merge duplicate keydefs in the default root map's closure (prompted
     * for when none is configured).
     */
    public void mergeKeydefs() {
        File rootMap = editor.getDefaultRootMapFile();
        if (rootMap == null || !rootMap.isFile()) {
            javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(
                    resolveRoot(activeDocumentFile()));
            chooser.setDialogTitle("Root map whose duplicate keydefs should be merged");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "DITA maps (*.ditamap)", "ditamap"));
            if (chooser.showOpenDialog(editor) != javax.swing.JFileChooser.APPROVE_OPTION) {
                return;
            }
            rootMap = chooser.getSelectedFile();
        }
        if (rootMap == null || !rootMap.isFile()) {
            return;
        }

        final File map = rootMap;
        File root = resolveRoot(map);
        runRefactor(
                new MergeKeydefsCommand(map.getAbsolutePath(), null, false),
                new MergeKeydefsCommand(map.getAbsolutePath(), null, true),
                root,
                plan -> "Merge duplicate keydefs under " + map.getName()
                        + "  (" + plan.edits().size() + " shadowed definition"
                        + (plan.edits().size() == 1 ? "" : "s") + " removed; "
                        + "resolution unchanged)",
                null);
    }

    /**
     * Inline a key: rewrite its keyref/conkeyref usages to direct paths,
     * resolved against {@code contextMap}.
     */
    public void inlineKey(String key, File contextMap, Runnable afterApply) {
        if (key == null || key.isEmpty() || contextMap == null || !contextMap.isFile()) {
            MessageHandler.showError(editor,
                    "Inline Key needs a key and a context map to resolve it.",
                    "Inline Key");
            return;
        }
        File root = resolveRoot(contextMap);
        runRefactor(
                new InlineKeyCommand(key, contextMap.getAbsolutePath(),
                        root.getAbsolutePath(), false),
                new InlineKeyCommand(key, contextMap.getAbsolutePath(),
                        root.getAbsolutePath(), true),
                root,
                plan -> "Inline key '" + key + "'  (" + plan.edits().size()
                        + " usage" + (plan.edits().size() == 1 ? "" : "s")
                        + " rewritten, resolved via " + contextMap.getName() + ")",
                afterApply);
    }

    /**
     * Extract-to-conref on the active document. Prefers the element under
     * the caret; falls back to prompting for an element id when the caret
     * isn't inside an extractable element.
     */
    public void extractConref() {
        try {
            javax.swing.text.JTextComponent pane = activeEditorPane();
            if (pane != null) {
                javax.swing.text.Document doc = pane.getDocument();
                String text = doc.getText(0, doc.getLength());
                java.util.List<com.dogsbay.dogsbayaieditor.links.ElementAtCaret.Located> chain =
                        com.dogsbay.dogsbayaieditor.links.ElementAtCaret
                                .locateChain(text, pane.getCaretPosition());
                if (chain.size() == 1) {
                    extractConrefAt(text, chain.get(0));
                    return;
                }
                if (chain.size() > 1) {
                    String[] options = chain.stream()
                            .map(l -> "<" + l.element() + ">  (line " + l.line() + ")")
                            .toArray(String[]::new);
                    String picked = (String) JOptionPane.showInputDialog(editor,
                            "Nested elements at the cursor — which one should be extracted?",
                            "Extract to Conref", JOptionPane.PLAIN_MESSAGE,
                            null, options, options[0]);
                    if (picked == null) {
                        return;
                    }
                    extractConrefAt(text, chain.get(
                            java.util.Arrays.asList(options).indexOf(picked)));
                    return;
                }
            }
        } catch (Exception ignored) {
            // fall through to the id prompt
        }
        extractConrefById();
    }

    /**
     * Extract the located caret element. {@code liveText} is the editor
     * buffer the element was located in — it must match the file on disk
     * (i.e. the document is saved), since the refactor edits the file.
     */
    public void extractConrefAt(String liveText,
            com.dogsbay.dogsbayaieditor.links.ElementAtCaret.Located located) {
        File file = activeDocumentFile();
        if (file == null || !file.isFile()) {
            MessageHandler.showError(editor,
                    "Save the document first — Extract to Conref works on the saved file.",
                    "Extract to Conref");
            return;
        }
        try {
            String disk = java.nio.file.Files.readString(file.toPath(),
                    java.nio.charset.StandardCharsets.UTF_8);
            if (!disk.equals(liveText)) {
                MessageHandler.showError(editor,
                        "The document has unsaved changes — save it first.",
                        "Extract to Conref");
                return;
            }
        } catch (Exception e) {
            MessageHandler.showError(editor, "Could not read " + file.getName()
                    + ": " + e.getMessage(), "Extract to Conref");
            return;
        }

        File root = resolveRoot(file);
        File warehouse = promptWarehouse(root);
        if (warehouse == null) {
            return;
        }

        // No id on the element: the user names the conref target (a
        // collision-free suggestion as default) rather than accumulating
        // meaningless generated names in the reuse topic.
        String desiredId = null;
        if (located.id() == null) {
            String suggested;
            try {
                suggested = com.dogsbay.dogsbayaieditor.links.ConrefExtractor
                        .suggestId(located.element(), warehouse);
            } catch (Exception e) {
                suggested = located.element() + "-reuse";
            }
            String input = (String) JOptionPane.showInputDialog(editor,
                    "The <" + located.element() + "> has no id.\n"
                            + "Id for the extracted element (becomes the conref target):",
                    "Extract to Conref", JOptionPane.PLAIN_MESSAGE, null, null,
                    suggested);
            if (input == null) {
                return;
            }
            desiredId = input.trim();
            if (desiredId.isEmpty() || !desiredId.matches("[A-Za-z][A-Za-z0-9._-]*")) {
                MessageHandler.showError(editor,
                        "Invalid id: must start with a letter and contain only "
                                + "letters, digits, '.', '-', '_'.",
                        "Extract to Conref");
                return;
            }
        }
        runExtract(file, located.start(), located.end(), located.id(),
                desiredId, warehouse, root);
    }

    /** The id-prompt fallback (also the path the CLI/MCP command takes). */
    private void extractConrefById() {
        File file = activeDocumentFile();
        if (file == null || !file.isFile()) {
            MessageHandler.showError(editor,
                    "Save the document first — Extract to Conref works on the saved file.",
                    "Extract to Conref");
            return;
        }
        File root = resolveRoot(file);

        String elementId = (String) JOptionPane.showInputDialog(editor,
                "No extractable element at the cursor.\nId of the element to extract:",
                "Extract to Conref",
                JOptionPane.PLAIN_MESSAGE, null, null, null);
        if (elementId == null || elementId.trim().isEmpty()) {
            return;
        }
        File warehouse = promptWarehouse(root);
        if (warehouse == null) {
            return;
        }

        final String id = elementId.trim();
        final File wh = warehouse;
        runRefactor(
                new ExtractConrefCommand(file.getAbsolutePath(), id,
                        wh.getAbsolutePath(), false),
                new ExtractConrefCommand(file.getAbsolutePath(), id,
                        wh.getAbsolutePath(), true),
                root,
                plan -> "Extract element '" + id + "' from " + relativize(root, file)
                        + " into " + relativize(root, wh),
                () -> editor.setStatus("Extracted '" + id + "' — reopen "
                        + file.getName() + " to see the conref stub"));
    }

    /** Caret-span extraction: plan → dialog → apply, outside the command path. */
    private void runExtract(File file, int start, int end, String existingId,
                            String desiredId, File warehouse, File root) {
        editor.setWait(true);
        editor.setStatus("Planning extraction...");
        new Thread(() -> {
            com.dogsbay.dogsbayaieditor.links.ConrefExtractor.Plan plan;
            try {
                plan = com.dogsbay.dogsbayaieditor.links.ConrefExtractor
                        .planAt(file, start, end, existingId, warehouse, desiredId);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    editor.setStatus("Done");
                    editor.setWait(false);
                    MessageHandler.showError(editor,
                            "Extract failed: " + ex.getMessage(), "Extract to Conref");
                });
                return;
            }
            SwingUtilities.invokeLater(() -> {
                editor.setStatus("Done");
                editor.setWait(false);
                java.util.List<RefactorResult.AttributeEditInfo> rows = java.util.List.of(
                        new RefactorResult.AttributeEditInfo(
                                file.getAbsolutePath(), plan.line(), "extract",
                                "<" + plan.elementName() + " id=\"" + plan.elementId()
                                        + "\">", plan.stub()),
                        new RefactorResult.AttributeEditInfo(
                                warehouse.getAbsolutePath(), -1,
                                plan.createWarehouse() ? "create" : "insert", "",
                                "<" + plan.elementName() + " id=\"" + plan.elementId()
                                        + "\"> into <concept id=\""
                                        + plan.warehouseTopicId() + "\">"));
                RefactorPlanDialog dialog = new RefactorPlanDialog(editor,
                        "Extract to Conref",
                        "Extract <" + plan.elementName() + "> (line " + plan.line()
                                + ") from " + relativize(root, file) + "\ninto "
                                + relativize(root, warehouse)
                                + " — the element is replaced by " + plan.stub(),
                        plan.warnings(),
                        new RefactorPlanTableModel(rows, root));
                if (!dialog.showAndConfirm()) {
                    return;
                }
                editor.setWait(true);
                editor.setStatus("Extracting...");
                new Thread(() -> {
                    com.dogsbay.dogsbayaieditor.links.ConrefExtractor.ApplyResult applied =
                            com.dogsbay.dogsbayaieditor.links.ConrefExtractor.apply(plan);
                    SwingUtilities.invokeLater(() -> {
                        editor.setWait(false);
                        if (!applied.failures().isEmpty()) {
                            editor.setStatus("Done");
                            MessageHandler.showError(editor,
                                    "Extract completed with failures:\n"
                                            + String.join("\n", applied.failures()),
                                    "Extract to Conref");
                        } else {
                            editor.setStatus("Extracted '" + plan.elementId()
                                    + "' — reopen " + file.getName()
                                    + " to see the conref stub");
                        }
                    });
                }).start();
            });
        }).start();
    }

    /**
     * Save-style chooser for the reuse topic the element moves into —
     * pick an existing topic or type a new name to create one.
     */
    private File promptWarehouse(File root) {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(root);
        chooser.setDialogTitle("Reuse topic to hold the element (pick existing or type a new name)");
        chooser.setDialogType(javax.swing.JFileChooser.SAVE_DIALOG);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "DITA files (*.dita, *.ditamap, *.xml)", "dita", "ditamap", "xml"));
        chooser.setSelectedFile(new File(root, "reused-content.dita"));
        if (chooser.showDialog(editor, "Select") != javax.swing.JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File chosen = chooser.getSelectedFile();
        if (chosen == null) {
            return null;
        }
        if (!chosen.getName().contains(".")) {
            chosen = new File(chosen.getParentFile(), chosen.getName() + ".dita");
        }
        return chosen;
    }

    /** The active document's text component, or null (for menu actions). */
    public javax.swing.text.JTextComponent activeEditorPaneOrNull() {
        return activeEditorPane();
    }

    /** The active document's text component, or null. */
    private javax.swing.text.JTextComponent activeEditorPane() {
        try {
            var view = editor.getView();
            var panel = view != null && view.getEditor() != null
                    ? view.getEditor().getSelectedEditorPanel() : null;
            return panel != null ? panel.getEditor() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Safe delete: scan for inbound references first. Unreferenced files
     * get a plain confirm; referenced files get the plan dialog with
     * "Delete and Remove References" / "Delete Anyway" / Cancel.
     * {@code afterApply} runs on the EDT after the delete; may be null.
     */
    public void deleteFileWithReferences(File file, Runnable afterApply) {
        if (file == null || !file.isFile()) {
            MessageHandler.showError(editor, "Select a saved file to delete.", "Delete");
            return;
        }
        File root = resolveRoot(file);

        editor.setWait(true);
        editor.setStatus("Scanning for references...");
        new Thread(() -> {
            RefactorResult plan;
            try {
                plan = executor.execute(new DeleteFileCommand(
                        file.getAbsolutePath(), root.getAbsolutePath(), true, false));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    editor.setStatus("Done");
                    editor.setWait(false);
                    MessageHandler.showError(editor,
                            "Delete failed: " + ex.getMessage(), "Delete");
                });
                return;
            }
            SwingUtilities.invokeLater(() -> {
                editor.setStatus("Done");
                editor.setWait(false);

                if (plan.edits().isEmpty() && plan.warnings().isEmpty()) {
                    // Unreferenced — keep the fast path
                    int choice = JOptionPane.showConfirmDialog(editor,
                            "Delete file \"" + file.getName() + "\"?\n\n"
                                    + "No references to it were found under\n"
                                    + root.getAbsolutePath() + ".",
                            "Delete", JOptionPane.YES_NO_OPTION);
                    if (choice == JOptionPane.YES_OPTION) {
                        applyRefactor(new DeleteFileCommand(file.getAbsolutePath(),
                                root.getAbsolutePath(), false, true), afterApply);
                    }
                    return;
                }

                int affected = plan.edits().size() + plan.warnings().size();
                RefactorPlanDialog dialog = new RefactorPlanDialog(editor, "Delete Preview",
                        "File to delete: " + file.getAbsolutePath() + "\n"
                                + affected + " reference" + (affected == 1 ? "" : "s")
                                + " to it found under " + root.getAbsolutePath() + ".\n"
                                + "Each row below is a reference in ANOTHER file; "
                                + "'Delete and Remove References' removes those map entries.",
                        plan.warnings(),
                        new RefactorPlanTableModel(plan.edits(), root),
                        "Delete and Remove References", "Delete Anyway");
                switch (dialog.showAndChoose()) {
                    case APPLY -> applyRefactor(new DeleteFileCommand(
                            file.getAbsolutePath(), root.getAbsolutePath(), true, true),
                            afterApply);
                    case ALTERNATIVE -> applyRefactor(new DeleteFileCommand(
                            file.getAbsolutePath(), root.getAbsolutePath(), false, true),
                            afterApply);
                    case CANCEL -> { }
                }
            });
        }).start();
    }

    /**
     * Runs the health command and shows the non-modal report dialog.
     */
    public void showHealthReport() {
        File root = resolveRoot(activeDocumentFile());
        File rootMap = editor.getDefaultRootMapFile();
        String rootMapPath = rootMap != null ? rootMap.getAbsolutePath() : null;

        editor.setWait(true);
        editor.setStatus("Analyzing project health...");
        new Thread(() -> {
            HealthTreeBuilder.Node reportTree = runHealth(root, rootMapPath);
            SwingUtilities.invokeLater(() -> {
                editor.setStatus("Done");
                editor.setWait(false);
                if (reportTree == null) {
                    return;
                }
                HealthReportDialog dialog = new HealthReportDialog(editor, reportTree,
                    () -> runHealth(root, rootMapPath),
                    this::navigateTo);
                dialog.setVisible(true);
            });
        }).start();
    }

    private HealthTreeBuilder.Node runHealth(File root, String rootMapPath) {
        try {
            HealthReport report = executor.execute(new HealthCommand(root.getAbsolutePath(), rootMapPath));
            return HealthTreeBuilder.build(report, root);
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> MessageHandler.showError(editor,
                "Health analysis failed: " + ex.getMessage(), "Project Health"));
            return null;
        }
    }

    /**
     * Dry-run → plan dialog → apply. Both command variants must describe
     * the same refactor; only the apply flag differs.
     */
    private void runRefactor(com.dogsbay.dogsbayaieditor.commands.Command<RefactorResult> dryRun,
                             com.dogsbay.dogsbayaieditor.commands.Command<RefactorResult> apply,
                             File root,
                             java.util.function.Function<RefactorResult, String> summarizer,
                             Runnable afterApply) {
        editor.setWait(true);
        editor.setStatus("Planning refactor...");
        new Thread(() -> {
            RefactorResult plan;
            try {
                plan = executor.execute(dryRun);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    editor.setStatus("Done");
                    editor.setWait(false);
                    MessageHandler.showError(editor, "Refactor failed: " + ex.getMessage(), "Refactor");
                });
                return;
            }
            SwingUtilities.invokeLater(() -> {
                editor.setStatus("Done");
                editor.setWait(false);
                RefactorPlanDialog dialog = new RefactorPlanDialog(editor, "Refactor Preview",
                    summarizer.apply(plan), plan.warnings(),
                    new RefactorPlanTableModel(plan.edits(), root));
                if (dialog.showAndConfirm()) {
                    applyRefactor(apply, afterApply);
                }
            });
        }).start();
    }

    private void applyRefactor(com.dogsbay.dogsbayaieditor.commands.Command<RefactorResult> apply,
                               Runnable afterApply) {
        editor.setWait(true);
        editor.setStatus("Applying refactor...");
        new Thread(() -> {
            RefactorResult result;
            try {
                result = executor.execute(apply);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    editor.setStatus("Done");
                    editor.setWait(false);
                    MessageHandler.showError(editor, "Refactor failed: " + ex.getMessage(), "Refactor");
                });
                return;
            }
            SwingUtilities.invokeLater(() -> {
                editor.setStatus("Done");
                editor.setWait(false);
                if (!result.failures().isEmpty()) {
                    MessageHandler.showError(editor,
                        "Refactor completed with failures:\n" + String.join("\n", result.failures()),
                        "Refactor");
                } else {
                    editor.setStatus("Refactor applied: " + result.editsApplied() + " edits in "
                        + result.filesChanged() + " files");
                }
                if (afterApply != null) {
                    afterApply.run();
                }
            });
        }).start();
    }

    /**
     * The scan root: the File Explorer root when it contains the context
     * file (or when there is no context file), else the file's directory.
     */
    File resolveRoot(File contextFile) {
        File explorerRoot = editor.getFileExplorer() != null
            ? editor.getFileExplorer().getRootDirectory() : null;
        if (explorerRoot != null && (contextFile == null || isUnder(explorerRoot, contextFile))) {
            return explorerRoot;
        }
        if (contextFile != null) {
            return contextFile.isDirectory() ? contextFile : contextFile.getParentFile();
        }
        return explorerRoot != null ? explorerRoot : new File(System.getProperty("user.home"));
    }

    private File activeDocumentFile() {
        try {
            URL url = editor.getDocument() != null ? editor.getDocument().getURL() : null;
            if (url != null && "file".equals(url.getProtocol())) {
                return new File(url.toURI());
            }
        } catch (Exception ignored) {
            // unsaved / non-file document
        }
        return null;
    }

    private static boolean isUnder(File root, File file) {
        try {
            return file.toPath().toAbsolutePath().normalize()
                .startsWith(root.toPath().toAbsolutePath().normalize());
        } catch (Exception e) {
            return false;
        }
    }

    private static String relativize(File root, File file) {
        try {
            java.nio.file.Path rootPath = root.toPath().toAbsolutePath().normalize();
            java.nio.file.Path filePath = file.toPath().toAbsolutePath().normalize();
            if (filePath.startsWith(rootPath)) {
                return rootPath.relativize(filePath).toString().replace(File.separatorChar, '/');
            }
        } catch (Exception ignored) {
            // fall through to absolute
        }
        return file.getAbsolutePath();
    }

    /**
     * Opens a file in the editor and selects the given 1-based line.
     */
    void navigateTo(File file, Integer line) {
        editor.setWait(true);
        editor.setStatus("Opening...");
        new Thread(() -> {
            try {
                URL url = file.toURI().toURL();
                editor.open(url, null, true);
            } catch (Exception ignored) {
                // open failed — nothing to navigate to
            } finally {
                editor.setStatus("Done");
                editor.setWait(false);
                SwingUtilities.invokeLater(() -> {
                    editor.switchToEditor();
                    try {
                        if (line != null && line > 0) {
                            editor.getView().getEditor().selectLineWithoutEnd(line);
                        }
                    } catch (Exception ignored) {
                        // navigation is best-effort
                    }
                });
            }
        }).start();
    }

    /** The active document's file, or null when unsaved/non-file. */
    public File activeFileOrNull() {
        return activeDocumentFile();
    }
}
