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

import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.IndexAuditCommand;
import com.dogsbay.dogsbayaieditor.commands.results.IndexAuditResult;

@Command(name = "index-audit",
        description = "Survey the DITA index (indexterm): entry inventory with "
                + "frequencies, topics with no index terms, and dangling index-see redirects.")
class IndexAuditCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Project root directory")
    private String root;

    @Option(names = {"-s", "--scope"}, description = "Scope: 'map:<p>', 'glob:<p>', or 'root'")
    private String scope;

    @Override
    public Integer call() throws Exception {
        IndexAuditResult r = new HeadlessExecutor()
                .execute(new IndexAuditCommand(root, scope));
        System.out.println("Index entries (" + r.entries().size() + "):");
        for (var e : r.entries()) {
            System.out.printf("  %-40s %d%n", e.path(), e.count());
        }
        if (!r.missing().isEmpty()) {
            System.out.println(r.missing().size() + " topic(s) with no index terms.");
        }
        if (!r.dangling().isEmpty()) {
            System.out.println("Dangling see/see-also (" + r.dangling().size() + "):");
            for (var d : r.dangling()) {
                System.out.printf("  %s: '%s' %s '%s' (no such entry)%n",
                        d.file(), d.from(), d.also() ? "see-also" : "see", d.target());
            }
        }
        return r.dangling().isEmpty() ? 0 : 1;
    }
}
