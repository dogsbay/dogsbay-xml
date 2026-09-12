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

package com.dogsbay.xml.browser;

import java.io.File;

/**
 * Optional rendering context for the preview pane.
 *
 * <p>For DITA documents: {@code ditaContextMap} is the root map used to
 * build the key space for keyref/conkeyref resolution (typically the
 * project's Default Root Map, overridable per preview tab), and
 * {@code ditaval} filters profiled content out of the rendering.
 * Either or both may be null.
 */
public final class PreviewOptions {

    public static final PreviewOptions EMPTY = new PreviewOptions(null, null);

    private final File ditaContextMap;
    private final File ditaval;
    private final boolean showChanges;

    public PreviewOptions(File ditaContextMap, File ditaval) {
        this(ditaContextMap, ditaval, false);
    }

    /**
     * @param showChanges render open review proposals as insertions, deletions
     *                    and comments instead of the accepted view
     */
    public PreviewOptions(File ditaContextMap, File ditaval, boolean showChanges) {
        this.ditaContextMap = ditaContextMap;
        this.ditaval = ditaval;
        this.showChanges = showChanges;
    }

    public boolean isShowChanges() {
        return showChanges;
    }

    public File getDitaContextMap() {
        return ditaContextMap;
    }

    public File getDitaval() {
        return ditaval;
    }

    public boolean isEmpty() {
        return ditaContextMap == null && ditaval == null && !showChanges;
    }
}
