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

import java.nio.file.Files;
import java.nio.file.Path;

import com.dogsbay.xml.format.FormatStyle;

/**
 * Resolves the effective {@link FormatStyle} for a file or workspace, so every
 * write path (F4 action, CLI/MCP {@code format}, Author view, format-on-save)
 * agrees. Precedence: <b>project {@code .dogsbay/config.xml} &lt;format-style&gt;
 * → built-in default</b>. (A user-preferences layer can slot between these later;
 * for now the project house style is authoritative and the default is the
 * fallback, which is what headless CLI/MCP use.)
 */
public final class FormatStyleResolver {

    private FormatStyleResolver() {}

    /**
     * Supplies the user's default house style (set once by the editor at startup from
     * preferences). When unset — e.g. headless CLI/MCP — the fallback is the built-in
     * {@link FormatStyle#defaults()}.
     */
    private static volatile java.util.function.Supplier<FormatStyle> userDefaultSupplier;

    /** Register the user-default supplier (editor startup). */
    public static void setUserDefaultSupplier(java.util.function.Supplier<FormatStyle> supplier) {
        userDefaultSupplier = supplier;
    }

    /** The effective fallback when no project declares a style: user default, else built-in. */
    public static FormatStyle userDefault() {
        java.util.function.Supplier<FormatStyle> s = userDefaultSupplier;
        if (s != null) {
            try {
                FormatStyle u = s.get();
                if (u != null) {
                    return u;
                }
            } catch (Exception ignore) {
                // bad supplier → built-in default
            }
        }
        return FormatStyle.defaults();
    }

    /** The style for a workspace: its declared {@code <format-style>}, else the user default. */
    public static FormatStyle forWorkspace(Path workspaceRoot) {
        if (workspaceRoot != null) {
            try {
                FormatStyle declared = DogsbayProjectConfig.load(workspaceRoot).getFormatStyle();
                if (declared != null) {
                    return declared;
                }
            } catch (Exception ignore) {
                // unreadable config → fall through to the user default
            }
        }
        return userDefault();
    }

    /** The style for a file: walk up to the nearest {@code .dogsbay} project, else default. */
    public static FormatStyle forFile(Path file) {
        return forWorkspace(workspaceRootFor(file));
    }

    /** The project's declared style for a file, or {@code null} when none is declared
     *  (lets callers fall back to a user preference rather than the built-in default). */
    public static FormatStyle declaredForFile(Path file) {
        Path root = workspaceRootFor(file);
        if (root == null) {
            return null;
        }
        try {
            return DogsbayProjectConfig.load(root).getFormatStyle();
        } catch (Exception e) {
            return null;
        }
    }

    /** True when the file's project mandates format-on-save. */
    public static boolean isFormatOnSave(Path file) {
        Path root = workspaceRootFor(file);
        if (root == null) {
            return false;
        }
        try {
            return DogsbayProjectConfig.load(root).isFormatOnSave();
        } catch (Exception e) {
            return false;
        }
    }

    /** The nearest ancestor directory containing a {@code .dogsbay} folder, or null. */
    public static Path workspaceRootFor(Path file) {
        Path p = (file == null) ? null : file.toAbsolutePath().getParent();
        while (p != null) {
            if (Files.isDirectory(DogsbayProjectConfig.dir(p))) {
                return p;
            }
            p = p.getParent();
        }
        return null;
    }
}
