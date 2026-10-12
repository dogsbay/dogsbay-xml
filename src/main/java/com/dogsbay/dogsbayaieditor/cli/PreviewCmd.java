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
import com.dogsbay.dogsbayaieditor.commands.RenderPreviewCommand;

@Command(name = "preview",
        description = "Render the styled DITA preview to HTML "
                + "(key resolution + DITAVAL aware)")
class PreviewCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "DITA topic or map")
    private String file;

    @Option(names = {"-o", "--output"},
            description = "Output HTML file (prints to stdout if omitted)")
    private String output;

    @Option(names = {"-m", "--map"},
            description = "Context map for keyref/conkeyref resolution")
    private String map;

    @Option(names = {"-d", "--ditaval"},
            description = "DITAVAL filter")
    private String ditaval;

    @Option(names = {"--show-changes"},
            description = "Render open review proposals as insertions, deletions and comments "
                    + "instead of the accepted view")
    private boolean showChanges;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        var result = executor.execute(new RenderPreviewCommand(file, output, map, ditaval, showChanges));
        if (result.outputPath() != null) {
            System.out.println("Wrote " + result.outputPath());
        } else {
            System.out.println(result.html());
        }
        return 0;
    }
}
