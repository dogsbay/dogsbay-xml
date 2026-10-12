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

package com.dogsbay.dogsbayaieditor.whereused;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.links.AttributeReferenceLocator;
import com.dogsbay.dogsbayaieditor.links.KeySpace;
import com.dogsbay.dogsbayaieditor.whereused.KeyNavigationItems.Item;
import com.dogsbay.dogsbayaieditor.whereused.KeyNavigationItems.Kind;

/** Headless tests for the navigation menu-item logic. */
class KeyNavigationItemsTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("keynav-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    private static AttributeReferenceLocator.Located locate(String probe) {
        int offset = probe.indexOf('|');
        return AttributeReferenceLocator.locate(probe.replace("|", ""), offset);
    }

    private static Item byKind(List<Item> items, Kind kind) {
        return items.stream().filter(i -> i.kind() == kind).findFirst().orElse(null);
    }

    @Test
    void keyref_withDefinition_gotoEnabledWithFileAndLine() throws IOException {
        File topic = write("intro.dita", "<concept id=\"i\"/>");
        File map = write("root.ditamap",
                "<map>\n<keydef keys=\"intro\" href=\"intro.dita\"/>\n</map>");
        KeySpace ks = KeySpace.fromRootMap(map);

        List<Item> items = KeyNavigationItems.itemsFor(
                locate("<xref keyref=\"int|ro\"/>"), ks, null, map);

        Item gotoDef = byKind(items, Kind.GOTO_DEFINITION);
        assertNotNull(gotoDef);
        assertTrue(gotoDef.enabled());
        assertEquals(map, gotoDef.file());
        assertEquals(2, gotoDef.line(), "keydef is on line 2");
        assertEquals("intro", gotoDef.key(), "key carried for sidebar sync");

        Item openTarget = byKind(items, Kind.OPEN_TARGET);
        assertNotNull(openTarget);
        assertTrue(openTarget.enabled());
        assertEquals(topic.getCanonicalFile(), openTarget.file().getCanonicalFile());
        assertEquals("intro", openTarget.key(), "key carried for sidebar sync");

        Item findKey = byKind(items, Kind.FIND_KEY_USAGES);
        assertNotNull(findKey);
        assertEquals("intro", findKey.key());

        Item rename = byKind(items, Kind.RENAME_KEY);
        assertNotNull(rename, "rename always offered on a keyref");
        assertTrue(rename.enabled());
        assertEquals("intro", rename.key());
        assertTrue(rename.label().endsWith("…"), "ellipsis signals a prompt");

        Item inline = byKind(items, Kind.INLINE_KEY);
        assertNotNull(inline, "inline offered on a keyref");
        assertTrue(inline.enabled(), "enabled — key resolves in the context map");
        assertEquals("intro", inline.key());
    }

    @Test
    void keyref_noMapOrNoDefinition_inlineDisabled() throws IOException {
        List<Item> noMap = KeyNavigationItems.itemsFor(
                locate("<ph keyref=\"pro|d\"/>"), KeySpace.empty(), null, null);
        Item inline = byKind(noMap, Kind.INLINE_KEY);
        assertNotNull(inline);
        assertFalse(inline.enabled(), "no context map to resolve against");

        File map = write("root.ditamap", "<map/>");
        List<Item> noDef = KeyNavigationItems.itemsFor(
                locate("<ph keyref=\"pro|d\"/>"), KeySpace.fromRootMap(map), null, map);
        Item inline2 = byKind(noDef, Kind.INLINE_KEY);
        assertNotNull(inline2);
        assertFalse(inline2.enabled(), "key has no definition");
    }

    @Test
    void keyref_noRootMap_offersPickMapGoto() {
        List<Item> items = KeyNavigationItems.itemsFor(
                locate("<ph keyref=\"pro|d\"/>"), KeySpace.empty(), null, null);

        assertNull(byKind(items, Kind.GOTO_DEFINITION),
                "plain goto needs a map");
        Item pick = byKind(items, Kind.GOTO_DEFINITION_PICK_MAP);
        assertNotNull(pick, "pick-map variant offered instead");
        assertTrue(pick.enabled(), "enabled — it prompts for the map");
        assertTrue(pick.label().endsWith("…"), "ellipsis signals a prompt");
        assertEquals("prod", pick.key());
        assertNotNull(byKind(items, Kind.FIND_KEY_USAGES), "usages always offered");
    }

    @Test
    void keyref_unknownKey_gotoDisabledNamesMap() throws IOException {
        File map = write("root.ditamap", "<map><keydef keys=\"other\" href=\"o.dita\"/></map>");
        KeySpace ks = KeySpace.fromRootMap(map);

        List<Item> items = KeyNavigationItems.itemsFor(
                locate("<ph keyref=\"mis|sing\"/>"), ks, null, map);

        Item gotoDef = byKind(items, Kind.GOTO_DEFINITION);
        assertFalse(gotoDef.enabled());
        assertTrue(gotoDef.tooltip().contains("root.ditamap"));
    }

    @Test
    void conkeyref_usesKeyNameSegment() throws IOException {
        File map = write("root.ditamap",
                "<map><keydef keys=\"warehouse\" href=\"wh.dita\"/></map>");
        write("wh.dita", "<concept id=\"wh\"/>");
        KeySpace ks = KeySpace.fromRootMap(map);

        List<Item> items = KeyNavigationItems.itemsFor(
                locate("<p conkeyref=\"ware|house/legal\"/>"), ks, null, map);

        assertEquals("warehouse", byKind(items, Kind.FIND_KEY_USAGES).key());
        assertTrue(byKind(items, Kind.GOTO_DEFINITION).enabled());
    }

    @Test
    void href_existingTarget_openEnabled() throws IOException {
        File active = write("topics/a.dita", "<concept id=\"a\"/>");
        write("topics/b.dita", "<concept id=\"b\"/>");

        List<Item> items = KeyNavigationItems.itemsFor(
                locate("<xref href=\"b.di|ta#b/x\">x</xref>"),
                KeySpace.empty(), active, null);

        Item open = byKind(items, Kind.OPEN_TARGET);
        assertTrue(open.enabled());
        assertEquals("b.dita", open.file().getName());

        Item usages = byKind(items, Kind.FIND_FILE_USAGES);
        assertNotNull(usages);
        assertEquals("b.dita", usages.file().getName());

        Item keyify = byKind(items, Kind.KEYIFY);
        assertNotNull(keyify, "keyify offered on an href");
        assertTrue(keyify.enabled());
        assertEquals("b.dita", keyify.file().getName());
    }

    @Test
    void href_missingTarget_openDisabled() throws IOException {
        File active = write("a.dita", "<concept id=\"a\"/>");

        List<Item> items = KeyNavigationItems.itemsFor(
                locate("<xref href=\"gone.di|ta\">x</xref>"),
                KeySpace.empty(), active, null);

        Item open = byKind(items, Kind.OPEN_TARGET);
        assertFalse(open.enabled());
        assertTrue(open.tooltip().contains("gone.dita"));

        Item keyify = byKind(items, Kind.KEYIFY);
        assertNotNull(keyify);
        assertFalse(keyify.enabled(), "keyify needs an existing target");
    }

    @Test
    void fragmentOnlyConref_targetsActiveFile() throws IOException {
        File active = write("a.dita", "<concept id=\"a\"/>");

        List<Item> items = KeyNavigationItems.itemsFor(
                locate("<p conref=\"#a/le|gal\"/>"), KeySpace.empty(), active, null);

        Item open = byKind(items, Kind.OPEN_TARGET);
        assertTrue(open.enabled());
        assertEquals(active, open.file());
    }

    @Test
    void externalUrl_noItems() {
        List<Item> items = KeyNavigationItems.itemsFor(
                locate("<xref href=\"https://exam|ple.com/x\">x</xref>"),
                KeySpace.empty(), new File("/tmp/a.dita"), null);
        assertTrue(items.isEmpty());
    }

    @Test
    void nullLocated_empty() {
        assertTrue(KeyNavigationItems.itemsFor(null, KeySpace.empty(), null, null).isEmpty());
    }

    private File write(String relPath, String content) throws IOException {
        Path p = tempDir.resolve(relPath);
        if (p.getParent() != null) {
            Files.createDirectories(p.getParent());
        }
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
        return p.toFile();
    }
}
