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
package com.dogsbay.agent.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** A chat transcript as a Markdown file: a header with the facts, then the text as shown. */
public final class TranscriptExport {

    private TranscriptExport() {
    }

    public static String markdown(String title, String agent, String tier, String project, Instant at,
            String transcript) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title == null || title.isBlank() ? agent : title).append("\n\n");
        sb.append("- Agent: ").append(agent).append('\n');
        if (tier != null) {
            sb.append("- Tier: ").append(tier).append('\n');
        }
        if (project != null) {
            sb.append("- Project: ").append(project).append('\n');
        }
        sb.append("- Exported: ").append(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .format(at.atZone(ZoneId.systemDefault()))).append("\n\n");
        sb.append("---\n\n");
        sb.append(transcript == null ? "" : transcript.strip()).append('\n');
        return sb.toString();
    }

    /** A file name for the export: the title, safe for any file system, with a date. */
    public static String fileName(String title, Instant at) {
        String base = (title == null || title.isBlank() ? "agent-session" : title)
                .replaceAll("[^\\p{L}\\p{N}._-]+", "-").replaceAll("^-+|-+$", "");
        String day = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(at.atZone(ZoneId.systemDefault()));
        return (base.isEmpty() ? "agent-session" : base) + "-" + day + ".md";
    }
}
