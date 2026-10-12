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

package com.dogsbay.dogsbayaieditor.plugin.yaml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dogsbay.dogsbayaieditor.plugin.Plugin;
import com.dogsbay.dogsbayaieditor.plugin.PluginContext;

/**
 * Built-in plugin that adds YAML support: syntax highlighting with
 * color-coded keys, strings, numbers, keywords, comments, anchors, and tags.
 */
public class YamlPlugin implements Plugin {

    private static final Logger LOG = LoggerFactory.getLogger(YamlPlugin.class);

    @Override public String getId() { return "com.dogsbay.yaml"; }
    @Override public String getName() { return "YAML Support"; }
    @Override public String getDescription() { return "Syntax highlighting and outline for YAML files"; }
    @Override public boolean isBuiltIn() { return true; }

    @Override
    public void activate(PluginContext ctx) {
        LOG.info("Activating YAML plugin");
        ctx.registerFormat(new YamlDocumentFormat());
        LOG.info("YAML plugin activated — registered format for .yml, .yaml");
    }
}
