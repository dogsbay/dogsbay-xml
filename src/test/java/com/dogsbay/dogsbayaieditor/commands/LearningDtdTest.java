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

package com.dogsbay.dogsbayaieditor.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * DITA 1.3's learning and training types validate against the bundled catalog.
 *
 * <p>They were missing from the bundle, so a project with a single {@code learningAssessment} topic could not be
 * validated at all — and a documentation tutorial that teaches them could not check its own later stages. Reaching
 * for an external DITA-OT instead is what the bundle exists to avoid.
 */
class LearningDtdTest {

    @TempDir Path dir;
    private final HeadlessExecutor exec = new HeadlessExecutor();

    private Path write(String name, String xml) throws Exception {
        return Files.writeString(dir.resolve(name), xml);
    }

    @Test
    void aLearningAssessmentValidatesAgainstTheBundledCatalog() throws Exception {
        Path file = write("check.dita", """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE learningAssessment PUBLIC "-//OASIS//DTD DITA Learning Assessment//EN" "learningAssessment.dtd">
            <learningAssessment id="check-your-understanding">
              <title>Check your understanding</title>
              <shortdesc>A few questions.</shortdesc>
              <learningAssessmentbody>
                <lcInteraction>
                  <lcTrueFalse id="q1">
                    <lcQuestion>DITA maps hold topics.</lcQuestion>
                    <lcAnswerOptionGroup>
                      <lcAnswerOption>
                        <lcAnswerContent>True</lcAnswerContent>
                        <lcCorrectResponse/>
                      </lcAnswerOption>
                      <lcAnswerOption>
                        <lcAnswerContent>False</lcAnswerContent>
                      </lcAnswerOption>
                    </lcAnswerOptionGroup>
                  </lcTrueFalse>
                </lcInteraction>
              </learningAssessmentbody>
            </learningAssessment>
            """);

        var result = exec.execute(new ValidateCommand(file));

        assertThat(result.valid()).as("%s", result.errors()).isTrue();
    }

    @Test
    void theGrammarIsReallyApplied() throws Exception {
        // the same topic with an element the learning grammar does not define: proof the DTD is doing the work
        Path file = write("broken.dita", """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE learningAssessment PUBLIC "-//OASIS//DTD DITA Learning Assessment//EN" "learningAssessment.dtd">
            <learningAssessment id="a">
              <title>A</title>
              <learningAssessmentbody>
                <lcInteraction><notAnElement/></lcInteraction>
              </learningAssessmentbody>
            </learningAssessment>
            """);

        var result = exec.execute(new ValidateCommand(file));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors().toString()).contains("notAnElement");
    }

    @Test
    void theOtherLearningTypesResolveToo() throws Exception {
        for (String[] kind : new String[][] {
                { "content.dita", "learningContent", "-//OASIS//DTD DITA Learning Content//EN",
                  "learningContent.dtd", "learningContentbody" },
                { "overview.dita", "learningOverview", "-//OASIS//DTD DITA Learning Overview//EN",
                  "learningOverview.dtd", "learningOverviewbody" },
                { "summary.dita", "learningSummary", "-//OASIS//DTD DITA Learning Summary//EN",
                  "learningSummary.dtd", "learningSummarybody" } }) {
            Path file = write(kind[0], """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE %s PUBLIC "%s" "%s">
                <%s id="a">
                  <title>A</title>
                  <%s/>
                </%s>
                """.formatted(kind[1], kind[2], kind[3], kind[1], kind[4], kind[1]));

            var result = exec.execute(new ValidateCommand(file));

            assertThat(result.valid()).as("%s: %s", kind[1], result.errors()).isTrue();
        }
    }
}
