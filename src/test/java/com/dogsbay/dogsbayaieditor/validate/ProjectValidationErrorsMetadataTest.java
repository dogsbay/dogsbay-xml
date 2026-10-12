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

import com.dogsbay.dogsbayaieditor.commands.results.MetadataFinding;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;
import com.dogsbay.xml.XMLError;

/** M3: MetadataFinding → XMLError mapping for the Project Validation pane. */
class ProjectValidationErrorsMetadataTest {

    @Test
    void mapsSeverityAndSystemId() {
        var err = new MetadataFinding("/p/t.dita", -1, "created", "missing required <created>", "error");
        var warn = new MetadataFinding("/p/c.dita", -1, "keyword", "recommended <keyword> missing", "warning");
        List<XMLError> out = ProjectValidationErrors.fromMetadata(
                new BatchResult<>(2, 0, 2, List.of(err, warn), 0));

        assertThat(out).hasSize(2);
        assertThat(out.get(0).getSystemId()).isEqualTo(new File("/p/t.dita").toURI().toString());
        assertThat(out.get(0).getType()).isEqualTo(XMLError.ERROR);
        assertThat(out.get(0).getMessage()).contains("created");
        assertThat(out.get(1).getType()).isEqualTo(XMLError.WARNING);
    }

    @Test
    void nullAndEmptySafe() {
        assertThat(ProjectValidationErrors.fromMetadata(null)).isEmpty();
        assertThat(ProjectValidationErrors.fromMetadata(
                new BatchResult<>(0, 0, 0, List.of(), 0))).isEmpty();
    }
}
