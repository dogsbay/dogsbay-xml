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

import com.dogsbay.dogsbayaieditor.commands.results.HealthReport;
import com.dogsbay.dogsbayaieditor.commands.results.RefactorResult;
import com.dogsbay.dogsbayaieditor.links.ReferenceRewriter;
import com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex;

/** Tests for health, ReferenceRewriter, and rename-file. Headless. */
class RefactorCommandsTest {

    private final HeadlessExecutor executor = new HeadlessExecutor();
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("refactor-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    private void writeFixtureProject() throws IOException {
        write("root.ditamap",
                "<map><title>Guide</title>"
                + "<keydef keys=\"intro\" href=\"topics/intro.dita\"/>"
                + "<keydef keys=\"never-used\" href=\"topics/other.dita\"/>"
                + "<topicref href=\"topics/intro.dita\"/>"
                + "<topicref href=\"topics/other.dita\"/>"
                + "</map>");
        write("topics/intro.dita", "<concept id=\"intro\"><title>Intro</title>"
                + "<conbody><p>Hello</p></conbody></concept>");
        write("topics/other.dita", "<concept id=\"o\"><title>Other</title><conbody>"
                + "<p><xref href=\"intro.dita#intro\">see</xref></p>"
                + "<p><xref keyref=\"intro\">via key</xref></p>"
                + "<p conref=\"intro.dita#intro/x\"/>"
                + "</conbody></concept>");
        write("topics/orphan.dita", "<concept id=\"orphan\"><title>Lonely</title>"
                + "<conbody><p>Nobody references me.</p></conbody></concept>");
    }

    // -------------------------------------------------------------------------
    // health
    // -------------------------------------------------------------------------

    @Test
    void health_reportsAllFourCategories() throws Exception {
        writeFixtureProject();
        write("topics/sick.dita", "<concept id=\"s\"><title>S</title><conbody>"
                + "<p><xref href=\"missing.dita\">gone</xref></p>"
                + "<p><ph keyref=\"ghost-key\"/></p>"
                + "</conbody></concept>");

        HealthReport report = executor.execute(new HealthCommand(
                tempDir.toString(), path("root.ditamap")));

        assertFalse(report.isClean());
        assertEquals(1, report.brokenReferences().size(), "missing.dita");
        assertEquals(1, report.undefinedKeys().size(), "ghost-key");
        assertEquals(1, report.unusedKeys().size());
        assertEquals("never-used", report.unusedKeys().get(0).name());
        // orphan.dita and sick.dita have no inbound refs
        assertTrue(report.orphanTopics().stream().anyMatch(p -> p.endsWith("orphan.dita")));
        assertFalse(report.orphanTopics().stream().anyMatch(p -> p.endsWith("intro.dita")),
                "referenced topics are not orphans");
        assertFalse(report.orphanTopics().stream().anyMatch(p -> p.endsWith(".ditamap")),
                "maps are entry points, never orphans");
    }

    @Test
    void health_cleanProjectExceptOrphan() throws Exception {
        writeFixtureProject();
        HealthReport report = executor.execute(new HealthCommand(
                tempDir.toString(), path("root.ditamap")));
        assertTrue(report.brokenReferences().isEmpty());
        assertTrue(report.undefinedKeys().isEmpty());
        // fixture intentionally carries one unused key + one orphan
        assertEquals(1, report.unusedKeys().size());
        assertEquals(1, report.orphanTopics().size());
    }

    // -------------------------------------------------------------------------
    // ReferenceRewriter unit behavior
    // -------------------------------------------------------------------------

    @Test
    void rewriter_planComputesRelativePathsAndKeepsFragments() throws Exception {
        writeFixtureProject();
        ReverseLinkIndex index = ReverseLinkIndex.build(tempDir.toFile(), null);
        File target = file("topics/intro.dita");
        File dest = file("topics/concepts/introduction.dita");

        ReferenceRewriter.Plan plan = ReferenceRewriter.planRenameFile(target, dest, index);

        // map keydef + map topicref + xref + conref = 4 edits
        assertEquals(4, plan.edits().size(), "edits: " + plan.edits());
        assertTrue(plan.edits().stream().anyMatch(e ->
                        e.newValue().equals("topics/concepts/introduction.dita")
                        && e.oldValue().equals("topics/intro.dita")),
                "map-relative path");
        assertTrue(plan.edits().stream().anyMatch(e ->
                        e.newValue().equals("concepts/introduction.dita#intro")),
                "sibling-relative path with fragment preserved");
        assertTrue(plan.edits().stream().anyMatch(e ->
                        e.newValue().equals("concepts/introduction.dita#intro/x")),
                "conref fragment preserved");
    }

    @Test
    void rewriter_replaceAttribute_bothQuoteStyles() {
        ReferenceRewriter.AttributeEdit edit = new ReferenceRewriter.AttributeEdit(
                null, 1, "href", "old.dita", "new.dita");
        assertEquals("<xref href=\"new.dita\"/>",
                ReferenceRewriter.replaceAttribute("<xref href=\"old.dita\"/>", edit));
        assertEquals("<xref href='new.dita'/>",
                ReferenceRewriter.replaceAttribute("<xref href='old.dita'/>", edit));
        assertNull(ReferenceRewriter.replaceAttribute("<xref href=\"other.dita\"/>", edit),
                "no match -> null");
    }

    // -------------------------------------------------------------------------
    // rename-file command
    // -------------------------------------------------------------------------

    @Test
    void renameFile_dryRunByDefault_changesNothing() throws Exception {
        writeFixtureProject();
        RefactorResult result = executor.execute(new RenameFileCommand(
                path("topics/intro.dita"), path("topics/introduction.dita"),
                tempDir.toString(), false));

        assertFalse(result.applied());
        assertEquals(4, result.edits().size());
        assertTrue(file("topics/intro.dita").isFile(), "file not moved");
        assertFalse(file("topics/introduction.dita").exists());
        String other = Files.readString(file("topics/other.dita").toPath());
        assertTrue(other.contains("intro.dita#intro"), "content untouched");
    }

    @Test
    void renameFile_apply_rewritesEverythingAndMoves() throws Exception {
        writeFixtureProject();
        RefactorResult result = executor.execute(new RenameFileCommand(
                path("topics/intro.dita"), path("topics/concepts/introduction.dita"),
                tempDir.toString(), true));

        assertTrue(result.applied());
        assertEquals(4, result.editsApplied());
        assertTrue(result.failures().isEmpty(), "failures: " + result.failures());
        assertFalse(file("topics/intro.dita").exists(), "moved away");
        assertTrue(file("topics/concepts/introduction.dita").isFile(), "moved here");

        // The strongest assertion: the project is link-clean after the refactor.
        List<com.dogsbay.dogsbayaieditor.commands.results.BrokenRef> broken =
                executor.execute(new CheckLinksCommand(tempDir.toString(), null));
        assertTrue(broken.isEmpty(), "no broken refs after rename: " + broken);

        String other = Files.readString(file("topics/other.dita").toPath());
        assertTrue(other.contains("concepts/introduction.dita#intro"), "xref rewritten");
        assertTrue(other.contains("concepts/introduction.dita#intro/x"), "conref rewritten");
        String map = Files.readString(file("root.ditamap").toPath());
        assertTrue(map.contains("topics/concepts/introduction.dita"), "map rewritten");
        assertFalse(map.contains("\"topics/intro.dita\""), "old path gone from map");
    }

    @Test
    void renameFile_apply_refusesExistingDestination() throws Exception {
        writeFixtureProject();
        CommandException e = assertThrows(CommandException.class, () ->
                executor.execute(new RenameFileCommand(
                        path("topics/intro.dita"), path("topics/other.dita"),
                        tempDir.toString(), true)));
        assertTrue(e.getMessage().contains("already exists"));
    }

    @Test
    void renameFile_unreferencedFile_planHasNoEditsButMoves() throws Exception {
        writeFixtureProject();
        RefactorResult result = executor.execute(new RenameFileCommand(
                path("topics/orphan.dita"), path("topics/lonely.dita"),
                tempDir.toString(), true));
        assertTrue(result.applied());
        assertEquals(0, result.editsApplied());
        assertTrue(file("topics/lonely.dita").isFile());
    }

    // -------------------------------------------------------------------------
    // rename-key command
    // -------------------------------------------------------------------------

    private void writeKeyFixture() throws IOException {
        write("root.ditamap",
                "<map>"
                + "<keydef keys=\"product intro-alias\" href=\"topics/intro.dita\"/>"
                + "<mapref href=\"sub.ditamap\"/>"
                + "<topicref href=\"topics/a.dita\"/>"
                + "</map>");
        // A shadowed duplicate definition in a submap — must be renamed too.
        write("sub.ditamap",
                "<map><keydef keys=\"product\" href=\"topics/a.dita\"/></map>");
        write("topics/intro.dita", "<concept id=\"intro\"/>");
        write("topics/a.dita", "<concept id=\"a\"><title>A</title><conbody>"
                + "<p><ph keyref=\"product\"/></p>"
                + "<p conkeyref=\"product/legal\"/>"
                + "</conbody></concept>");
    }

    @Test
    void renameKey_dryRun_plansDefinitionsAndUsages() throws Exception {
        writeKeyFixture();
        RefactorResult result = executor.execute(new RenameKeyCommand(
                "product", "product-name", tempDir.toString(), false));

        assertFalse(result.applied());
        // 2 keydef definitions (root + shadowed submap) + keyref + conkeyref
        assertEquals(4, result.edits().size(), "edits: " + result.edits());
        assertTrue(result.edits().stream().anyMatch(e ->
                        e.attribute().equals("keys")
                        && e.oldValue().equals("product intro-alias")
                        && e.newValue().equals("product-name intro-alias")),
                "multi-key attribute replaces only the matching token");
        assertTrue(result.edits().stream().anyMatch(e ->
                        e.attribute().equals("conkeyref")
                        && e.newValue().equals("product-name/legal")),
                "conkeyref element-id preserved");
        assertTrue(result.warnings().isEmpty(), "warnings: " + result.warnings());
    }

    @Test
    void renameKey_apply_rewritesAndStaysHealthy() throws Exception {
        writeKeyFixture();
        RefactorResult result = executor.execute(new RenameKeyCommand(
                "product", "product-name", tempDir.toString(), true));

        assertTrue(result.applied());
        assertEquals(4, result.editsApplied());
        assertTrue(result.failures().isEmpty(), "failures: " + result.failures());

        String map = Files.readString(file("root.ditamap").toPath());
        assertTrue(map.contains("keys=\"product-name intro-alias\""));
        String topic = Files.readString(file("topics/a.dita").toPath());
        assertTrue(topic.contains("keyref=\"product-name\""));
        assertTrue(topic.contains("conkeyref=\"product-name/legal\""));

        // Post-refactor: the renamed key resolves; no undefined keys remain.
        var resolved = executor.execute(new ResolveKeyCommand(
                "product-name", path("root.ditamap"), null));
        assertNotNull(resolved.resolvedPath());
        var report = executor.execute(new HealthCommand(
                tempDir.toString(), path("root.ditamap")));
        assertTrue(report.undefinedKeys().isEmpty(),
                "no stale keyrefs after rename: " + report.undefinedKeys());
    }

    @Test
    void renameKey_collision_warnsButPlans() throws Exception {
        writeKeyFixture();
        write("extra.ditamap",
                "<map><keydef keys=\"taken\" href=\"topics/a.dita\"/></map>");

        RefactorResult result = executor.execute(new RenameKeyCommand(
                "product", "taken", tempDir.toString(), false));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("collision")),
                "warnings: " + result.warnings());
        assertFalse(result.edits().isEmpty(), "plan still produced");
    }

    @Test
    void renameKey_unknownKey_warns() throws Exception {
        writeKeyFixture();
        RefactorResult result = executor.execute(new RenameKeyCommand(
                "ghost", "spirit", tempDir.toString(), false));
        assertTrue(result.edits().isEmpty());
        assertTrue(result.warnings().stream()
                .anyMatch(w -> w.contains("no definitions and no usages")));
    }

    @Test
    void renameKey_sameName_throws() {
        CommandException e = assertThrows(CommandException.class, () ->
                executor.execute(new RenameKeyCommand(
                        "x", "x", tempDir.toString(), false)));
        assertTrue(e.getMessage().contains("same"));
    }

    // -------------------------------------------------------------------------
    // ReferenceRewriter.removeElement (safe delete)
    // -------------------------------------------------------------------------

    @Test
    void removeElement_selfClosingOnOwnLine_removesWholeLine() {
        String map = "<map>\n"
                + "  <topicref href=\"a.dita\"/>\n"
                + "  <topicref href=\"b.dita\"/>\n"
                + "</map>\n";
        String result = ReferenceRewriter.removeElement(map, "href", "a.dita");
        assertEquals("<map>\n  <topicref href=\"b.dita\"/>\n</map>\n", result);
    }

    @Test
    void removeElement_pairedWithTopicmeta_removed() {
        String map = "<map><topicref href=\"a.dita\"><topicmeta>"
                + "<navtitle>A</navtitle></topicmeta></topicref>"
                + "<topicref href=\"b.dita\"/></map>";
        String result = ReferenceRewriter.removeElement(map, "href", "a.dita");
        assertEquals("<map><topicref href=\"b.dita\"/></map>", result);
    }

    @Test
    void removeElement_refusesNestedReferences() {
        String map = "<map><topicref href=\"parent.dita\">"
                + "<topicref href=\"child.dita\"/></topicref></map>";
        assertNull(ReferenceRewriter.removeElement(map, "href", "parent.dita"),
                "subtree carries its own references");
    }

    @Test
    void removeElement_singleQuotesAndNotFound() {
        assertEquals("<map></map>", ReferenceRewriter.removeElement(
                "<map><topicref href='a.dita'/></map>", "href", "a.dita"));
        assertNull(ReferenceRewriter.removeElement(
                "<map><topicref href=\"b.dita\"/></map>", "href", "a.dita"));
    }

    // -------------------------------------------------------------------------
    // delete-file command
    // -------------------------------------------------------------------------

    @Test
    void deleteFile_dryRun_plansRemovalsAndWarnsAboutTheRest() throws Exception {
        writeFixtureProject();
        RefactorResult result = executor.execute(new DeleteFileCommand(
                path("topics/intro.dita"), tempDir.toString(), true, false));

        assertFalse(result.applied());
        // map keydef + map topicref are removable
        assertEquals(2, result.edits().size(), "edits: " + result.edits());
        assertTrue(result.edits().stream().allMatch(e -> e.file().endsWith("root.ditamap")));
        // xref href + conref break; key 'intro' goes undefined
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("cross-reference")),
                "warnings: " + result.warnings());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("transcluded content")));
        assertTrue(result.warnings().stream().anyMatch(w ->
                w.contains("'intro' becomes undefined")));
        assertTrue(file("topics/intro.dita").isFile(), "dry-run deletes nothing");
        assertTrue(Files.readString(file("root.ditamap").toPath())
                .contains("topics/intro.dita"), "map untouched");
    }

    @Test
    void deleteFile_apply_removesMapRefsAndDeletes() throws Exception {
        writeFixtureProject();
        RefactorResult result = executor.execute(new DeleteFileCommand(
                path("topics/intro.dita"), tempDir.toString(), true, true));

        assertTrue(result.applied());
        assertEquals(2, result.editsApplied());
        assertTrue(result.failures().isEmpty(), "failures: " + result.failures());
        assertFalse(file("topics/intro.dita").exists(), "file deleted");

        String map = Files.readString(file("root.ditamap").toPath());
        assertFalse(map.contains("topics/intro.dita"), "map references removed");
        assertTrue(map.contains("topics/other.dita"), "unrelated references kept");

        // Post-delete health matches what the plan warned about: the xref
        // href + conref in other.dita are broken, key 'intro' is undefined.
        HealthReport report = executor.execute(new HealthCommand(
                tempDir.toString(), path("root.ditamap")));
        assertEquals(2, report.brokenReferences().size(),
                "broken: " + report.brokenReferences());
        assertEquals(1, report.undefinedKeys().size());
    }

    @Test
    void deleteFile_apply_withoutRemoveRefs_leavesMapAlone() throws Exception {
        writeFixtureProject();
        RefactorResult result = executor.execute(new DeleteFileCommand(
                path("topics/intro.dita"), tempDir.toString(), false, true));

        assertTrue(result.applied());
        assertEquals(0, result.editsApplied());
        assertFalse(file("topics/intro.dita").exists(), "file deleted");
        assertTrue(Files.readString(file("root.ditamap").toPath())
                .contains("topics/intro.dita"), "map untouched (left broken)");
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("left broken")),
                "warnings: " + result.warnings());
    }

    @Test
    void deleteFile_unreferenced_noPlanNoWarnings() throws Exception {
        writeFixtureProject();
        RefactorResult result = executor.execute(new DeleteFileCommand(
                path("topics/orphan.dita"), tempDir.toString(), true, true));
        assertTrue(result.applied());
        assertEquals(0, result.editsApplied());
        assertTrue(result.warnings().isEmpty());
        assertFalse(file("topics/orphan.dita").exists());
    }

    @Test
    void deleteFile_nestedTopicref_warnsInsteadOfRemoving() throws Exception {
        write("nested.ditamap", "<map>\n"
                + "  <topicref href=\"topics/parent.dita\">\n"
                + "    <topicref href=\"topics/child.dita\"/>\n"
                + "  </topicref>\n"
                + "</map>\n");
        write("topics/parent.dita", "<concept id=\"p\"/>");
        write("topics/child.dita", "<concept id=\"c\"/>");

        RefactorResult result = executor.execute(new DeleteFileCommand(
                path("topics/parent.dita"), tempDir.toString(), true, false));
        assertTrue(result.edits().isEmpty(), "parent topicref not auto-removable");
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("not auto-removable")),
                "warnings: " + result.warnings());

        // The child, by contrast, is removable — and apply keeps the parent.
        RefactorResult child = executor.execute(new DeleteFileCommand(
                path("topics/child.dita"), tempDir.toString(), true, true));
        assertEquals(1, child.editsApplied());
        String map = Files.readString(file("nested.ditamap").toPath());
        assertFalse(map.contains("child.dita"));
        assertTrue(map.contains("parent.dita"));
    }

    // -------------------------------------------------------------------------
    // retarget command
    // -------------------------------------------------------------------------

    @Test
    void retarget_apply_repointsEverythingWithoutMoving() throws Exception {
        writeFixtureProject();
        write("topics/intro2.dita", "<concept id=\"intro\"><title>Intro v2</title>"
                + "<conbody><p id=\"x\">Hi</p></conbody></concept>");

        RefactorResult result = executor.execute(new RetargetCommand(
                path("topics/intro.dita"), path("topics/intro2.dita"),
                tempDir.toString(), true));

        assertTrue(result.applied());
        assertEquals(4, result.editsApplied(), "keydef + topicref + xref + conref");
        assertTrue(file("topics/intro.dita").isFile(), "old target NOT moved or deleted");
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Fragment")),
                "fragment verification warning: " + result.warnings());

        String other = Files.readString(file("topics/other.dita").toPath());
        assertTrue(other.contains("href=\"intro2.dita#intro\""), "xref repointed");
        assertTrue(other.contains("conref=\"intro2.dita#intro/x\""), "conref repointed");
        List<com.dogsbay.dogsbayaieditor.commands.results.BrokenRef> broken =
                executor.execute(new CheckLinksCommand(tempDir.toString(), null));
        assertTrue(broken.isEmpty(), "link-clean after retarget: " + broken);
    }

    @Test
    void retarget_missingDestination_warns() throws Exception {
        writeFixtureProject();
        RefactorResult result = executor.execute(new RetargetCommand(
                path("topics/intro.dita"), path("topics/nope.dita"),
                tempDir.toString(), false));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("does not exist")),
                "warnings: " + result.warnings());
        assertFalse(result.edits().isEmpty(), "plan still produced");
    }

    // -------------------------------------------------------------------------
    // keyify / inline-key commands
    // -------------------------------------------------------------------------

    @Test
    void keyify_apply_convertsHrefsAndInsertsKeydef() throws Exception {
        writeFixtureProject();
        RefactorResult result = executor.execute(new KeyifyCommand(
                path("topics/intro.dita"), "intro-k", path("root.ditamap"),
                tempDir.toString(), null, true));

        assertTrue(result.applied());
        assertTrue(result.failures().isEmpty(), "failures: " + result.failures());
        // xref href→keyref + conref→conkeyref + keydef insertion = 3
        assertEquals(3, result.editsApplied(), "applied: " + result);
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("map reference")),
                "map refs left direct: " + result.warnings());

        String other = Files.readString(file("topics/other.dita").toPath());
        assertTrue(other.contains("keyref=\"intro-k\""), "xref keyed: " + other);
        assertTrue(other.contains("conkeyref=\"intro-k/x\""), "conref keyed: " + other);
        assertFalse(other.contains("href=\"intro.dita"), "no direct hrefs left in topic");
        String map = Files.readString(file("root.ditamap").toPath());
        assertTrue(map.contains("<keydef keys=\"intro-k\" href=\"topics/intro.dita\"/>"),
                "keydef inserted: " + map);

        // Post-refactor: the new key resolves and nothing is broken.
        var resolved = executor.execute(new ResolveKeyCommand(
                "intro-k", path("root.ditamap"), null));
        assertNotNull(resolved.resolvedPath());
        HealthReport report = executor.execute(new HealthCommand(
                tempDir.toString(), path("root.ditamap")));
        assertTrue(report.brokenReferences().isEmpty());
        assertTrue(report.undefinedKeys().isEmpty());
    }

    @Test
    void keyify_existingKey_warnsCollision() throws Exception {
        writeFixtureProject();
        RefactorResult result = executor.execute(new KeyifyCommand(
                path("topics/intro.dita"), "intro", path("root.ditamap"),
                tempDir.toString(), null, false));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("collision")),
                "warnings: " + result.warnings());
    }

    @Test
    void keyify_relativeCliPaths_keydefHrefStaysRelative() throws Exception {
        // CLI users pass relative paths; the inserted keydef href must be
        // map-relative, not an absolute machine path.
        writeFixtureProject();
        java.nio.file.Path cwd = java.nio.file.Paths.get("").toAbsolutePath();
        // No relative path joins two Windows drives (a checkout on D:, temp on C:).
        org.junit.jupiter.api.Assumptions.assumeTrue(cwd.getRoot().equals(tempDir.toAbsolutePath().getRoot()),
                "the working directory and the temp directory are on different drives");
        String relTopic = cwd.relativize(
                tempDir.resolve("topics/intro.dita").toAbsolutePath()).toString();
        String relMap = cwd.relativize(
                tempDir.resolve("root.ditamap").toAbsolutePath()).toString();
        String relRoot = cwd.relativize(tempDir.toAbsolutePath()).toString();

        executor.execute(new KeyifyCommand(relTopic, "intro-k", relMap,
                relRoot, null, true));

        String map = Files.readString(file("root.ditamap").toPath());
        assertTrue(map.contains("<keydef keys=\"intro-k\" href=\"topics/intro.dita\"/>"),
                "relative keydef href: " + map);
    }

    @Test
    void keyify_mapOutsideRootMapClosure_warns() throws Exception {
        writeFixtureProject();
        write("keys.ditamap", "<map><title>Keys</title></map>");

        // keys.ditamap is not referenced from root.ditamap → unreachable
        RefactorResult result = executor.execute(new KeyifyCommand(
                path("topics/intro.dita"), "intro-k", path("keys.ditamap"),
                tempDir.toString(), path("root.ditamap"), false));
        assertTrue(result.warnings().stream().anyMatch(w ->
                        w.contains("not included from")),
                "warnings: " + result.warnings());
    }

    @Test
    void keyify_mapInsideRootMapClosure_noReachabilityWarning() throws Exception {
        writeFixtureProject();
        write("keys.ditamap", "<map><title>Keys</title></map>");
        // Include the keys submap from the root map
        String map = Files.readString(file("root.ditamap").toPath());
        Files.writeString(file("root.ditamap").toPath(),
                map.replace("</map>", "<mapref href=\"keys.ditamap\"/></map>"));

        RefactorResult result = executor.execute(new KeyifyCommand(
                path("topics/intro.dita"), "intro-k", path("keys.ditamap"),
                tempDir.toString(), path("root.ditamap"), false));
        assertTrue(result.warnings().stream().noneMatch(w ->
                        w.contains("not included from")),
                "warnings: " + result.warnings());

        // And the end-to-end proof: apply, then the key resolves via root map
        executor.execute(new KeyifyCommand(
                path("topics/intro.dita"), "intro-k", path("keys.ditamap"),
                tempDir.toString(), path("root.ditamap"), true));
        var resolved = executor.execute(new ResolveKeyCommand(
                "intro-k", path("root.ditamap"), null));
        assertNotNull(resolved.resolvedPath(), "key resolves through the submap");
    }

    @Test
    void inlineKey_apply_rewritesToDirectPaths() throws Exception {
        write("root.ditamap", "<map>"
                + "<keydef keys=\"product\" href=\"topics/intro.dita\"/>"
                + "<topicref href=\"topics/a.dita\"/>"
                + "</map>");
        write("topics/intro.dita", "<concept id=\"intro\"><title>P</title>"
                + "<conbody><p id=\"legal\">(c)</p></conbody></concept>");
        write("topics/a.dita", "<concept id=\"a\"><title>A</title><conbody>"
                + "<p><xref keyref=\"product\">see</xref></p>"
                + "<p conkeyref=\"product/legal\"/>"
                + "<p><ph keyref=\"product\"/></p>"
                + "</conbody></concept>");

        RefactorResult result = executor.execute(new InlineKeyCommand(
                "product", path("root.ditamap"), tempDir.toString(), true));

        assertTrue(result.applied());
        assertEquals(2, result.editsApplied(), "xref + conkeyref; ph skipped");
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("pulls the key's text")),
                "ph keyref warned: " + result.warnings());

        String topic = Files.readString(file("topics/a.dita").toPath());
        assertTrue(topic.contains("<xref href=\"intro.dita\">"), "keyref → href: " + topic);
        assertTrue(topic.contains("conref=\"intro.dita#intro/legal\""),
                "conkeyref → conref with topic id: " + topic);
        assertTrue(topic.contains("<ph keyref=\"product\"/>"), "text-pulling keyref untouched");
        List<com.dogsbay.dogsbayaieditor.commands.results.BrokenRef> broken =
                executor.execute(new CheckLinksCommand(tempDir.toString(), null));
        assertTrue(broken.isEmpty(), "link-clean after inline: " + broken);
    }

    @Test
    void inlineKey_phBeforeXrefSameValue_onlyXrefRewritten() throws Exception {
        write("root.ditamap", "<map>"
                + "<keydef keys=\"product\" href=\"topics/intro.dita\"/>"
                + "<topicref href=\"topics/a.dita\"/>"
                + "</map>");
        write("topics/intro.dita", "<concept id=\"intro\"><title>P</title></concept>");
        // The text-pulling ph comes FIRST — element-aware matching must skip
        // past it to the xref, not just take the first occurrence.
        write("topics/a.dita", "<concept id=\"a\"><title>A</title><conbody>"
                + "<p><ph keyref=\"product\"/></p>"
                + "<p><xref keyref=\"product\">see</xref></p>"
                + "</conbody></concept>");

        RefactorResult result = executor.execute(new InlineKeyCommand(
                "product", path("root.ditamap"), tempDir.toString(), true));

        assertEquals(1, result.editsApplied());
        String topic = Files.readString(file("topics/a.dita").toPath());
        assertTrue(topic.contains("<ph keyref=\"product\"/>"), "ph untouched: " + topic);
        assertTrue(topic.contains("<xref href=\"intro.dita\">"), "xref inlined: " + topic);
    }

    @Test
    void inlineKey_undefinedKey_warnsAndPlansNothing() throws Exception {
        writeFixtureProject();
        RefactorResult result = executor.execute(new InlineKeyCommand(
                "ghost", path("root.ditamap"), tempDir.toString(), false));
        assertTrue(result.edits().isEmpty());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("not defined")),
                "warnings: " + result.warnings());
    }

    @Test
    void inlineKey_scopedKey_resolvesAndRewritesBareKeyrefUsages() throws Exception {
        // "product" is defined only inside a @keyscope; topics use it via a bare
        // keyref. Inlining must resolve the scoped definition AND find the bare
        // usages (matched by resolved definition, not literal key string).
        write("root.ditamap", "<map>"
                + "<topicgroup keyscope=\"ext\">"
                + "<keydef keys=\"product\" href=\"topics/intro.dita\"/>"
                + "</topicgroup>"
                + "<topicref href=\"topics/a.dita\"/>"
                + "</map>");
        write("topics/intro.dita", "<concept id=\"intro\"><title>P</title></concept>");
        write("topics/a.dita", "<concept id=\"a\"><title>A</title><conbody>"
                + "<p><xref keyref=\"product\">see</xref></p>"
                + "</conbody></concept>");

        RefactorResult result = executor.execute(new InlineKeyCommand(
                "product", path("root.ditamap"), tempDir.toString(), true));

        assertEquals(1, result.editsApplied(), "scoped key inlined: " + result.warnings());
        String topic = Files.readString(file("topics/a.dita").toPath());
        assertTrue(topic.contains("<xref href=\"intro.dita\">"), "keyref → href: " + topic);
    }

    // -------------------------------------------------------------------------
    // create-keydef command
    // -------------------------------------------------------------------------

    @Test
    void createKeydef_apply_definesResolvableTextKey() throws Exception {
        write("keys.ditamap", "<map><title>Keys</title></map>");
        write("root.ditamap", "<map><mapref href=\"keys.ditamap\"/></map>");

        RefactorResult dry = executor.execute(new CreateKeydefCommand(
                "product-name", "DogsBay XML", path("keys.ditamap"),
                path("root.ditamap"), null, false));
        assertFalse(dry.applied());
        assertTrue(dry.warnings().isEmpty(), "warnings: " + dry.warnings());
        assertFalse(Files.readString(file("keys.ditamap").toPath())
                .contains("product-name"), "dry-run writes nothing");

        RefactorResult result = executor.execute(new CreateKeydefCommand(
                "product-name", "DogsBay XML", path("keys.ditamap"),
                path("root.ditamap"), null, true));
        assertTrue(result.applied());
        assertTrue(result.failures().isEmpty());

        String map = Files.readString(file("keys.ditamap").toPath());
        assertTrue(map.contains("<keydef keys=\"product-name\"><topicmeta><keywords>"
                        + "<keyword>DogsBay XML</keyword></keywords></topicmeta></keydef>"),
                "keydef inserted: " + map);

        // The key resolves through the root map with the keyword text.
        var resolved = executor.execute(new ResolveKeyCommand(
                "product-name", path("root.ditamap"), null));
        assertEquals("DogsBay XML", resolved.text());
    }

    @Test
    void createKeydef_collisionAndReachability_warn() throws Exception {
        writeKeyFixture();
        write("orphan-keys.ditamap", "<map><title>Unlinked</title></map>");

        RefactorResult collision = executor.execute(new CreateKeydefCommand(
                "product", "Anything", path("root.ditamap"),
                path("root.ditamap"), null, false));
        assertTrue(collision.warnings().stream().anyMatch(w -> w.contains("collision")),
                "warnings: " + collision.warnings());

        RefactorResult unreachable = executor.execute(new CreateKeydefCommand(
                "new-key", "Anything", path("orphan-keys.ditamap"),
                path("root.ditamap"), null, false));
        assertTrue(unreachable.warnings().stream().anyMatch(w ->
                        w.contains("not included from")),
                "warnings: " + unreachable.warnings());
    }

    @Test
    void createKeydef_replaceAll_replacesTextContentOnly() throws Exception {
        write("keys.ditamap", "<map><title>Keys</title></map>");
        write("root.ditamap", "<map><mapref href=\"keys.ditamap\"/>"
                + "<topicref href=\"topics/a.dita\"/></map>");
        write("topics/a.dita", "<concept id=\"a\"><title>About DogsBay XML</title>"
                + "<conbody><p>DogsBay XML is an editor.</p>"
                + "<p product=\"DogsBay XML\">attribute stays</p>"
                + "<codeblock>run DogsBay XML</codeblock>"
                + "</conbody></concept>");
        write("topics/b.dita", "<concept id=\"b\"><title>B</title>"
                + "<conbody><p>Try DogsBay XML today.</p></conbody></concept>");

        RefactorResult dry = executor.execute(new CreateKeydefCommand(
                "product-name", "DogsBay XML", path("keys.ditamap"),
                path("root.ditamap"), tempDir.toString(), false));
        assertFalse(dry.applied());
        // title + p in a.dita, p in b.dita — attribute and codeblock skipped
        assertEquals(3, dry.edits().stream()
                .filter(e -> "text".equals(e.attribute())).count(),
                "edits: " + dry.edits());

        RefactorResult result = executor.execute(new CreateKeydefCommand(
                "product-name", "DogsBay XML", path("keys.ditamap"),
                path("root.ditamap"), tempDir.toString(), true));
        assertTrue(result.applied());
        assertTrue(result.failures().isEmpty(), "failures: " + result.failures());
        assertEquals(4, result.editsApplied(), "keydef + 3 replacements");

        String a = Files.readString(file("topics/a.dita").toPath());
        assertTrue(a.contains("<title>About <ph keyref=\"product-name\"/></title>"), a);
        assertTrue(a.contains("<p><ph keyref=\"product-name\"/> is an editor.</p>"), a);
        assertTrue(a.contains("product=\"DogsBay XML\""), "attribute untouched");
        assertTrue(a.contains("<codeblock>run DogsBay XML</codeblock>"), "code untouched");
        String b = Files.readString(file("topics/b.dita").toPath());
        assertTrue(b.contains("Try <ph keyref=\"product-name\"/> today."), b);

        // End to end: every new keyref resolves through the root map.
        HealthReport report = executor.execute(new HealthCommand(
                tempDir.toString(), path("root.ditamap")));
        assertTrue(report.undefinedKeys().isEmpty(),
                "all keyrefs resolve: " + report.undefinedKeys());
    }

    @Test
    void createKeydef_markupText_rejected() throws Exception {
        write("keys.ditamap", "<map/>");
        CommandException e = assertThrows(CommandException.class, () ->
                executor.execute(new CreateKeydefCommand(
                        "k", "no <b>markup</b>", path("keys.ditamap"), null, null, false)));
        assertTrue(e.getMessage().contains("plain text"), e.getMessage());
    }

    // -------------------------------------------------------------------------
    // extract-conref command
    // -------------------------------------------------------------------------

    @Test
    void extractConref_apply_createsWarehouseAndStubs() throws Exception {
        write("topics/intro.dita", "<concept id=\"intro\"><title>Intro</title>"
                + "<conbody>\n"
                + "<note id=\"safety\">Wear gloves.</note>\n"
                + "<p>Other content</p>\n"
                + "</conbody></concept>");

        RefactorResult result = executor.execute(new ExtractConrefCommand(
                path("topics/intro.dita"), "safety",
                path("shared/warehouse.dita"), true));

        assertTrue(result.applied());
        assertTrue(result.failures().isEmpty(), "failures: " + result.failures());

        String warehouse = Files.readString(file("shared/warehouse.dita").toPath());
        assertTrue(warehouse.contains("<concept id=\"warehouse\">"), warehouse);
        assertTrue(warehouse.contains("<note id=\"safety\">Wear gloves.</note>"), warehouse);

        String source = Files.readString(file("topics/intro.dita").toPath());
        assertFalse(source.contains("Wear gloves"), "element moved out: " + source);
        assertTrue(source.contains(
                "<note conref=\"../shared/warehouse.dita#warehouse/safety\"/>"),
                "conref stub: " + source);
        assertTrue(source.contains("<p>Other content</p>"), "siblings untouched");

        // The new conref resolves: project is link-clean.
        List<com.dogsbay.dogsbayaieditor.commands.results.BrokenRef> broken =
                executor.execute(new CheckLinksCommand(tempDir.toString(), null));
        assertTrue(broken.isEmpty(), "link-clean after extract: " + broken);
    }

    @Test
    void extractConref_apply_appendsToExistingWarehouse() throws Exception {
        write("topics/intro.dita", "<concept id=\"intro\"><title>I</title><conbody>"
                + "<note id=\"safety\">Gloves.</note>"
                + "</conbody></concept>");
        write("shared/warehouse.dita", "<concept id=\"wh\"><title>W</title><conbody>\n"
                + "<p id=\"existing\">Already here</p>\n"
                + "</conbody></concept>");

        RefactorResult result = executor.execute(new ExtractConrefCommand(
                path("topics/intro.dita"), "safety",
                path("shared/warehouse.dita"), true));

        assertTrue(result.applied());
        String warehouse = Files.readString(file("shared/warehouse.dita").toPath());
        assertTrue(warehouse.contains("<p id=\"existing\">Already here</p>"), warehouse);
        assertTrue(warehouse.contains("<note id=\"safety\">Gloves.</note>"), warehouse);
        String source = Files.readString(file("topics/intro.dita").toPath());
        assertTrue(source.contains("conref=\"../shared/warehouse.dita#wh/safety\""),
                "conref uses the existing topic id: " + source);
    }

    @Test
    void extractConref_dryRun_touchesNothing() throws Exception {
        write("topics/intro.dita", "<concept id=\"intro\"><title>I</title><conbody>"
                + "<note id=\"safety\">Gloves.</note></conbody></concept>");

        RefactorResult result = executor.execute(new ExtractConrefCommand(
                path("topics/intro.dita"), "safety",
                path("shared/warehouse.dita"), false));

        assertFalse(result.applied());
        assertEquals(2, result.edits().size(), "extract + create rows");
        assertFalse(file("shared/warehouse.dita").exists(), "dry-run creates nothing");
        assertTrue(Files.readString(file("topics/intro.dita").toPath())
                .contains("Gloves."), "source untouched");
    }

    @Test
    void extractConref_idCollisionInWarehouse_refuses() throws Exception {
        write("topics/intro.dita", "<concept id=\"intro\"><title>I</title><conbody>"
                + "<note id=\"safety\">Gloves.</note></conbody></concept>");
        write("shared/warehouse.dita", "<concept id=\"wh\"><title>W</title><conbody>"
                + "<p id=\"safety\">Conflict</p></conbody></concept>");

        CommandException e = assertThrows(CommandException.class, () ->
                executor.execute(new ExtractConrefCommand(
                        path("topics/intro.dita"), "safety",
                        path("shared/warehouse.dita"), false)));
        assertTrue(e.getMessage().contains("already has an element"),
                e.getMessage());
    }

    @Test
    void extractConrefAtCaret_generatesIdWhenElementHasNone() throws Exception {
        File source = write("topics/intro.dita",
                "<concept id=\"intro\"><title>I</title><conbody>\n"
                + "<note>Wear gloves.</note>\n"
                + "</conbody></concept>");
        String text = Files.readString(source.toPath());
        com.dogsbay.dogsbayaieditor.links.ElementAtCaret.Located located =
                com.dogsbay.dogsbayaieditor.links.ElementAtCaret.locate(
                        text, text.indexOf("gloves"));
        assertNotNull(located);
        assertEquals("note", located.element());
        assertNull(located.id(), "fixture note has no id");

        com.dogsbay.dogsbayaieditor.links.ConrefExtractor.Plan plan =
                com.dogsbay.dogsbayaieditor.links.ConrefExtractor.planAt(
                        source, located.start(), located.end(), located.id(),
                        file("shared/wh.dita"));
        assertEquals("note-reuse", plan.elementId());
        assertTrue(plan.idGenerated());
        assertTrue(plan.warnings().stream().anyMatch(w -> w.contains("had no id")),
                "warnings: " + plan.warnings());

        var applied = com.dogsbay.dogsbayaieditor.links.ConrefExtractor.apply(plan);
        assertTrue(applied.failures().isEmpty(), "failures: " + applied.failures());

        String warehouse = Files.readString(file("shared/wh.dita").toPath());
        assertTrue(warehouse.contains("<note id=\"note-reuse\">Wear gloves.</note>"),
                "id injected into warehouse copy: " + warehouse);
        String after = Files.readString(source.toPath());
        assertTrue(after.contains("<note conref=\"../shared/wh.dita#wh/note-reuse\"/>"),
                "stub references the generated id: " + after);
        List<com.dogsbay.dogsbayaieditor.commands.results.BrokenRef> broken =
                executor.execute(new CheckLinksCommand(tempDir.toString(), null));
        assertTrue(broken.isEmpty(), "link-clean after extract: " + broken);
    }

    @Test
    void extractConrefAtCaret_userChosenIdIsUsed() throws Exception {
        File source = write("topics/intro.dita",
                "<concept id=\"intro\"><title>I</title><conbody>"
                + "<note>Wear gloves.</note></conbody></concept>");
        String text = Files.readString(source.toPath());
        com.dogsbay.dogsbayaieditor.links.ElementAtCaret.Located located =
                com.dogsbay.dogsbayaieditor.links.ElementAtCaret.locate(
                        text, text.indexOf("gloves"));

        com.dogsbay.dogsbayaieditor.links.ConrefExtractor.Plan plan =
                com.dogsbay.dogsbayaieditor.links.ConrefExtractor.planAt(
                        source, located.start(), located.end(), located.id(),
                        file("shared/wh.dita"), "safety-gloves");
        assertEquals("safety-gloves", plan.elementId());

        var applied = com.dogsbay.dogsbayaieditor.links.ConrefExtractor.apply(plan);
        assertTrue(applied.failures().isEmpty());
        assertTrue(Files.readString(file("shared/wh.dita").toPath())
                .contains("<note id=\"safety-gloves\">"));
        assertTrue(Files.readString(source.toPath())
                .contains("#wh/safety-gloves\"/>"));
    }

    @Test
    void extractConrefAtCaret_userChosenIdCollision_refuses() throws Exception {
        File source = write("topics/intro.dita",
                "<concept id=\"intro\"><title>I</title><conbody>"
                + "<note>New.</note></conbody></concept>");
        write("shared/wh.dita", "<concept id=\"wh\"><title>W</title><conbody>\n"
                + "<p id=\"taken\">x</p>\n</conbody></concept>");
        String text = Files.readString(source.toPath());
        com.dogsbay.dogsbayaieditor.links.ElementAtCaret.Located located =
                com.dogsbay.dogsbayaieditor.links.ElementAtCaret.locate(
                        text, text.indexOf("New"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                com.dogsbay.dogsbayaieditor.links.ConrefExtractor.planAt(
                        source, located.start(), located.end(), located.id(),
                        file("shared/wh.dita"), "taken"));
        assertTrue(e.getMessage().contains("already has an element"), e.getMessage());
    }

    @Test
    void extractConref_suggestId_avoidsExistingIds() throws Exception {
        write("shared/wh.dita", "<concept id=\"wh\"><title>W</title><conbody>\n"
                + "<note id=\"note-reuse\">x</note>\n</conbody></concept>");
        assertEquals("note-reuse-2",
                com.dogsbay.dogsbayaieditor.links.ConrefExtractor.suggestId(
                        "note", file("shared/wh.dita")));
        assertEquals("note-reuse",
                com.dogsbay.dogsbayaieditor.links.ConrefExtractor.suggestId(
                        "note", file("shared/new.dita")),
                "fresh file: base suggestion");
    }

    @Test
    void extractConrefAtCaret_generatedIdAvoidsCollisions() throws Exception {
        File source = write("topics/intro.dita",
                "<concept id=\"intro\"><title>I</title><conbody>"
                + "<note>New note.</note></conbody></concept>");
        write("shared/wh.dita", "<concept id=\"wh\"><title>W</title><conbody>\n"
                + "<note id=\"note-reuse\">Taken</note>\n</conbody></concept>");
        String text = Files.readString(source.toPath());
        com.dogsbay.dogsbayaieditor.links.ElementAtCaret.Located located =
                com.dogsbay.dogsbayaieditor.links.ElementAtCaret.locate(
                        text, text.indexOf("New"));

        com.dogsbay.dogsbayaieditor.links.ConrefExtractor.Plan plan =
                com.dogsbay.dogsbayaieditor.links.ConrefExtractor.planAt(
                        source, located.start(), located.end(), located.id(),
                        file("shared/wh.dita"));
        assertEquals("note-reuse-2", plan.elementId(), "collision skipped");
    }

    @Test
    void extractConref_unknownId_throws() throws Exception {
        write("topics/intro.dita", "<concept id=\"intro\"><title>I</title>"
                + "<conbody><p>x</p></conbody></concept>");
        CommandException e = assertThrows(CommandException.class, () ->
                executor.execute(new ExtractConrefCommand(
                        path("topics/intro.dita"), "nope",
                        path("warehouse.dita"), false)));
        assertTrue(e.getMessage().contains("No element with id"), e.getMessage());
    }

    // -------------------------------------------------------------------------
    // inline-conref command (Tier 3)
    // -------------------------------------------------------------------------

    private void writeInlineFixture() throws IOException {
        write("shared/warehouse.dita", "<concept id=\"wh\"><title>W</title><conbody>"
                + "<note id=\"safety\">Wear <xref href=\"gloves.dita\">gloves</xref>.</note>"
                + "</conbody></concept>");
        write("shared/gloves.dita", "<concept id=\"g\"/>");
        write("topics/a.dita", "<concept id=\"a\"><title>A</title><conbody>"
                + "<note id=\"local-note\" conref=\"../shared/warehouse.dita#wh/safety\"/>"
                + "</conbody></concept>");
        write("topics/deep/b.dita", "<concept id=\"b\"><title>B</title><conbody>"
                + "<note conref=\"../../shared/warehouse.dita#wh/safety\"/>"
                + "</conbody></concept>");
    }

    @Test
    void inlineConref_apply_allInstances_rebasedPerDestination() throws Exception {
        writeInlineFixture();
        RefactorResult dry = executor.execute(new InlineConrefCommand(
                path("shared/warehouse.dita"), "safety", tempDir.toString(),
                null, false));
        assertFalse(dry.applied());
        assertEquals(2, dry.edits().size(), "both instances planned: " + dry.edits());

        RefactorResult result = executor.execute(new InlineConrefCommand(
                path("shared/warehouse.dita"), "safety", tempDir.toString(),
                null, true));
        assertTrue(result.applied());
        assertEquals(2, result.editsApplied());
        assertTrue(result.failures().isEmpty(), "failures: " + result.failures());

        String a = Files.readString(file("topics/a.dita").toPath());
        assertTrue(a.contains("<note id=\"local-note\">Wear "
                        + "<xref href=\"../shared/gloves.dita\">gloves</xref>.</note>"),
                "local id kept, nested href rebased for topics/: " + a);
        String b = Files.readString(file("topics/deep/b.dita").toPath());
        assertTrue(b.contains("<note>Wear "
                        + "<xref href=\"../../shared/gloves.dita\">gloves</xref>.</note>"),
                "no id, href rebased for topics/deep/: " + b);
        assertTrue(Files.readString(file("shared/warehouse.dita").toPath())
                .contains("id=\"safety\""), "target element untouched");
        List<com.dogsbay.dogsbayaieditor.commands.results.BrokenRef> broken =
                executor.execute(new CheckLinksCommand(tempDir.toString(), null));
        assertTrue(broken.isEmpty(), "link-clean after inline: " + broken);
    }

    @Test
    void inlineConref_fileOption_onlyThatFile() throws Exception {
        writeInlineFixture();
        RefactorResult result = executor.execute(new InlineConrefCommand(
                path("shared/warehouse.dita"), "safety", tempDir.toString(),
                path("topics/a.dita"), true));
        assertEquals(1, result.editsApplied());
        assertTrue(Files.readString(file("topics/a.dita").toPath())
                .contains("Wear "), "a.dita inlined");
        assertTrue(Files.readString(file("topics/deep/b.dita").toPath())
                .contains("conref="), "b.dita untouched");
    }

    // -------------------------------------------------------------------------
    // rename-element-id command (Tier 3)
    // -------------------------------------------------------------------------

    @Test
    void renameElementId_apply_rewritesFragmentsEverywhere() throws Exception {
        write("root.ditamap", "<map>"
                + "<keydef keys=\"wh\" href=\"shared.dita\"/>"
                + "<topicref href=\"topics/a.dita\"/></map>");
        write("shared.dita", "<concept id=\"shared\"><title>S</title><conbody>"
                + "<p id=\"legal\">(c)</p></conbody></concept>");
        write("topics/a.dita", "<concept id=\"a\"><title>A</title><conbody>"
                + "<p conref=\"../shared.dita#shared/legal\"/>"
                + "<p><xref href=\"../shared.dita#shared/legal\">see</xref></p>"
                + "<p conkeyref=\"wh/legal\"/>"
                + "</conbody></concept>");

        RefactorResult result = executor.execute(new RenameElementIdCommand(
                path("shared.dita"), "legal", "copyright",
                tempDir.toString(), path("root.ditamap"), true));
        assertTrue(result.applied());
        // id + conref + xref href + conkeyref
        assertEquals(4, result.editsApplied(), "edits: " + result.edits());

        assertTrue(Files.readString(file("shared.dita").toPath())
                .contains("id=\"copyright\""));
        String a = Files.readString(file("topics/a.dita").toPath());
        assertTrue(a.contains("conref=\"../shared.dita#shared/copyright\""), a);
        assertTrue(a.contains("href=\"../shared.dita#shared/copyright\""), a);
        assertTrue(a.contains("conkeyref=\"wh/copyright\""), a);
        List<com.dogsbay.dogsbayaieditor.commands.results.BrokenRef> broken =
                executor.execute(new CheckLinksCommand(tempDir.toString(), null));
        assertTrue(broken.isEmpty(), "link-clean after rename: " + broken);
    }

    @Test
    void renameElementId_topicId_rewritesBothFragmentForms() throws Exception {
        write("shared.dita", "<concept id=\"shared\"><title>S</title><conbody>"
                + "<p id=\"legal\">(c)</p></conbody></concept>");
        write("topics/a.dita", "<concept id=\"a\"><title>A</title><conbody>"
                + "<p><xref href=\"../shared.dita#shared\">topic</xref></p>"
                + "<p conref=\"../shared.dita#shared/legal\"/>"
                + "</conbody></concept>");

        RefactorResult result = executor.execute(new RenameElementIdCommand(
                path("shared.dita"), "shared", "shared-topic",
                tempDir.toString(), null, true));
        assertTrue(result.applied());
        String a = Files.readString(file("topics/a.dita").toPath());
        assertTrue(a.contains("href=\"../shared.dita#shared-topic\""), a);
        assertTrue(a.contains("conref=\"../shared.dita#shared-topic/legal\""), a);
    }

    @Test
    void renameElementId_unknownId_warnsAndPlansNothing() throws Exception {
        write("shared.dita", "<concept id=\"s\"/>");
        RefactorResult result = executor.execute(new RenameElementIdCommand(
                path("shared.dita"), "nope", "new", tempDir.toString(), null, false));
        assertTrue(result.edits().isEmpty());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("No element")),
                "warnings: " + result.warnings());
    }

    // -------------------------------------------------------------------------
    // merge-keydefs command (Tier 3)
    // -------------------------------------------------------------------------

    @Test
    void mergeKeydefs_apply_removesShadowedDefinitions() throws Exception {
        write("root.ditamap", "<map>\n"
                + "  <keydef keys=\"product\" href=\"topics/a.dita\"/>\n"
                + "  <mapref href=\"sub.ditamap\"/>\n"
                + "</map>");
        // Shadowed: 'product' duplicates; 'unique' must survive the trim
        write("sub.ditamap", "<map>\n"
                + "  <keydef keys=\"product\" href=\"topics/b.dita\"/>\n"
                + "  <keydef keys=\"product unique\" href=\"topics/b.dita\"/>\n"
                + "</map>");
        write("topics/a.dita", "<concept id=\"a\"/>");
        write("topics/b.dita", "<concept id=\"b\"/>");

        var before = executor.execute(new ResolveKeyCommand(
                "product", path("root.ditamap"), null));

        RefactorResult dry = executor.execute(new MergeKeydefsCommand(
                path("root.ditamap"), null, false));
        assertEquals(2, dry.edits().size(), "one removal + one token trim: " + dry.edits());
        assertTrue(dry.warnings().stream().anyMatch(w -> w.contains("differs")),
                "differing-definition warning: " + dry.warnings());

        RefactorResult result = executor.execute(new MergeKeydefsCommand(
                path("root.ditamap"), null, true));
        assertTrue(result.applied());
        assertTrue(result.failures().isEmpty(), "failures: " + result.failures());

        String sub = Files.readString(file("sub.ditamap").toPath());
        assertFalse(sub.contains("keys=\"product\""), "single-key duplicate removed: " + sub);
        assertTrue(sub.contains("keys=\"unique\""), "multi-key keydef trimmed to 'unique': " + sub);

        // Resolution unchanged: the effective definition still wins.
        var after = executor.execute(new ResolveKeyCommand(
                "product", path("root.ditamap"), null));
        assertEquals(before.resolvedPath(), after.resolvedPath());
        var unique = executor.execute(new ResolveKeyCommand(
                "unique", path("root.ditamap"), null));
        assertNotNull(unique.definedIn(), "'unique' still defined");
    }

    @Test
    void mergeKeydefs_sameFileDuplicates_keepsTheEffectiveDefinition() throws Exception {
        // Both keydefs have IDENTICAL @keys — removal must be line-aware
        // or it deletes the first (winning) definition instead.
        write("root.ditamap", "<map>\n"
                + "  <keydef keys=\"download-url\">"
                + "<topicmeta><keywords><keyword>https://good.example/dl</keyword>"
                + "</keywords></topicmeta></keydef>\n"
                + "  <keydef keys=\"download-url\">"
                + "<topicmeta><keywords><keyword>https://stale.example/dl</keyword>"
                + "</keywords></topicmeta></keydef>\n"
                + "</map>");

        RefactorResult result = executor.execute(new MergeKeydefsCommand(
                path("root.ditamap"), null, true));
        assertTrue(result.applied());
        assertTrue(result.failures().isEmpty(), "failures: " + result.failures());

        String map = Files.readString(file("root.ditamap").toPath());
        assertTrue(map.contains("good.example"), "effective definition kept: " + map);
        assertFalse(map.contains("stale.example"), "shadowed duplicate removed: " + map);

        var resolved = executor.execute(new ResolveKeyCommand(
                "download-url", path("root.ditamap"), null));
        assertEquals("https://good.example/dl", resolved.text());
    }

    @Test
    void mergeKeydefs_noDuplicates_warns() throws Exception {
        write("root.ditamap", "<map><keydef keys=\"only\" href=\"a.dita\"/></map>");
        write("a.dita", "<concept id=\"a\"/>");
        RefactorResult result = executor.execute(new MergeKeydefsCommand(
                path("root.ditamap"), null, false));
        assertTrue(result.edits().isEmpty());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("No duplicate")),
                "warnings: " + result.warnings());
    }

    // -------------------------------------------------------------------------
    // rename-profile-value command (Tier 4)
    // -------------------------------------------------------------------------

    @Test
    void renameProfileValue_apply_tokensTopicsMapsAndDitaval() throws Exception {
        write("topics/a.dita", "<concept id=\"a\"><title>A</title><conbody>"
                + "<p product=\"widget_v1\">v1 only</p>"
                + "<p product=\"widget_v1 widget_v2\">both</p>"
                + "<p platform=\"widget_v1\">other attribute</p>"
                + "</conbody></concept>");
        write("root.ditamap", "<map>"
                + "<topicref href=\"topics/a.dita\" product=\"widget_v1\"/>"
                + "</map>");
        write("filters/rosa.ditaval", "<val>"
                + "<prop att=\"product\" val=\"widget_v1\" action=\"exclude\"/>"
                + "<prop att=\"platform\" val=\"widget_v1\" action=\"exclude\"/>"
                + "<prop val=\"widget_v1\" action=\"exclude\"/>"
                + "</val>");

        RefactorResult dry = executor.execute(new RenameProfileValueCommand(
                "product", "widget_v1", "widget_enterprise",
                tempDir.toString(), false));
        assertFalse(dry.applied());
        // 2 topic attrs + 1 map attr + 1 ditaval rule
        assertEquals(4, dry.edits().size(), "edits: " + dry.edits());
        assertTrue(dry.warnings().stream().anyMatch(w -> w.contains("without @att")),
                "att-less ditaval rule warned: " + dry.warnings());

        RefactorResult result = executor.execute(new RenameProfileValueCommand(
                "product", "widget_v1", "widget_enterprise",
                tempDir.toString(), true));
        assertTrue(result.applied());
        assertTrue(result.failures().isEmpty(), "failures: " + result.failures());

        String a = Files.readString(file("topics/a.dita").toPath());
        assertTrue(a.contains("product=\"widget_enterprise\">v1 only"), a);
        assertTrue(a.contains("product=\"widget_enterprise widget_v2\""),
                "only the matching token changes: " + a);
        assertTrue(a.contains("platform=\"widget_v1\""),
                "other attributes untouched: " + a);
        assertTrue(Files.readString(file("root.ditamap").toPath())
                .contains("product=\"widget_enterprise\""), "map attr updated");
        String ditaval = Files.readString(file("filters/rosa.ditaval").toPath());
        assertTrue(ditaval.contains(
                "att=\"product\" val=\"widget_enterprise\""), "matching rule updated: " + ditaval);
        assertTrue(ditaval.contains(
                "att=\"platform\" val=\"widget_v1\""), "other-attribute rule untouched");
        assertTrue(ditaval.contains(
                "<prop val=\"widget_v1\" action=\"exclude\"/>"), "att-less rule untouched");
    }

    @Test
    void renameProfileValue_duplicateTokenMerged() throws Exception {
        write("topics/a.dita", "<concept id=\"a\"><conbody>"
                + "<p product=\"alpha beta\">x</p></conbody></concept>");

        RefactorResult result = executor.execute(new RenameProfileValueCommand(
                "product", "alpha", "beta", tempDir.toString(), true));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("merged")),
                "warnings: " + result.warnings());
        assertTrue(Files.readString(file("topics/a.dita").toPath())
                .contains("product=\"beta\">"), "tokens deduped");
    }

    @Test
    void renameProfileValue_notFound_warns() throws Exception {
        write("topics/a.dita", "<concept id=\"a\"/>");
        RefactorResult result = executor.execute(new RenameProfileValueCommand(
                "product", "ghost", "spirit", tempDir.toString(), false));
        assertTrue(result.edits().isEmpty());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("No product")),
                "warnings: " + result.warnings());
    }

    // -------------------------------------------------------------------------
    // split-topic command (Tier 4)
    // -------------------------------------------------------------------------

    private void writeSplitFixture() throws IOException {
        write("topics/guide.dita", "<concept id=\"guide\">\n"
                + "<title>The Guide</title>\n"
                + "<prolog><critdates><created date=\"2024-01-01\"/></critdates></prolog>\n"
                + "<conbody>\n"
                + "<p>Intro paragraph stays.</p>\n"
                + "<section id=\"install\"><title>Installing</title>"
                + "<p id=\"install-step\">Run the installer.</p></section>\n"
                + "<section><title>Configuration &amp; Setup</title>"
                + "<p>Edit the config.</p></section>\n"
                + "</conbody>\n"
                + "</concept>");
        write("root.ditamap", "<map>\n"
                + "<topicref href=\"topics/guide.dita\"/>\n"
                + "<topicref href=\"topics/other.dita\"/>\n"
                + "</map>");
        write("topics/other.dita", "<concept id=\"o\"><title>O</title><conbody>"
                + "<p conref=\"guide.dita#guide/install-step\"/>"
                + "<p><xref href=\"guide.dita#guide/install\">install</xref></p>"
                + "</conbody></concept>");
    }

    @Test
    void splitTopic_apply_sectionsBecomeTopicsWiredIntoMap() throws Exception {
        writeSplitFixture();

        RefactorResult dry = executor.execute(new SplitTopicCommand(
                path("topics/guide.dita"), tempDir.toString(),
                path("root.ditamap"), false));
        assertFalse(dry.applied());
        assertEquals(2, dry.edits().stream()
                .filter(e -> "split".equals(e.attribute())).count(),
                "two sections planned: " + dry.edits());
        assertFalse(file("topics/installing.dita").exists(), "dry-run creates nothing");

        RefactorResult result = executor.execute(new SplitTopicCommand(
                path("topics/guide.dita"), tempDir.toString(),
                path("root.ditamap"), true));
        assertTrue(result.applied());
        assertTrue(result.failures().isEmpty(), "failures: " + result.failures());
        assertEquals(2, result.filesChanged(), "two topics created");

        // New topics: id from section @id (install) / title slug (config)
        String installing = Files.readString(file("topics/installing.dita").toPath());
        assertTrue(installing.contains("<concept id=\"install\">"), installing);
        assertTrue(installing.contains("<title>Installing</title>"), installing);
        assertTrue(installing.contains("<p id=\"install-step\">Run the installer.</p>"),
                installing);
        String config = Files.readString(
                file("topics/configuration-setup.dita").toPath());
        assertTrue(config.contains("<concept id=\"configuration-setup\">"), config);

        // A7: the source topic's prolog metadata is carried into each split topic.
        assertTrue(installing.contains("<created date=\"2024-01-01\"/>"),
                "A7: prolog carried into split topic: " + installing);
        assertTrue(config.contains("<created date=\"2024-01-01\"/>"),
                "A7: prolog carried into split topic: " + config);

        // Source: sections gone, intro stays
        String guide = Files.readString(file("topics/guide.dita").toPath());
        assertFalse(guide.contains("<section"), "sections removed: " + guide);
        assertTrue(guide.contains("Intro paragraph stays."), guide);

        // Map: nested topicrefs under the source's (now paired) topicref
        String map = Files.readString(file("root.ditamap").toPath());
        assertTrue(map.contains("<topicref href=\"topics/guide.dita\">"
                        + "<topicref href=\"topics/installing.dita\"/>"
                        + "<topicref href=\"topics/configuration-setup.dita\"/>"
                        + "</topicref>"),
                "nested wiring: " + map);

        // References into the split-out content rewritten
        String other = Files.readString(file("topics/other.dita").toPath());
        assertTrue(other.contains("conref=\"installing.dita#install/install-step\""),
                "conref into section content: " + other);
        assertTrue(other.contains("href=\"installing.dita#install\""),
                "xref to the section id → new topic root: " + other);

        // The strongest assertion: link-clean afterwards.
        List<com.dogsbay.dogsbayaieditor.commands.results.BrokenRef> broken =
                executor.execute(new CheckLinksCommand(tempDir.toString(), null));
        assertTrue(broken.isEmpty(), "link-clean after split: " + broken);
    }

    @Test
    void splitTopic_noMap_createsTopicsAndWarns() throws Exception {
        writeSplitFixture();
        RefactorResult result = executor.execute(new SplitTopicCommand(
                path("topics/guide.dita"), tempDir.toString(), null, true));
        assertTrue(result.applied());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("No map")),
                "warnings: " + result.warnings());
        assertTrue(file("topics/installing.dita").isFile());
    }

    @Test
    void splitTopic_noSections_throws() throws Exception {
        write("topics/flat.dita", "<concept id=\"f\"><title>F</title>"
                + "<conbody><p>No sections here.</p></conbody></concept>");
        CommandException e = assertThrows(CommandException.class, () ->
                executor.execute(new SplitTopicCommand(
                        path("topics/flat.dita"), tempDir.toString(), null, false)));
        assertTrue(e.getMessage().contains("No top-level <section>"), e.getMessage());
    }

    private static final String PLATFORM_SCHEME = "<subjectScheme>"
            + "<subjectdef keys=\"os\"><subjectdef keys=\"windows\"/>"
            + "<subjectdef keys=\"mac\"/><subjectdef keys=\"linux\"/></subjectdef>"
            + "<enumerationdef><attributedef name=\"platform\"/>"
            + "<subjectdef keyref=\"os\"/></enumerationdef></subjectScheme>";

    @Test
    void renameProfileValue_toUngovernedValue_warnsAgainstScheme() throws Exception {
        write("conditions.ditamap", PLATFORM_SCHEME);
        write("topics/a.dita", "<concept id=\"a\"><title>A</title><conbody>"
                + "<p platform=\"macos\">x</p></conbody></concept>");

        // macos -> makos: still not a controlled value -> warn.
        RefactorResult bad = executor.execute(new RenameProfileValueCommand(
                "platform", "macos", "makos", tempDir.toString(), false));
        assertTrue(bad.warnings().stream().anyMatch(w ->
                        w.contains("controlled value") && w.contains("makos")),
                "warns rename-to-ungoverned: " + bad.warnings());

        // macos -> mac: mac IS controlled -> no scheme warning (the correct fix).
        RefactorResult good = executor.execute(new RenameProfileValueCommand(
                "platform", "macos", "mac", tempDir.toString(), false));
        assertTrue(good.warnings().stream().noneMatch(w -> w.contains("controlled value")),
                "no scheme warning when fixing to a controlled value: " + good.warnings());
    }

    @Test
    void renameProfileValue_controlledValue_alsoRenamesSubjectKey() throws Exception {
        write("conditions.ditamap", PLATFORM_SCHEME);
        write("topics/a.dita", "<concept id=\"a\"><title>A</title><conbody>"
                + "<p platform=\"mac\">x</p></conbody></concept>");

        // Renaming a controlled value also renames its <subjectdef keys="mac">.
        RefactorResult dry = executor.execute(new RenameProfileValueCommand(
                "platform", "mac", "macos", tempDir.toString(), false));
        assertTrue(dry.edits().stream().anyMatch(e ->
                        e.file().endsWith("conditions.ditamap")
                        && "keys".equals(e.attribute()) && "macos".equals(e.newValue())),
                "scheme subjectdef key included: " + dry.edits());

        RefactorResult applied = executor.execute(new RenameProfileValueCommand(
                "platform", "mac", "macos", tempDir.toString(), true));
        assertTrue(applied.applied());
        assertTrue(Files.readString(file("conditions.ditamap").toPath()).contains("keys=\"macos\""),
                "scheme updated on disk");
    }

    // -------------------------------------------------------------------------

    private String path(String rel) {
        return tempDir.resolve(rel).toString();
    }

    private File file(String rel) {
        return tempDir.resolve(rel).toFile();
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
