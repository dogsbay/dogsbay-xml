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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * What a mutating command touches, declared per command class so the write
 * gate can lease, contain and conflict-check it. The switch is over the
 * sealed {@link Command} hierarchy with no default: adding a command without
 * classifying it here is a compile error, which is the coverage guarantee.
 *
 * @param scope  how the command changes things
 * @param files  the files it changes; empty when the target is the active
 *               document ({@link #activeDocument()} is then true) or when the
 *               command works on a whole tree rooted at {@link #root()}
 * @param root   the tree a project-wide command may change, or null
 * @param activeDocument true when a null file component means "the document
 *               that is active in the editor"
 */
public record CommandTargets(Scope scope, List<Path> files, Path root, boolean activeDocument) {

    public enum Scope {
        /** Read-only: nothing to gate. */
        NONE,
        /** Edits an open editor buffer; conflict-checked against the agent's last read. */
        BUFFER,
        /** Writes named files on disk. */
        FILES,
        /** Rewrites files under a root, chosen by the command itself (refactorings, builds). */
        TREE
    }

    public static final CommandTargets NONE = new CommandTargets(Scope.NONE, List.of(), null, false);

    public boolean isMutating() {
        return scope != Scope.NONE;
    }

    /** Everything the gate should contain: named files plus the root, if any. */
    public List<Path> allPaths() {
        List<Path> out = new ArrayList<>(files);
        if (root != null) {
            out.add(root);
        }
        return out;
    }

    public static CommandTargets of(Command<?> command) {
        return switch (command) {
            // Buffer edits (file null = the active document)
            case SetContentCommand c -> buffer(c.file());
            case ReplaceSelectionCommand c -> buffer(null);
            case AuthorSetTextCommand c -> buffer(c.file());
            case AuthorInsertBlockCommand c -> buffer(c.file());
            case SaveCommand c -> bufferState(c.file());
            case CloseCommand c -> bufferState(c.file());

            // Named files
            case NewDocumentCommand c -> files(c.file());
            case ScreenshotCommand c -> files(path(c.output()));
            case TransformCommand c -> files(c.output());
            case FormatCommand c -> files(c.output() != null ? c.output() : c.file());
            case ReflowCommand c -> files(c.output() != null ? c.output() : c.file());
            case ExportMetadataSchematronCommand c -> files(path(c.output()));
            case CreateProjectCommand c -> files(c.folderPath());
            // Writes a render artefact to a caller-chosen path; a plain read when there is none.
            case RenderPreviewCommand c -> c.output() == null || c.output().isBlank() ? NONE : files(path(c.output()));
            // A report page is always written to the path the caller chose.
            case RenderReportCommand c -> files(path(reportOutput(c)));
            // Moves the project root, which is the containment boundary: state, not a read.
            case OpenProjectCommand c -> new CommandTargets(Scope.FILES, List.of(), null, false);
            case EditMapCommand c -> files(path(c.map()));
            case EditReltableCommand c -> files(path(c.map()));

            // Project-wide rewrites under a root
            case RenameFileCommand c -> tree(c.root(), c.file(), c.newPath());
            case DeleteFileCommand c -> tree(c.root(), c.file());
            case RetargetCommand c -> tree(c.root(), c.from(), c.to());
            case RenameKeyCommand c -> tree(c.root());
            case KeyifyCommand c -> tree(c.root(), c.file(), c.map());
            case InlineKeyCommand c -> tree(c.root(), c.map());
            case ExtractConrefCommand c -> tree(null, c.file(), c.to());
            case CreateKeydefCommand c -> tree(c.replaceRoot(), c.map());
            case InlineConrefCommand c -> tree(c.root(), c.file());
            case RenameElementIdCommand c -> tree(c.root(), c.file());
            case MergeKeydefsCommand c -> tree(null, c.rootMap());
            case RenameProfileValueCommand c -> tree(c.root());
            case SplitTopicCommand c -> tree(c.root(), c.file(), c.map());
            case MetadataSetCommand c -> tree(c.root());
            case BuildDeliverablesCommand c -> tree(c.outputBaseDir() != null ? c.outputBaseDir() : c.root());

            // Read-only
            case ValidateCommand c -> NONE;
            case ParseCommand c -> NONE;
            case InfoCommand c -> NONE;
            case QueryCommand c -> NONE;
            case WhereUsedCommand c -> NONE;
            case ListKeysCommand c -> NONE;
            case ResolveKeyCommand c -> NONE;
            case CheckLinksCommand c -> NONE;
            case HealthCommand c -> NONE;
            case SearchProjectCommand c -> NONE;
            case ListProjectFilesCommand c -> NONE;
            case ListProjectsCommand c -> NONE;
            case GetProjectCommand c -> NONE;
            case ListDocumentsCommand c -> NONE;
            case GetContentCommand c -> NONE;
            case GetSelectionCommand c -> NONE;
            case GetOutlineCommand c -> NONE;
            case GetErrorsCommand c -> NONE;
            case GetCursorCommand c -> NONE;
            case GotoLineCommand c -> NONE;
            case SetCursorCommand c -> NONE;
            case SelectElementCommand c -> NONE;
            case AuthorOutlineCommand c -> NONE;
            case AuthorIssuesCommand c -> NONE;
            case AuthorSwitchCommand c -> NONE;
            case ListSidebarsCommand c -> NONE;
            case SwitchSidebarCommand c -> NONE;
            case OpenCommand c -> NONE;
            case OpenDitaMapCommand c -> NONE;
            case ValidateProjectCommand c -> NONE;
            case ProjectHealthCommand c -> NONE;
            case ProjectGraphCommand c -> NONE;
            case ConrefAuditCommand c -> NONE;
            case SchematronProjectCommand c -> NONE;
            case SchematronCommand c -> NONE;
            case ValidateDeliverablesCommand c -> NONE;
            case ValidateDeepCommand c -> NONE;
            case ValidateConditionsCommand c -> NONE;
            case ListSubjectsCommand c -> NONE;
            case MetadataAuditCommand c -> NONE;
            case ReltableAuditCommand c -> NONE;
            case KeywordAuditCommand c -> NONE;
            case IndexAuditCommand c -> NONE;
            case GlossaryAuditCommand c -> NONE;
            case ConrefPushAuditCommand c -> NONE;
            case ChunkAuditCommand c -> NONE;
            case SpecializationInfoCommand c -> NONE;
            case ListBranchesCommand c -> NONE;
            case ReviewListCommand c -> NONE;
            case ListSessionsCommand c -> NONE;
            case ListAgentsCommand c -> NONE;
            case AuditLogCommand c -> NONE;

            // Review decisions and comments edit one document, but never its running text:
            // leased, not conflict-checked, so an agent may comment on a file it has not read.
            case ReviewAcceptCommand c -> bufferState(path(c.file()));
            case ReviewRejectCommand c -> bufferState(path(c.file()));
            case ReviewCommentCommand c -> bufferState(path(c.file()));
        };
    }

    private static CommandTargets buffer(Path file) {
        return file == null
                ? new CommandTargets(Scope.BUFFER, List.of(), null, true)
                : new CommandTargets(Scope.BUFFER, List.of(file), null, false);
    }

    /** Save/close touch a buffer's state, not its text: leased, never conflict-checked. */
    private static CommandTargets bufferState(Path file) {
        return file == null
                ? new CommandTargets(Scope.FILES, List.of(), null, true)
                : new CommandTargets(Scope.FILES, List.of(file), null, false);
    }

    private static CommandTargets files(Path... paths) {
        List<Path> out = new ArrayList<>();
        for (Path p : paths) {
            if (p != null) {
                out.add(p);
            }
        }
        return new CommandTargets(Scope.FILES, out, null, false);
    }

    private static CommandTargets tree(String root, String... files) {
        List<Path> out = new ArrayList<>();
        for (String f : files) {
            Path p = path(f);
            if (p != null) {
                out.add(p);
            }
        }
        return new CommandTargets(Scope.TREE, out, path(root), false);
    }

    private static Path path(String s) {
        return s == null || s.isBlank() ? null : Path.of(s);
    }

    /** A report's output as the executor writes it: a relative path is relative to the project root. */
    private static String reportOutput(RenderReportCommand c) {
        if (c.output() == null || c.output().isBlank() || c.root() == null || c.root().isBlank()
                || Path.of(c.output()).isAbsolute()) {
            return c.output();
        }
        return Path.of(c.root()).resolve(c.output()).toString();
    }
}
