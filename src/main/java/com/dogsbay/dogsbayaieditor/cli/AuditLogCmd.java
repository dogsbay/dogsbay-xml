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

import com.dogsbay.dogsbayaieditor.commands.AuditLogCommand;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.results.AuditEntryInfo;

@Command(name = "audit-log", description = "What agents did in a project: the .dogsbay/agent-audit log, newest first")
class AuditLogCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Project root (default: current directory)", defaultValue = ".")
    private String root;

    @Option(names = {"--identity"}, description = "Only this identity, e.g. ai:claude-acp")
    private String identity;

    @Option(names = {"--command"}, description = "Only this command, e.g. set-content")
    private String command;

    @Option(names = {"-n", "--limit"}, description = "At most this many entries (default 50; 0 for all)",
            defaultValue = "50")
    private int limit;

    @Override
    public Integer call() throws Exception {
        var entries = new HeadlessExecutor().execute(new AuditLogCommand(root, identity, command, limit));
        for (AuditEntryInfo e : entries) {
            System.out.printf("%s  %-24s %-18s %s%s%s%n", e.at(), e.identity(), e.command(),
                    e.dryRun() ? "dry run  " : "", e.outcome(),
                    e.files().isEmpty() ? "" : "  " + String.join(", ", e.files()));
        }
        System.out.println(entries.size() + " entr" + (entries.size() == 1 ? "y" : "ies") + ".");
        return 0;
    }
}
