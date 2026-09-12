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

package com.dogsbay.dogsbayaieditor.ditaproject;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.services.DeliverableService;
import com.dogsbay.dogsbayaieditor.services.EventBus;
import com.dogsbay.dogsbayaieditor.services.events.DeliverableChangedEvent;

/**
 * P2: the active-deliverable model. {@link EventBus} dispatches on the EDT, so
 * service mutations run inside {@code invokeAndWait} (making {@code publish}
 * synchronous); captured events are read after it returns (happens-before).
 */
class DeliverableServiceTest {

    @TempDir Path tmp;

    private final Path root = Path.of("/ws").toAbsolutePath();
    private final Path fileA = root.resolve("projects/rosa.json");
    private final Path fileB = root.resolve("projects/enterprise.json");

    /** Two deliverables that COLLIDE on name ("HTML5"), differing only by file. */
    private ProjectContext ctx() {
        Deliverable a = new Deliverable("HTML5", root.resolve("g.ditamap"),
                List.of(root.resolve("rosa.ditaval")), "html5", null, List.of(), fileA, "html");
        Deliverable b = new Deliverable("HTML5", root.resolve("g.ditamap"),
                List.of(root.resolve("ent.ditaval")), "html5", null, List.of(), fileB, "html");
        return new ProjectContext(root, fileA, "json", List.of(), List.of(a, b), null,
                List.of(fileA, fileB));
    }

    private static void edt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    private List<Deliverable> subscribe(EventBus bus) {
        List<Deliverable> seen = new ArrayList<>();
        bus.subscribe(DeliverableChangedEvent.class, e -> seen.add(e.deliverable()));
        return seen;
    }

    @Test
    void setContextSelectsDefaultAndFiresOnce() throws Exception {
        EventBus bus = new EventBus();
        List<Deliverable> seen = subscribe(bus);
        DeliverableService svc = new DeliverableService(bus);

        edt(() -> svc.setContext(ctx(), null, null));

        assertThat(svc.getActiveDeliverable()).isNotNull();
        assertThat(svc.getActiveDeliverable().sourceFile()).isEqualTo(fileA); // default = first
        assertThat(seen).hasSize(1);
    }

    @Test
    void restoresPersistedSelectionDisambiguatedByFile() throws Exception {
        EventBus bus = new EventBus();
        DeliverableService svc = new DeliverableService(bus);

        // Restore the enterprise "HTML5" specifically — name alone is ambiguous.
        edt(() -> svc.setContext(ctx(), fileB, "HTML5"));

        assertThat(svc.getActiveDeliverable().sourceFile()).isEqualTo(fileB);
        assertThat(svc.getActiveDeliverable().ditaval()).isEqualTo(root.resolve("ent.ditaval"));
    }

    @Test
    void switchingSelectionFiresEventButNoOpDoesNot() throws Exception {
        EventBus bus = new EventBus();
        List<Deliverable> seen = subscribe(bus);
        DeliverableService svc = new DeliverableService(bus);

        edt(() -> {
            svc.setContext(ctx(), fileA, "HTML5");     // 1 event
            svc.setActiveDeliverable(fileB, "HTML5");  // 2nd event (different file)
            svc.setActiveDeliverable(fileB, "HTML5");  // same selection -> no event
        });

        assertThat(seen).hasSize(2);
        assertThat(svc.getActiveDeliverable().sourceFile()).isEqualTo(fileB);
    }

    @Test
    void reloadRefreshesReferenceWithoutFiringWhenIdentitySame() throws Exception {
        EventBus bus = new EventBus();
        List<Deliverable> seen = subscribe(bus);
        DeliverableService svc = new DeliverableService(bus);
        Deliverable[] first = new Deliverable[1];

        edt(() -> {
            svc.setContext(ctx(), fileA, "HTML5"); // 1 event
            first[0] = svc.getActiveDeliverable();
            svc.setContext(ctx(), null, null);     // reload: same default identity (fileA/HTML5)
        });

        assertThat(seen).hasSize(1); // no spurious event
        assertThat(svc.getActiveDeliverable()).isNotSameAs(first[0]); // reference refreshed
        assertThat(svc.getActiveDeliverable().sourceFile()).isEqualTo(fileA);
    }

    @Test
    void nullContextClearsActive() throws Exception {
        EventBus bus = new EventBus();
        List<Deliverable> seen = subscribe(bus);
        DeliverableService svc = new DeliverableService(bus);

        edt(() -> {
            svc.setContext(ctx(), null, null);  // active set (1 event)
            svc.setContext(null, null, null);   // cleared (2nd event, null)
        });

        assertThat(svc.getActiveDeliverable()).isNull();
        assertThat(seen).hasSize(2);
        assertThat(seen.get(1)).isNull();
    }

    @Test
    void variantsOfActiveProjectsTheActiveMapsBranches() throws Exception {
        Files.writeString(tmp.resolve("base.ditaval"), "<val/>");
        Files.writeString(tmp.resolve("win.ditaval"), "<val/>");
        Path map = tmp.resolve("guide.ditamap");
        Files.writeString(map, """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>G</title>
              <topicref href="install.dita">
                <ditavalref href="win.ditaval"><ditavalmeta>
                  <dvrResourcePrefix>win-</dvrResourcePrefix></ditavalmeta></ditavalref>
              </topicref>
            </map>
            """);
        Path projectFile = tmp.resolve("project.json");
        Deliverable d = new Deliverable("full", map, List.of(tmp.resolve("base.ditaval")),
                "html5", null, List.of(), projectFile, null);
        ProjectContext ctx = new ProjectContext(tmp, projectFile, "json",
                List.of(), List.of(d), null, List.of(projectFile));

        DeliverableService svc = new DeliverableService(new EventBus());
        edt(() -> svc.setContext(ctx, null, null));

        assertThat(svc.variantsOfActive()).extracting(Deliverable::name)
                .containsExactly("full · win-");

        edt(() -> svc.setContext(null, null, null));   // no active → no variants
        assertThat(svc.variantsOfActive()).isEmpty();
    }

    @Test
    void variantsOfActiveIsEmptyWhenTheActiveSelectionIsItselfAVariant() throws Exception {
        Files.writeString(tmp.resolve("base.ditaval"), "<val/>");
        Files.writeString(tmp.resolve("win.ditaval"), "<val/>");
        Path map = tmp.resolve("guide.ditamap");
        Files.writeString(map, """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>G</title>
              <topicref href="install.dita">
                <ditavalref href="win.ditaval"><ditavalmeta>
                  <dvrResourcePrefix>win-</dvrResourcePrefix></ditavalmeta></ditavalref>
              </topicref>
            </map>
            """);
        Path projectFile = tmp.resolve("project.json");
        Deliverable d = new Deliverable("full", map, List.of(tmp.resolve("base.ditaval")),
                "html5", null, List.of(), projectFile, null);
        ProjectContext ctx = new ProjectContext(tmp, projectFile, "json",
                List.of(), List.of(d), null, List.of(projectFile));

        DeliverableService svc = new DeliverableService(new EventBus());
        edt(() -> svc.setContext(ctx, null, null));
        Deliverable variant = svc.variantsOfActive().get(0);     // base → one variant
        edt(() -> svc.setActiveDeliverable(variant));            // make the variant active

        // A variant has no further branches — must not re-project into "full · win- · win-".
        assertThat(svc.variantsOfActive()).isEmpty();
    }

    @Test
    void restoresAPersistedBranchVariantSelectionAcrossReload() throws Exception {
        Files.writeString(tmp.resolve("base.ditaval"), "<val/>");
        Files.writeString(tmp.resolve("win.ditaval"), "<val/>");
        Path map = tmp.resolve("guide.ditamap");
        Files.writeString(map, """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>G</title>
              <topicref href="install.dita">
                <ditavalref href="win.ditaval"><ditavalmeta>
                  <dvrResourcePrefix>win-</dvrResourcePrefix></ditavalmeta></ditavalref>
              </topicref>
            </map>
            """);
        Path projectFile = tmp.resolve("project.json");
        Deliverable d = new Deliverable("full", map, List.of(tmp.resolve("base.ditaval")),
                "html5", null, List.of(), projectFile, null);
        ProjectContext ctx = new ProjectContext(tmp, projectFile, "json",
                List.of(), List.of(d), null, List.of(projectFile));

        DeliverableService svc = new DeliverableService(new EventBus());
        // A variant is the persisted selection; it isn't in ctx.deliverables(), so the
        // service must re-derive it rather than fall back to the base default.
        edt(() -> svc.setContext(ctx, projectFile, "full · win-"));

        Deliverable active = svc.getActiveDeliverable();
        assertThat(active).isNotNull();
        assertThat(active.name()).isEqualTo("full · win-");
        assertThat(active.ditavals())
                .containsExactly(tmp.resolve("base.ditaval"), tmp.resolve("win.ditaval"));
    }

    @Test
    void contextLookupByFileAndNameIsUnambiguous() {
        ProjectContext ctx = ctx();
        assertThat(ctx.deliverable(fileA, "HTML5").sourceFile()).isEqualTo(fileA);
        assertThat(ctx.deliverable(fileB, "HTML5").sourceFile()).isEqualTo(fileB);
        assertThat(ctx.deliverable("HTML5").sourceFile()).isEqualTo(fileA); // name-only = first
        assertThat(ctx.deliverable(fileA, "nope")).isNull();
    }
}
