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
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 1 consumer wiring: the key consumers (lenient resolution, the reverse
 * index, the closure crawl) honour DITA 1.3 {@code @keyscope}. A bare key that
 * is unique to one scope resolves for callers that know the name but not its
 * exact authoring scope (cross-file map placement isn't modeled in-process).
 */
class KeyScopeConsumersTest {

    @TempDir Path dir;

    private File write(String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, content);
        return p.toFile();
    }

    private static String kd(String keys, String href) {
        return "<keydef keys=\"" + keys + "\" href=\"" + href + "\"/>";
    }

    // ── KeySpace.resolveLenient ──────────────────────────────────────────────

    @Test
    void lenientResolvesUniqueScopedKeyFromRoot() throws Exception {
        File m = write("root.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>R</title>
              <topicgroup keyscope="prod">
                %s
              </topicgroup>
            </map>
            """.formatted(kd("only", "reused.dita")));
        write("reused.dita", "<topic id=\"reused\"><title>X</title></topic>");
        KeySpace ks = KeySpace.fromRootMap(m);

        // exact root lookup misses (the key lives in scope "prod")…
        assertThat(ks.resolve("only")).isNull();
        // …but the lenient fallback finds the unique scoped definition.
        assertThat(ks.resolveLenient("only", "").href()).isEqualTo("reused.dita");
        assertThat(ks.resolveHrefFileLenient("only", "").getName()).isEqualTo("reused.dita");
    }

    @Test
    void lenientFallbackIsGatedToRootContextNotSiblingScopes() throws Exception {
        // "only" is unique to scope alpha. A reference genuinely sitting in sibling
        // scope beta must NOT cross-resolve to alpha's definition — sibling scopes
        // are isolated; only a root (empty) context gets the unique-scope fallback.
        File m = write("root.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>R</title>
              <topicgroup keyscope="alpha">%s</topicgroup>
              <topicgroup keyscope="beta"><title>B</title></topicgroup>
            </map>
            """.formatted(kd("only", "a.dita")));
        KeySpace ks = KeySpace.fromRootMap(m);

        assertThat(ks.resolveLenient("only", "")).isNotNull();       // root context: fallback fires
        assertThat(ks.resolveLenient("only", "beta")).isNull();      // sibling scope: no cross-resolve
        assertThat(ks.resolveLenient("only", "alpha").href()).isEqualTo("a.dita"); // own scope: exact
    }

    @Test
    void entriesWithBareAliasesAddsUniqueScopedKeysOnly() throws Exception {
        File m = write("root.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>R</title>
              %s
              <topicgroup keyscope="s">%s</topicgroup>
              <topicgroup keyscope="x">%s</topicgroup>
              <topicgroup keyscope="y">%s</topicgroup>
            </map>
            """.formatted(kd("root-k", "r.dita"), kd("only", "o.dita"),
                          kd("dup", "x.dita"), kd("dup", "y.dita")));
        KeySpace ks = KeySpace.fromRootMap(m);
        var aliased = ks.entriesWithBareAliases();

        // qualified names always present; a unique scope-only key gets a bare alias…
        assertThat(aliased).containsKeys("root-k", "s.only", "x.dup", "y.dup", "only");
        // …but an ambiguous bare name (defined in two scopes) does not.
        assertThat(aliased).doesNotContainKey("dup");
        assertThat(aliased.get("only").href()).isEqualTo("o.dita");
    }

    @Test
    void resolveQualifiedNameReportsTheScopeActuallyFound() throws Exception {
        // "k" is a root key; resolving it from scope "s" walks outward and lands at
        // root, so the qualified name is bare "k" (scope ""), not "s.k".
        File m = write("root.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>R</title>
              %s
              <topicgroup keyscope="s"><title>S</title></topicgroup>
            </map>
            """.formatted(kd("k", "k.dita")));
        KeySpace ks = KeySpace.fromRootMap(m);

        assertThat(ks.resolveQualifiedName("k", "s")).isEqualTo("k");
        assertThat(KeySpace.scopeOf(ks.resolveQualifiedName("k", "s"))).isEmpty();
        assertThat(ks.resolveQualifiedName("missing", "s")).isNull();
    }

    @Test
    void usedKeyDefinitionsResolvesBareKeyrefsToScopedKeys() throws Exception {
        File reused = write("reused.dita", "<topic id=\"reused\"><title>X</title></topic>");
        write("start.dita", """
            <topic id="start"><title>S</title>
              <body><p><xref keyref="only">x</xref></p></body>
            </topic>
            """);
        File root = write("root.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>R</title>
              <topicref href="start.dita"/>
              <topicgroup keyscope="ext">%s</topicgroup>
            </map>
            """.formatted(kd("only", "reused.dita")));
        KeySpace ks = KeySpace.fromRootMap(root);
        ReverseLinkIndex index = ReverseLinkIndex.build(dir.toFile(), null);

        Set<KeyDefinition> used = index.usedKeyDefinitions(ks);
        // the scoped key, used via a bare keyref, counts as used (not orphaned).
        assertThat(used).contains(ks.resolve("ext.only"));
        assertThat(reused).exists();
    }

    @Test
    void lenientDoesNotGuessAmbiguousOrQualifiedNames() throws Exception {
        File m = write("root.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>R</title>
              <topicgroup keyscope="a">%s</topicgroup>
              <topicgroup keyscope="b">%s</topicgroup>
            </map>
            """.formatted(kd("dup", "a.dita"), kd("dup", "b.dita")));
        KeySpace ks = KeySpace.fromRootMap(m);

        // defined in two scopes → ambiguous → no guess
        assertThat(ks.resolveLenient("dup", "")).isNull();
        // an already-qualified name gets no fallback beyond the exact lookup
        assertThat(ks.resolveLenient("a.dup", "").href()).isEqualTo("a.dita");
        assertThat(ks.resolveLenient("c.dup", "")).isNull();
    }

    @Test
    void lenientIsIdenticalToExactForFlatRootKeys() throws Exception {
        File m = write("flat.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>F</title>%s</map>
            """.formatted(kd("k", "t.dita")));
        KeySpace ks = KeySpace.fromRootMap(m);
        assertThat(ks.resolveLenient("k", "")).isSameAs(ks.resolve("k"));
    }

    // ── Reference.scope stamping ─────────────────────────────────────────────

    @Test
    void linkExtractorStampsAuthoringScopeOnKeyrefs() throws Exception {
        File m = write("m.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>M</title>
              <topicref keyref="outer"/>
              <topicgroup keyscope="prod">
                <topicref keyref="inner"/>
              </topicgroup>
            </map>
            """);
        List<Reference> refs = LinkExtractor.extract(m).references();
        Reference outer = refs.stream().filter(r -> "outer".equals(r.keyName())).findFirst().orElseThrow();
        Reference inner = refs.stream().filter(r -> "inner".equals(r.keyName())).findFirst().orElseThrow();
        assertThat(outer.scope()).isEmpty();
        assertThat(inner.scope()).isEqualTo("prod");
    }

    // ── ReverseLinkIndex.usagesOfIncludingKeys ───────────────────────────────

    @Test
    void whereUsedFindsBareKeyrefBoundToAScopedKey() throws Exception {
        File reused = write("reused.dita", "<topic id=\"reused\"><title>X</title></topic>");
        write("start.dita", """
            <topic id="start"><title>S</title>
              <body><p><xref keyref="reused">x</xref></p></body>
            </topic>
            """);
        File root = write("root.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>R</title>
              <topicref href="start.dita"/>
              <topicgroup keyscope="prod">
                %s
              </topicgroup>
            </map>
            """.formatted(kd("reused", "reused.dita")));

        KeySpace ks = KeySpace.fromRootMap(root);
        ReverseLinkIndex index = ReverseLinkIndex.build(dir.toFile(), null);

        List<Reference> usages = index.usagesOfIncludingKeys(reused, ks);
        // the bare keyref in start.dita binds (cross-file) to the scoped key and
        // is surfaced as an indirect usage of the target file.
        assertThat(usages)
                .anySatisfy(r -> {
                    assertThat(r.attribute()).isEqualTo("keyref");
                    assertThat(r.source().getName()).isEqualTo("start.dita");
                });
    }
}
