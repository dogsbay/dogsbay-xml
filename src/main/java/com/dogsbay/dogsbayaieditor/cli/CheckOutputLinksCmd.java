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

import com.dogsbay.dogsbayaieditor.commands.CheckOutputLinksCommand;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.results.BrokenRef;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Report links in built output that lead nowhere.
 *
 * <p>The companion to {@code check-links}, which asks the same question of the source. A reference can be sound in the DITA
 * and still break in the output, because a topic was filtered out of this deliverable, chunking moved content to another
 * page, a copy-to renamed one, or an image was never carried across. Those faults appear only after a build, and they are
 * the ones a reader meets.
 */
@Command(name = "check-output-links",
        description = "Report links in built output that lead nowhere: missing pages, fragments and images. "
                + "Exits 1 when any are found — CI-friendly.")
class CheckOutputLinksCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "The folder a build wrote")
    private String dir;

    @Override
    public Integer call() throws Exception {
        var broken = new HeadlessExecutor().execute(new CheckOutputLinksCommand(dir));
        if (broken.isEmpty()) {
            System.out.println("No broken links in the built output.");
            return 0;
        }
        for (BrokenRef ref : broken) {
            System.out.printf("%s:%d  @%s=\"%s\" — %s%n",
                    ref.source(), ref.line(), ref.attribute(), ref.value(), ref.reason());
        }
        System.out.println(broken.size() + " broken link(s) in the built output.");
        return 1;
    }
}
