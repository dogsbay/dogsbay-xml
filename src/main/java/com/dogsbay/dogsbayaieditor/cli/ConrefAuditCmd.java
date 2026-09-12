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

import com.dogsbay.dogsbayaieditor.commands.ConrefAuditCommand;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.results.BrokenRef;

@Command(name = "conref-audit",
        description = "Audit reuse references for broken element ids (target file "
                + "exists but #fragment id is missing). Exits 1 when any are found.")
class ConrefAuditCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Project root directory to scan")
    private String root;

    @Option(names = {"-m", "--map"},
            description = "Root map — resolves keyref/conkeyref targets")
    private String rootMap;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        var broken = executor.execute(new ConrefAuditCommand(root, rootMap));
        if (broken.isEmpty()) {
            System.out.println("No broken element ids.");
            return 0;
        }
        for (BrokenRef ref : broken) {
            System.out.printf("%s:%d  @%s=\"%s\" — %s%n",
                    ref.source(), ref.line(), ref.attribute(), ref.value(), ref.reason());
        }
        System.out.println(broken.size() + " broken element id reference(s).");
        return 1;
    }
}
