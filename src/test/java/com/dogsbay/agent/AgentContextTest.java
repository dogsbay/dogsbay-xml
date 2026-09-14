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

package com.dogsbay.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class AgentContextTest {

    @Test
    void emptyContextRendersNothing() {
        assertThat(AgentContext.empty().toPromptFragment()).isEmpty();
    }

    @Test
    void includesActiveFileAndSelection() {
        AgentContext ctx = new AgentContext(
                Path.of("/proj/install.dita"), "<note>be careful</note>",
                List.of(Path.of("/proj/install.dita")), "Agent");
        String fragment = ctx.toPromptFragment();

        assertThat(fragment).contains("install.dita");
        assertThat(fragment).contains("Current selection");
        assertThat(fragment).contains("be careful");
    }

    @Test
    void blankSelectionIsOmitted() {
        AgentContext ctx = new AgentContext(Path.of("/proj/x.dita"), "  ", List.of(), "Agent");
        assertThat(ctx.toPromptFragment()).doesNotContain("Current selection");
    }

    @Test
    void minimalConstructorOmitsEnrichedLines() {
        AgentContext ctx = new AgentContext(
                Path.of("/p/topic.dita"), null, List.of(Path.of("/p/topic.dita")), "Agent");
        String out = ctx.toPromptFragment();
        assertThat(out)
                .contains("Active file: " + Path.of("/p/topic.dita"))
                .doesNotContain("(")                 // no type/grammar annotation
                .doesNotContain("Active deliverable");
    }

    @Test
    void enrichedFragmentSurfacesDoctypeGrammarAndDeliverable() {
        AgentContext ctx = new AgentContext(
                Path.of("/p/topic.dita"), "<p>x</p>",
                List.of(Path.of("/p/topic.dita"), Path.of("/p/user-guide.ditamap")),
                "Agent",
                "concept (DITA)", "concept.dtd (DTD)",
                "PDF", Path.of("/p/user-guide.ditamap"));
        String out = ctx.toPromptFragment();
        assertThat(out)
                .contains("Active file: " + Path.of("/p/topic.dita") + "  (concept (DITA); concept.dtd (DTD))")
                .contains("Active deliverable: PDF  (map: user-guide.ditamap)")
                .contains("Current selection:")
                .contains("<p>x</p>");
    }

    @Test
    void doctypeWithoutGrammarStillAnnotates() {
        AgentContext ctx = new AgentContext(
                Path.of("/p/a.xml"), null, List.of(Path.of("/p/a.xml")), "Agent",
                "book", null, null, null);
        assertThat(ctx.toPromptFragment()).contains("Active file: " + Path.of("/p/a.xml") + "  (book)");
    }

    @Test
    void longOpenDocumentListIsCapped() {
        List<Path> many = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            many.add(Path.of("/p/file" + i + ".dita"));
        }
        AgentContext ctx = new AgentContext(many.get(0), null, many, "Agent");
        String out = ctx.toPromptFragment();
        assertThat(out).contains("…+8 more");          // 20 - 12 cap
        assertThat(out).doesNotContain("file19.dita");  // tail not listed inline
    }
}
