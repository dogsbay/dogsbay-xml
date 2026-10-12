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

import com.dogsbay.dogsbayaieditor.commands.results.ReportResult;

/**
 * Write a standalone HTML page: a template filled with JSON. The page is one
 * file that works offline, so it can be opened from disk, attached or published
 * as it is.
 *
 * <p>The data is the output of a read-only command. Callers that name a source
 * command (the MCP tool and the CLI) run it first and pass its JSON here, so a
 * report can never disagree with the tool it came from, and a new kind of page
 * is a template, not a new extractor.
 *
 * @param root     project root: resolves a project template path; may be null
 * @param template a built-in template name ({@code relationship-map}, {@code health}) or a template path
 * @param output   where to write the page
 * @param data     the JSON document the page shows
 * @param source   what produced the data, recorded in the page (for example the command and its arguments); may be null
 */
public record RenderReportCommand(
    String root,
    String template,
    String output,
    String data,
    String source
) implements Command<ReportResult> {}
