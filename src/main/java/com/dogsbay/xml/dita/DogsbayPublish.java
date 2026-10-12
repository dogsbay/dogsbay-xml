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

package com.dogsbay.xml.dita;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * DITA-OT builds with the dogsbay publishing plugin ({@code com.dogsbay.publish}),
 * whose converter runs with Node.js: the {@code dogsbay} and {@code dogsbay-site}
 * transtypes get {@code dogsbay.node} from {@link NodeLocator} unless the build's
 * parameters already set it.
 */
public final class DogsbayPublish {

    /** The plugin's parameter for the Node.js executable. */
    public static final String NODE_PARAM = "dogsbay.node";

    /** The plugin's parameter for where a publish writes the files it changed. */
    public static final String CHANGED_PATHS_PARAM = "dogsbay.changed.paths";

    /** The plugin folder inside a DITA-OT installation. */
    private static final String PLUGIN = "plugins/com.dogsbay.publish";

    /** Shown when a dogsbay build finds no Node.js. */
    public static final String NODE_MISSING = "Publishing a dogsbay site needs Node.js 20 or later, and none was found."
            + " Install it from https://nodejs.org, or set its path in Preferences > System > Node.js.";

    private DogsbayPublish() {}

    /**
     * The bundled dogsbay-ssg dev session entry in a DITA-OT installation, if the plugin
     * carries one. The site builder travels as a folder — {@code dev.mjs} loads chunks from
     * beside it — so this is a path into the installation, never a copied file.
     */
    public static Optional<Path> devSession(String ditaOtHome) {
        if (ditaOtHome == null || ditaOtHome.isBlank()) {
            return Optional.empty();
        }
        Path entry = Path.of(ditaOtHome).resolve(PLUGIN).resolve("ssg").resolve("dev.mjs");
        return Files.isRegularFile(entry) ? Optional.of(entry) : Optional.empty();
    }

    /** Shown when a live preview finds no bundled dev session. */
    public static final String DEV_SESSION_MISSING =
            "This DITA-OT installation has no dogsbay site builder, so it cannot run a live preview."
                    + " Import the bundled DITA-OT framework, or install the com.dogsbay.publish plugin that carries it.";

    /** True for the plugin's transtypes: {@code dogsbay} and {@code dogsbay-site}. */
    public static boolean isDogsbay(String transtype) {
        return transtype != null && (transtype.equals("dogsbay") || transtype.startsWith("dogsbay-"));
    }

    /**
     * The build's parameters with {@code dogsbay.node} added for a dogsbay transtype, found by
     * {@code locator}; other transtypes, and parameters that set it, are returned unchanged.
     *
     * @throws IOException with {@link #NODE_MISSING} when a dogsbay build finds no Node.js
     */
    public static Map<String, String> withNode(String transtype, Map<String, String> params, NodeLocator locator)
            throws IOException {
        if (!isDogsbay(transtype) || (params != null && params.get(NODE_PARAM) != null && !params.get(NODE_PARAM).isBlank())) {
            return params;
        }
        Optional<Path> node = locator.find();
        if (node.isEmpty()) {
            throw new IOException(NODE_MISSING);
        }
        Map<String, String> out = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
        out.put(NODE_PARAM, node.get().toString());
        return out;
    }
}
