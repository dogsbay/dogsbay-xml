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

package com.dogsbay.dogsbayaieditor.links.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.links.metadata.MetadataRule.Presence;
import com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig;

/** M2: policy model + storage (config.xml block, standalone file, selectors). */
class MetadataPolicyTest {

    @TempDir Path dir;

    private MetadataPolicy samplePolicy() {
        return new MetadataPolicy(List.of(
                new MetadataRule("task", MetadataField.CREATED, Presence.REQUIRED,
                        List.of(), "\\d{4}-\\d\\d-\\d\\d"),
                new MetadataRule("concept", MetadataField.AUDIENCE, Presence.REQUIRED,
                        List.of("administrator", "user"), null),
                new MetadataRule(null, MetadataField.KEYWORD, Presence.RECOMMENDED,
                        List.of(), null),
                new MetadataRule("reference", MetadataField.AUTHOR, Presence.FORBIDDEN,
                        List.of(), null)));
    }

    @Test
    void roundTripsThroughItsElement() {
        MetadataPolicy reparsed = MetadataPolicy.parse(samplePolicy().toElement());
        assertThat(reparsed.rules()).hasSize(4);
        MetadataRule created = reparsed.rules().get(0);
        assertThat(created.topicType()).isEqualTo("task");
        assertThat(created.field()).isEqualTo(MetadataField.CREATED);
        assertThat(created.presence()).isEqualTo(Presence.REQUIRED);
        assertThat(created.pattern()).isEqualTo("\\d{4}-\\d\\d-\\d\\d");
        assertThat(reparsed.rules().get(1).allowedValues()).containsExactly("administrator", "user");
        assertThat(reparsed.rules().get(3).presence()).isEqualTo(Presence.FORBIDDEN);
    }

    @Test
    void selectorMatchesByTopicType() {
        MetadataPolicy p = samplePolicy();
        // task rules = the task-required-created + the any-topic keyword
        assertThat(p.rulesFor("task")).extracting(MetadataRule::field)
            .containsExactlyInAnyOrder(MetadataField.CREATED, MetadataField.KEYWORD);
        assertThat(p.rulesFor("concept")).extracting(MetadataRule::field)
            .containsExactlyInAnyOrder(MetadataField.AUDIENCE, MetadataField.KEYWORD);
        // an untyped rule applies to everything
        assertThat(p.rulesFor("glossentry")).extracting(MetadataRule::field)
            .containsExactly(MetadataField.KEYWORD);
    }

    @Test
    void roundTripsThroughDogsbayConfig() throws Exception {
        DogsbayProjectConfig c = new DogsbayProjectConfig();
        c.setProjectType("DITA");
        c.setMetadataPolicy(samplePolicy());
        c.saveShared(dir);

        DogsbayProjectConfig reloaded = DogsbayProjectConfig.load(dir);
        assertThat(reloaded.getMetadataPolicy().rules()).hasSize(4);
        assertThat(reloaded.getMetadataPolicy().rulesFor("reference"))
            .anyMatch(r -> r.field() == MetadataField.AUTHOR
                    && r.presence() == Presence.FORBIDDEN);
        // no machine-specific paths leaked into the shared file
        String configXml = Files.readString(dir.resolve(".dogsbay/config.xml"));
        assertThat(configXml).contains("<metadata-policy>").doesNotContain(dir.toString());
    }

    @Test
    void emptyPolicyIsNotWritten() throws Exception {
        DogsbayProjectConfig c = new DogsbayProjectConfig();
        c.setProjectType("DITA");
        c.saveShared(dir);
        assertThat(Files.readString(dir.resolve(".dogsbay/config.xml")))
            .doesNotContain("metadata-policy");
        assertThat(DogsbayProjectConfig.load(dir).getMetadataPolicy().isEmpty()).isTrue();
    }

    @Test
    void parsesStandalonePolicyFileAndSkipsUnknownFields() throws Exception {
        Path f = dir.resolve("policy.xml");
        Files.writeString(f, """
            <metadata-policy>
              <rule topic-type="task" field="created" presence="required"/>
              <rule field="bogusfield" presence="required"/>
              <rule field="keyword" presence="recommended"/>
            </metadata-policy>
            """);
        MetadataPolicy p = MetadataPolicy.fromFile(f.toFile());
        assertThat(p.rules()).extracting(MetadataRule::field)
            .containsExactly(MetadataField.CREATED, MetadataField.KEYWORD); // bogus skipped
    }

    @Test
    void malformedOrMissingYieldsEmpty() throws Exception {
        assertThat(MetadataPolicy.fromFile(dir.resolve("nope.xml").toFile()).isEmpty()).isTrue();
        Path bad = dir.resolve("bad.xml");
        Files.writeString(bad, "<not-a-policy/>");
        assertThat(MetadataPolicy.fromFile(bad.toFile()).isEmpty()).isTrue();
        assertThat(MetadataPolicy.parse(null).isEmpty()).isTrue();
    }
}
