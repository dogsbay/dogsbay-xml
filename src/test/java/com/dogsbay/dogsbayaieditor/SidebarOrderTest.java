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

package com.dogsbay.dogsbayaieditor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The sidebar's arrangement is the product's, not a consequence of the order
 * plugins happen to start in — which is an order that changes when one of them
 * is disabled. Ranks are compared within a rail, so one list orders both.
 */
class SidebarOrderTest {

    @Test
    void theAgentAndItsProposalsLeadTheirRail() {
        assertThat(SidebarOrder.rankOf("agent")).isZero();
        assertThat(SidebarOrder.rankOf("proposals")).isEqualTo(1);
        // They share the right-hand rail with these, and come before all of them.
        assertThat(SidebarOrder.rankOf("proposals"))
            .isLessThan(SidebarOrder.rankOf("outline"))
            .isLessThan(SidebarOrder.rankOf("properties"))
            .isLessThan(SidebarOrder.rankOf("metadata"));
    }

    @Test
    void aPanelNobodyNamedStillGetsAPlace() {
        // A plugin someone else wrote should appear at the end, not be refused.
        int unknown = SidebarOrder.rankOf("someone-elses-plugin");

        assertThat(unknown).isGreaterThan(SidebarOrder.rankOf("bookmarks"));
        assertThat(SidebarOrder.rankOf(null)).isEqualTo(unknown);
    }

    @Test
    void arrivalOrderDoesNotDecideTheRail() {
        // Plugins start in whatever order the registry walks them; sorting by
        // rank is what makes the result the same either way.
        List<String> asStarted = List.of("metadata", "proposals", "outline", "agent");

        List<String> shown = asStarted.stream()
            .sorted(Comparator.comparingInt(SidebarOrder::rankOf))
            .toList();

        assertThat(shown).containsExactly("agent", "proposals", "outline", "metadata");
    }

    @Test
    void bothPanelsAreOnTheRight() throws Exception {
        String agent = Files.readString(Path.of(
            "src/main/java/com/dogsbay/dogsbayaieditor/plugin/agent/AgentPlugin.java"));
        String proposals = Files.readString(Path.of(
            "src/main/java/com/dogsbay/dogsbayaieditor/plugin/proposals/ProposalsPlugin.java"));

        // Adding, showing and removing must agree: a panel added to one rail
        // and removed from the other is a panel that cannot be turned off.
        assertThat(agent).doesNotContain("SidebarPosition.LEFT");
        assertThat(proposals).doesNotContain("SidebarPosition.LEFT");
        assertThat(agent).contains("SidebarPosition.RIGHT");
        assertThat(proposals).contains("SidebarPosition.RIGHT");
    }

    @Test
    void theLeadingPanelIsTheOneShownOnStartUp() throws Exception {
        // The editor adds Properties itself and the plugins activate after, so
        // "select the first that arrives" showed Properties every time however
        // the rail was ordered.
        var shown = new java.util.concurrent.atomic.AtomicReference<String>();
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            ExplorerContainer container = new ExplorerContainer();
            container.addExplorer("properties", null, "Properties", new javax.swing.JPanel());
            container.addExplorer("navigator", null, "Navigator", new javax.swing.JPanel());
            container.addExplorer("agent", null, "AI Agent", new javax.swing.JPanel());
            container.addExplorer("proposals", null, "Proposals", new javax.swing.JPanel());
            shown.set(container.getSelectedId());
        });

        assertThat(shown.get()).isEqualTo("agent");
    }

    @Test
    void aPanelTheReaderChoseIsNotTakenAway() throws Exception {
        // A plugin finishing its start-up must not move someone who has already
        // clicked somewhere.
        var shown = new java.util.concurrent.atomic.AtomicReference<String>();
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            ExplorerContainer container = new ExplorerContainer();
            container.addExplorer("properties", null, "Properties", new javax.swing.JPanel());
            container.setSelectedExplorer("properties");
            container.addExplorer("agent", null, "AI Agent", new javax.swing.JPanel());
            shown.set(container.getSelectedId());
        });

        assertThat(shown.get()).isEqualTo("properties");
    }
}
