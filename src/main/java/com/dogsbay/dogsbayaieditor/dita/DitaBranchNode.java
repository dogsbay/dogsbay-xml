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

/**
 * A DITA 1.3 branch-filter variant in the Map Explorer tree: a {@code <ditavalref>}
 * placed inside a topicref/topicgroup. Shown as a badged leaf under its parent so
 * authors can see, inline, that the subtree fans out into filtered variants. The
 * actual duplication is DITA-OT's at build; this node is the in-process inventory
 * marker (one per {@code <ditavalref>}).
 */
public class DitaBranchNode extends DitaNode {

    /** The branch DITAVAL href as written (for the tooltip), or null. */
    private final String ditavalHref;

    public DitaBranchNode(String label, String ditavalHref) {
        super(label);
        this.ditavalHref = ditavalHref;
    }

    public String getDitavalHref() {
        return ditavalHref;
    }
}
