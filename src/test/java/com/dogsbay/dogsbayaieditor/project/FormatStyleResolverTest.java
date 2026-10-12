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

package com.dogsbay.dogsbayaieditor.project;

import com.dogsbay.xml.format.FormatStyle;
import com.dogsbay.xml.format.FormatStyle.IndentUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Precedence: project .dogsbay/config.xml → user default → built-in default. */
class FormatStyleResolverTest {

    @Test
    void userDefaultSupplierIsTheFallback() {
        FormatStyle custom = FormatStyle.defaults().withIndent(IndentUnit.SPACES, 4);
        FormatStyleResolver.setUserDefaultSupplier(() -> custom);
        try {
            assertThat(FormatStyleResolver.userDefault()).isEqualTo(custom);
            // no project → user default
            assertThat(FormatStyleResolver.forWorkspace(null)).isEqualTo(custom);
        } finally {
            FormatStyleResolver.setUserDefaultSupplier(null);
        }
        // cleared → built-in default
        assertThat(FormatStyleResolver.userDefault()).isEqualTo(FormatStyle.defaults());
    }

    @Test
    void projectStyleWinsOverUserDefault(@TempDir Path workspace) throws Exception {
        DogsbayProjectConfig cfg = DogsbayProjectConfig.load(workspace);
        cfg.setFormatStyle(FormatStyle.defaults().withIndent(IndentUnit.SPACES, 8));
        cfg.saveShared(workspace);

        FormatStyle userDefault = FormatStyle.defaults().withIndent(IndentUnit.SPACES, 4);
        FormatStyleResolver.setUserDefaultSupplier(() -> userDefault);
        try {
            assertThat(FormatStyleResolver.forWorkspace(workspace).indentSize()).isEqualTo(8);
        } finally {
            FormatStyleResolver.setUserDefaultSupplier(null);
        }
    }
}
