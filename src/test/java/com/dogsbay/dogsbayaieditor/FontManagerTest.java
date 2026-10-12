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

package com.dogsbay.dogsbayaieditor;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class FontManagerTest {

    @Test
    void firaCodeIsNotBundled() {
        assertThat(FontManager.getInstance().hasBundledFont("Fira Code")).isFalse();
    }

    @Test
    void firaCodeIsNotOfferedInTheList() {
        // excluded even if installed as a system font (its name contains "code")
        assertThat(FontManager.getInstance().getAvailableFonts()).doesNotContain("Fira Code");
    }

    @Test
    void sourceCodeProIsBundledAndHeadsTheList() {
        FontManager fm = FontManager.getInstance();
        assertThat(fm.hasBundledFont("Source Code Pro")).isTrue();
        List<String> fonts = fm.getAvailableFonts();
        // bundled fonts sort first, Source Code Pro before JetBrains Mono
        assertThat(fonts.get(0)).isEqualTo("Source Code Pro");
        int scp = fonts.indexOf("Source Code Pro");
        int jbm = fonts.indexOf("JetBrains Mono");
        assertThat(scp).isLessThan(jbm < 0 ? Integer.MAX_VALUE : jbm);
    }
}
