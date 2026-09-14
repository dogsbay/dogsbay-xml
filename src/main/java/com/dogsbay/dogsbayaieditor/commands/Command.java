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

/**
 * Sealed interface for all editor commands. Each implementation is a record
 * with typed input fields and a typed result.
 *
 * <p>Commands are the single abstraction through which all external interfaces
 * (CLI, MCP, REST) and the embedded AI agent interact with editor services.</p>
 */
public sealed interface Command<R> permits
    ValidateCommand, ParseCommand, TransformCommand, QueryCommand,
    FormatCommand, ReflowCommand, InfoCommand,
    OpenCommand, CloseCommand, SaveCommand, ListDocumentsCommand,
    GetContentCommand, SetContentCommand,
    GetSelectionCommand, ReplaceSelectionCommand,
    GotoLineCommand, GetCursorCommand, SetCursorCommand, SelectElementCommand,
    GetOutlineCommand, GetErrorsCommand,
    SearchProjectCommand, ListProjectFilesCommand,
    CreateProjectCommand, OpenProjectCommand, ListProjectsCommand, GetProjectCommand,
    ListSidebarsCommand, SwitchSidebarCommand, OpenDitaMapCommand,
    NewDocumentCommand,
    WhereUsedCommand, ListKeysCommand, ResolveKeyCommand,
    CheckLinksCommand, RenderPreviewCommand, RenderReportCommand, ProjectGraphCommand,
    HealthCommand, RenameFileCommand, RenameKeyCommand, DeleteFileCommand,
    RetargetCommand, KeyifyCommand, InlineKeyCommand, ExtractConrefCommand,
    CreateKeydefCommand, InlineConrefCommand, RenameElementIdCommand,
    MergeKeydefsCommand, RenameProfileValueCommand, SplitTopicCommand,
    AuthorSwitchCommand, AuthorOutlineCommand, AuthorInsertBlockCommand,
    AuthorSetTextCommand, AuthorIssuesCommand, ScreenshotCommand,
    ValidateProjectCommand, ProjectHealthCommand, ConrefAuditCommand,
    SchematronProjectCommand, SchematronCommand, ValidateDeliverablesCommand, ValidateDeepCommand,
    BuildDeliverablesCommand,
    ValidateConditionsCommand, ListSubjectsCommand,
    MetadataAuditCommand, ExportMetadataSchematronCommand, MetadataSetCommand,
    EditMapCommand, ReltableAuditCommand, EditReltableCommand,
    KeywordAuditCommand, IndexAuditCommand, GlossaryAuditCommand,
    ConrefPushAuditCommand, ChunkAuditCommand, SpecializationInfoCommand,
    ListBranchesCommand,
    ReviewListCommand, ReviewAcceptCommand, ReviewRejectCommand, ReviewCommentCommand,
    ListSessionsCommand, ListAgentsCommand, AuditLogCommand { }
