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

package com.dogsbay.dogsbayaieditor.terminal;

import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;

import java.awt.*;

/**
 * Terminal settings provider for JediTerm terminal emulator.
 * Simplified for JediTerm 3.48 API compatibility.
 */
public class TerminalSettings extends DefaultSettingsProvider {

    @Override
    public Font getTerminalFont() {
        return new Font("Monospaced", Font.PLAIN, 12);
    }

    @Override
    public float getTerminalFontSize() {
        return 12f;
    }
}
