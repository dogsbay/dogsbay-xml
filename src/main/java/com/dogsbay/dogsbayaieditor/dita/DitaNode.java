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

package com.dogsbay.dogsbayaieditor.dita;

import java.io.File;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Abstract base class for all nodes in the DITA map tree.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/24 $
 * @author DogsBay Ltd
 */
public abstract class DitaNode extends DefaultMutableTreeNode {
    private String title;
    private String href;
    private File resolvedFile;

    public DitaNode(String title) {
        super(title);
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        setUserObject(title);
    }

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public File getResolvedFile() {
        return resolvedFile;
    }

    public void setResolvedFile(File resolvedFile) {
        this.resolvedFile = resolvedFile;
    }

    @Override
    public String toString() {
        return title != null ? title : (href != null ? href : "Unknown");
    }
}
