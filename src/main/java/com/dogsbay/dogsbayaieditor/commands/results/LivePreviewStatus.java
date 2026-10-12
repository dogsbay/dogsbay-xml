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

package com.dogsbay.dogsbayaieditor.commands.results;

import java.io.File;
import java.util.List;

import com.dogsbay.dogsbayaieditor.dita.live.LivePreview;
import com.dogsbay.xml.dita.DitaOtMessage;

/**
 * A live preview's state, for an agent polling it.
 *
 * @param deliverable  the deliverable previewed
 * @param state        STARTING, PUBLISHING, READY, READY_WITH_ERRORS, FAILED or STOPPED
 * @param message      what the preview is doing, or why it failed
 * @param url          the dev server's address, once it is running; else null
 * @param outputDir    the published site project's folder, once published; else null
 * @param lastPublish  the last (re)publish, or null before the first has finished
 * @param recentOutput the dev server's last output lines, oldest first
 * @param owner        who runs it: {@code editor}, {@code mcp} (a headless MCP server) or {@code cli} (a
 *                     background preview from the command line); null when none runs
 * @param pid          the process that runs it, or 0
 * @param log          the background preview's log file, or null
 */
public record LivePreviewStatus(
    String deliverable,
    String state,
    String message,
    String url,
    String outputDir,
    PublishSummary lastPublish,
    List<String> recentOutput,
    String owner,
    long pid,
    String log
) {
    /** How many of a publish's errors a summary lists. */
    static final int LISTED_ERRORS = 5;

    /**
     * A publish in brief; {@code build_deliverables} gives every diagnostic.
     *
     * @param success     no ERROR or FATAL diagnostics
     * @param errors      the ERROR and FATAL diagnostics
     * @param warnings    the WARN diagnostics (informational ones are not counted)
     * @param totalMillis the publish's time
     * @param firstErrors the first few errors' text
     */
    public record PublishSummary(boolean success, int errors, int warnings, long totalMillis, List<String> firstErrors) {
        public static PublishSummary of(DeliverableBuild build) {
            List<DitaOtMessage> errors = build.messages().stream().filter(DitaOtMessage::isError).toList();
            int warnings = (int) build.messages().stream()
                    .filter(m -> "WARN".equals(m.severity()) || "WARNING".equals(m.severity())).count();
            return new PublishSummary(build.success(), errors.size(), warnings,
                    build.totalMillis(), errors.stream().limit(LISTED_ERRORS).map(DitaOtMessage::message).toList());
        }
    }

    public static LivePreviewStatus of(LivePreview preview) {
        File out = preview.outputDir();
        DeliverableBuild build = preview.lastBuild();
        return new LivePreviewStatus(preview.deliverable().name(), preview.state().name(), preview.message(), preview.url(),
                out == null ? null : out.getAbsolutePath(), build == null ? null : PublishSummary.of(build),
                preview.recentOutput(), com.dogsbay.dogsbayaieditor.dita.live.PreviewRegistry.owner(),
                ProcessHandle.current().pid(), com.dogsbay.dogsbayaieditor.dita.live.PreviewRegistry.log());
    }

    /** A preview another process runs, as its registry entry describes it. */
    public static LivePreviewStatus of(com.dogsbay.dogsbayaieditor.dita.live.PreviewRegistry.Entry e) {
        PublishSummary publish = e.errors() == null ? null
                : new PublishSummary(e.errors() == 0, e.errors(), e.warnings() == null ? 0 : e.warnings(), 0, List.of());
        return new LivePreviewStatus(e.deliverable(), e.state(), e.message(), e.url(), e.outputDir(), publish, List.of(),
                e.owner(), e.pid(), e.log());
    }

    /** A deliverable with no running preview. */
    public static LivePreviewStatus stopped(String deliverable) {
        return new LivePreviewStatus(deliverable, LivePreview.State.STOPPED.name(), "No live preview is running",
                null, null, null, List.of(), null, 0, null);
    }
}
