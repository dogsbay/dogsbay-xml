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

package com.dogsbay.dogsbayaieditor.ditaproject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 * Writes deliverable edits back into a DITA-OT project file <em>in place</em>,
 * preserving every field we don't manage. The user's project files are
 * authoritative; we mutate the parsed tree (not reconstruct it), so {@code output},
 * {@code params}, {@code context.id}, top-level {@code includes}/{@code contexts}/
 * {@code publications}, and any unknown keys survive the edit.
 *
 * <p>Supported for JSON and YAML (the formats DITA-OT round-trips and that the
 * starters use). XML project files are reported as unsupported for in-place edit
 * (callers fall back to hand-editing) — a follow-up can add dom4j-based writing.
 * YAML comments are not preserved (a Jackson limitation).
 */
public final class DitaProjectWriter {

    private DitaProjectWriter() {}

    /**
     * Add a deliverable (or update the existing one with the same {@code name})
     * in {@code projectFile}, preserving all other content. Creates the file if it
     * doesn't exist.
     */
    public static void upsertDeliverable(Path projectFile, DeliverableEdit edit)
            throws IOException {
        ObjectMapper mapper = mapperFor(projectFile);
        ObjectNode root;
        if (Files.exists(projectFile)) {
            JsonNode tree = mapper.readTree(projectFile.toFile());
            if (tree != null && !tree.isObject()) {
                // Refuse rather than silently discard a file whose root isn't an
                // object (a scalar/array root, or malformed) — don't destroy content.
                throw new IOException("Not a project object: " + projectFile);
            }
            root = tree instanceof ObjectNode on ? on : mapper.createObjectNode();
        } else {
            root = mapper.createObjectNode();
        }
        applyEdit(root, edit);
        writeTree(mapper, projectFile, root);
    }

    /**
     * Create a new project file containing a single deliverable. Fails if the file
     * already exists (use {@link #upsertDeliverable} to add to an existing one).
     */
    public static void createProject(Path projectFile, DeliverableEdit first)
            throws IOException {
        if (Files.exists(projectFile)) {
            throw new IOException("Project file already exists: " + projectFile);
        }
        upsertDeliverable(projectFile, first);
    }

    /**
     * Remove the deliverable named {@code name} from {@code projectFile}, preserving
     * all other content. Returns true if one was removed (false if absent or no
     * deliverables array). JSON/YAML only — XML is refused (edit by hand).
     */
    public static boolean deleteDeliverable(Path projectFile, String name)
            throws IOException {
        if (!Files.exists(projectFile)) {
            return false;
        }
        ObjectMapper mapper = mapperFor(projectFile);
        JsonNode tree = mapper.readTree(projectFile.toFile());
        if (!(tree instanceof ObjectNode root)) {
            throw new IOException("Not a project object: " + projectFile);
        }
        if (!(root.get("deliverables") instanceof ArrayNode arr)) {
            return false;
        }
        boolean removed = false;
        for (int i = arr.size() - 1; i >= 0; i--) {
            JsonNode n = arr.get(i);
            if (n.isObject() && name.equals(text(n, "name"))) {
                arr.remove(i);
                removed = true;
            }
        }
        if (removed) {
            writeTree(mapper, projectFile, root);
        }
        return removed;
    }

    /** True when this project file format supports in-place editing (JSON/YAML). */
    public static boolean isEditable(Path projectFile) {
        String fmt = ProjectContextLoader.formatOf(projectFile);
        return "json".equals(fmt) || "yaml".equals(fmt);
    }

    private static ObjectMapper mapperFor(Path projectFile) throws IOException {
        String fmt = ProjectContextLoader.formatOf(projectFile);
        return switch (fmt) {
            case "yaml" -> new YAMLMapper();
            case "json" -> new ObjectMapper();
            default -> throw new IOException(
                "In-place editing supports JSON/YAML project files; "
                + projectFile.getFileName() + " is " + fmt + ". Edit it by hand.");
        };
    }

    private static void applyEdit(ObjectNode root, DeliverableEdit edit) {
        ArrayNode deliverables = arrayChild(root, "deliverables");
        ObjectNode d = null;
        for (JsonNode n : deliverables) {
            if (n.isObject() && edit.name().equals(text(n, "name"))) {
                d = (ObjectNode) n;
                break;
            }
        }
        boolean isNew = d == null;
        if (isNew) {
            d = deliverables.addObject();
        }
        d.put("name", edit.name());

        // context — if it references a shared definition by id, leave the reference
        // intact (the input/ditavals live in that definition; inlining here would
        // break the link). Otherwise edit it in place, preserving id/unknown keys.
        if (isReference(d.get("context"))) {
            // shared context by idref — preserved as-is
        } else {
            ObjectNode context = objectChild(d, "context");
            if (isNew && context.get("id") == null) {
                context.put("id", edit.name());
            }
            context.put("input", edit.input());
            context.remove("ditaval"); // migrate any legacy flat key to profiles.ditavals
            if (edit.ditavals().isEmpty()) {
                JsonNode profiles = context.get("profiles");
                if (profiles instanceof ObjectNode prof) {
                    prof.remove("ditavals");
                    if (prof.isEmpty()) {
                        context.remove("profiles");
                    }
                }
            } else {
                ObjectNode profiles = objectChild(context, "profiles");
                ArrayNode ditavals = arrayChild(profiles, "ditavals");
                ditavals.removeAll();
                for (String dv : edit.ditavals()) {
                    ditavals.add(dv);
                }
            }
        }

        // output
        if (edit.output() == null || edit.output().isBlank()) {
            d.remove("output");
        } else {
            d.put("output", edit.output());
        }

        // publication — likewise preserve an idref reference; else set transtype + params
        if (isReference(d.get("publication"))) {
            // shared publication by idref — preserved as-is
        } else {
            ObjectNode publication = objectChild(d, "publication");
            if (edit.transtype() != null && !edit.transtype().isBlank()) {
                publication.put("transtype", edit.transtype());
            }
            if (edit.params().isEmpty()) {
                publication.remove("params");
            } else {
                ArrayNode params = arrayChild(publication, "params");
                params.removeAll();
                for (Param p : edit.params()) {
                    ObjectNode pn = params.addObject();
                    pn.put("name", p.name());
                    switch (p.kind()) {
                        case HREF -> pn.put("href", p.value());
                        case PATH -> pn.put("path", p.value());
                        default -> pn.put("value", p.value());
                    }
                }
            }
        }
    }

    /** True when a context/publication node references a shared definition by id
     *  (a textual idref, or an object carrying {@code idref}) rather than inlining it. */
    private static boolean isReference(JsonNode node) {
        if (node == null) {
            return false;
        }
        return node.isTextual() || (node.isObject() && node.get("idref") != null);
    }

    private static void writeTree(ObjectMapper mapper, Path projectFile, ObjectNode root)
            throws IOException {
        Path parent = projectFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (mapper instanceof YAMLMapper) {
            mapper.writeValue(projectFile.toFile(), root); // YAML is inherently formatted
        } else {
            mapper.writerWithDefaultPrettyPrinter().writeValue(projectFile.toFile(), root);
        }
    }

    /** Existing {@code ObjectNode} child, or a new empty one attached at {@code field}. */
    private static ObjectNode objectChild(ObjectNode parent, String field) {
        JsonNode n = parent.get(field);
        return n instanceof ObjectNode o ? o : parent.putObject(field);
    }

    /** Existing {@code ArrayNode} child, or a new empty one attached at {@code field}. */
    private static ArrayNode arrayChild(ObjectNode parent, String field) {
        JsonNode n = parent.get(field);
        return n instanceof ArrayNode a ? a : parent.putArray(field);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() ? v.asText() : null;
    }
}
