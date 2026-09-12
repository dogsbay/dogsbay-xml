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

import com.dogsbay.dogsbayaieditor.commands.GlossaryAuditCommand;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.results.GlossaryAuditResult;

@Command(name = "glossary-audit",
        description = "Survey the DITA glossary (glossentry): term inventory, undefined "
                + "abbreviated-form/term references, and unused glossentries.")
class GlossaryAuditCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Project root directory")
    private String root;

    @Option(names = {"-s", "--scope"}, description = "Scope: 'map:<p>', 'glob:<p>', or 'root'")
    private String scope;

    @Option(names = {"-m", "--root-map"},
            description = "Root map whose key space resolves keyrefs (for undefined/unused)")
    private String rootMap;

    @Override
    public Integer call() throws Exception {
        GlossaryAuditResult r = new HeadlessExecutor()
                .execute(new GlossaryAuditCommand(root, scope, rootMap));
        System.out.println("Glossary entries (" + r.entries().size() + "):");
        for (var e : r.entries()) {
            System.out.printf("  %-24s %s%n", e.term(), e.id());
        }
        if (!r.undefined().isEmpty()) {
            System.out.println("Undefined references (" + r.undefined().size() + "):");
            for (var u : r.undefined()) {
                System.out.printf("  %s:%d <%s keyref=\"%s\"> — not a glossary term%n",
                        u.file(), u.line(), u.element(), u.keyref());
            }
        }
        if (!r.unused().isEmpty()) {
            System.out.println("Unused glossentries (" + r.unused().size() + "):");
            for (var e : r.unused()) {
                System.out.printf("  %s (%s)%n", e.term(), e.id());
            }
        }
        return r.undefined().isEmpty() ? 0 : 1;
    }
}
