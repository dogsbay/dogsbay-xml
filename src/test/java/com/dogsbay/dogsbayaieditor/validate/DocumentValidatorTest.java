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

package com.dogsbay.dogsbayaieditor.validate;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.ValidationResult;

class DocumentValidatorTest {

    private Path write(Path dir, String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, content);
        return p;
    }

    @Test
    void wellFormedDocumentIsValid(@TempDir Path dir) throws Exception {
        Path f = write(dir, "ok.xml", "<root><a/></root>");
        ValidationResult r = DocumentValidator.validate(f, null, List.of());
        assertThat(r.valid()).isTrue();
        assertThat(r.errors()).isEmpty();
    }

    @Test
    void malformedDocumentIsInvalid(@TempDir Path dir) throws Exception {
        Path f = write(dir, "bad.xml", "<root><a></root>");
        ValidationResult r = DocumentValidator.validate(f, null, List.of());
        assertThat(r.valid()).isFalse();
        assertThat(r.errors()).isNotEmpty();
    }

    @Test
    void validatesAgainstXsd(@TempDir Path dir) throws Exception {
        write(dir, "note.xsd", """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="note">
                <xs:complexType><xs:sequence>
                  <xs:element name="to" type="xs:string"/>
                  <xs:element name="from" type="xs:string"/>
                </xs:sequence></xs:complexType>
              </xs:element>
            </xs:schema>
            """);
        Path xsd = dir.resolve("note.xsd");

        Path good = write(dir, "good.xml", "<note><to>a</to><from>b</from></note>");
        assertThat(DocumentValidator.validate(good, xsd, List.of()).valid()).isTrue();

        Path bad = write(dir, "bad.xml", "<note><to>a</to></note>");
        ValidationResult r = DocumentValidator.validate(bad, xsd, List.of());
        assertThat(r.valid()).isFalse();
        assertThat(r.errors()).isNotEmpty();
    }

    @Test
    void validatesAgainstDtdViaDoctype(@TempDir Path dir) throws Exception {
        write(dir, "note.dtd", """
            <!ELEMENT note (to, from)>
            <!ELEMENT to (#PCDATA)>
            <!ELEMENT from (#PCDATA)>
            """);
        Path dtd = dir.resolve("note.dtd");

        Path good = write(dir, "good.xml", """
            <?xml version="1.0"?>
            <!DOCTYPE note SYSTEM "note.dtd">
            <note><to>a</to><from>b</from></note>
            """);
        assertThat(DocumentValidator.validate(good, dtd, List.of()).valid()).isTrue();

        Path bad = write(dir, "bad.xml", """
            <?xml version="1.0"?>
            <!DOCTYPE note SYSTEM "note.dtd">
            <note><to>a</to></note>
            """);
        assertThat(DocumentValidator.validate(bad, dtd, List.of()).valid()).isFalse();
    }

    @Test
    void externalDtdParameterEntityModuleStillResolves(@TempDir Path dir) throws Exception {
        // A DTD that pulls in an entity module via an external PARAMETER entity —
        // the mechanism DITA/XHTML DTDs use to declare content entities. XXE hardening
        // must not break this legitimate resolution.
        write(dir, "mod.ent", "<!ENTITY trade \"(TM)\">");
        write(dir, "main.dtd", """
            <!ELEMENT doc (#PCDATA)>
            <!ENTITY %% mod SYSTEM "mod.ent">
            %%mod;
            """.replace("%%", "%"));

        Path doc = write(dir, "doc.xml", """
            <?xml version="1.0"?>
            <!DOCTYPE doc SYSTEM "main.dtd">
            <doc>&trade;</doc>
            """);

        // No catalog: the external DTD loads, its parameter-entity module resolves,
        // and the &trade; general entity it declares is defined → well-formed/valid.
        ValidationResult r = DocumentValidator.validate(doc, null, List.of());
        assertThat(r.valid())
                .as("external DTD + parameter-entity module must still resolve: " + r.errors())
                .isTrue();
    }

    @Test
    void externalGeneralEntityIsNotExpanded(@TempDir Path dir) throws Exception {
        // A sentinel "secret" file the malicious entity tries to read.
        Path secret = write(dir, "secret.txt", "TOP-SECRET-XXE-SENTINEL");

        // Document declaring an external general entity that points at the sentinel.
        Path evil = write(dir, "evil.xml", """
            <?xml version="1.0"?>
            <!DOCTYPE root [
              <!ENTITY xxe SYSTEM "%s">
            ]>
            <root>&xxe;</root>
            """.formatted(secret.toUri().toString()));

        ValidationResult r = DocumentValidator.validate(evil, null, List.of());

        // The sentinel must never appear in any error/message — the entity is blocked,
        // not expanded to the file's contents.
        String joined = r.errors().toString();
        assertThat(joined).doesNotContain("TOP-SECRET-XXE-SENTINEL");
    }

    @Test
    void recognisesDitaFiles() {
        assertThat(DocumentValidator.isDitaFile(Path.of("topics/install.dita"))).isTrue();
        assertThat(DocumentValidator.isDitaFile(Path.of("guide.ditamap"))).isTrue();
        assertThat(DocumentValidator.isDitaFile(Path.of("notes.xml"))).isFalse();
    }
}
