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

import com.dogsbay.dogsbayaieditor.commands.ConrefPushAuditCommand;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.results.ConrefPushAuditResult;

@Command(name = "conref-push-audit",
        description = "Audit DITA conref push (conaction): inventory of push operations, "
                + "broken sibling pairing, and unresolvable push targets.")
class ConrefPushAuditCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Project root directory")
    private String root;

    @Option(names = {"-s", "--scope"}, description = "Scope: 'map:<p>', 'glob:<p>', or 'root'")
    private String scope;

    @Option(names = {"-m", "--root-map"},
            description = "Root map whose key space resolves conkeyref keys")
    private String rootMap;

    @Override
    public Integer call() throws Exception {
        ConrefPushAuditResult r = new HeadlessExecutor()
                .execute(new ConrefPushAuditCommand(root, scope, rootMap));
        System.out.println("Conref-push operations (" + r.pushes().size() + "):");
        for (var p : r.pushes()) {
            System.out.printf("  %s:%d <%s conaction=\"%s\"> %s%n",
                    p.file(), p.line(), p.element(), p.action(), p.target());
        }
        if (!r.issues().isEmpty()) {
            System.out.println("Issues (" + r.issues().size() + "):");
            for (var i : r.issues()) {
                System.out.printf("  %s:%d <%s> — %s%n", i.file(), i.line(), i.element(),
                        i.problem());
            }
        }
        return r.issues().isEmpty() ? 0 : 1;
    }
}
