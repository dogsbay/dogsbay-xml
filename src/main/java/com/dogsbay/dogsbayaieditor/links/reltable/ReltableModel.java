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

package com.dogsbay.dogsbayaieditor.links.reltable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.dogsbay.dogsbayaieditor.links.KeySpace;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;

/**
 * Parses every {@code <reltable>} in a DITA map into a positioned {@link Reltable}
 * tree over the map's exact source text (so {@link ReltableWriter} can splice
 * formatting-preserving edits). GUI-free — parsed via the headless positioned
 * document model, the same path {@link com.dogsbay.dogsbayaieditor.links.map.MapModel}
 * uses. Targets are resolved against the map directory ({@code @href}) and the key
 * space ({@code @keyref}) when one is supplied.
 */
public record ReltableModel(File map, String text, String rootName,
        int rootContentEnd, List<Reltable> reltables) {

    public static ReltableModel parse(File mapFile) throws IOException {
        return parse(mapFile, null);
    }

    public static ReltableModel parse(File mapFile, KeySpace keys) throws IOException {
        String text = Files.readString(mapFile.toPath(), StandardCharsets.UTF_8);
        XElement root;
        try {
            root = new DogsBayDocument(text).getRoot();
        } catch (Exception e) {
            throw new IOException("Failed to parse map: " + mapFile, e);
        }
        if (root == null) {
            throw new IOException("Empty or invalid map: " + mapFile);
        }
        File mapDir = mapFile.getAbsoluteFile().getParentFile();
        List<Reltable> tables = new ArrayList<>();
        for (XElement child : children(root)) {
            if ("reltable".equals(child.getName())) {
                tables.add(buildTable(child, mapDir, keys, mapFile));
            }
        }
        return new ReltableModel(mapFile, text, root.getName(),
                root.getContentEndPosition(), tables);
    }

    private static Reltable buildTable(XElement el, File mapDir, KeySpace keys, File map) {
        List<Reltable.Column> columns = new ArrayList<>();
        List<Reltable.Row> rows = new ArrayList<>();
        for (XElement child : children(el)) {
            switch (child.getName()) {
                case "relheader" -> {
                    for (XElement spec : children(child)) {
                        if ("relcolspec".equals(spec.getName())) {
                            columns.add(new Reltable.Column(
                                    attr(spec, "type"), attr(spec, "linking"),
                                    attr(spec, "collection-type"), columnLabel(spec)));
                        }
                    }
                }
                case "relrow" -> rows.add(buildRow(child, mapDir, keys));
                default -> { /* title, data, etc. — ignored */ }
            }
        }
        return new Reltable(el, attr(el, "title"), attr(el, "linking"),
                attr(el, "collection-type"), columns, rows, map);
    }

    private static Reltable.Row buildRow(XElement rowEl, File mapDir, KeySpace keys) {
        List<Reltable.Cell> cells = new ArrayList<>();
        for (XElement cellEl : children(rowEl)) {
            if (!"relcell".equals(cellEl.getName())) {
                continue;
            }
            List<Reltable.Target> targets = new ArrayList<>();
            for (XElement t : children(cellEl)) {
                if ("topicref".equals(t.getName())) {
                    String href = attr(t, "href");
                    targets.add(new Reltable.Target(t, href, attr(t, "keyref"),
                            attr(t, "navtitle"), attr(t, "type"),
                            resolve(href, attr(t, "keyref"), mapDir, keys)));
                }
            }
            cells.add(new Reltable.Cell(cellEl, attr(cellEl, "type"), attr(cellEl, "linking"),
                    attr(cellEl, "collection-type"), targets));
        }
        return new Reltable.Row(rowEl, cells);
    }

    private static File resolve(String href, String keyref, File mapDir, KeySpace keys) {
        if (href != null && !href.isBlank() && !href.startsWith("#") && !href.contains("://")) {
            String path = href;
            int hash = path.indexOf('#');
            if (hash >= 0) {
                path = path.substring(0, hash);
            }
            if (!path.isBlank() && mapDir != null) {
                return new File(mapDir, path).getAbsoluteFile().toPath().normalize().toFile();
            }
        }
        if (keyref != null && !keyref.isBlank() && keys != null) {
            String key = keyref.contains("/") ? keyref.substring(0, keyref.indexOf('/')) : keyref;
            return keys.resolveHrefFile(key);
        }
        return null;
    }

    private static String columnLabel(XElement spec) {
        String type = spec.getAttribute("type");
        return type != null && !type.isBlank() ? type : "topic";
    }

    private static String attr(XElement el, String name) {
        String v = el.getAttribute(name);
        return v == null || v.isBlank() ? null : v;
    }

    private static List<XElement> children(XElement parent) {
        List<XElement> out = new ArrayList<>();
        for (Object o : parent.elements()) {
            if (o instanceof XElement e) {
                out.add(e);
            }
        }
        return out;
    }
}
