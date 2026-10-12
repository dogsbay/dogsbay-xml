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

package com.dogsbay.dogsbayaieditor.framework;

import com.dogsbay.xml.XElement;
import com.dogsbay.xml.properties.Properties;

/**
 * Handles the properties for an installed Framework.
 * Stored as {@code <framework-properties>} elements inside the main config file.
 *
 * Fields:
 * <ul>
 *   <li>name — display name of the framework</li>
 *   <li>version — version string from framework.xml</li>
 *   <li>folder-path — absolute path to installed framework directory</li>
 *   <li>dita-ot-path — absolute path to dita-ot/ subdirectory (if present)</li>
 *   <li>source-url — original GitHub URL used for import (for updates)</li>
 * </ul>
 */
public class FrameworkProperties extends Properties {

    public static final String FRAMEWORK_PROPERTIES = "framework-properties";

    private static final String NAME        = "name";
    private static final String VERSION     = "version";
    private static final String FOLDER_PATH = "folder-path";
    private static final String DITA_OT_PATH = "dita-ot-path";
    private static final String SOURCE_URL  = "source-url";

    /** Creates a new, empty framework properties object. */
    public FrameworkProperties() {
        super(new XElement(FRAMEWORK_PROPERTIES));
    }

    /** Wraps an existing XML element (loaded from config). */
    public FrameworkProperties(XElement element) {
        super(element);
    }

    /** Wraps an existing Properties object (loaded from config). */
    public FrameworkProperties(Properties props) {
        super(props.getElement());
    }

    public String getName() {
        return getText(NAME);
    }

    public void setName(String name) {
        set(NAME, name);
    }

    public String getVersion() {
        return getText(VERSION);
    }

    public void setVersion(String version) {
        set(VERSION, version);
    }

    public String getFolderPath() {
        return getText(FOLDER_PATH);
    }

    public void setFolderPath(String path) {
        set(FOLDER_PATH, path);
    }

    public String getDitaOtPath() {
        return getText(DITA_OT_PATH);
    }

    public void setDitaOtPath(String path) {
        set(DITA_OT_PATH, path);
    }

    public String getSourceUrl() {
        return getText(SOURCE_URL);
    }

    public void setSourceUrl(String url) {
        set(SOURCE_URL, url);
    }
}
