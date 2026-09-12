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

import java.awt.Window;
import java.util.function.Consumer;

import javax.swing.Icon;

import com.dogsbay.agent.FixRequest;
import com.dogsbay.agent.runtime.AgentChatController;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.plugin.DefaultPluginContext;
import com.dogsbay.dogsbayaieditor.plugin.Plugin;
import com.dogsbay.dogsbayaieditor.plugin.PluginContext;
import com.dogsbay.dogsbayaieditor.plugin.UIService;
import com.dogsbay.dogsbayaieditor.services.events.ActiveDocumentChangedEvent;
import com.dogsbay.dogsbayaieditor.services.events.DocumentOpenedEvent;

/**
 * Built-in plugin that docks the agent chat in the right sidebar. It is a thin
 * editor-coupled layer over the standalone-clean {@code com.dogsbay.agent}
 * module: it supplies an {@link EditorAgentHost} and delegates all wiring to
 * {@link AgentChatController}, so the same panel runs standalone via
 * {@code AgentApp}.
 *
 * <p>Responsive with no credentials (lazy provider startup): the panel appears
 * with a provider bar; no error at activation. Per
 * {@code plans/phase-5-xagent-plugin.md} this becomes an optional bundled
 * (proprietary) plugin; for now it is built-in for development.
 */
public final class AgentPlugin implements Plugin {

    private static final String ID = "agent";
    private static final String ICON_PATH =
            "com/dogsbay/dogsbayaieditor/icons/sidebar/search-sparkle.png";

    /** The active plugin instance, for app-wide "Fix with AI" routing from markers. */
    private static volatile AgentPlugin active;

    private UIService uiService;
    private AgentChatController controller;
    private PluginContext context;
    private Consumer<ActiveDocumentChangedEvent> activeDocHandler;
    private Consumer<DocumentOpenedEvent> openedHandler;
    private AgentSidebarPanel sidebar;
    private com.dogsbay.agent.acp.AgentRegistryCatalog catalog;
    private javax.swing.JLabel statusItem;
    private java.util.function.Consumer<String> selectionSink;
    private com.dogsbay.xml.editor.EditorPopupContributors.Contributor popupContributor;
    private javax.swing.JMenuItem activityItem;
    private Consumer<com.dogsbay.agent.session.SessionEvent> sessionListener;
    private DogsBayAIEditor editorRef;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return "AI Agent";
    }

    @Override
    public String getDescription() {
        return "Embedded AI agent chat (xagent) in the right sidebar.";
    }

    @Override
    public boolean isBuiltIn() {
        return true;
    }

    @Override
    public void activate(PluginContext ctx) {
        Window parent = ((DefaultPluginContext) ctx).getEditor();
        EditorAgentHost host = new EditorAgentHost(ctx, parent);

        controller = new AgentChatController(host);
        applySessionRetention(editorOf(ctx));

        uiService = ctx.getUIService();
        Icon icon = uiService.loadSidebarIcon(ICON_PATH);
        DogsBayAIEditor editor = ((DefaultPluginContext) ctx).getEditor();
        editorRef = editor;
        catalog = editor.getAgentCatalog();
        sidebar = new AgentSidebarPanel(editor, catalog, controller.panel());
        uiService.addSidebarPanel(ID, icon, "AI Agent", sidebar, UIService.SidebarPosition.RIGHT);
        // "N agents" in the status bar, live from the session registry.
        statusItem = new javax.swing.JLabel();
        statusItem.setToolTipText("Agent sessions connected to this editor");
        sessionListener = e -> javax.swing.SwingUtilities.invokeLater(this::refreshStatusItem);
        editor.getSessionRegistry().addListener(sessionListener);
        refreshStatusItem();
        uiService.addStatusBarItem(ID + ".sessions", statusItem);
        // "Send selection to AI Agent": the editor's popup and the terminal's menu route here.
        selectionSink = text -> javax.swing.SwingUtilities.invokeLater(() -> {
            uiService.showSidebarPanel(ID, UIService.SidebarPosition.RIGHT);
            sidebar.sendToCurrentAgent(text);
        });
        com.dogsbay.dogsbayaieditor.SelectionToAgent.register(selectionSink);
        popupContributor = (editorPane, offset) -> {
            String selected = editorPane.getSelectedText();
            if (selected == null || selected.isBlank()) {
                return java.util.List.of();
            }
            javax.swing.JMenuItem item = new javax.swing.JMenuItem(com.dogsbay.dogsbayaieditor.SelectionToAgent.LABEL);
            item.setToolTipText("Run the selected text as a prompt in the agent tab that is in front");
            item.addActionListener(e -> com.dogsbay.dogsbayaieditor.SelectionToAgent.send(selected));
            return java.util.List.of(item);
        };
        uiService.addEditorPopupContributor(popupContributor);
        activityItem = new javax.swing.JMenuItem("Agent activity…");
        activityItem.setToolTipText("What agents changed in this project, from the audit log");
        activityItem.addActionListener(e -> new AgentActivityDialog(editor, editor).setVisible(true));
        uiService.addMenuItem(com.dogsbay.dogsbayaieditor.services.MenuBuilder.PROJECT_MENU, activityItem);

        // The plugin activates before a project folder is open, so the agent may
        // be rooted at the launch dir. Nudge a re-sync on document activity (which
        // follows opening a project) so the root + AGENTS.md status track the
        // project even when the panel was already visible. The panel-shown
        // listener in the controller covers the reveal-the-panel flow.
        this.context = ctx;
        activeDocHandler = e -> controller.refreshForProject();
        openedHandler = e -> controller.refreshForProject();
        ctx.getEventBus().subscribe(ActiveDocumentChangedEvent.class, activeDocHandler);
        ctx.getEventBus().subscribe(DocumentOpenedEvent.class, openedHandler);

        active = this;   // enable "Fix with AI" routing from validation markers
    }

    /**
     * The integration server was started or stopped. Hosted agents are given
     * their tools at session start and cannot be handed more, so the sessions
     * already running need telling rather than fixing.
     */
    public static void integrationServerChanged() {
        AgentPlugin plugin = active;
        if (plugin != null && plugin.sidebar != null) {
            plugin.sidebar.integrationServerChanged();
        }
    }

    /**
     * Route a validation-marker fix to the live agent panel, revealing it first.
     * Returns false when the agent plugin isn't active (caller should hint the
     * user to open/configure it). Safe to call from the EDT.
     */
    public static boolean fixWithAI(FixRequest request) {
        AgentPlugin plugin = active;
        if (plugin == null || plugin.controller == null) {
            return false;
        }
        if (plugin.uiService != null) {
            plugin.uiService.showSidebarPanel(ID, UIService.SidebarPosition.RIGHT);
        }
        plugin.controller.fixWithAgent(request);
        return true;
    }

    @Override
    public void deactivate() {
        if (active == this) {
            active = null;
        }
        if (selectionSink != null) {
            com.dogsbay.dogsbayaieditor.SelectionToAgent.unregister(selectionSink);
            selectionSink = null;
        }
        if (popupContributor != null && uiService != null) {
            uiService.removeEditorPopupContributor(popupContributor);
            popupContributor = null;
        }
        if (context != null && context.getEventBus() != null) {
            if (activeDocHandler != null) {
                context.getEventBus().unsubscribe(ActiveDocumentChangedEvent.class, activeDocHandler);
            }
            if (openedHandler != null) {
                context.getEventBus().unsubscribe(DocumentOpenedEvent.class, openedHandler);
            }
            context = null;
        }
        if (sidebar != null) {
            sidebar.closeAll();
            sidebar = null;
        }
        if (controller != null) {
            controller.dispose();
            controller = null;
        }
        if (uiService != null) {
            uiService.removeSidebarPanel(ID, UIService.SidebarPosition.RIGHT);
            uiService.removeStatusBarItem(ID + ".sessions");
            if (activityItem != null) {
                uiService.removeMenuItem(com.dogsbay.dogsbayaieditor.services.MenuBuilder.PROJECT_MENU, activityItem);
                activityItem = null;
            }
            uiService = null;
        }
        if (sessionListener != null && editorRef != null) {
            editorRef.getSessionRegistry().removeListener(sessionListener);
        }
        sessionListener = null;
        editorRef = null;
    }

    private void refreshStatusItem() {
        if (statusItem == null || editorRef == null) {
            return;
        }
        long agents = editorRef.getSessionRegistry().list().stream()
                .filter(com.dogsbay.agent.session.AgentSession::isAgent).count();
        statusItem.setText(agents == 1 ? "1 agent" : agents + " agents");
    }

    /**
     * Tidy the session transcripts: remove the empty ones a launch used to
     * leave behind, and, when an age is configured, those older than it. Runs
     * when the plugin activates, which is at editor startup, on a virtual
     * thread so it never delays the window appearing.
     *
     * <p>Nothing happens by default — {@code 0} days means keep everything, and
     * a transcript is the readable record of what an agent did to a project.
     */
    private static void applySessionRetention(DogsBayAIEditor editor) {
        if (editor == null) {
            return;
        }
        int days = editor.getProperties().getAgentSessionRetentionDays();
        Thread.ofVirtual().name("agent-session-retention").start(() -> {
            // Measuring the fixed-width font takes a couple of hundred
            // milliseconds; do it here rather than on the event thread when the
            // first reply arrives.
            com.dogsbay.agent.ui.MarkdownStyler.warmUp();
            var sessions = new com.xagent.session.SessionManager();
            try {
                // Empty transcripts go regardless of the retention setting:
                // earlier builds wrote one per launch, and a session nobody
                // spoke in is not a record of anything.
                int empty = sessions.deleteEmptySessions();
                if (empty > 0) {
                    System.out.println("[agent] removed " + empty + " empty session file(s)");
                }
                if (days > 0) {
                    var deleted = sessions.deleteSessionsOlderThan(days);
                    if (!deleted.isEmpty()) {
                        System.out.println("[agent] deleted " + deleted.size()
                                + " session transcript(s) older than " + days + " days");
                    }
                }
            } catch (Exception e) {
                System.err.println("[agent] could not tidy agent sessions: " + e.getMessage());
            }
        });
    }

    private static DogsBayAIEditor editorOf(PluginContext ctx) {
        return ctx instanceof DefaultPluginContext d ? d.getEditor() : null;
    }
}
