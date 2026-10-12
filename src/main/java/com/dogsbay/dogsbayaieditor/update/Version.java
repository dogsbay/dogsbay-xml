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

package com.dogsbay.dogsbayaieditor.update;

/**
 * Simple semantic version parser and comparator.
 * Handles versions like "3.3.1" or "v3.3.1".
 */
public class Version implements Comparable<Version> {

    private final int major;
    private final int minor;
    private final int patch;
    private final String raw;

    public Version(String version) {
        this.raw = version;
        String v = version.startsWith("v") ? version.substring(1) : version;
        String[] parts = v.split("\\.");
        this.major = parts.length > 0 ? parseIntSafe(parts[0]) : 0;
        this.minor = parts.length > 1 ? parseIntSafe(parts[1]) : 0;
        this.patch = parts.length > 2 ? parseIntSafe(parts[2]) : 0;
    }

    private static int parseIntSafe(String s) {
        try {
            // Strip any suffix like "-beta", "-rc1"
            int idx = 0;
            while (idx < s.length() && Character.isDigit(s.charAt(idx))) idx++;
            return Integer.parseInt(s.substring(0, Math.max(1, idx)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public int compareTo(Version other) {
        int c = Integer.compare(major, other.major);
        if (c != 0) return c;
        c = Integer.compare(minor, other.minor);
        if (c != 0) return c;
        return Integer.compare(patch, other.patch);
    }

    public boolean isNewerThan(Version other) {
        return compareTo(other) > 0;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }

    public String getRaw() {
        return raw;
    }
}
