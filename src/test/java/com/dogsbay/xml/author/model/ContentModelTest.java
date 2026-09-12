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

import static com.dogsbay.xml.author.model.ContentModel.many;
import static com.dogsbay.xml.author.model.ContentModel.one;
import static com.dogsbay.xml.author.model.ContentModel.optional;
import static com.dogsbay.xml.author.model.ContentModel.some;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ContentModelTest {

    private final ContentModel topic = ContentModel.of(one("title"), optional("shortdesc", "abstract"),
            optional("prolog"), optional("conbody"), optional("related-links"));

    @Test
    void aDocumentAlreadyOutsideTheModelStillTakesEdits() {
        var adapter = new com.dogsbay.xml.author.adapter.DitaBlockAdapter();
        // two titles: outside the concept model, as an imported file may be
        AuthorDocument doc = adapter.importDocument(com.dogsbay.xml.author.adapter.AdapterTestSupport.parse(
                "<concept id='c'><title>A</title><title>B</title><conbody><p>x</p></conbody></concept>"));
        AuthorBlock concept = doc.getRoot();
        assertThat(doc.getRegistry().insertableChildren(concept)).extracting(BlockType::getName).contains("shortdesc");
        var tx = doc.begin("t");
        tx.insertBlock("shortdesc", concept, 2);   // would be refused by the strict sequence check
        tx.commit();
        assertThat(concept.getChildren()).extracting(b -> b.getType().getName())
                .containsExactly("title", "title", "shortdesc", "conbody");
    }

    @Test
    void aStepTakesANoteBeforeItsCommand() {
        var adapter = new com.dogsbay.xml.author.adapter.DitaBlockAdapter();
        AuthorDocument doc = adapter.importDocument(com.dogsbay.xml.author.adapter.AdapterTestSupport.parse(
                "<task id='t'><title>T</title><taskbody><steps><step><cmd>Do</cmd></step></steps></taskbody></task>"));
        AuthorBlock step = BlockOperations.flatten(doc.getRoot()).stream()
                .filter(b -> "step".equals(b.getType().getName())).findFirst().orElseThrow();
        assertThat(doc.getRegistry().insertIndex(step, "note", 0)).isEqualTo(0);
        assertThat(doc.getRegistry().insertIndex(step, "info", 0)).as("info sits after cmd").isEqualTo(1);
    }

    @Test
    void acceptsOrderAndUpperBoundsButNotMissingRequiredChildren() {
        assertThat(topic.accepts(List.of("title", "shortdesc", "conbody"))).isTrue();
        assertThat(topic.accepts(List.of("conbody"))).as("a document in progress may still lack its title").isTrue();
        assertThat(topic.accepts(List.of("title", "title"))).as("no second title").isFalse();
        assertThat(topic.accepts(List.of("title", "conbody", "shortdesc"))).as("order matters").isFalse();
        assertThat(topic.accepts(List.of("title", "shortdesc", "abstract"))).as("one of the two").isFalse();
    }

    @Test
    void insertIndexFindsTheNearestValidPosition() {
        List<String> children = List.of("title", "conbody");
        assertThat(topic.insertIndex(children, "shortdesc", 2)).as("asked for the end, goes between").isEqualTo(1);
        assertThat(topic.insertIndex(children, "related-links", 0)).isEqualTo(2);
        assertThat(topic.insertIndex(children, "title", 2)).isEqualTo(-1);
        assertThat(topic.canInsert(children, "prolog")).isTrue();
        assertThat(topic.canInsert(List.of("title", "abstract"), "shortdesc")).isFalse();
    }

    @Test
    void theRegistryRefusesASecondTitleAndStepsBeforePrerequisites() {
        AuthorDocument doc = new AuthorDocument(BlockTypeRegistry.ditaProfile());
        AuthorBlock task = doc.createBlock("task");
        doc.setRoot(task);
        Transaction setup = doc.begin("setup");
        setup.insertBlock("title", task, 0);
        AuthorBlock body = setup.insertBlock("taskbody", task, 1);
        AuthorBlock steps = setup.insertBlock("steps", body, 0);
        setup.commit();

        assertThat(doc.getRegistry().insertableChildren(task)).extracting(BlockType::getName)
                .doesNotContain("title").contains("shortdesc", "related-links");
        Transaction tx = doc.begin("bad");
        assertThatThrownBy(() -> tx.insertBlock("title", task, 2)).isInstanceOf(AuthorStructureException.class);

        // prereq asked for after the steps lands before them
        var result = BlockOperations.insertBlock(doc, "prereq", body, body.getChildren().size());
        assertThat(body.getChildren()).extracting(b -> b.getType().getName()).containsExactly("prereq", "steps");
        assertThat(result.focus().getType().getName()).isEqualTo("prereq");
        // a second steps container is not offered at all
        assertThat(doc.getRegistry().insertableChildren(body)).extracting(BlockType::getName).doesNotContain("steps");
        assertThatThrownBy(() -> BlockOperations.insertBlock(doc, "steps-unordered", body, 1))
                .isInstanceOf(AuthorStructureException.class);
        assertThat(steps.getParent()).isSameAs(body);
    }

    @Test
    void aGeneratedTableHasItsColumnCountAndAColumnDefinition() {
        AuthorDocument doc = new AuthorDocument(BlockTypeRegistry.ditaProfile());
        AuthorBlock concept = doc.createBlock("concept");
        doc.setRoot(concept);
        Transaction setup = doc.begin("setup");
        AuthorBlock body = setup.insertBlock("conbody", concept, 0);
        setup.commit();

        var result = BlockOperations.insertBlock(doc, "table", body, 0);
        AuthorBlock table = body.getChildren().get(0);
        AuthorBlock tgroup = table.getChildren().get(0);
        assertThat(tgroup.getAttributes()).containsEntry("cols", "1");
        assertThat(tgroup.getChildren()).extracting(b -> b.getType().getName()).containsExactly("colspec", "tbody");
        assertThat(tgroup.getChildren().get(0).getAttributes()).containsEntry("colname", "col1");
        assertThat(result.focus().getType().getName()).isEqualTo("entry");
    }

    @Test
    void movingKeepsTheOrderTheModelDemands() {
        AuthorDocument doc = new AuthorDocument(BlockTypeRegistry.ditaProfile());
        AuthorBlock task = doc.createBlock("task");
        doc.setRoot(task);
        Transaction setup = doc.begin("setup");
        AuthorBlock title = setup.insertBlock("title", task, 0);
        setup.insertBlock("taskbody", task, 1);
        setup.commit();
        Transaction tx = doc.begin("move");
        assertThatThrownBy(() -> tx.moveBlock(title, task, 2)).isInstanceOf(AuthorStructureException.class);
        assertThat(task.getChildren().get(0)).isSameAs(title);
    }
}
