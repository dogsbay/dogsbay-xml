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

package com.dogsbay.dogsbayaieditor.validate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.commands.results.FileValidation;
import com.dogsbay.dogsbayaieditor.commands.results.SchematronFinding;
import com.dogsbay.dogsbayaieditor.commands.results.ValidationError;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;
import com.dogsbay.dogsbayaieditor.links.Branch;
import com.dogsbay.xml.XMLError;

class ProjectValidationErrorsTest {

    @Test
    void attributesDitaOtMessagesToBranchesByResourceAffix() {
        // win- prefix and -draft suffix name DITA-OT's generated branch resources.
        Branch win = new Branch("install.dita", "win.ditaval", null,
                "", "", "win-", "");
        Branch draft = new Branch("install.dita", "draft.ditaval", null,
                "", "", "rev-", "-draft");
        List<Branch> branches = List.of(win, draft);

        // prefix match → the prefix branch's label
        assertThat(ProjectValidationErrors.branchAttribution(
                "topics/win-install.dita", branches)).isEqualTo("win-");
        // suffix match → that branch's label (its resource prefix)
        assertThat(ProjectValidationErrors.branchAttribution(
                "topics/install-draft.dita", branches)).isEqualTo("rev-");
        // a plain (non-branch) resource isn't attributed
        assertThat(ProjectValidationErrors.branchAttribution(
                "topics/install.dita", branches)).isNull();
        // no branches → no attribution
        assertThat(ProjectValidationErrors.branchAttribution(
                "topics/win-install.dita", List.of())).isNull();
    }

    @Test
    void flattensFindingsToXmlErrorsWithFileUrlAndSeverity() {
        var fv = new FileValidation("/p/bad.dita", false, List.of(
                new ValidationError(3, 5, "error", "boom", "parser"),
                new ValidationError(7, 1, "warning", "careful", "parser")));
        BatchResult<FileValidation> result =
                BatchResult.capped(2, 1, 1, List.of(fv), 200);

        List<XMLError> errors = ProjectValidationErrors.toErrors(result);

        assertThat(errors).hasSize(2);
        XMLError e0 = errors.get(0);
        assertThat(e0.getLineNumber()).isEqualTo(3);
        assertThat(e0.getColumnNumber()).isEqualTo(5);
        assertThat(e0.getType()).isEqualTo(XMLError.ERROR);
        assertThat(e0.getMessage()).isEqualTo("boom");
        // systemId is a file URL so the error pane can open it on click
        assertThat(e0.getSystemId()).startsWith("file:").endsWith("bad.dita");
        assertThat(errors.get(1).getType()).isEqualTo(XMLError.WARNING);
    }

    @Test
    void unresolvedLineKeepsLocationInMessage() {
        var finding = new SchematronFinding("/p/topic.dita", "/topic[1]/body[1]",
                "A topic must have a title.", "warning", "title", -1);
        BatchResult<SchematronFinding> result =
                BatchResult.capped(1, 0, 1, List.of(finding), 200);

        XMLError e = ProjectValidationErrors.fromSchematron(result).get(0);
        assertThat(e.getSystemId()).startsWith("file:").endsWith("topic.dita");
        assertThat(e.getType()).isEqualTo(XMLError.WARNING);
        assertThat(e.getLineNumber()).isEqualTo(-1);
        assertThat(e.getMessage()).contains("must have a title").contains("/topic[1]/body[1]");
    }

    @Test
    void resolvedLineNavigatesAndDropsLocationFromMessage() {
        var finding = new SchematronFinding("/p/topic.dita", "/topic[1]/body[1]/p[1]",
                "A paragraph needs an id.", "error", "@id", 7);
        BatchResult<SchematronFinding> result =
                BatchResult.capped(1, 0, 1, List.of(finding), 200);

        XMLError e = ProjectValidationErrors.fromSchematron(result).get(0);
        assertThat(e.getLineNumber()).isEqualTo(7);
        assertThat(e.getType()).isEqualTo(XMLError.ERROR);
        assertThat(e.getMessage()).isEqualTo("A paragraph needs an id.");
    }

    @Test
    void cleanResultProducesNoErrors() {
        BatchResult<FileValidation> clean = BatchResult.capped(5, 5, 0, List.of(), 200);
        assertThat(ProjectValidationErrors.toErrors(clean)).isEmpty();
        assertThat(ProjectValidationErrors.toErrors(null)).isEmpty();
    }
}
