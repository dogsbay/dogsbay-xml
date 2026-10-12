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

package com.dogsbay.dogsbayaieditor.services;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.dogsbay.dogsbayaieditor.ditaproject.Deliverable;
import com.dogsbay.dogsbayaieditor.ditaproject.DeliverableVariants;
import com.dogsbay.dogsbayaieditor.ditaproject.ProjectContext;
import com.dogsbay.dogsbayaieditor.services.events.DeliverableChangedEvent;

/**
 * Holds the workspace's resolved {@link ProjectContext} and the single
 * <em>active deliverable</em> — the shared "current map · profile" state that
 * deep validate, preview, publish, and the status bar all read. Changing it
 * broadcasts a {@link DeliverableChangedEvent} on the {@link EventBus}.
 *
 * <p>An editor-app service (it uses the {@link EventBus}), so it lives in
 * {@code services} rather than the GUI-free {@code ditaproject} core. Pure state +
 * notification: the editor loads the context (and persists the selection in
 * {@code ProjectProperties}); this service neither does I/O nor touches Swing.
 */
public final class DeliverableService {

    private final EventBus eventBus;
    private ProjectContext context;
    private Deliverable active;

    public DeliverableService(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /** The resolved DITA project context, or null when none is loaded. */
    public ProjectContext getContext() {
        return context;
    }

    /**
     * Install a freshly-resolved context and pick the active deliverable:
     * the {@code (preferredFile, preferredName)} if it still resolves (a restored
     * persisted selection), else the previously-active one if it survives a
     * reload, else the {@linkplain ProjectContext#defaultDeliverable() default}.
     */
    public void setContext(ProjectContext ctx, Path preferredFile, String preferredName) {
        this.context = ctx;
        Deliverable next = null;
        if (ctx != null) {
            if (preferredName != null) {
                next = ctx.deliverable(preferredFile, preferredName);
                if (next == null) {
                    next = findVariant(ctx, preferredFile, preferredName);
                }
            }
            if (next == null && active != null) {
                next = ctx.deliverable(active.sourceFile(), active.name());
                if (next == null) {
                    next = findVariant(ctx, active.sourceFile(), active.name());
                }
            }
            if (next == null) {
                next = ctx.defaultDeliverable();
            }
        }
        setActiveDeliverable(next);
    }

    /**
     * A derived branch variant matching {@code (file, name)}, or null. Branch
     * variants aren't in {@link ProjectContext#deliverables()} (they're computed
     * from each deliverable's map), so a persisted/previously-active variant
     * selection is restored by re-deriving and matching by identity — keeping
     * variant selection sticky across a context reload, just like a base deliverable.
     */
    private static Deliverable findVariant(ProjectContext ctx, Path file, String name) {
        // A variant name is always "<base> · <label>" (see DeliverableVariants). Skip
        // the per-map enumeration I/O for plain/stale names that can't be a variant.
        if (name == null || !name.contains(" · ")) {
            return null;
        }
        for (Deliverable base : ctx.deliverables()) {
            for (Deliverable variant : DeliverableVariants.of(base)) {
                if (variant.name().equals(name)
                        && (file == null || Objects.equals(variant.sourceFile(), file))) {
                    return variant;
                }
            }
        }
        return null;
    }

    /** The active deliverable, or null when no DITA project / deliverable. */
    public Deliverable getActiveDeliverable() {
        return active;
    }

    /**
     * Set the active deliverable. The reference is always updated (so a reload
     * refreshes a same-identity deliverable's map/ditaval), but a
     * {@link DeliverableChangedEvent} fires only when the <em>selection identity</em>
     * (source file + name) actually changes.
     */
    public void setActiveDeliverable(Deliverable d) {
        boolean changed = !sameSelection(active, d);
        this.active = d;
        if (changed && eventBus != null) {
            eventBus.publish(new DeliverableChangedEvent(d));
        }
    }

    /**
     * The DITA 1.3 branch-filter variants of the active deliverable (one derived
     * {@link Deliverable} per {@code <ditavalref>} in its map), or an empty list when
     * there is no active deliverable or its map has no branches. The shared accessor
     * every UI surface (status-bar popup, Map Explorer) reads, so the variant
     * inventory is computed one way. Recomputed on call — branches live in the map,
     * which the user may have just edited.
     */
    public List<Deliverable> variantsOfActive() {
        // Only base deliverables have branches; a derived variant shares its base's
        // map, so re-projecting it would yield nonsensical second-order variants.
        if (active == null || !isBaseDeliverable(active)) {
            return List.of();
        }
        return DeliverableVariants.of(active);
    }

    /** True when {@code d} is one of the context's own deliverables (not a derived
     *  branch variant, which isn't in {@link ProjectContext#deliverables()}). */
    private boolean isBaseDeliverable(Deliverable d) {
        return context != null && context.deliverables().stream().anyMatch(b ->
                Objects.equals(b.sourceFile(), d.sourceFile()) && b.name().equals(d.name()));
    }

    /** Select by identity, resolving against the current context; no-op if unknown. */
    public void setActiveDeliverable(Path sourceFile, String name) {
        if (context == null) {
            return;
        }
        Deliverable d = context.deliverable(sourceFile, name);
        if (d != null) {
            setActiveDeliverable(d);
        }
    }

    private static boolean sameSelection(Deliverable a, Deliverable b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return Objects.equals(a.sourceFile(), b.sourceFile()) && a.name().equals(b.name());
    }
}
