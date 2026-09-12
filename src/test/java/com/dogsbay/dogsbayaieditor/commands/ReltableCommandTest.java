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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.MapEditResult;
import com.dogsbay.dogsbayaieditor.commands.results.ReltableAuditResult;

/** P2/P3: reltable_audit + edit_reltable across the headless executor. */
class ReltableCommandTest {

    @TempDir Path dir;
    private final HeadlessExecutor exec = new HeadlessExecutor();

    private File write(String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, content);
        return p.toFile();
    }

    private String read(File f) throws Exception {
        return Files.readString(f.toPath());
    }

    private File mapWithReltable() throws Exception {
        write("c.dita", "<concept id=\"c\"><title>C</title><conbody/></concept>");
        write("t.dita", "<task id=\"t\"><title>T</title><taskbody/></task>");
        return write("g.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map>
              <title>Guide</title>
              <reltable>
                <relheader>
                  <relcolspec type="concept"/>
                  <relcolspec type="task"/>
                </relheader>
                <relrow>
                  <relcell><topicref href="c.dita"/></relcell>
                  <relcell><topicref href="t.dita"/></relcell>
                </relrow>
              </reltable>
            </map>
            """);
    }

    @Test
    void auditReportsCleanWithGeneratedLinks() throws Exception {
        ReltableAuditResult r = exec.execute(new ReltableAuditCommand(mapWithReltable().toString()));
        assertThat(r.isClean()).isTrue();
        // c <-> t bidirectional
        assertThat(r.generatedLinks()).hasSize(2);
    }

    @Test
    void auditFlagsBrokenAndMapTargets() throws Exception {
        File m = write("g.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>G</title>
              <reltable><relrow>
                <relcell><topicref href="missing.dita"/></relcell>
                <relcell><topicref href="other.ditamap"/></relcell>
              </relrow></reltable>
            </map>
            """);
        ReltableAuditResult r = exec.execute(new ReltableAuditCommand(m.toString()));
        assertThat(r.isClean()).isFalse();
        assertThat(r.issues()).anyMatch(i -> i.reason().contains("not found"));
        assertThat(r.issues()).anyMatch(i -> i.reason().contains("targets a map"));
    }

    @Test
    void editCreateAddRowAddTargetRemoveSetAttr() throws Exception {
        File m = write("g.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map>
              <title>Guide</title>
            </map>
            """);
        write("x.dita", "<concept id=\"x\"><title>X</title><conbody/></concept>");

        // create a table with two columns
        exec.execute(edit(m, 0, "create-table", b -> b.columns = "concept,task"));
        assertThat(read(m)).contains("<reltable>").contains("relcolspec type=\"concept\"");

        // add a row, then a target into cell (0,0)
        exec.execute(edit(m, 0, "add-row", b -> { }));
        exec.execute(edit(m, 0, "add-target", b -> { b.row = 0; b.col = 0; b.href = "x.dita"; }));
        assertThat(read(m)).contains("<topicref href=\"x.dita\"/>");

        // set a table attribute, then remove the target
        exec.execute(edit(m, 0, "set-attr", b -> { b.name = "title"; b.value = "Concept/Task"; }));
        assertThat(read(m)).contains("title=\"Concept/Task\"");
        exec.execute(edit(m, 0, "remove-target", b -> { b.row = 0; b.col = 0; b.index = 0; }));
        assertThat(read(m)).doesNotContain("x.dita");

        // re-parses cleanly throughout
        exec.execute(new ReltableAuditCommand(m.toString()));
    }

    @Test
    void editDryRunWritesNothing() throws Exception {
        File m = mapWithReltable();
        String before = read(m);
        MapEditResult r = exec.execute(edit(m, 0, "add-row", b -> b.dryRun = true));
        assertThat(r.dryRun()).isTrue();
        assertThat(read(m)).isEqualTo(before);
    }

    @Test
    void addRowOnSelfClosingTableDoesNotCorruptTheFile() throws Exception {
        File m = write("g.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map>
              <title>Guide</title>
              <reltable/>
            </map>
            """);
        exec.execute(edit(m, 0, "add-row", b -> { }));
        String out = read(m);
        assertThat(out).contains("<relrow>").contains("<relcell/>");
        // the XML declaration / root still come first — nothing spliced at offset 0
        assertThat(out.stripLeading()).startsWith("<?xml");
        var model = com.dogsbay.dogsbayaieditor.links.reltable.ReltableModel.parse(m);
        assertThat(model.reltables().get(0).rows()).hasSize(1);
    }

    @Test
    void setAttrColumnWithoutRowIsRejected() throws Exception {
        File m = mapWithReltable();
        assertThatThrownBy(() -> exec.execute(edit(m, 0, "set-attr",
                b -> { b.col = 1; b.name = "linking"; b.value = "none"; })))
            .isInstanceOf(CommandException.class)
            .hasFieldOrPropertyWithValue("code", CommandException.ErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void addTargetRequiresHrefOrKeyref() throws Exception {
        File m = mapWithReltable();
        assertThatThrownBy(() -> exec.execute(edit(m, 0, "add-target",
                b -> { b.row = 0; b.col = 0; })))
            .isInstanceOf(CommandException.class)
            .hasFieldOrPropertyWithValue("code", CommandException.ErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void mapTargetJudgedByResolvedExtensionNotSubstring() throws Exception {
        // a topic whose name merely contains ".ditamap" is NOT a map target
        write("notes.ditamap.dita", "<concept id=\"n\"><title>N</title><conbody/></concept>");
        write("sub.ditamap", "<map><title>S</title></map>");
        File m = write("g.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>G</title>
              <reltable><relrow>
                <relcell><topicref href="notes.ditamap.dita"/></relcell>
                <relcell><topicref href="sub.ditamap"/></relcell>
              </relrow></reltable>
            </map>
            """);
        var r = exec.execute(new ReltableAuditCommand(m.toString()));
        assertThat(r.issues()).filteredOn(i -> i.reason().contains("targets a map"))
            .singleElement().satisfies(i -> assertThat(i.value()).isEqualTo("sub.ditamap"));
    }

    @Test
    void createTableSurvivesTrailingCommentAfterRoot() throws Exception {
        File m = write("g.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map>
              <title>Guide</title>
            </map>
            <!-- end </map> -->
            """);
        exec.execute(edit(m, 0, "create-table", b -> b.columns = "concept,task"));
        // the reltable landed inside the root, before the real </map>
        String out = read(m);
        assertThat(out.indexOf("<reltable>")).isLessThan(out.indexOf("</map>"));
        assertThat(com.dogsbay.dogsbayaieditor.links.reltable.ReltableModel.parse(m)
                .reltables()).hasSize(1);
    }

    @Test
    void editRejectsBadOpAndMissingMap() throws Exception {
        File m = mapWithReltable();
        assertThatThrownBy(() -> exec.execute(edit(m, 0, "frobnicate", b -> { })))
            .isInstanceOf(CommandException.class);
        assertThatThrownBy(() -> exec.execute(new ReltableAuditCommand(
                dir.resolve("nope.ditamap").toString())))
            .isInstanceOf(CommandException.class)
            .hasFieldOrPropertyWithValue("code", CommandException.ErrorCode.FILE_NOT_FOUND);
    }

    // tiny builder so the tests read cleanly
    private static final class B {
        Integer row;
        Integer col;
        Integer index;
        String href;
        String keyref;
        String navtitle;
        String name;
        String value;
        String columns;
        boolean dryRun;
    }

    private static EditReltableCommand edit(File map, int table, String op,
            java.util.function.Consumer<B> cfg) {
        B b = new B();
        cfg.accept(b);
        return new EditReltableCommand(map.toString(), table, op, b.row, b.col, b.index,
                b.href, b.keyref, b.navtitle, b.name, b.value, b.columns, b.dryRun);
    }
}
