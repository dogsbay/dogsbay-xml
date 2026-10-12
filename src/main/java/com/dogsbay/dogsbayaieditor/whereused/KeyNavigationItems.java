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

package com.dogsbay.dogsbayaieditor.whereused;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.dogsbay.dogsbayaieditor.links.AttributeReferenceLocator.Located;
import com.dogsbay.dogsbayaieditor.links.KeyDefinition;
import com.dogsbay.dogsbayaieditor.links.KeySpace;

/**
 * Decides which navigation menu items a right-clicked reference offers —
 * pure logic (labels, enablement, targets), separated from Swing so it
 * tests headlessly. The plugin turns these descriptors into JMenuItems.
 */
public final class KeyNavigationItems {

    public enum Kind {
        GOTO_DEFINITION,
        /** No context map configured — prompt for one, then go to the definition. */
        GOTO_DEFINITION_PICK_MAP,
        OPEN_TARGET,
        FIND_KEY_USAGES,
        FIND_FILE_USAGES,
        /** Rename the key project-wide (plan shown before applying). */
        RENAME_KEY,
        /** Replace the key's usages with direct paths (plan shown first). */
        INLINE_KEY,
        /** Convert direct references to the target into key references. */
        KEYIFY,
        /** Replace conrefs to this element with the content itself. */
        INLINE_CONREF
    }

    /**
     * One menu item to offer.
     *
     * @param label   menu text
     * @param enabled false renders a disabled item whose tooltip explains why
     * @param tooltip explanation / target path (may be null)
     * @param kind    what the action does
     * @param file    navigation target (GOTO_DEFINITION, OPEN_TARGET) or
     *                usage target (FIND_FILE_USAGES); null otherwise
     * @param line    1-based line for GOTO_DEFINITION; -1 otherwise
     * @param key     key name for FIND_KEY_USAGES; null otherwise
     */
    public record Item(String label, boolean enabled, String tooltip,
                Kind kind, File file, int line, String key) {
    }

    private KeyNavigationItems() {
    }

    /**
     * Items for a located navigation attribute.
     *
     * @param located    the attribute under the click
     * @param keySpace   key space of the context map (never null; may be empty)
     * @param activeFile the file of the document being edited (may be null
     *                   for unsaved documents)
     * @param rootMap    the context map file, or null when none is configured
     */
    public static List<Item> itemsFor(Located located, KeySpace keySpace,
                               File activeFile, File rootMap) {
        List<Item> items = new ArrayList<>();
        if (located == null) {
            return items;
        }

        if (located.isKeyReference()) {
            String key = located.keyName();
            KeyDefinition def = keySpace.resolve(key);

            // Go to Key Definition
            if (rootMap == null) {
                // No context map known — offer to pick one (remembered for
                // the session), rather than a dead disabled item.
                items.add(new Item("Go to Key Definition ‘" + key + "’…",
                        true, "Choose a context map (no Default Root Map configured)",
                        Kind.GOTO_DEFINITION_PICK_MAP, null, -1, key));
            } else if (def == null) {
                items.add(new Item("Go to Key Definition ‘" + key + "’",
                        false, "Key not defined in " + rootMap.getName(),
                        Kind.GOTO_DEFINITION, null, -1, key));
            } else {
                items.add(new Item("Go to Key Definition ‘" + key + "’",
                        true, def.source().getAbsolutePath() + ":" + def.line(),
                        Kind.GOTO_DEFINITION, def.source(), def.line(), key));
            }

            // Open Key Target (the topic the key's href points at)
            File target = keySpace.resolveHrefFile(key);
            if (target != null && target.isFile()) {
                items.add(new Item("Open Key Target (" + target.getName() + ")",
                        true, target.getAbsolutePath(),
                        Kind.OPEN_TARGET, target, -1, key));
            } else if (def != null) {
                items.add(new Item("Open Key Target", false,
                        def.href() == null ? "Key has no href target"
                                : "Target not found: " + def.href(),
                        Kind.OPEN_TARGET, null, -1, key));
            }

            // Find Usages of the key — always possible
            items.add(new Item("Find Usages of Key ‘" + key + "’",
                    true, null, Kind.FIND_KEY_USAGES, null, -1, key));

            // Rename Key — always possible; the plan dialog handles scope
            items.add(new Item("Rename Key ‘" + key + "’…",
                    true, null, Kind.RENAME_KEY, null, -1, key));

            // Inline Key — needs a resolvable definition
            if (rootMap == null) {
                items.add(new Item("Inline Key ‘" + key + "’…", false,
                        "No context map to resolve the key",
                        Kind.INLINE_KEY, null, -1, key));
            } else if (def == null) {
                items.add(new Item("Inline Key ‘" + key + "’…", false,
                        "Key not defined in " + rootMap.getName(),
                        Kind.INLINE_KEY, null, -1, key));
            } else {
                items.add(new Item("Inline Key ‘" + key + "’…", true,
                        "Replace keyref/conkeyref usages with the direct path",
                        Kind.INLINE_KEY, null, -1, key));
            }
            return items;
        }

        // href / conref / src — a direct path reference
        String raw = located.value();
        String path = raw;
        int hash = path.indexOf('#');
        if (hash >= 0) {
            path = path.substring(0, hash);
        }
        if (path.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
            return items;                       // external URL — nothing to offer
        }

        File target;
        if (path.isEmpty()) {
            target = activeFile;                // fragment-only conref: same file
        } else if (new File(path).isAbsolute()) {
            target = new File(path);
        } else if (activeFile != null) {
            target = new File(activeFile.getParentFile(), path);
        } else {
            target = null;
        }

        if (target != null) {
            boolean exists = target.isFile();
            items.add(new Item("Open ‘" + target.getName() + "’",
                    exists, exists ? target.getAbsolutePath()
                            : "Target not found: " + raw,
                    Kind.OPEN_TARGET, exists ? target : null, -1, null));
            items.add(new Item("Find Usages of ‘" + target.getName() + "’",
                    true, null, Kind.FIND_FILE_USAGES, target, -1, null));

            // Keyify — href/conref convert to keyref/conkeyref; src doesn't
            if ("href".equals(located.attribute()) || "conref".equals(located.attribute())) {
                items.add(new Item("Convert References to Key…", exists,
                        exists ? "Replace direct references to " + target.getName()
                                + " with key references (project-wide)"
                                : "Target not found: " + raw,
                        Kind.KEYIFY, exists ? target : null, -1, null));
            }

            // Inline Conref — needs an element-level fragment to copy
            if ("conref".equals(located.attribute())) {
                String fragment = hash >= 0 ? located.value().substring(hash + 1) : "";
                int slash = fragment.indexOf('/');
                // conref fragments are topicId/elemId; no element id → no inline
                String elemId = slash >= 0 ? fragment.substring(slash + 1) : null;
                boolean usable = exists && elemId != null && !elemId.isEmpty();
                items.add(new Item("Inline Conref…", usable,
                        usable ? "Replace conrefs to " + target.getName() + "#…/"
                                + elemId + " with the content itself"
                                : (exists ? "Conref has no element id"
                                        : "Target not found: " + raw),
                        Kind.INLINE_CONREF, usable ? target : null, -1, elemId));
            }
        }
        return items;
    }
}
