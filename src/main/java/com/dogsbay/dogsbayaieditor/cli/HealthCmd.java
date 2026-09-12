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

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.dogsbay.dogsbayaieditor.commands.HealthCommand;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.results.BrokenRef;
import com.dogsbay.dogsbayaieditor.commands.results.HealthReport;
import com.dogsbay.dogsbayaieditor.commands.results.KeyInfo;

@Command(name = "health",
        description = "Project reuse-health report: broken refs, undefined/unused keys, "
                + "orphan topics. Exits 1 when anything is found.")
class HealthCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Project root directory to scan")
    private String root;

    @Option(names = {"-m", "--map"},
            description = "Root map — enables key analysis (undefined/unused keys, via-key orphan checks)")
    private String rootMap;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        HealthReport report = executor.execute(new HealthCommand(root, rootMap));

        if (report.isClean()) {
            System.out.println("Project is clean: no broken references, undefined keys, "
                    + "unused keys, or orphan topics.");
            return 0;
        }

        if (!report.brokenReferences().isEmpty()) {
            System.out.println("Broken references (" + report.brokenReferences().size() + "):");
            for (BrokenRef ref : report.brokenReferences()) {
                System.out.printf("  %s:%d  @%s=\"%s\"%n",
                        ref.source(), ref.line(), ref.attribute(), ref.value());
            }
        }
        if (!report.undefinedKeys().isEmpty()) {
            System.out.println("Undefined keys (" + report.undefinedKeys().size() + "):");
            for (BrokenRef ref : report.undefinedKeys()) {
                System.out.printf("  %s:%d  %s%n", ref.source(), ref.line(), ref.reason());
            }
        }
        if (!report.unusedKeys().isEmpty()) {
            System.out.println("Unused keys (" + report.unusedKeys().size() + "):");
            for (KeyInfo key : report.unusedKeys()) {
                System.out.printf("  %s  [%s:%d]%n", key.name(), key.definedIn(), key.line());
            }
        }
        if (!report.orphanTopics().isEmpty()) {
            System.out.println("Orphan topics (" + report.orphanTopics().size() + "):");
            for (String orphan : report.orphanTopics()) {
                System.out.println("  " + orphan);
            }
        }
        return 1;
    }
}
