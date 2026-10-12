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

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild;
import com.dogsbay.xml.dita.BuildStep;

/**
 * A build's step times as aligned text, for the Build Deliverables summary and the
 * {@code build} command:
 *
 * <pre>
 *   Loading DITA-OT                  0.3 s
 *   Preprocessing                   12.1 s
 *     topic-conref                   3.2 s
 *   Building the site               21.4 s
 *   Total (DITA-OT 4.3.5 (Java))    41.2 s
 * </pre>
 *
 * A step's stages are listed slowest first, leaving out those under {@link #MIN_STAGE_MILLIS}.
 */
public final class BuildStepReport {

    /** Stages quicker than this are left out: they are noise next to the steps. */
    public static final long MIN_STAGE_MILLIS = 10;

    private static final int NAME_WIDTH = 40;

    private BuildStepReport() {}

    /** The steps of a build, one per line, each line starting with {@code indent}; "" when it has none. */
    public static String format(DeliverableBuild build, String indent) {
        if (build.steps().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (BuildStep step : build.steps()) {
            line(sb, indent, "", step.name(), step.millis());
            List<BuildStep> stages = step.stages().stream()
                    .filter(s -> s.millis() >= MIN_STAGE_MILLIS)
                    .sorted(Comparator.comparingLong(BuildStep::millis).reversed())
                    .toList();
            for (BuildStep stage : stages) {
                line(sb, indent, "  ", stage.name(), stage.millis());
            }
        }
        line(sb, indent, "", "Total" + (build.engine() != null ? " (" + build.engine() + ")" : ""), build.totalMillis());
        return sb.toString();
    }

    /** One line: the name after the indent (and a stage's extra indent), the time in a column. */
    private static void line(StringBuilder sb, String indent, String extra, String name, long millis) {
        String label = extra + name;
        sb.append(indent).append(label);
        sb.append(" ".repeat(Math.max(1, NAME_WIDTH - label.length())));
        sb.append(String.format("%9s", duration(millis))).append('\n');
    }

    /** A duration for people: "340 ms", "12.1 s", "2 min 5 s". */
    public static String duration(long millis) {
        if (millis < 1000) {
            return millis + " ms";
        }
        if (millis < 60_000) {
            return String.format(Locale.ROOT, "%.1f s", millis / 1000.0);
        }
        long seconds = Math.round(millis / 1000.0);
        return (seconds / 60) + " min " + (seconds % 60) + " s";
    }
}
