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

import com.dogsbay.dogsbayaieditor.commands.results.ConrefPushAuditResult;

/** conref_push_audit: inventory + pairing + unresolved-target. */
class ConrefPushAuditCommandTest {

    @TempDir Path dir;
    private final HeadlessExecutor exec = new HeadlessExecutor();

    private void write(String name, String content) throws Exception {
        Files.writeString(dir.resolve(name), content);
    }

    @Test
    void inventoryPairingAndTargetResolution() throws Exception {
        write("target.dita", "<concept id=\"o\"><title>O</title><conbody>"
                + "<p id=\"p1\">existing</p></conbody></concept>");
        write("push.dita", "<concept id=\"c\"><title>T</title><conbody>"
                + "<section>"
                + "  <p conaction=\"mark\" conref=\"target.dita#o/p1\"/>"   // resolves
                + "  <p conaction=\"pushbefore\">before</p>"
                + "</section>"
                + "<section>"
                + "  <p conaction=\"mark\" conref=\"target.dita#o/ghost\"/>" // bad id
                + "  <p conaction=\"pushafter\">after</p>"
                + "</section>"
                + "</conbody></concept>");

        ConrefPushAuditResult r = exec.execute(
                new ConrefPushAuditCommand(dir.toString(), "root", null));

        // inventory: 4 conaction operations
        assertThat(r.pushes()).hasSize(4);
        // the only issue is the bad element id (ghost); pairing is fine everywhere
        assertThat(r.issues()).hasSize(1);
        assertThat(r.issues().get(0).problem()).contains("ghost").contains("not found");
    }

    @Test
    void markWithoutAnyTargetReferenceDoesNotCrash() throws Exception {
        // a <… conaction="mark"/> with no @conref/@conkeyref must be reported, not NPE
        write("push.dita", "<concept id=\"c\"><title>T</title><conbody>"
                + "<section>"
                + "  <p conaction=\"mark\"/>"                  // mark, no target ref
                + "  <p conaction=\"pushbefore\">x</p>"
                + "</section>"
                + "</conbody></concept>");

        ConrefPushAuditResult r = exec.execute(
                new ConrefPushAuditCommand(dir.toString(), "root", null));
        assertThat(r.issues()).anySatisfy(i ->
                assertThat(i.problem()).contains("has no @conref/@conkeyref target"));
    }

    @Test
    void flagsBrokenPairing() throws Exception {
        write("push.dita", "<concept id=\"c\"><title>T</title><conbody>"
                + "<section><p conaction=\"pushbefore\">orphan</p></section>"
                + "</conbody></concept>");

        ConrefPushAuditResult r = exec.execute(
                new ConrefPushAuditCommand(dir.toString(), "root", null));
        assertThat(r.issues()).anySatisfy(i ->
                assertThat(i.problem()).contains("no sibling conaction=\"mark\""));
    }
}
