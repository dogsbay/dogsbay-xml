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

package com.dogsbay.xml.edit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.dom4j.io.SAXReader;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import com.dogsbay.xml.edit.StructuralEdit.Step;
import com.dogsbay.xml.review.ProposalOps;

/**
 * Random batches of steps on a topic: a batch applies plainly exactly when it applies as proposals, both
 * results are well-formed, nothing fails other than as a step, and the marks are almost always exact
 * (the policy checks, and writes the rest without marks).
 */
class StructuralEditPropertyTest {

    private static final String AUTHOR = "ai:claude-acp";

    private static final String TOPIC = """
            <?xml version="1.0" encoding="UTF-8"?>
            <task id="t">
              <title>Installing <ph>it</ph></title>
              <taskbody>
                <context>
                  <p id="p1">Available for <b>three</b> systems.</p>
                  <note type="tip">Check the version.</note>
                </context>
                <steps>
                  <step id="s1" platform="windows">
                    <cmd>Run the installer.</cmd>
                  </step>
                  <step id="s2" platform="macos">
                    <cmd>Drag it to <filepath>Applications</filepath>.</cmd>
                    <info><codeblock>cp -r x \\
              /Applications</codeblock></info>
                  </step>
                  <step id="s3" platform="linux">
                    <cmd>Use the package manager.</cmd>
                  </step>
                </steps>
              </taskbody>
            </task>
            """;

    private static final String[] SELECT = {"#p1", "#s1", "#s2", "#s3", "//note", "//context", "//steps",
        "//step[@id='s2']/cmd", "//filepath", "//b", "//title/ph", "//codeblock", "//step[last()]", "//step[1]/cmd",
        "#new1", "//info"};
    private static final String[] OPS = {"insert", "delete", "replace", "move", "wrap", "unwrap", "rename",
        "set-attribute", "remove-attribute", "set-text"};
    private static final String[] POSITIONS = {"before", "after", "first-child", "last-child"};
    private static final String[] XML = {"<step id=\"new1\"><cmd>New.</cmd></step>", "<p>A <b>new</b> one.</p>",
        "<note type=\"tip\">\n  <p>Tip.</p>\n</note>", "text & more", "<ph/>"};

    @Test
    void randomBatchesApplyTheSameWayPlainAndMarked() throws Exception {
        Random random = new Random(20260918);
        int applied = 0;
        int exact = 0;
        for (int run = 0; run < 1500; run++) {
            List<Step> steps = new ArrayList<>();
            for (int k = 1 + random.nextInt(4); k > 0; k--) {
                steps.add(randomStep(random));
            }
            String plain = null;
            String marked = null;
            String plainFailure = null;
            String markedFailure = null;
            try {
                plain = StructuralEdit.apply(TOPIC, steps, null).text();
            } catch (StructuralEdit.StepException e) {
                plainFailure = e.getMessage();
            }
            try {
                marked = StructuralEdit.apply(TOPIC, steps, AUTHOR).text();
            } catch (StructuralEdit.StepException e) {
                markedFailure = e.getMessage();
            }
            assertThat(markedFailure == null).as("%s\nplain: %s\nmarked: %s", steps, plainFailure, markedFailure)
                    .isEqualTo(plainFailure == null);
            assertThat(String.valueOf(plainFailure) + markedFailure).as(steps.toString()).doesNotContain("could not apply");
            if (plain == null) {
                continue;
            }
            applied++;
            wellFormed(plain, steps);
            wellFormed(marked, steps);
            if (ProposalOps.acceptAll(marked, AUTHOR).equals(plain) && ProposalOps.rejectAll(marked, AUTHOR).equals(TOPIC)) {
                exact++;
            }
        }
        assertThat(applied).isGreaterThan(200);
        assertThat(exact).as("exact marks, of " + applied).isGreaterThanOrEqualTo(applied * 99 / 100);
    }

    private static Step randomStep(Random random) {
        String op = OPS[random.nextInt(OPS.length)];
        String select = SELECT[random.nextInt(SELECT.length)];
        String xml = op.equals("wrap") ? "<b/>" : XML[random.nextInt(XML.length)];
        String name = op.equals("rename") ? "ph" : random.nextBoolean() ? "importance" : "platform";
        return new Step(op, select, POSITIONS[random.nextInt(POSITIONS.length)], xml, name, "optional",
                "Some text.", SELECT[random.nextInt(SELECT.length)]);
    }

    private static void wellFormed(String xml, List<Step> steps) {
        try {
            SAXReader reader = new SAXReader();
            reader.setEntityResolver((p, s) -> new InputSource(new StringReader("")));
            reader.read(new StringReader(xml));
        } catch (Exception e) {
            throw new AssertionError(steps + "\n" + xml, e);
        }
    }
}
