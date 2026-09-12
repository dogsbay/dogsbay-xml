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

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

/**
 * Project-local, shareable editor configuration stored in a {@code .dogsbay/}
 * folder at the workspace root — the committed-and-personal split that lets a
 * team share a project's editor setup:
 *
 * <ul>
 *   <li>{@code .dogsbay/config.xml} (committed) — shared settings: project type,
 *       default root map (relative), framework <em>requirement</em> by name, and
 *       the default deliverable.</li>
 *   <li>{@code .dogsbay/local.xml} (gitignored) — per-user/machine overrides: a
 *       DITA-OT install path, and this user's active deliverable.</li>
 *   <li>{@code .dogsbay/.gitignore} — ignores {@code local.xml}.</li>
 * </ul>
 *
 * <p>Machine-specific <em>values</em> (DITA-OT paths) live only in {@code local.xml}
 * or the global config — never in the committed file, which names the framework by
 * requirement and stores all paths relative to the project root.
 *
 * <p>This holds the editor's <em>view</em> of the project — not deliverable
 * definitions, which stay in the DITA-OT {@code project.&#123;json,xml,yaml&#125;}
 * files (see {@code ditaproject.ProjectContextLoader}).
 */
public final class DogsbayProjectConfig {

    public static final String DIR = ".dogsbay";
    public static final String CONFIG_FILE = "config.xml";
    public static final String LOCAL_FILE = "local.xml";
    public static final String GITIGNORE_FILE = ".gitignore";

    // ── shared (config.xml) ──────────────────────────────────────────────
    private String projectType;
    private String defaultRootMap;          // relative to the workspace root
    private String framework;               // framework name (resolved locally)
    private String defaultDeliverableFile;  // relative to the workspace root
    private String defaultDeliverableName;
    private com.dogsbay.dogsbayaieditor.links.metadata.MetadataPolicy metadataPolicy =
            com.dogsbay.dogsbayaieditor.links.metadata.MetadataPolicy.empty();
    private com.dogsbay.xml.format.FormatStyle formatStyle;   // null = not declared (use user/default)
    private boolean formatOnSave;                              // project mandates format-on-save

    // ── personal (local.xml) ─────────────────────────────────────────────
    private String ditaOtPath;              // absolute, machine-specific
    private String activeDeliverableFile;   // relative to the workspace root
    private String activeDeliverableName;

    /** The {@code .dogsbay} directory for a workspace. */
    public static Path dir(Path workspaceRoot) {
        return workspaceRoot.resolve(DIR);
    }

    /** True when a committed {@code .dogsbay/config.xml} exists for the workspace. */
    public static boolean exists(Path workspaceRoot) {
        return Files.isRegularFile(dir(workspaceRoot).resolve(CONFIG_FILE));
    }

    /**
     * Load the shared config and personal overrides for a workspace. Missing files
     * are fine (their fields stay null); a parse error leaves the affected layer's
     * fields null rather than throwing.
     */
    public static DogsbayProjectConfig load(Path workspaceRoot) {
        DogsbayProjectConfig c = new DogsbayProjectConfig();
        Path d = dir(workspaceRoot);
        Element shared = readRoot(d.resolve(CONFIG_FILE));
        if (shared != null) {
            c.projectType = text(shared, "project-type");
            c.defaultRootMap = text(shared, "default-root-map");
            c.framework = text(shared, "framework");
            Element dd = shared.element("default-deliverable");
            if (dd != null) {
                c.defaultDeliverableFile = attr(dd, "file");
                c.defaultDeliverableName = attr(dd, "name");
            }
            c.metadataPolicy = com.dogsbay.dogsbayaieditor.links.metadata.MetadataPolicy
                    .parse(shared.element("metadata-policy"));
            c.formatStyle = com.dogsbay.xml.format.FormatStyleXml
                    .parse(shared.element(com.dogsbay.xml.format.FormatStyleXml.ELEMENT));
            Element fos = shared.element("format-on-save");
            c.formatOnSave = fos != null && Boolean.parseBoolean(fos.getTextTrim());
        }
        Element local = readRoot(d.resolve(LOCAL_FILE));
        if (local != null) {
            c.ditaOtPath = text(local, "dita-ot-path");
            Element ad = local.element("active-deliverable");
            if (ad != null) {
                c.activeDeliverableFile = attr(ad, "file");
                c.activeDeliverableName = attr(ad, "name");
            }
        }
        return c;
    }

    /** Write the shared {@code config.xml} and ensure {@code .gitignore} ignores
     *  {@code local.xml}. Creates the {@code .dogsbay} folder. */
    public void saveShared(Path workspaceRoot) throws IOException {
        Path d = dir(workspaceRoot);
        Files.createDirectories(d);

        Document doc = DocumentHelper.createDocument();
        Element root = doc.addElement("dogsbay-project");
        addText(root, "project-type", projectType);
        addText(root, "default-root-map", defaultRootMap);
        addText(root, "framework", framework);
        if (notBlank(defaultDeliverableFile) || notBlank(defaultDeliverableName)) {
            Element dd = root.addElement("default-deliverable");
            addAttr(dd, "file", defaultDeliverableFile);
            addAttr(dd, "name", defaultDeliverableName);
        }
        if (metadataPolicy != null && !metadataPolicy.isEmpty()) {
            root.add(metadataPolicy.toElement());
        }
        if (formatStyle != null) {
            root.add(com.dogsbay.xml.format.FormatStyleXml.toElement(formatStyle));
        }
        if (formatOnSave) {
            root.addElement("format-on-save").setText("true");
        }
        write(doc, d.resolve(CONFIG_FILE));
        ensureGitignore(d);
    }

    /** Write the personal {@code local.xml}. Creates the {@code .dogsbay} folder. */
    public void saveLocal(Path workspaceRoot) throws IOException {
        Path d = dir(workspaceRoot);
        Files.createDirectories(d);

        Document doc = DocumentHelper.createDocument();
        Element root = doc.addElement("dogsbay-project-local");
        addText(root, "dita-ot-path", ditaOtPath);
        if (notBlank(activeDeliverableFile) || notBlank(activeDeliverableName)) {
            Element ad = root.addElement("active-deliverable");
            addAttr(ad, "file", activeDeliverableFile);
            addAttr(ad, "name", activeDeliverableName);
        }
        write(doc, d.resolve(LOCAL_FILE));
        ensureGitignore(d);
    }

    private static void ensureGitignore(Path dogsbayDir) throws IOException {
        Path gi = dogsbayDir.resolve(GITIGNORE_FILE);
        String line = LOCAL_FILE;
        if (Files.isRegularFile(gi)) {
            if (Files.readAllLines(gi).stream().anyMatch(l -> l.trim().equals(line))) {
                return;
            }
            Files.writeString(gi, System.lineSeparator() + line + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.APPEND);
        } else {
            Files.writeString(gi, "# Personal, machine-specific overrides — not shared.\n"
                    + line + "\n");
        }
    }

    // ── dom4j helpers ────────────────────────────────────────────────────

    private static Element readRoot(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            SAXReader reader = new SAXReader();
            reader.setFeature(
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            Document doc = reader.read(file.toFile());
            return doc.getRootElement();
        } catch (Exception e) {
            return null; // malformed → treat as absent
        }
    }

    private static void write(Document doc, Path file) throws IOException {
        OutputFormat fmt = OutputFormat.createPrettyPrint();
        try (Writer w = Files.newBufferedWriter(file)) {
            XMLWriter xw = new XMLWriter(w, fmt);
            xw.write(doc);
            xw.flush();
        }
    }

    private static String text(Element parent, String name) {
        return blankToNull(parent.elementText(name));
    }

    private static String attr(Element e, String name) {
        return blankToNull(e.attributeValue(name));
    }

    private static void addText(Element parent, String name, String value) {
        if (notBlank(value)) {
            parent.addElement(name).setText(value);
        }
    }

    private static void addAttr(Element e, String name, String value) {
        if (notBlank(value)) {
            e.addAttribute(name, value);
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return notBlank(s) ? s : null;
    }

    // ── accessors ────────────────────────────────────────────────────────

    public String getProjectType() { return projectType; }
    public void setProjectType(String v) { this.projectType = v; }

    public String getDefaultRootMap() { return defaultRootMap; }
    public void setDefaultRootMap(String v) { this.defaultRootMap = v; }

    public String getFramework() { return framework; }
    public void setFramework(String v) { this.framework = v; }

    public String getDefaultDeliverableFile() { return defaultDeliverableFile; }
    public String getDefaultDeliverableName() { return defaultDeliverableName; }
    public void setDefaultDeliverable(String file, String name) {
        this.defaultDeliverableFile = file;
        this.defaultDeliverableName = name;
    }

    public String getDitaOtPath() { return ditaOtPath; }
    public void setDitaOtPath(String v) { this.ditaOtPath = v; }

    public com.dogsbay.dogsbayaieditor.links.metadata.MetadataPolicy getMetadataPolicy() {
        return metadataPolicy;
    }
    public void setMetadataPolicy(
            com.dogsbay.dogsbayaieditor.links.metadata.MetadataPolicy policy) {
        this.metadataPolicy = policy == null
                ? com.dogsbay.dogsbayaieditor.links.metadata.MetadataPolicy.empty() : policy;
    }

    /** The project's house style, or null when none is declared. */
    public com.dogsbay.xml.format.FormatStyle getFormatStyle() { return formatStyle; }
    public void setFormatStyle(com.dogsbay.xml.format.FormatStyle style) { this.formatStyle = style; }

    /** True when the project mandates format-on-save. */
    public boolean isFormatOnSave() { return formatOnSave; }
    public void setFormatOnSave(boolean on) { this.formatOnSave = on; }

    public String getActiveDeliverableFile() { return activeDeliverableFile; }
    public String getActiveDeliverableName() { return activeDeliverableName; }
    public void setActiveDeliverable(String file, String name) {
        this.activeDeliverableFile = file;
        this.activeDeliverableName = name;
    }
}
