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
import picocli.CommandLine.Parameters;

import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.ListSubjectsCommand;
import com.dogsbay.dogsbayaieditor.commands.results.SubjectDefinition;

@Command(name = "list-subjects",
        description = "List a subjectScheme's controlled vocabulary: each governed "
                + "attribute and its allowed values.")
class ListSubjectsCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Subject scheme (.ditamap)")
    private String subjectScheme;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        var subjects = executor.execute(new ListSubjectsCommand(subjectScheme));

        if (subjects.isEmpty()) {
            System.out.println("No controlled values (no subjectScheme bindings found).");
            return 0;
        }
        for (SubjectDefinition s : subjects) {
            System.out.println("@" + s.attribute() + ": " + String.join(", ", s.values()));
        }
        return 0;
    }
}
