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

package com.dogsbay.dogsbayaieditor.dita.live;

import java.util.List;
import java.util.stream.Collectors;

import com.dogsbay.dogsbayaieditor.ditaproject.Deliverable;

/** Which deliverable an agent's live preview request means. */
public final class LivePreviewTargets {

    private LivePreviewTargets() {
    }

    /**
     * The deliverable to preview: the one named, which must publish with {@code dogsbay} or
     * {@code dogsbay-site}; without a name, the project's only such deliverable, else the active
     * deliverable when it is one.
     *
     * @param deliverables the project's deliverables
     * @param requested    the name asked for, or null or blank
     * @param active       the editor's active deliverable name, or null
     * @throws IllegalArgumentException with a message for the agent when no deliverable fits
     */
    public static Deliverable choose(List<Deliverable> deliverables, String requested, String active) {
        List<Deliverable> previewable = deliverables.stream().filter(LivePreview::canPreview).toList();
        if (requested != null && !requested.isBlank()) {
            Deliverable named = deliverables.stream().filter(d -> d.name().equals(requested)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No deliverable named '" + requested + "' in this project."));
            if (!LivePreview.canPreview(named)) {
                throw new IllegalArgumentException("The deliverable '" + requested + "' publishes " + named.transtype()
                        + "; only dogsbay and dogsbay-site deliverables have a live preview." + names(previewable));
            }
            return named;
        }
        if (previewable.isEmpty()) {
            throw new IllegalArgumentException("No deliverable in this project publishes a dogsbay site "
                    + "(transtype dogsbay or dogsbay-site).");
        }
        if (previewable.size() == 1) {
            return previewable.get(0);
        }
        return previewable.stream().filter(d -> d.name().equals(active)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Several deliverables can be previewed; name one." + names(previewable)));
    }

    private static String names(List<Deliverable> previewable) {
        return previewable.isEmpty() ? ""
                : " Previewable: " + previewable.stream().map(Deliverable::name).collect(Collectors.joining(", ")) + ".";
    }
}
