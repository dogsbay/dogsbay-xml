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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild;
import com.dogsbay.xml.dita.BuildStep;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The step times of a project's builds, one JSON line per deliverable build in
 * {@code <project>/.dogsbay/build-times/builds.jsonl}, so builds can be compared over
 * time and across build engines, should the preprocessing one ever change. The
 * folder carries a {@code .gitignore}: the times are this machine's.
 */
public final class BuildTimes {

    private static final Logger LOG = LoggerFactory.getLogger(BuildTimes.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private BuildTimes() {}

    /** The folder the times are kept in. */
    public static Path folder(Path root) {
        return root.resolve(".dogsbay").resolve("build-times");
    }

    /** The JSON Lines file of a project's builds. */
    public static Path file(Path root) {
        return folder(root).resolve("builds.jsonl");
    }

    /** Append a build's times; a build without steps (it did not run) is not recorded. Never throws. */
    public static void append(Path root, DeliverableBuild build) {
        if (build.steps().isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(folder(root));
            Path ignore = folder(root).resolve(".gitignore");
            if (!Files.exists(ignore)) {
                Files.writeString(ignore, "*\n", StandardCharsets.UTF_8);
            }
            Files.writeString(file(root), line(build, Instant.now()) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not record build times in {}: {}", file(root), e.getMessage());
        }
    }

    /** One build as a JSON line. */
    static String line(DeliverableBuild build, Instant at) throws IOException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("time", at.toString());
        row.put("deliverable", build.name());
        row.put("transtype", build.transtype());
        row.put("engine", build.engine());
        row.put("success", build.success());
        row.put("totalMillis", build.totalMillis());
        row.put("steps", steps(build.steps()));
        return JSON.writeValueAsString(row);
    }

    private static List<Map<String, Object>> steps(List<BuildStep> steps) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (BuildStep step : steps) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("name", step.name());
            s.put("millis", step.millis());
            if (!step.stages().isEmpty()) {
                s.put("stages", steps(step.stages()));
            }
            out.add(s);
        }
        return out;
    }
}
