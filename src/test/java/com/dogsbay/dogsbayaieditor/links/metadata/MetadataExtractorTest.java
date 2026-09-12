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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** M1: metadata model + extractor (topic prolog + map topicmeta). */
class MetadataExtractorTest {

    @TempDir Path dir;

    private java.io.File write(String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, content);
        return p.toFile();
    }

    @Test
    void extractsEveryFieldFromARichTopicProlog() throws Exception {
        java.io.File f = write("c.dita", """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE concept PUBLIC "-//OASIS//DTD DITA Concept//EN" "concept.dtd">
            <concept id="c">
              <title>C</title>
              <prolog>
                <author>Jane Doe</author>
                <copyright><copyrholder>ACME</copyrholder></copyright>
                <critdates><created date="2025-01-01"/><revised modified="2025-06-01"/></critdates>
                <permissions view="all"/>
                <metadata>
                  <audience type="administrator"/>
                  <category>Networking</category>
                  <keywords><keyword>setup</keyword><keyword>install</keyword></keywords>
                  <prodinfo><prodname>Audacity</prodname></prodinfo>
                  <othermeta name="reviewer" content="Sam"/>
                </metadata>
                <data name="sku" value="A-100"/>
              </prolog>
              <conbody/>
            </concept>
            """);

        MetadataSnapshot s = MetadataExtractor.extract(f);

        assertThat(s.container()).isEqualTo("prolog");
        assertThat(s.isEmpty()).isFalse();
        assertThat(s.get(MetadataField.AUTHOR)).containsExactly("Jane Doe");
        assertThat(s.get(MetadataField.COPYRHOLDER)).containsExactly("ACME");
        assertThat(s.get(MetadataField.CREATED)).containsExactly("2025-01-01");
        assertThat(s.get(MetadataField.REVISED)).containsExactly("2025-06-01");
        assertThat(s.get(MetadataField.PERMISSIONS)).containsExactly("all");
        assertThat(s.get(MetadataField.AUDIENCE)).containsExactly("administrator");
        assertThat(s.get(MetadataField.CATEGORY)).containsExactly("Networking");
        assertThat(s.get(MetadataField.KEYWORD)).containsExactly("setup", "install"); // order kept
        assertThat(s.get(MetadataField.PRODNAME)).containsExactly("Audacity");
        assertThat(s.get(MetadataField.OTHERMETA)).containsExactly("reviewer=Sam");
        assertThat(s.get(MetadataField.DATA)).containsExactly("sku=A-100");
    }

    @Test
    void emptyOrAbsentPrologYieldsEmptySnapshot() throws Exception {
        java.io.File noProlog = write("np.dita", """
            <?xml version="1.0" encoding="UTF-8"?>
            <concept id="c"><title>C</title><conbody/></concept>
            """);
        assertThat(MetadataExtractor.extract(noProlog).isEmpty()).isTrue();
        assertThat(MetadataExtractor.extract(noProlog).container()).isEqualTo("none");

        java.io.File emptyProlog = write("ep.dita", """
            <?xml version="1.0" encoding="UTF-8"?>
            <concept id="c"><title>C</title><prolog/><conbody/></concept>
            """);
        MetadataSnapshot s = MetadataExtractor.extract(emptyProlog);
        assertThat(s.container()).isEqualTo("prolog");
        assertThat(s.isEmpty()).isTrue();
    }

    @Test
    void extractsFromMapTopicmeta() throws Exception {
        java.io.File m = write("m.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE map PUBLIC "-//OASIS//DTD DITA Map//EN" "map.dtd">
            <map>
              <title>M</title>
              <topicmeta>
                <author>Team Docs</author>
                <audience type="beginner"/>
                <keywords><keyword>guide</keyword></keywords>
              </topicmeta>
              <topicref href="c.dita"/>
            </map>
            """);

        MetadataSnapshot s = MetadataExtractor.extract(m);
        assertThat(s.container()).isEqualTo("topicmeta");
        assertThat(s.get(MetadataField.AUTHOR)).containsExactly("Team Docs");
        assertThat(s.get(MetadataField.AUDIENCE)).containsExactly("beginner");
        assertThat(s.get(MetadataField.KEYWORD)).containsExactly("guide");
    }

    @Test
    void extractionIsStableAcrossRepeatedReads() throws Exception {
        java.io.File f = write("c.dita", """
            <?xml version="1.0" encoding="UTF-8"?>
            <concept id="c"><title>C</title><prolog>
              <author>A</author><metadata><category>X</category></metadata>
            </prolog><conbody/></concept>
            """);
        assertThat(MetadataExtractor.extract(f)).isEqualTo(MetadataExtractor.extract(f));
    }

    @Test
    void extractsFluidTopicsFieldsAndMultiAttributeElements() throws Exception {
        java.io.File f = write("ft.dita", """
            <?xml version="1.0" encoding="UTF-8"?>
            <concept id="c"><title>C</title>
              <prolog>
                <source>Manual</source>
                <publisher>ACME</publisher>
                <critdates><created date="2025-01-01" golive="2025-02-01"/>
                  <revised modified="2025-06-01" expiry="2026-01-01"/></critdates>
                <metadata>
                  <audience type="administrator" job="installing" experiencelevel="expert"/>
                  <keywords><keyword>setup</keyword><indexterm>Install</indexterm></keywords>
                  <prodinfo><prodname>Widget</prodname>
                    <vrmlist><vrm version="3.0" release="1"/></vrmlist>
                    <brand>Acme</brand><platform>linux</platform></prodinfo>
                </metadata>
                <resourceid appid="W-100"/>
              </prolog><conbody/></concept>
            """);
        MetadataSnapshot s = MetadataExtractor.extract(f);
        assertThat(s.get(MetadataField.SOURCE)).containsExactly("Manual");
        assertThat(s.get(MetadataField.PUBLISHER)).containsExactly("ACME");
        assertThat(s.get(MetadataField.CREATED)).containsExactly("2025-01-01");
        assertThat(s.get(MetadataField.CREATED_GOLIVE)).containsExactly("2025-02-01");
        assertThat(s.get(MetadataField.REVISED_EXPIRY)).containsExactly("2026-01-01");
        // one <audience> element → three distinct fields
        assertThat(s.get(MetadataField.AUDIENCE)).containsExactly("administrator");
        assertThat(s.get(MetadataField.AUDIENCE_JOB)).containsExactly("installing");
        assertThat(s.get(MetadataField.AUDIENCE_EXPERIENCELEVEL)).containsExactly("expert");
        assertThat(s.get(MetadataField.INDEXTERM)).containsExactly("Install");
        assertThat(s.get(MetadataField.VRM_VERSION)).containsExactly("3.0");
        assertThat(s.get(MetadataField.VRM_RELEASE)).containsExactly("1");
        assertThat(s.get(MetadataField.BRAND)).containsExactly("Acme");
        assertThat(s.get(MetadataField.PLATFORM)).containsExactly("linux");
        assertThat(s.get(MetadataField.RESOURCEID)).containsExactly("W-100");
    }

    @Test
    void byKeyMapsKnownFieldsOnly() {
        assertThat(MetadataField.byKey("author")).isEqualTo(MetadataField.AUTHOR);
        assertThat(MetadataField.byKey("keyword")).isEqualTo(MetadataField.KEYWORD);
        assertThat(MetadataField.byKey("audience-job")).isEqualTo(MetadataField.AUDIENCE_JOB);
        assertThat(MetadataField.byKey("keywords")).isNull(); // container, not a field
        assertThat(MetadataField.byKey("conbody")).isNull();
        // several fields share the <audience> element, distinguished by attribute
        assertThat(MetadataField.fieldsForElement("audience"))
            .contains(MetadataField.AUDIENCE, MetadataField.AUDIENCE_JOB,
                    MetadataField.AUDIENCE_EXPERIENCELEVEL);
    }
}
