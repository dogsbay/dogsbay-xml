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
import com.dogsbay.dogsbayaieditor.commands.ReltableAuditCommand;
import com.dogsbay.dogsbayaieditor.commands.results.ReltableAuditResult;

@Command(name = "reltable-audit",
        description = "Validate a map's relationship tables and preview the "
                + "related-links they generate.")
class ReltableAuditCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "The .ditamap")
    private String map;

    @Option(names = "--links", description = "Also print the generated related-links")
    private boolean links;

    @Override
    public Integer call() throws Exception {
        ReltableAuditResult r = new HeadlessExecutor().execute(new ReltableAuditCommand(map));
        for (var i : r.issues()) {
            System.out.printf("%s:%d [%s] %s — %s%s%n", i.source(), i.line(), i.severity(),
                    i.cell(), i.reason(),
                    i.value() == null || i.value().isBlank() ? "" : " (" + i.value() + ")");
        }
        if (links) {
            for (var l : r.generatedLinks()) {
                System.out.printf("  link: %s → %s%n", l.source(), l.target());
            }
        }
        System.out.printf("%d issue(s), %d generated link(s).%n",
                r.issues().size(), r.generatedLinks().size());
        return r.isClean() ? 0 : 1;
    }
}
