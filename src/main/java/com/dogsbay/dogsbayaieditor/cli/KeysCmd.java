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
import com.dogsbay.dogsbayaieditor.commands.ListKeysCommand;
import com.dogsbay.dogsbayaieditor.commands.ResolveKeyCommand;
import com.dogsbay.dogsbayaieditor.commands.results.KeyInfo;

@Command(name = "keys",
        description = "List or resolve keys in a DITA root map's key space")
class KeysCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "The root map file")
    private String rootMap;

    @Option(names = {"-k", "--resolve"},
            description = "Resolve a single key instead of listing all")
    private String key;

    @Option(names = {"-d", "--ditaval"},
            description = "DITAVAL conditioning the key space")
    private String ditaval;

    @Option(names = {"-s", "--scope"},
            description = "Key-scope context to resolve from (DITA 1.3 @keyscope), "
                    + "e.g. 'podcaster'. Only with --resolve.")
    private String scope;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        if (key != null) {
            KeyInfo info = executor.execute(new ResolveKeyCommand(key, rootMap, ditaval, scope));
            print(info);
            return 0;
        }
        var keys = executor.execute(new ListKeysCommand(rootMap, ditaval));
        for (KeyInfo info : keys) {
            print(info);
        }
        System.out.println(keys.size() + " key(s).");
        return keys.isEmpty() ? 1 : 0;
    }

    private static void print(KeyInfo info) {
        StringBuilder line = new StringBuilder();
        line.append(info.name()).append("  →  ");
        if (info.text() != null) {
            line.append('"').append(info.text()).append('"');
        } else if (info.href() != null) {
            line.append(info.href());
        } else {
            line.append("(definition-only)");
        }
        // Surface the resolving scope when it isn't already encoded in the name
        // (the list path uses fully-qualified names; --resolve prints the bare key).
        if (info.scope() != null && !info.scope().isEmpty()
                && !info.name().startsWith(info.scope() + ".")) {
            line.append("  (scope: ").append(info.scope()).append(')');
        }
        line.append("   [").append(info.definedIn()).append(':').append(info.line()).append(']');
        if (info.resolvedPath() != null) {
            line.append("  file: ").append(info.resolvedPath());
        }
        System.out.println(line);
    }
}
