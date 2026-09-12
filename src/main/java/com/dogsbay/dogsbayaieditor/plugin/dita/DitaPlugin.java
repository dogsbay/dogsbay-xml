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

package com.dogsbay.dogsbayaieditor.plugin.dita;

import java.io.File;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.ExplorerContainer;
import com.dogsbay.dogsbayaieditor.dita.ui.DitaExplorerPanel;
import com.dogsbay.dogsbayaieditor.plugin.DefaultPluginContext;
import com.dogsbay.dogsbayaieditor.plugin.Plugin;
import com.dogsbay.dogsbayaieditor.plugin.PluginContext;
import com.dogsbay.dogsbayaieditor.plugin.UIService;
import com.dogsbay.dogsbayaieditor.services.MenuBuilder;

/**
 * Built-in plugin that provides DITA support out of the box:
 * <ul>
 *   <li>Registers 7 DITA grammar types, 6 templates, and the DITA 1.3 DTD catalog
 *       as built-in (transient) entries — see {@link DitaBuiltInAssets}.</li>
 *   <li>Adds the DITA Map explorer to the left sidebar.</li>
 *   <li>Auto-loads the default root map when the DITA explorer tab is selected
 *       and a DITA project is active.</li>
 * </ul>
 *
 * <p>Bundled DTDs/types/templates ship inside the JAR and are extracted to
 * {@code ~/.dogsbay/builtin/dita/} on first activation per editor version.
 * No framework import is required to validate or template DITA documents.
 */
public class DitaPlugin implements Plugin {

    private static final Logger LOG = LoggerFactory.getLogger(DitaPlugin.class);
    private static final String SIDEBAR_ID = "dita";
    private static final String ICON_PATH = "com/dogsbay/dogsbayaieditor/icons/sidebar/list-tree.png";

    private static final String DELIVERABLE_STATUS_ID = "dita-deliverable";
    private static final String METADATA_SIDEBAR_ID = "dita-metadata";
    private static final String METADATA_ICON_PATH =
            "com/dogsbay/dogsbayaieditor/icons/sidebar/symbol-file.png";

    private PluginContext context;
    private DogsBayAIEditor editor;
    private DitaExplorerPanel ditaExplorer;
    private ChangeListener changeListener;
    private ExplorerContainer explorerContainer;

    /** Project-menu entries this plugin contributed, for removal on deactivate. */
    private final java.util.List<javax.swing.JMenuItem> projectMenuItems =
            new java.util.ArrayList<>();
    /** Items contributed into the core's Project → Validate submenu. */
    private final java.util.List<javax.swing.JMenuItem> validateMenuItems =
            new java.util.ArrayList<>();
    private com.dogsbay.dogsbayaieditor.StatusSegment deliverableSegment;
    private java.util.function.Consumer<
            com.dogsbay.dogsbayaieditor.services.events.DeliverableChangedEvent> deliverableListener;
    private com.dogsbay.dogsbayaieditor.dita.ui.MetadataPanel metadataPanel;
    private java.util.function.Consumer<
            com.dogsbay.dogsbayaieditor.services.events.ActiveDocumentChangedEvent> activeDocListener;

    /** Cached controlled-value scheme for attribute-value completion (rebuilt off-EDT). */
    private volatile com.dogsbay.dogsbayaieditor.links.scheme.SubjectScheme conditionScheme;
    private com.dogsbay.xml.editor.AttributeValueContributors.ValueProvider valueProvider;

    @Override
    public String getId() {
        return "com.dogsbay.dita";
    }

    @Override
    public String getName() {
        return "DITA Support";
    }

    @Override
    public String getDescription() {
        return "DITA 1.3 grammar, templates, catalog, map explorer, and publishing";
    }

    @Override
    public boolean isBuiltIn() {
        return true;
    }

    @Override
    public void activate(PluginContext ctx) {
        this.context = ctx;

        LOG.info("Activating DITA Support plugin");

        this.editor = ((DefaultPluginContext) ctx).getEditor();
        DogsBayAIEditor editor = this.editor;

        // Bundled DITA editing assets (types, templates, DTD catalog).
        // Failure here must not prevent the Map Explorer from loading — the user
        // can still import a custom DITA framework if the bundled assets break.
        try {
            File extracted = DitaBuiltInAssets.install(editor.getProperties());
            LOG.info("DITA built-in assets ready at {}", extracted);
        } catch (Exception e) {
            LOG.error("Failed to install DITA built-in assets", e);
        }

        // Seed the default DITA-OT publishing framework on a fresh install so the
        // Framework Manager isn't empty and HTML5 publishing works out of the box.
        // Idempotent + version-gated; never throws.
        com.dogsbay.dogsbayaieditor.framework.DefaultDitaOtFramework
                .installIfNeeded(editor.getProperties());

        try {
            ditaExplorer = new DitaExplorerPanel(editor);

            javax.swing.Icon icon = ctx.getUIService().loadSidebarIcon(ICON_PATH);
            ctx.getUIService().addSidebarPanel(SIDEBAR_ID, icon, "Topic Maps",
                    ditaExplorer, UIService.SidebarPosition.LEFT);

            // Auto-load DITA map when explorer tab is selected
            explorerContainer = editor.getExplorerContainer();
            changeListener = new ChangeListener() {
                public void stateChanged(ChangeEvent event) {
                    if (explorerContainer.getSelectedComponent() == ditaExplorer) {
                        autoLoadDitaMap(editor);
                    }
                }
            };
            explorerContainer.addChangeListener(changeListener);

            // Contribute DITA-specific GUI to the (core) Project menu + status bar.
            contributeProjectMenuItems(ctx.getUIService(), editor);
            contributeDeliverableSelector(ctx.getUIService(), editor);

            // Offer subjectScheme controlled values in attribute-value completion.
            valueProvider = (elementName, attributeName, file, docTypePublicId) -> {
                var scheme = conditionScheme;
                if (scheme == null || !isDitaDocument(docTypePublicId, file)) {
                    return java.util.Set.of(); // don't leak DITA values into non-DITA docs
                }
                return scheme.allowedValues(attributeName);
            };
            ctx.getUIService().addAttributeValueProvider(valueProvider);
            rebuildConditionSchemeAsync();

            // Metadata panel (right sidebar) — shows the active doc's metadata + policy.
            metadataPanel = new com.dogsbay.dogsbayaieditor.dita.ui.MetadataPanel(editor);
            javax.swing.Icon metaIcon = ctx.getUIService().loadSidebarIcon(METADATA_ICON_PATH);
            ctx.getUIService().addSidebarPanel(METADATA_SIDEBAR_ID, metaIcon, "Metadata",
                    metadataPanel, UIService.SidebarPosition.RIGHT);
            activeDocListener = e -> metadataPanel.refresh();
            if (editor.getEventBus() != null) {
                editor.getEventBus().subscribe(
                        com.dogsbay.dogsbayaieditor.services.events.ActiveDocumentChangedEvent.class,
                        activeDocListener);
            }

            LOG.info("DITA Support plugin activated");
        } catch (Exception e) {
            LOG.error("Failed to activate DITA Support plugin", e);
        }
    }

    private void autoLoadDitaMap(DogsBayAIEditor editor) {
        try {
            if (ditaExplorer != null) {
                // Resolution (persisted last-open map, else default root map) lives in
                // the panel so launch-restore and tab-selection behave identically.
                ditaExplorer.restoreMapForCurrentProject();
            }
        } catch (Exception e) {
            LOG.error("Error auto-loading DITA map", e);
        }
    }

    @Override
    public void deactivate() {
        LOG.info("Deactivating DITA Explorer plugin");

        try {
            if (explorerContainer != null && changeListener != null) {
                explorerContainer.removeChangeListener(changeListener);
            }
            if (context != null) {
                UIService ui = context.getUIService();
                ui.removeSidebarPanel(SIDEBAR_ID, UIService.SidebarPosition.LEFT);
                ui.removeSidebarPanel(METADATA_SIDEBAR_ID, UIService.SidebarPosition.RIGHT);
                for (javax.swing.JMenuItem item : projectMenuItems) {
                    ui.removeMenuItem(MenuBuilder.PROJECT_MENU, item);
                }
                projectMenuItems.clear();
                for (javax.swing.JMenuItem item : validateMenuItems) {
                    ui.removeMenuItem(MenuBuilder.PROJECT_MENU,
                            MenuBuilder.PROJECT_VALIDATE_MENU, item);
                }
                validateMenuItems.clear();
                ui.removeStatusBarItem(DELIVERABLE_STATUS_ID);
                if (valueProvider != null) {
                    ui.removeAttributeValueProvider(valueProvider);
                    valueProvider = null;
                }
            }
            if (deliverableListener != null && editor != null && editor.getEventBus() != null) {
                editor.getEventBus().unsubscribe(
                        com.dogsbay.dogsbayaieditor.services.events.DeliverableChangedEvent.class,
                        deliverableListener);
                deliverableListener = null;
            }
            if (activeDocListener != null && editor != null && editor.getEventBus() != null) {
                editor.getEventBus().unsubscribe(
                        com.dogsbay.dogsbayaieditor.services.events.ActiveDocumentChangedEvent.class,
                        activeDocListener);
                activeDocListener = null;
            }
        } catch (Exception e) {
            LOG.error("Error during DITA Explorer plugin deactivation", e);
        }
    }

    // ── DITA-specific GUI contributed to core menus / status bar ─────────

    private void contributeProjectMenuItems(UIService ui, DogsBayAIEditor editor) {
        // Validators go into the core's Project → Validate submenu, so DITA validation
        // sits with project and Schematron validation instead of in a separate run.
        // Current map first: narrower scope and the more frequent choice.
        addValidateItem(ui, "With DITA-OT — Current Map...",
                e -> editor.validateCurrentMapWithDitaOt());
        addValidateItem(ui, "With DITA-OT — All Deliverables...",
                e -> editor.validateAllDeliverablesWithDitaOt());
        addValidateItem(ui, "Controlled Values (Subject Scheme)...",
                e -> editor.validateConditions());

        javax.swing.JMenu metadataMenu = new javax.swing.JMenu("Metadata");
        metadataMenu.add(menuItem("Edit Policy...", e -> editor.editMetadataPolicy()));
        metadataMenu.add(menuItem("Audit...", e -> editor.auditMetadata()));
        metadataMenu.add(menuItem("Normalize...", e -> editor.normalizeMetadata()));
        metadataMenu.add(menuItem("Export Policy as Schematron...",
                e -> editor.exportMetadataSchematron()));
        addProjectGroup(ui, metadataMenu);

        // Both of these act on the active deliverable's map, not the project. The submenu
        // makes that scope legible; moving them onto the Map Explorer's clicked node is a
        // separate change, since it would alter what they operate on.
        javax.swing.JMenu mapMenu = new javax.swing.JMenu("Map");
        mapMenu.add(menuItem("Edit Structure...", e -> editor.editMapStructure()));
        mapMenu.add(menuItem("Edit Relationship Tables...", e -> editor.editReltables()));
        addProjectGroup(ui, mapMenu);

        // The build is the most-used command here, so it leads its group.
        addProjectGroup(ui, menuItem("Build Deliverables...", e -> editor.buildDeliverables()));
        addProjectGroup(ui, menuItem("Manage Deliverables...", e -> editor.manageDeliverables()));
    }

    /** Builds a menu item with its action already attached. */
    private javax.swing.JMenuItem menuItem(String text, java.awt.event.ActionListener action) {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(text);
        item.addActionListener(action);
        return item;
    }

    /** Adds an item into the core's Project → Validate submenu. */
    private void addValidateItem(UIService ui, String text,
            java.awt.event.ActionListener action) {
        javax.swing.JMenuItem item = menuItem(text, action);
        ui.addMenuItem(MenuBuilder.PROJECT_MENU, MenuBuilder.PROJECT_VALIDATE_MENU, item);
        validateMenuItems.add(item);
    }

    /**
     * Adds a top-level entry to the Project menu, positioned above the formatting commands
     * rather than appended after them.
     */
    private void addProjectGroup(UIService ui, javax.swing.JMenuItem item) {
        ui.addMenuItemBefore(MenuBuilder.PROJECT_MENU, MenuBuilder.FORMAT_PROJECT_ITEM, item);
        projectMenuItems.add(item);
    }

    private void contributeDeliverableSelector(UIService ui, DogsBayAIEditor editor) {
        // Always-visible fixed segment matching the project/branch style; shows
        // "No deliverable" until one is active.
        deliverableSegment = new com.dogsbay.dogsbayaieditor.StatusSegment(
                "📦", "Active deliverable (map · profile) — click to switch");
        deliverableSegment.setOnClick(() -> showDeliverableSwitcher(editor));
        deliverableListener = e -> {
            updateDeliverableLabel(e.deliverable());
            rebuildConditionSchemeAsync();
            // Follow the deliverable in the Topic Maps panel: quietly load its root map so
            // the panel reflects the active deliverable (no persist/nag — the event can
            // fire on project open/restore). The 🗺 chooser stays the explicit, persisted
            // override that does NOT change the deliverable. (New HTML previews already
            // seed from the active deliverable when opened.)
            if (ditaExplorer != null && e.deliverable() != null && e.deliverable().map() != null) {
                ditaExplorer.followDeliverableMap(e.deliverable().map().toFile());
            }
        };
        if (editor.getEventBus() != null) {
            editor.getEventBus().subscribe(
                    com.dogsbay.dogsbayaieditor.services.events.DeliverableChangedEvent.class,
                    deliverableListener);
        }
        ui.addStatusBarItem(DELIVERABLE_STATUS_ID, deliverableSegment);
        if (editor.getDeliverableService() != null) {
            updateDeliverableLabel(editor.getDeliverableService().getActiveDeliverable());
        }
    }

    /**
     * Rebuild the cached {@link com.dogsbay.dogsbayaieditor.links.scheme.SubjectScheme}
     * off the EDT (a map-closure crawl + parse), so attribute-value completion can
     * read it without IO on the EDT. The scheme is project-global (v1), so it is
     * discovered from the project's default root map. Triggered on activation and
     * whenever the active deliverable changes.
     */
    private void rebuildConditionSchemeAsync() {
        java.io.File rootMap = (editor != null) ? editor.getDefaultRootMapFile() : null;
        if (rootMap == null || !rootMap.isFile()) {
            conditionScheme = com.dogsbay.dogsbayaieditor.links.scheme.SubjectScheme.empty();
            return;
        }
        Thread t = new Thread(() -> {
            try {
                conditionScheme = com.dogsbay.dogsbayaieditor.links.scheme.SubjectScheme
                        .fromRootMap(rootMap);
            } catch (Exception ex) {
                LOG.debug("Subject-scheme cache rebuild failed: {}", ex.toString());
            }
        }, "dita-scheme-cache");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Only offer subjectScheme values for DITA documents, not generic XML being
     * edited. The DOCTYPE public id is authoritative ({@code -//OASIS//DTD DITA …});
     * the file extension is a fallback for RNG/no-doctype DITA files.
     */
    private static boolean isDitaDocument(String docTypePublicId, java.io.File file) {
        if (docTypePublicId != null
                && docTypePublicId.toUpperCase(java.util.Locale.ROOT).contains("DITA")) {
            return true;
        }
        return isDitaFile(file);
    }

    private static boolean isDitaFile(java.io.File f) {
        if (f == null) {
            return false;
        }
        String n = f.getName().toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".dita") || n.endsWith(".ditamap")
                || n.endsWith(".ditaval") || n.endsWith(".bookmap");
    }

    private void updateDeliverableLabel(com.dogsbay.dogsbayaieditor.ditaproject.Deliverable d) {
        if (deliverableSegment == null) {
            return;
        }
        // Fixed segment with a muted "No deliverable" placeholder when none is active.
        deliverableSegment.setValue(
                d != null ? deliverableTitle(d, currentDeliverables()) : null, "No deliverable");
    }

    private java.util.List<com.dogsbay.dogsbayaieditor.ditaproject.Deliverable>
            currentDeliverables() {
        var svc = editor != null ? editor.getDeliverableService() : null;
        var ctx = svc != null ? svc.getContext() : null;
        return ctx != null ? ctx.deliverables() : java.util.List.of();
    }

    /**
     * Just the deliverable {@code name} — the common case — prefixed with the
     * project-file stem only when the name is ambiguous (the same name in more than
     * one of the project's deliverables, e.g. two project files both shipping "HTML5").
     */
    private static String deliverableTitle(
            com.dogsbay.dogsbayaieditor.ditaproject.Deliverable d,
            java.util.List<com.dogsbay.dogsbayaieditor.ditaproject.Deliverable> all) {
        long sameName = all == null ? 1
                : all.stream().filter(x -> x.name().equals(d.name())).count();
        if (sameName <= 1 || d.sourceFile() == null) {
            return d.name();
        }
        String file = d.sourceFile().getFileName().toString();
        int dot = file.lastIndexOf('.');
        if (dot > 0) {
            file = file.substring(0, dot);
        }
        return file + " · " + d.name();
    }

    /** True when {@code d} is the active selection (same source file + name) — also
     *  matches a derived variant, whose name carries its branch label. */
    private static boolean isActiveDeliverable(
            com.dogsbay.dogsbayaieditor.ditaproject.Deliverable active,
            com.dogsbay.dogsbayaieditor.ditaproject.Deliverable d) {
        return active != null
                && java.util.Objects.equals(active.sourceFile(), d.sourceFile())
                && active.name().equals(d.name());
    }

    private void showDeliverableSwitcher(DogsBayAIEditor editor) {
        var svc = editor.getDeliverableService();
        if (svc == null) {
            return;
        }
        // Re-resolve so newly-added project files / deliverables show up.
        editor.refreshDeliverableContext();
        var ctx = svc.getContext();
        if (ctx == null || ctx.deliverables().isEmpty()) {
            return;
        }
        var active = svc.getActiveDeliverable();
        javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
        for (var d : ctx.deliverables()) {
            javax.swing.JMenuItem item =
                    new javax.swing.JMenuItem(deliverableTitle(d, ctx.deliverables()));
            if (isActiveDeliverable(active, d)) {
                item.setFont(item.getFont().deriveFont(java.awt.Font.BOLD));
            }
            item.addActionListener(ev -> editor.setActiveDeliverableAndPersist(d));
            popup.add(item);
            // DITA 1.3 branch-filter variants of this deliverable's map (one per
            // <ditavalref>, derived — not in the project file). Selecting one targets
            // that branch for deep-validate / build / preview; the selection sticks
            // across reloads via DeliverableService's variant-aware restore.
            for (var variant
                    : com.dogsbay.dogsbayaieditor.ditaproject.DeliverableVariants.of(d)) {
                javax.swing.JMenuItem vItem =
                        new javax.swing.JMenuItem("    " + variant.name());
                vItem.setToolTipText("Branch-filter variant (DITA 1.3 ditavalref)");
                if (isActiveDeliverable(active, variant)) {
                    vItem.setFont(vItem.getFont().deriveFont(java.awt.Font.BOLD));
                }
                vItem.addActionListener(ev -> editor.setActiveDeliverableAndPersist(variant));
                popup.add(vItem);
            }
        }
        popup.addSeparator();
        javax.swing.JMenuItem manage = new javax.swing.JMenuItem("Manage Deliverables…");
        manage.addActionListener(ev -> editor.manageDeliverables());
        popup.add(manage);
        popup.show(deliverableSegment, 0, -popup.getPreferredSize().height);
    }

    public DitaExplorerPanel getDitaExplorer() {
        return ditaExplorer;
    }
}
