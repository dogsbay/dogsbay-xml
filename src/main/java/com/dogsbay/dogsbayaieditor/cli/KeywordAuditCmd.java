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
import com.dogsbay.dogsbayaieditor.commands.KeywordAuditCommand;
import com.dogsbay.dogsbayaieditor.commands.results.KeywordAuditResult;

@Command(name = "keyword-audit",
        description = "Survey the keyword vocabulary across a project: frequencies, "
                + "topics with none, near-duplicate spellings, and keyword co-occurrence.")
class KeywordAuditCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Project root directory")
    private String root;

    @Option(names = {"-s", "--scope"}, description = "Scope: 'map:<p>', 'glob:<p>', or 'root'")
    private String scope;

    @Option(names = "--related", description = "Print the keyword co-occurrence (relatedness) pairs")
    private boolean related;

    @Override
    public Integer call() throws Exception {
        KeywordAuditResult r = new HeadlessExecutor()
                .execute(new KeywordAuditCommand(root, scope));
        System.out.println("Vocabulary (" + r.vocabulary().size() + " keywords):");
        for (var t : r.vocabulary()) {
            System.out.printf("  %-24s %d%n", t.keyword(), t.count());
        }
        if (!r.nearDuplicates().isEmpty()) {
            System.out.println("Possible spelling variants:");
            for (var v : r.nearDuplicates()) {
                System.out.printf("  '%s' ~ '%s'%n", v.keyword(), v.canonical());
            }
        }
        if (!r.missing().isEmpty()) {
            System.out.println(r.missing().size() + " topic(s) with no keywords.");
        }
        if (related) {
            System.out.println("Related (shared keywords):");
            for (var p : r.relatedness()) {
                System.out.printf("  %s  ~%d~  %s  %s%n", p.source(), p.shared(),
                        p.target(), p.keywords());
            }
            if (r.truncated() > 0) {
                System.out.println("  … and " + r.truncated() + " more pair(s).");
            }
        }
        return 0;
    }
}
