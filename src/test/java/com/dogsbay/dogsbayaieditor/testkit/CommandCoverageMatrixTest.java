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

package com.dogsbay.dogsbayaieditor.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.commands.Command;

/**
 * Tier 1 coverage gate for the comprehensive test suite (see
 * {@code plans/comprehensive-ui-test-suite.md}).
 *
 * <p>The command engine is the product's control plane: nearly every feature
 * maps to a {@link Command}. This test reflects over the sealed interface's
 * {@code permits} list and asserts that <em>every</em> command is assigned an
 * owning test tier in {@link #MATRIX}. That makes "comprehensive" measurable:
 * when someone adds a new command, this test fails until they declare which
 * tier covers it — a forcing function against silent coverage gaps.
 */
class CommandCoverageMatrixTest {

    /** Which test tier owns the primary coverage for a command. */
    enum Tier {
        /** Headless / running-editor functional, asserted on typed results. */
        T1_FUNCTIONAL,
        /** In-process GUI render + golden image (offscreen, no display). */
        T2_RENDER,
        /** Real input pipeline via Robot under Xvfb. */
        T3_ROBOT,
        /** Computer-use end-to-end journey (MCP-agent first). */
        T4_E2E
    }

    /**
     * Primary owning tier per command. Keyed by {@code Class.getSimpleName()}.
     * A command may be exercised in several tiers; this records the one that
     * owns its definitive coverage. Keep in sync with the sealed interface —
     * the test below fails if any permitted command is missing here.
     */
    static final Map<String, Tier> MATRIX = Map.ofEntries(
        // ── Headless XML processing ──────────────────────────────────────
        Map.entry("ValidateCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ParseCommand", Tier.T1_FUNCTIONAL),
        Map.entry("TransformCommand", Tier.T1_FUNCTIONAL),
        Map.entry("QueryCommand", Tier.T1_FUNCTIONAL),
        Map.entry("FormatCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ReflowCommand", Tier.T1_FUNCTIONAL),
        Map.entry("InfoCommand", Tier.T1_FUNCTIONAL),

        // ── Document lifecycle (running editor) ──────────────────────────
        Map.entry("OpenCommand", Tier.T1_FUNCTIONAL),
        Map.entry("CloseCommand", Tier.T1_FUNCTIONAL),
        Map.entry("SaveCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ListDocumentsCommand", Tier.T1_FUNCTIONAL),
        Map.entry("NewDocumentCommand", Tier.T1_FUNCTIONAL),
        Map.entry("GetContentCommand", Tier.T1_FUNCTIONAL),
        Map.entry("SetContentCommand", Tier.T1_FUNCTIONAL),
        Map.entry("GetSelectionCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ReplaceSelectionCommand", Tier.T1_FUNCTIONAL),
        Map.entry("GetOutlineCommand", Tier.T1_FUNCTIONAL),
        Map.entry("GetErrorsCommand", Tier.T1_FUNCTIONAL),

        // ── Caret / element selection ────────────────────────────────────
        // Ported from the legacy Rhino scripting API. The offset-to-line/column
        // maths and the headless refusal are covered by CaretCommandsTest; the
        // caret actually moving in a live buffer is a render-tier concern.
        Map.entry("GotoLineCommand", Tier.T1_FUNCTIONAL),
        Map.entry("GetCursorCommand", Tier.T1_FUNCTIONAL),
        Map.entry("SetCursorCommand", Tier.T1_FUNCTIONAL),
        Map.entry("SelectElementCommand", Tier.T1_FUNCTIONAL),

        // ── Projects ─────────────────────────────────────────────────────
        Map.entry("SearchProjectCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ListProjectFilesCommand", Tier.T1_FUNCTIONAL),
        Map.entry("CreateProjectCommand", Tier.T1_FUNCTIONAL),
        Map.entry("OpenProjectCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ListProjectsCommand", Tier.T1_FUNCTIONAL),
        Map.entry("GetProjectCommand", Tier.T1_FUNCTIONAL),

        // ── Keys / conref / links ────────────────────────────────────────
        Map.entry("WhereUsedCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ListKeysCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ResolveKeyCommand", Tier.T1_FUNCTIONAL),
        Map.entry("CheckLinksCommand", Tier.T1_FUNCTIONAL),
        Map.entry("HealthCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ValidateProjectCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ProjectHealthCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ConrefAuditCommand", Tier.T1_FUNCTIONAL),
        Map.entry("SchematronProjectCommand", Tier.T1_FUNCTIONAL),
        Map.entry("SchematronCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ValidateDeliverablesCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ValidateConditionsCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ListSubjectsCommand", Tier.T1_FUNCTIONAL),
        Map.entry("MetadataAuditCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ExportMetadataSchematronCommand", Tier.T1_FUNCTIONAL),
        Map.entry("MetadataSetCommand", Tier.T1_FUNCTIONAL),
        Map.entry("EditMapCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ReltableAuditCommand", Tier.T1_FUNCTIONAL),
        Map.entry("EditReltableCommand", Tier.T1_FUNCTIONAL),
        Map.entry("KeywordAuditCommand", Tier.T1_FUNCTIONAL),
        Map.entry("IndexAuditCommand", Tier.T1_FUNCTIONAL),
        Map.entry("GlossaryAuditCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ConrefPushAuditCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ChunkAuditCommand", Tier.T1_FUNCTIONAL),
        Map.entry("SpecializationInfoCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ListBranchesCommand", Tier.T1_FUNCTIONAL),
        // Review proposals and agent sessions (ReviewCommandsTest; sessions/agents need the editor)
        Map.entry("ReviewListCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ReviewAcceptCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ReviewRejectCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ReviewCommentCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ListSessionsCommand", Tier.T1_FUNCTIONAL),
        Map.entry("AuditLogCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ListAgentsCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ValidateDeepCommand", Tier.T1_FUNCTIONAL),
        Map.entry("BuildDeliverablesCommand", Tier.T1_FUNCTIONAL),
        Map.entry("KeyifyCommand", Tier.T1_FUNCTIONAL),
        Map.entry("InlineKeyCommand", Tier.T1_FUNCTIONAL),
        Map.entry("ExtractConrefCommand", Tier.T1_FUNCTIONAL),
        Map.entry("CreateKeydefCommand", Tier.T1_FUNCTIONAL),
        Map.entry("InlineConrefCommand", Tier.T1_FUNCTIONAL),
        Map.entry("MergeKeydefsCommand", Tier.T1_FUNCTIONAL),

        // ── Refactoring ──────────────────────────────────────────────────
        Map.entry("RenameFileCommand", Tier.T1_FUNCTIONAL),
        Map.entry("RenameKeyCommand", Tier.T1_FUNCTIONAL),
        Map.entry("DeleteFileCommand", Tier.T1_FUNCTIONAL),
        Map.entry("RetargetCommand", Tier.T1_FUNCTIONAL),
        Map.entry("RenameElementIdCommand", Tier.T1_FUNCTIONAL),
        Map.entry("RenameProfileValueCommand", Tier.T1_FUNCTIONAL),
        Map.entry("SplitTopicCommand", Tier.T1_FUNCTIONAL),

        // ── Author (data assertions; visuals covered in T2/T3) ───────────
        Map.entry("AuthorOutlineCommand", Tier.T1_FUNCTIONAL),
        Map.entry("AuthorInsertBlockCommand", Tier.T1_FUNCTIONAL),
        Map.entry("AuthorSetTextCommand", Tier.T1_FUNCTIONAL),
        Map.entry("AuthorIssuesCommand", Tier.T1_FUNCTIONAL),

        // ── Visual / view (golden images) ────────────────────────────────
        Map.entry("RenderPreviewCommand", Tier.T2_RENDER),
        Map.entry("AuthorSwitchCommand", Tier.T2_RENDER),
        Map.entry("ScreenshotCommand", Tier.T2_RENDER),

        // ── Sidebars / map (running editor) ──────────────────────────────
        Map.entry("ListSidebarsCommand", Tier.T1_FUNCTIONAL),
        Map.entry("SwitchSidebarCommand", Tier.T1_FUNCTIONAL),
        Map.entry("OpenDitaMapCommand", Tier.T1_FUNCTIONAL)
    );

    @Test
    @DisplayName("every Command in the sealed permits list has an owning test tier")
    void everyCommandHasAnOwningTier() {
        Class<?>[] permitted = Command.class.getPermittedSubclasses();
        assertThat(permitted)
                .as("Command must be a sealed interface with permitted subclasses")
                .isNotEmpty();

        List<String> uncovered = Arrays.stream(permitted)
                .map(Class::getSimpleName)
                .filter(name -> !MATRIX.containsKey(name))
                .sorted()
                .toList();

        assertThat(uncovered)
                .as("commands missing from the coverage matrix — assign each a Tier "
                        + "in CommandCoverageMatrixTest.MATRIX (see plans/comprehensive-ui-test-suite.md)")
                .isEmpty();
    }

    @Test
    @DisplayName("the coverage matrix has no entries for commands that no longer exist")
    void matrixHasNoStaleEntries() {
        List<String> live = Arrays.stream(Command.class.getPermittedSubclasses())
                .map(Class::getSimpleName)
                .toList();

        List<String> stale = MATRIX.keySet().stream()
                .filter(name -> !live.contains(name))
                .sorted()
                .toList();

        assertThat(stale)
                .as("matrix references commands removed from the sealed interface")
                .isEmpty();
    }
}
