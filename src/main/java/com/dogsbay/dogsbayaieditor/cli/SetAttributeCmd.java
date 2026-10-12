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

import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.SetAttributeCommand;
import com.dogsbay.dogsbayaieditor.commands.results.AttributeChange;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** One attribute on one element, across a scope: the edit_structure step over a file set. */
@Command(name = "set-attribute",
        description = {"Set one attribute on one element in every file of a scope, keeping each file's "
                + "formatting: only the element's start tag is rewritten.",
            "Prints the plan and changes nothing until you pass --apply.",
            "  dogsbay-xml set-attribute . --name xml:lang --value en --only-if-absent"})
class SetAttributeCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Project root directory")
    private String root;

    @Option(names = {"-m", "--map"}, description = "Root map — apply to its publication set")
    private String map;

    @Option(names = {"-s", "--scope"}, description = "Scope: 'map:<p>', 'glob:<p>', or 'root'")
    private String scope;

    @Option(names = "--select", description = "The element, as XPath or #id (default: /*, the root element)")
    private String select;

    @Option(names = "--name", required = true, description = "The attribute to set")
    private String name;

    @Option(names = "--value", required = true, description = "The value to set it to")
    private String value;

    @Option(names = "--only-if-absent",
            description = "Leave a file alone when the element already has the attribute, whatever its value")
    private boolean onlyIfAbsent;

    @Option(names = "--apply", description = "Write the changes (without this, the plan is printed)")
    private boolean apply;

    @Override
    public Integer call() throws Exception {
        String resolved = scope;
        if (resolved == null && map != null) {
            resolved = "map:" + map;
        }

        var result = new HeadlessExecutor().execute(new SetAttributeCommand(
                root, resolved, select, name, value, onlyIfAbsent, !apply));

        for (AttributeChange ch : result.findings()) {
            System.out.printf("%s — %s @%s=\"%s\" on %s%n", ch.file(),
                    ch.replaced() ? "replace" : "set", ch.name(), ch.value(), ch.element());
        }
        if (result.truncated() > 0) {
            System.out.println("… and " + result.truncated() + " more file(s).");
        }
        System.out.printf("%s%d file(s): %d %s, %d already as asked or not matching.%n",
                apply ? "" : "[plan] ", result.total(), result.failed(),
                apply ? "changed" : "to change", result.passed());
        return 0;
    }
}
