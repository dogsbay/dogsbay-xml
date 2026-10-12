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

package com.dogsbay.dogsbayaieditor.plugin.agent;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import com.dogsbay.agent.AiAction;
import com.dogsbay.agent.runtime.OneShot;
import com.dogsbay.agent.runtime.OneShotResult;
import com.dogsbay.agent.ui.DiffReviewDialog;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.commands.ReplaceSelectionCommand;
import com.dogsbay.dogsbayaieditor.plugin.DefaultPluginContext;
import com.dogsbay.dogsbayaieditor.plugin.Plugin;
import com.dogsbay.dogsbayaieditor.plugin.PluginContext;
import com.dogsbay.dogsbayaieditor.plugin.UIService;
import com.dogsbay.xml.editor.EditorPopupContributors;

/**
 * Quick AI actions on the editor selection (Rewrite, Simplify, Fix, …) via the
 * editor's right-click menu. Each is a single-turn LLM call ({@link OneShot})
 * whose result is shown in a {@link DiffReviewDialog}; on Accept the change is
 * applied through {@link ReplaceSelectionCommand} (undoable). Editor-coupled
 * glue over the standalone-clean agent runtime.
 */
public final class AiActionsPlugin implements Plugin {

    private static final String ID = "ai-actions";

    private UIService uiService;
    private DogsBayAIEditor editor;
    private com.dogsbay.agent.session.AgentSession session;
    private EditorPopupContributors.Contributor contributor;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return "AI Actions";
    }

    @Override
    public String getDescription() {
        return "Right-click AI actions on a selection (rewrite, simplify, fix, …) with diff review.";
    }

    @Override
    public boolean isBuiltIn() {
        return true;
    }

    @Override
    public void activate(PluginContext ctx) {
        editor = ((DefaultPluginContext) ctx).getEditor();
        // Accepted suggestions are agent-authored text; attribute and audit
        // them as such rather than as the user's own edit.
        session = editor.getSessionRegistry().open(
                com.dogsbay.agent.session.SessionKind.BUILTIN, "AI Actions", "ai:xagent/actions",
                com.dogsbay.agent.session.CapabilityTier.T1_COMMANDS);
        uiService = ctx.getUIService();
        contributor = (pane, offset) -> contribute(pane);
        uiService.addEditorPopupContributor(contributor);
    }

    @Override
    public void deactivate() {
        if (uiService != null && contributor != null) {
            uiService.removeEditorPopupContributor(contributor);
        }
        contributor = null;
        uiService = null;
        if (editor != null && session != null) {
            editor.getSessionRegistry().close(session.id());
        }
        session = null;
        editor = null;
    }

    /** Contribute an "AI" submenu when there is a selection to act on. */
    private List<JMenuItem> contribute(JTextComponent pane) {
        String selection = pane.getSelectedText();
        if (selection == null || selection.isBlank()) {
            return List.of();
        }
        JMenu menu = new JMenu("AI");
        boolean addedDitaSeparator = false;
        for (AiAction action : AiAction.values()) {
            if (action.isDita() && !addedDitaSeparator) {
                menu.addSeparator();   // split generic edits from the DITA section
                addedDitaSeparator = true;
            }
            JMenuItem item = new JMenuItem(action.label());
            item.addActionListener(e -> run(action, selection));
            menu.add(item);
        }
        List<JMenuItem> items = new ArrayList<>();
        items.add(menu);
        return items;
    }

    /**
     * A short note telling the model what document type the selection lives in, so
     * transforms (especially Fix / Expand / the DITA actions) stay markup-correct.
     */
    private String contextNote() {
        try {
            var doc = editor.getDocumentManager().getActiveDocument();
            if (doc != null && doc.getRoot() != null && doc.getRoot().getName() != null) {
                String name = doc.getRoot().getName();
                boolean dita = EditorAgentHost.isDitaClass(doc.getRoot().getAttribute("class"));
                return "Context: the surrounding document is "
                        + (dita ? "a DITA <" + name + ">" : "<" + name + ">")
                        + ". Preserve valid markup for that document type.";
            }
        } catch (Exception ignore) {
            // no active document / model not ready → no context note
        }
        return null;
    }

    /** Run the action off the EDT, then review + apply on the EDT. */
    private void run(AiAction action, String selection) {
        Thread.ofVirtual().name("ai-action").start(() -> {
            try {
                OneShotResult result = OneShot.complete(action.systemPrompt(), selection, contextNote());
                SwingUtilities.invokeLater(() -> review(action, selection, result));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(editor,
                        "AI action failed: " + e.getMessage()
                                + "\n\nConfigure a provider in the AI Agent panel first.",
                        "AI Actions", JOptionPane.WARNING_MESSAGE));
            }
        });
    }

    private void review(AiAction action, String original, OneShotResult result) {
        // The footer identifies which provider/model produced the suggestion and
        // its token/cost, so it's clear what ran (these actions reuse the chat
        // agent's configured provider).
        String accepted = DiffReviewDialog.review(
                editor, action.label(), original, result.text(), result.summary());
        if (accepted == null) {
            return;   // rejected
        }
        try {
            // The suggestion was made against the selection the user saw; read
            // the document under this session so the gate can check nothing
            // moved, apply, then end the "turn" so the lease is released.
            editor.getCommandExecutor().execute(
                    new com.dogsbay.dogsbayaieditor.commands.GetSelectionCommand(), session);
            editor.getCommandExecutor().execute(new ReplaceSelectionCommand(accepted), session);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(editor, "Could not apply change: " + e.getMessage(),
                    "AI Actions", JOptionPane.ERROR_MESSAGE);
        } finally {
            if (editor.getWriteGate() != null) {
                editor.getWriteGate().turnEnded(session);
            }
        }
    }
}
