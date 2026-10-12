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

package com.dogsbay.dogsbayaieditor.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The audits other than project_health take the project's default root map and house rules too. */
class AuditDefaultsTest {

    @TempDir Path root;
    private final HeadlessExecutor executor = new HeadlessExecutor();

    private Path write(String name, String content) throws Exception {
        Path p = root.resolve(name);
        Files.createDirectories(p.getParent());
        return Files.writeString(p, content);
    }

    @BeforeEach
    void project() throws Exception {
        root = root.toRealPath();
        write(".dogsbay/config.xml", """
                <dogsbay-project>
                  <default-root-map>guide.ditamap</default-root-map>
                  <default-schematron>house.sch</default-schematron>
                </dogsbay-project>
                """);
        write("house.sch", """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron">
                  <pattern><rule context="topic"><assert test="shortdesc">Every topic needs a shortdesc.</assert></rule></pattern>
                </schema>
                """);
        write("guide.ditamap", "<map><keydef keys=\"product\" href=\"a.dita\"/><topicref href=\"a.dita\"/></map>");
        write("a.dita", "<topic id=\"a\"><title>A</title><body><p><keyword keyref=\"prodcut\"/>"
                + "<ph conkeyref=\"product/nothere\"/></p></body></topic>");
    }

    @Test
    void checkLinksResolvesKeysWithTheProjectsMapUnlessToldNone() throws Exception {
        assertThat(executor.execute(new CheckLinksCommand(root.toString(), null))).extracting("value")
                .contains("prodcut");
        assertThat(executor.execute(new CheckLinksCommand(root.toString(), "none"))).extracting("value")
                .doesNotContain("prodcut");
        assertThat(executor.execute(new HealthCommand(root.toString(), null)).undefinedKeys()).extracting("value")
                .contains("prodcut");
    }

    @Test
    void aMissingDefaultMapIsReported() throws Exception {
        Files.delete(root.resolve("guide.ditamap"));
        assertThat(executor.execute(new CheckLinksCommand(root.toString(), null))).first()
                .satisfies(r -> assertThat(r.attribute()).isEqualTo("default-root-map"));
    }

    @Test
    void schematronProjectRunsTheProjectsHouseRules() throws Exception {
        var r = executor.execute(new SchematronProjectCommand(root.toString(), null, null));
        assertThat(r.findings()).extracting("message").contains("Every topic needs a shortdesc.");

        Files.delete(root.resolve("house.sch"));
        assertThatThrownBy(() -> executor.execute(new SchematronProjectCommand(root.toString(), null, null)))
                .hasMessageContaining("do not exist");
        Files.delete(root.resolve(".dogsbay/config.xml"));
        assertThatThrownBy(() -> executor.execute(new SchematronProjectCommand(root.toString(), null, null)))
                .hasMessageContaining("No house rules");
    }

    @Test
    void conrefAuditAndWhereUsedUseTheProjectsMapForKeys() throws Exception {
        assertThat(executor.execute(new ConrefAuditCommand(root.toString(), null))).extracting("value")
                .anyMatch(v -> v.toString().contains("nothere"));
        assertThat(executor.execute(new WhereUsedCommand(root.resolve("a.dita").toString(), root.toString(), null)))
                .isNotNull();
    }
}
