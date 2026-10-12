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

/** Small cross-platform UI helpers. */
public final class Platform {

    private Platform() {}

    /**
     * True on macOS when the menu bar is the OS <em>screen</em> menu bar (top of the screen,
     * not in the window). A screen menu bar is owned by the OS and cannot host arbitrary
     * buttons, so menu-bar button groups (view switcher, split, layout toggles) must fall
     * back to the toolbar there. The app opts into the screen menu bar via
     * {@code apple.laf.useScreenMenuBar} (see {@code Main.setupSystemUIProperties}); this
     * keys off the OS + that property rather than the obsolete {@code mrj.version}, which
     * modern Mac JDKs no longer set.
     */
    public static boolean isMacScreenMenuBar() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac")
                && Boolean.getBoolean("apple.laf.useScreenMenuBar");
    }
}
