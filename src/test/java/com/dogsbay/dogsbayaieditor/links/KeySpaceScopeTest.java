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

package com.dogsbay.dogsbayaieditor.links;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Phase 1: DITA 1.3 @keyscope resolution in KeySpace. */
class KeySpaceScopeTest {

    @TempDir Path dir;

    private File write(String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, content);
        return p.toFile();
    }

    private static String kd(String keys, String word) {
        return "<keydef keys=\"" + keys + "\"><topicmeta><keywords><keyword>"
                + word + "</keyword></keywords></topicmeta></keydef>";
    }

    @Test
    void scopedResolutionLocalShadowingAndQualifiedNames() throws Exception {
        File m = write("root.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map>
              <title>R</title>
              %s
              <topicgroup keyscope="prod">
                %s
                %s
              </topicgroup>
            </map>
            """.formatted(kd("k", "ROOT"), kd("k", "PROD"), kd("only", "ONLY")));
        KeySpace ks = KeySpace.fromRootMap(m);

        // root scope sees the root definition; the scoped key is qualified
        assertThat(ks.resolve("k").keywordText()).isEqualTo("ROOT");
        assertThat(ks.resolve("prod.k").keywordText()).isEqualTo("PROD");
        // from inside the scope, the local definition shadows the outer one
        assertThat(ks.resolve("k", "prod").keywordText()).isEqualTo("PROD");
        assertThat(ks.resolve("k", "").keywordText()).isEqualTo("ROOT");
        // a scope-only key is invisible at root, visible qualified or from the scope
        assertThat(ks.resolve("only")).isNull();
        assertThat(ks.resolve("prod.only").keywordText()).isEqualTo("ONLY");
        assertThat(ks.resolve("only", "prod").keywordText()).isEqualTo("ONLY");
        assertThat(ks.entries().keySet()).contains("k", "prod.k", "prod.only");
    }

    @Test
    void nestedScopesAndScopeAcrossAMapref() throws Exception {
        write("sub.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map>
              <title>S</title>
              %s
              <topicgroup keyscope="inner">
                %s
              </topicgroup>
            </map>
            """.formatted(kd("sk", "SK"), kd("ik", "IK")));
        File root = write("root2.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map>
              <title>R2</title>
              <mapref href="sub.ditamap" keyscope="s"/>
            </map>
            """);
        KeySpace ks = KeySpace.fromRootMap(root);

        // the mapref's @keyscope scopes the whole submap; nesting compounds
        assertThat(ks.resolve("s.sk").keywordText()).isEqualTo("SK");
        assertThat(ks.resolve("s.inner.ik").keywordText()).isEqualTo("IK");
        // bare names aren't visible at root; resolve from the scope context works
        assertThat(ks.resolve("sk")).isNull();
        assertThat(ks.resolve("ik", "s.inner").keywordText()).isEqualTo("IK");
        // outward walk: a key referenced from a deeper scope falls back outward
        assertThat(ks.resolve("sk", "s.inner").keywordText()).isEqualTo("SK");
    }

    @Test
    void noScopeMapIsUnchangedFlatNamespace() throws Exception {
        File m = write("flat.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>F</title>
              %s
              %s
            </map>
            """.formatted(kd("a", "A"), kd("b", "B")));
        KeySpace ks = KeySpace.fromRootMap(m);
        assertThat(ks.resolve("a").keywordText()).isEqualTo("A");
        assertThat(ks.resolve("b").keywordText()).isEqualTo("B");
        assertThat(ks.entries().keySet()).containsExactlyInAnyOrder("a", "b");
        assertThat(KeySpace.scopeOf("a")).isEmpty();
        assertThat(KeySpace.scopeOf("prod.k")).isEqualTo("prod");
    }
}
