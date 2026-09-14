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
package com.dogsbay.dogsbayaieditor.plugin.proposals;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.Icon;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.plugin.DefaultPluginContext;
import com.dogsbay.dogsbayaieditor.plugin.Plugin;
import com.dogsbay.dogsbayaieditor.plugin.PluginContext;
import com.dogsbay.dogsbayaieditor.plugin.UIService;
import com.dogsbay.dogsbayaieditor.plugin.proposals.ProjectProposals.FileProposals;
import com.dogsbay.dogsbayaieditor.services.events.ActiveDocumentChangedEvent;
import com.dogsbay.dogsbayaieditor.services.events.DocumentClosedEvent;
import com.dogsbay.xml.DogsBayURLUtilities;
import com.dogsbay.xml.editor.EditorPopupContributors;
import com.dogsbay.xml.review.Proposal;

/**
 * Shows agent proposals in the text editor and lets the writer decide:
 * highlights in the buffer, a Proposals sidebar, and Accept/Reject on the
 * right-click menu. Works on the live Swing document of the active view,
 * refreshing shortly after each edit. The sidebar can also list every file
 * in the project that carries proposals, found by a background scan.
 */
public final class ProposalsPlugin implements Plugin {

    public static final String ID = "proposals";
    private static final String ICON_PATH = "com/dogsbay/dogsbayaieditor/icons/sidebar/lightbulb.png";
    private static final int REFRESH_MS = 400;
    /** How often the project is scanned while the panel is showing: agents write files that are not open. */
    private static final int RESCAN_MS = 15_000;

    private PluginContext context;
    private DogsBayAIEditor editor;
    private ProposalsPanel panel;
    private ProposalActions actions;
    private final ProposalHighlighter highlighter = new ProposalHighlighter();
    private Consumer<ActiveDocumentChangedEvent> activeDocHandler;
    private Consumer<DocumentClosedEvent> closedHandler;
    private EditorPopupContributors.Contributor popup;
    private Document watched;
    private DocumentListener watcher;
    private Timer refresh;
    private Timer rescan;
    /** Where to continue after a decision: the decided proposal's offset, or -1. */
    private int continueAt = -1;
    /** The file the decision was made in, for continuing in project scope. */
    private Path continueFile;
    /** The last project scan, with the active document's entry kept live between scans. */
    private List<FileProposals> project = List.of();
    private Path projectRoot;
    private boolean scanning;
    private boolean scanAgain;
    /** Bumped by every decision: a scan that started earlier read text the decision has since changed. */
    private long decisions;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return "Proposals";
    }

    @Override
    public String getDescription() {
        return "Agent proposals in the active document or across the project: highlights, a list, accept and reject.";
    }

    @Override
    public boolean isBuiltIn() {
        return true;
    }

    @Override
    public void activate(PluginContext ctx) {
        this.context = ctx;
        this.editor = ((DefaultPluginContext) ctx).getEditor();
        this.actions = new ProposalActions(editor::projectRootForAgents,
                () -> editor.getSessionRegistry().user().identity(), new ProposalActions.Compound() {
                    @Override
                    public void begin() {
                        DogsBayView v = editor.getView();
                        if (v != null && v.getChangeManager() != null) {
                            v.getChangeManager().startCompound(true);
                        }
                    }

                    @Override
                    public void end() {
                        DogsBayView v = editor.getView();
                        if (v != null && v.getChangeManager() != null) {
                            v.getChangeManager().endCompound();
                        }
                    }
                });
        panel = new ProposalsPanel(new PanelActions());
        Icon icon = ctx.getUIService().loadSidebarIcon(ICON_PATH);
        ctx.getUIService().addSidebarPanel(ID, icon, "Proposals", panel, UIService.SidebarPosition.RIGHT);

        refresh = new Timer(REFRESH_MS, e -> refresh());
        refresh.setRepeats(false);
        rescan = new Timer(RESCAN_MS, e -> {
            if (panel.isShowing()) {
                scanProject();
            }
        });
        rescan.start();
        activeDocHandler = e -> {
            rebind();
            scanProject();
        };
        closedHandler = e -> rebind();
        ctx.getEventBus().subscribe(ActiveDocumentChangedEvent.class, activeDocHandler);
        ctx.getEventBus().subscribe(DocumentClosedEvent.class, closedHandler);

        popup = (text, offset) -> {
            Proposal p = ProposalActions.at(actions.list(text.getDocument()), offset);
            if (p == null) {
                return List.of();
            }
            if (!p.isChange()) {
                JMenuItem resolve = new JMenuItem("Resolve comment by " + p.author());
                resolve.addActionListener(ev -> decide(p, actions.resolve(text.getDocument(), p, "accepted", file())));
                return List.of(resolve);
            }
            String what = p.kind().name().toLowerCase() + " by " + p.author();
            JMenuItem accept = new JMenuItem("Accept " + what);
            accept.addActionListener(ev -> decide(p, actions.accept(text.getDocument(), p, file())));
            JMenuItem reject = new JMenuItem("Reject " + what);
            reject.addActionListener(ev -> decide(p, actions.reject(text.getDocument(), p, file())));
            return List.of(accept, reject);
        };
        ctx.getUIService().addEditorPopupContributor(popup);
        rebind();
        scanProject();
    }

    @Override
    public void deactivate() {
        unwatch();
        highlighter.clear();
        if (refresh != null) {
            refresh.stop();
        }
        if (rescan != null) {
            rescan.stop();
        }
        if (context != null) {
            if (activeDocHandler != null) {
                context.getEventBus().unsubscribe(ActiveDocumentChangedEvent.class, activeDocHandler);
            }
            if (closedHandler != null) {
                context.getEventBus().unsubscribe(DocumentClosedEvent.class, closedHandler);
            }
            if (popup != null) {
                context.getUIService().removeEditorPopupContributor(popup);
            }
            context.getUIService().removeSidebarPanel(ID, UIService.SidebarPosition.RIGHT);
        }
        context = null;
    }

    // ── binding to the active document ──────────────────────────────────

    private JTextComponent pane() {
        return paneOf(editor.getView());
    }

    private static JTextComponent paneOf(DogsBayView view) {
        if (view == null || view.getEditor() == null || view.getEditor().getSelectedEditorPanel() == null) {
            return null;
        }
        return view.getEditor().getSelectedEditorPanel().getEditor();
    }

    private Path file() {
        return fileOf(editor.getView());
    }

    private static Path fileOf(DogsBayView view) {
        try {
            if (view != null && view.getDocument() != null && view.getDocument().getURL() != null
                    && "file".equals(view.getDocument().getURL().getProtocol())) {
                return ProjectProposals.key(Path.of(view.getDocument().getURL().toURI()));
            }
        } catch (Exception ignore) {
            // not a file
        }
        return null;
    }

    private void rebind() {
        SwingUtilities.invokeLater(() -> {
            unwatch();
            JTextComponent pane = pane();
            if (pane != null) {
                watched = pane.getDocument();
                watcher = new DocumentListener() {
                    @Override public void insertUpdate(DocumentEvent e) { refresh.restart(); }
                    @Override public void removeUpdate(DocumentEvent e) { refresh.restart(); }
                    @Override public void changedUpdate(DocumentEvent e) { }
                };
                watched.addDocumentListener(watcher);
            }
            refresh();
        });
    }

    private void unwatch() {
        if (watched != null && watcher != null) {
            watched.removeDocumentListener(watcher);
        }
        watched = null;
        watcher = null;
    }

    private void refresh() {
        JTextComponent pane = pane();
        List<Proposal> proposals = pane == null ? List.of() : actions.list(pane.getDocument());
        if (pane == null) {
            highlighter.clear();
        } else {
            highlighter.show(pane, proposals);
        }
        Path active = file();
        if (active != null && ProjectProposals.inScope(projectRoot, active)) {
            // The buffer is newer than the last scan: a decision or an edit shows at once.
            // Only for files the scan covers, or the next scan would drop the entry again.
            project = ProjectProposals.replace(project, active,
                    proposals.isEmpty() ? null : new FileProposals(active, proposals));
        }
        if (panel.isProjectScope()) {
            panel.showProject(project, projectRoot);
        } else {
            panel.show(proposals);
            panel.setOtherFiles((int) project.stream().filter(fp -> !fp.file().equals(active)).count());
        }
        if (continueAt >= 0) {
            int at = continueAt;
            continueAt = -1;
            panel.continueAt(continueFile, at);   // selects the next row, which jumps the editor there
        }
    }

    // ── the project ─────────────────────────────────────────────────────

    /**
     * Look for proposals in every project file, off the Swing thread. Open
     * documents are read from their buffers. A request while a scan runs is
     * remembered and run once that scan finishes.
     */
    private void scanProject() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::scanProject);
            return;
        }
        if (context == null) {
            return;
        }
        if (scanning) {
            scanAgain = true;
            return;
        }
        Path root = editor.projectRootForAgents();
        Map<Path, String> live = new HashMap<>();
        for (Object o : editor.getViews()) {
            DogsBayView view = (DogsBayView) o;
            Path f = fileOf(view);
            JTextComponent pane = paneOf(view);
            if (f != null && pane != null) {
                live.put(f, ProposalActions.text(pane.getDocument()));
            }
        }
        long startedAt = decisions;
        scanning = true;
        Thread.ofVirtual().name("proposals-scan").start(() -> {
            List<FileProposals> found;
            try {
                found = ProjectProposals.scan(root, live);
            } catch (RuntimeException e) {
                found = null;
            }
            List<FileProposals> result = found;
            SwingUtilities.invokeLater(() -> {
                scanning = false;
                if (context == null) {
                    return;
                }
                if (startedAt != decisions) {
                    scanAgain = true;   // read before a decision: it would bring the decided proposal back
                } else if (result != null && !(result.equals(project) && Objects.equals(root, projectRoot))) {
                    // Redraw only on a difference: a periodic scan that changes nothing leaves the list alone.
                    projectRoot = root;
                    project = result;
                    refresh();
                }
                if (scanAgain) {
                    scanAgain = false;
                    scanProject();
                }
            });
        });
    }

    /**
     * The text pane showing {@code file}, bringing its view to the front or
     * opening it first; null when it could not be shown. A null file is the
     * active document.
     */
    private JTextComponent show(Path file) {
        if (file == null || file.equals(file())) {
            return pane();
        }
        // Match open views by file, not by URL text: a view opened through another
        // spelling of the path must not get a second buffer.
        for (Object o : editor.getViews()) {
            DogsBayView view = (DogsBayView) o;
            if (file.equals(fileOf(view))) {
                editor.select(view);
                return file.equals(file()) ? pane() : null;
            }
        }
        try {
            editor.getDocumentManager().open(DogsBayURLUtilities.getURLFromFile(file.toFile()), null, false);
        } catch (Exception e) {
            return null;
        }
        return file.equals(file()) ? pane() : null;
    }

    /** Apply a decision's outcome; on success the review continues at the next proposal. */
    private void decide(Proposal decided, String problem) {
        if (problem != null) {
            JOptionPane.showMessageDialog(editor, problem, "Proposals", JOptionPane.WARNING_MESSAGE);
        } else {
            decisions++;
            // Accepting is the writer committing to the change, and the audit
            // trail records it on disk the instant the button is pressed. The
            // file has to follow, or the record says "accepted" over a file that
            // still carries the marks — which is what the Git panel, reading
            // disk, went on reporting. An unsaved decision is also lost outright
            // when the buffer is reloaded, by an agent turn or a checkout.
            String failed = saveDecision();
            if (failed != null) {
                JOptionPane.showMessageDialog(editor, failed, "Proposals", JOptionPane.WARNING_MESSAGE);
            }
            if (decided != null) {
                continueAt = decided.start();
                continueFile = file();
            }
        }
        refresh.restart();
    }

    /**
     * Write the decided document to disk.
     *
     * @return null when saved, or when there is nothing on disk to save to;
     *         otherwise a message for the user
     */
    private String saveDecision() {
        DogsBayView view = editor.getView();
        if (view == null) {
            return null;
        }
        String failed = save(view.getDocument(), view::updateModelOrThrow, () -> {
            if (view.getChangeManager() != null) {
                view.getChangeManager().markSave();
            }
        });
        if (failed == null && editor.getGitPanel() != null) {
            editor.getGitPanel().refreshGitStatus();   // the working tree just changed
        }
        return failed;
    }

    /**
     * Write a decided document to disk.
     *
     * <p>
     * Given its pieces rather than reaching through the editor, so the rule can
     * be tested without a window.
     *
     * @param document  the document to write
     * @param flush     pushes the view's text into the model, which is what save writes
     * @param markSaved tells the editor the buffer is no longer modified
     * @return null when saved, otherwise a message for the user
     */
    static String save(com.dogsbay.xml.DogsBayDocument document, Runnable flush, Runnable markSaved) {
        if (document == null || document.getURL() == null) {
            return null;   // untitled: the decision stands in the buffer
        }
        if (document.isReadOnly()) {
            return "The change was applied, but " + document.getName()
                    + " is read-only and could not be saved.";
        }
        try {
            flush.run();
            document.save();
            markSaved.run();
            return null;
        } catch (Exception e) {
            return "The change was applied but could not be saved:\n" + e.getMessage();
        }
    }

    private final class PanelActions implements ProposalsPanel.Actions {
        @Override
        public void select(Path file, Proposal p) {
            JTextComponent pane = show(file);
            if (pane != null) {
                // A row from the project scan was read from disk; find it in the buffer.
                Proposal live = file == null ? p : ProposalActions.relocate(pane.getDocument(), p);
                Proposal at = live == null ? p : live;
                int length = pane.getDocument().getLength();
                pane.select(Math.min(at.start(), length), Math.min(at.end(), length));
                pane.requestFocusInWindow();
            }
        }

        @Override
        public void accept(Path file, Proposal p) {
            JTextComponent pane = show(file);
            if (pane != null) {
                decide(p, actions.accept(pane.getDocument(), p, file()));
            }
        }

        @Override
        public void reject(Path file, Proposal p) {
            JTextComponent pane = show(file);
            if (pane != null) {
                decide(p, actions.reject(pane.getDocument(), p, file()));
            }
        }

        @Override
        public void acceptAll(Path file, String author) {
            JTextComponent pane = show(file);
            if (pane != null) {
                decide(null, actions.acceptAll(pane.getDocument(), author, file()));
            }
        }

        @Override
        public void rejectAll(Path file, String author) {
            JTextComponent pane = show(file);
            if (pane != null) {
                decide(null, actions.rejectAll(pane.getDocument(), author, file()));
            }
        }

        @Override
        public void resolve(Path file, Proposal comment) {
            JTextComponent pane = show(file);
            if (pane != null) {
                decide(comment, actions.resolve(pane.getDocument(), comment, "accepted", file()));
            }
        }

        @Override
        public void scopeChanged(boolean project) {
            refresh();
            scanProject();
        }

        @Override
        public void rescan() {
            scanProject();
        }
    }
}
