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

package com.dogsbay.xml.author.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class BlockTypeRegistryTest {

    private final BlockTypeRegistry registry = BlockTypeRegistry.ditaProfile();

    private boolean valid(String parent, String child) {
        return registry.isValidChild(registry.require(parent), registry.require(child));
    }

    @Test
    void ditaProfileContainsCoreTopicTypes() {
        for (String name : new String[] {
                "concept", "task", "reference", "topic",
                "title", "shortdesc", "conbody", "taskbody", "refbody", "body",
                "p", "section", "note", "ul", "ol", "li", "codeblock", "fig", "image",
                "prereq", "context", "steps", "step", "cmd", "info", "stepresult",
                "substeps", "substep", "choices", "choice", "result", "postreq", "example",
                "simpletable", "sthead", "strow", "stentry",
                "properties", "property", "proptype", "propvalue", "propdesc" }) {
            assertThat(registry.get(name)).as("type '%s'", name).isNotNull();
        }
    }

    @Test
    void taskNestingRules() {
        assertThat(valid("taskbody", "steps")).isTrue();
        assertThat(valid("steps", "step")).isTrue();
        assertThat(valid("step", "cmd")).isTrue();
        assertThat(valid("step", "substeps")).isTrue();
        assertThat(valid("substeps", "substep")).isTrue();

        // steps belong only to task bodies, not concept bodies
        assertThat(valid("conbody", "steps")).isFalse();
        // step cannot appear outside steps
        assertThat(valid("taskbody", "step")).isFalse();
        assertThat(valid("conbody", "step")).isFalse();
        // p is not a direct child of steps
        assertThat(valid("steps", "p")).isFalse();
    }

    @Test
    void listAndTableNestingRules() {
        assertThat(valid("ul", "li")).isTrue();
        assertThat(valid("ol", "li")).isTrue();
        assertThat(valid("li", "ul")).isTrue(); // nested lists
        assertThat(valid("ul", "p")).isFalse();
        assertThat(valid("conbody", "li")).isFalse();

        assertThat(valid("simpletable", "strow")).isTrue();
        assertThat(valid("simpletable", "sthead")).isTrue();
        assertThat(valid("strow", "stentry")).isTrue();
        assertThat(valid("simpletable", "stentry")).isFalse();
        assertThat(valid("strow", "p")).isFalse();
    }

    @Test
    void rootTypesRequireMatchingBody() {
        assertThat(valid("concept", "conbody")).isTrue();
        assertThat(valid("concept", "taskbody")).isFalse();
        assertThat(valid("task", "taskbody")).isTrue();
        assertThat(valid("task", "conbody")).isFalse();
        assertThat(valid("reference", "refbody")).isTrue();
    }

    @Test
    void rawBlocksAreAllowedInAnyContainerButNotInVoids() {
        BlockType raw = registry.raw();
        assertThat(registry.isValidChild(registry.require("conbody"), raw)).isTrue();
        assertThat(registry.isValidChild(registry.require("step"), raw)).isTrue();
        assertThat(registry.isValidChild(registry.require("p"), raw)).isTrue();
        assertThat(registry.isValidChild(registry.require("image"), raw)).isFalse();
        // nothing nests inside a raw block
        assertThat(registry.isValidChild(raw, registry.require("p"))).isFalse();
    }

    @Test
    void allowedChildrenPreservesDeclarationOrder() {
        List<BlockType> children = registry.allowedChildren(registry.require("step"));
        assertThat(children).extracting(BlockType::getName)
                .containsExactly("note", "cmd", "info", "substeps", "tutorialinfo", "stepxmp", "choicetable",
                        "choices", "stepresult", "steptroubleshooting");   // DITA: notes may precede cmd
    }

    @Test
    void behaviourFlags() {
        assertThat(registry.require("p").isSplitOnEnter()).isTrue();
        assertThat(registry.require("li").isSplitOnEnter()).isTrue();
        assertThat(registry.require("choice").isSplitOnEnter()).isTrue();
        assertThat(registry.require("title").isSplitOnEnter()).isFalse();
        assertThat(registry.require("cmd").isSplitOnEnter()).isFalse();

        assertThat(registry.require("codeblock").isPreserveSpace()).isTrue();
        assertThat(registry.require("p").isPreserveSpace()).isFalse();

        assertThat(registry.require("stentry").isDeletable()).isFalse();
        assertThat(registry.require("p").isDeletable()).isTrue();

        assertThat(registry.require("image").getCategory()).isEqualTo(BlockType.Category.VOID);
        assertThat(registry.require("li").hasText()).isTrue();
        assertThat(registry.require("ul").hasText()).isFalse();
    }

    @Test
    void unknownTypeIsRejected() {
        assertThatThrownBy(() -> registry.require("no-such-element"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-such-element");
        assertThat(registry.get("no-such-element")).isNull();
    }
}
