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

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.commands.results.ConditionViolation;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;
import com.dogsbay.xml.XMLError;

/** P4: ConditionViolation → XMLError mapping for the Project Validation pane. */
class ProjectValidationErrorsConditionsTest {

    @Test
    void mapsViolationToWarningWithSystemIdAndLine() {
        var v = new ConditionViolation("/proj/intro.dita", "platform", "macos",
                "“macos” is not a controlled value for @platform (did you mean “mac”?)", "mac", 7);
        List<XMLError> errors = ProjectValidationErrors.fromConditions(
                new BatchResult<>(1, 0, 1, List.of(v), 0));

        assertThat(errors).hasSize(1);
        XMLError e = errors.get(0);
        assertThat(e.getSystemId()).isEqualTo(new File("/proj/intro.dita").toURI().toString());
        assertThat(e.getLineNumber()).isEqualTo(7);
        assertThat(e.getType()).isEqualTo(XMLError.WARNING);
        assertThat(e.getMessage()).contains("macos").contains("mac");
    }

    @Test
    void unresolvedLineBecomesMinusOne() {
        var v = new ConditionViolation("/proj/f.ditaval", "audience", "noob", "msg", null, -1);
        List<XMLError> errors = ProjectValidationErrors.fromConditions(
                new BatchResult<>(1, 0, 1, List.of(v), 0));
        assertThat(errors.get(0).getLineNumber()).isEqualTo(-1);
    }

    @Test
    void nullAndEmptyAreSafe() {
        assertThat(ProjectValidationErrors.fromConditions(null)).isEmpty();
        assertThat(ProjectValidationErrors.fromConditions(
                new BatchResult<>(0, 0, 0, List.of(), 0))).isEmpty();
    }
}
