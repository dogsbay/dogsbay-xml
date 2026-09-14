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

package com.dogsbay.dogsbayaieditor.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Main entry point for the dogsbay CLI.
 *
 * Usage: dogsbay-xml <command> [args...]
 */
@Command(
    name = "dogsbay-xml",
    mixinStandardHelpOptions = true,
    versionProvider = DogsBayCli.Version.class,
    description = "DogsBay XML — command-line tools for XML processing",
    subcommands = {
        // Headless commands
        ValidateCmd.class,
        ParseCmd.class,
        InfoCmd.class,
        FormatCmd.class,
        ReflowCmd.class,
        QueryCmd.class,
        TransformCmd.class,
        WhereUsedCmd.class,
        KeysCmd.class,
        CheckLinksCmd.class,
        PreviewCmd.class,
        ReportCmd.class,
        ProjectGraphCmd.class,
        HealthCmd.class,
        ProjectHealthCmd.class,
        ValidateProjectCmd.class,
        ConrefAuditCmd.class,
        SchematronProjectCmd.class,
        SchematronCmd.class,
        ReviewCmd.class,
        SessionsCmd.class,
        AgentsCmd.class,
        AuditLogCmd.class,
        ValidateDeliverablesCmd.class,
        ValidateConditionsCmd.class,
        ListSubjectsCmd.class,
        MetadataAuditCmd.class,
        MetadataSetCmd.class,
        MetadataExportSchematronCmd.class,
        EditMapCmd.class,
        ReltableAuditCmd.class,
        EditReltableCmd.class,
        ListBranchesCmd.class,
        KeywordAuditCmd.class,
        IndexAuditCmd.class,
        GlossaryAuditCmd.class,
        ConrefPushAuditCmd.class,
        ChunkAuditCmd.class,
        SpecializationInfoCmd.class,
        ValidateDeepCmd.class,
        BuildCmd.class,
        RenameFileCmd.class,
        RenameKeyCmd.class,
        DeleteFileCmd.class,
        RetargetCmd.class,
        KeyifyCmd.class,
        InlineKeyCmd.class,
        ExtractConrefCmd.class,
        CreateKeydefCmd.class,
        InlineConrefCmd.class,
        RenameElementIdCmd.class,
        MergeKeydefsCmd.class,
        RenameProfileValueCmd.class,
        SplitTopicCmd.class,
        // Editor commands (require running editor)
        OpenCmd.class,
        CloseCmd.class,
        ListCmd.class,
        SaveCmd.class,
        SelectionCmd.class,
        GotoLineCmd.class,
        CursorCmd.class,
        SelectElementCmd.class,
        WrapCmd.class,
        AuthorCmd.class,
        ScreenshotCmd.class,
        // Project management
        ProjectCmd.class,
        // Meta
        StatusCmd.class,
    }
)
public class DogsBayCli implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new DogsBayCli())
            .setExecutionExceptionHandler((ex, cmd, parseResult) -> {
                cmd.getErr().println("Error: " + ex.getMessage());
                return 2;
            })
            .execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    /**
     * The version, from the jar manifest rather than a literal. A hardcoded
     * string here reported 0.1.0 while the product was at 4.0.0-beta.1, which
     * is worse than no version at all when someone is chasing a
     * version-specific problem. Falls back to the same literal the About
     * dialog uses, for runs from classes on disk where there is no manifest.
     */
    static class Version implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            String v = DogsBayCli.class.getPackage().getImplementationVersion();
            // No manifest means classes on disk — bin/dogsbay-xml, or an IDE.
            // Naming a version there would be the same lie in a new place: the
            // literal goes stale at the next release and reports the old number
            // for a build that is not it.
            return new String[] {v != null ? "dogsbay-xml " + v : "dogsbay-xml (development build)"};
        }
    }
}
