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

import java.util.List;

import com.dogsbay.xml.dita.BuildStep;
import com.dogsbay.xml.dita.BuildSteps;
import com.dogsbay.xml.dita.DitaOtMessage;

/**
 * Build result for one DITA-project deliverable: where it was built, its
 * transtype, whether it succeeded, and any diagnostics produced.
 *
 * @param name      the deliverable name
 * @param transtype the output type built (e.g. {@code html5})
 * @param outputDir the directory the output was written to (absolute)
 * @param success   true when DITA-OT produced no ERROR/FATAL diagnostics
 * @param messages  the captured diagnostics (errors and warnings), in log order
 * @param tempDir   where DITA-OT's temporary files were kept (absolute), or null
 *                  when the build did not keep them
 * @param steps     how long each step of the build took (Preprocessing, Converting to
 *                  dogsbay Markdown, ...), in order; empty when the build did not run
 * @param engine    what ran the build ("DITA-OT 4.3.5 (Java)"), or null
 */
public record DeliverableBuild(
    String name,
    String transtype,
    String outputDir,
    boolean success,
    List<DitaOtMessage> messages,
    String tempDir,
    List<BuildStep> steps,
    String engine
) {
    public DeliverableBuild {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /** A build without step times. */
    public DeliverableBuild(String name, String transtype, String outputDir, boolean success,
            List<DitaOtMessage> messages, String tempDir) {
        this(name, transtype, outputDir, success, messages, tempDir, List.of(), null);
    }

    /** A build that did not keep its temporary files. */
    public DeliverableBuild(String name, String transtype, String outputDir, boolean success,
            List<DitaOtMessage> messages) {
        this(name, transtype, outputDir, success, messages, null, List.of(), null);
    }

    /** The build's total time in milliseconds (the sum of its steps). */
    public long totalMillis() {
        return BuildSteps.total(steps);
    }
}
