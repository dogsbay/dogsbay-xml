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

package com.dogsbay.dogsbayaieditor.plugin.inspector;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.dom4j.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XDocument;
import com.dogsbay.xml.editor.DocumentFormat;
import com.dogsbay.dogsbayaieditor.plugin.PluginContext;

/**
 * Panel that checks and displays link/reference status for the current document.
 * Supports XML/DITA, AsciiDoc, and Markdown link types.
 */
public class LinkCheckerPanel extends JPanel {

    private static final Logger LOG = LoggerFactory.getLogger(LinkCheckerPanel.class);

    private final PluginContext context;
    private DogsBayDocument currentDocument;
    private boolean needsRefresh = true;

    private final LinkTableModel tableModel;
    private final JTable linkTable;
    private final JLabel summaryLabel;

    public LinkCheckerPanel(PluginContext context) {
        super(new BorderLayout(0, 2));
        this.context = context;

        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // Summary label at top
        summaryLabel = new JLabel("No document open");
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        topBar.add(summaryLabel);
        add(topBar, BorderLayout.NORTH);

        // Links table
        tableModel = new LinkTableModel();
        linkTable = new JTable(tableModel);
        linkTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        linkTable.setFillsViewportHeight(true);
        linkTable.setShowGrid(false);
        linkTable.setRowHeight(20);

        // Column widths
        linkTable.getColumnModel().getColumn(0).setPreferredWidth(300);
        linkTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        linkTable.getColumnModel().getColumn(1).setMaxWidth(120);
        linkTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        linkTable.getColumnModel().getColumn(2).setMaxWidth(120);
        linkTable.getColumnModel().getColumn(3).setPreferredWidth(50);
        linkTable.getColumnModel().getColumn(3).setMaxWidth(80);

        // Status column renderer with colors
        linkTable.getColumnModel().getColumn(2).setCellRenderer(new StatusCellRenderer());

        // Click handler — double-click opens the target file
        linkTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = linkTable.getSelectedRow();
                if (row < 0 || row >= tableModel.getRowCount()) return;

                LinkEntry entry = tableModel.getEntry(row);
                try {
                    context.getUIService().setStatusMessage(
                            entry.type + ": " + entry.target);
                } catch (Exception ex) {
                    LOG.debug("Could not set status message", ex);
                }

                if (e.getClickCount() == 2) {
                    openLinkTarget(entry);
                }
            }
        });

        add(new JScrollPane(linkTable), BorderLayout.CENTER);

        // Button bar
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JButton checkButton = new JButton("Check Links");
        checkButton.addActionListener(e -> checkLinks());
        buttonBar.add(checkButton);
        add(buttonBar, BorderLayout.SOUTH);
    }

    /**
     * Sets the document and marks the panel as needing refresh.
     */
    public void setDocument(DogsBayDocument document) {
        this.currentDocument = document;
        this.needsRefresh = true;
        tableModel.clear();
        if (document == null) {
            summaryLabel.setText("No document open");
        } else {
            summaryLabel.setText("Click 'Check Links' to scan");
        }
    }

    /**
     * Returns true if a refresh is needed (for lazy loading on tab switch).
     */
    public boolean needsRefresh() {
        return needsRefresh;
    }

    /**
     * Scans the document for links and checks their status.
     */
    public void checkLinks() {
        needsRefresh = false;

        if (currentDocument == null) {
            tableModel.clear();
            summaryLabel.setText("No document open");
            return;
        }

        List<LinkEntry> links = new ArrayList<>();
        File baseDir = getBaseDirectory();

        try {
            DocumentFormat format = currentDocument.getDocumentFormat();
            String formatName = (format != null) ? format.getName() : "";

            if (currentDocument.isXML()) {
                links.addAll(checkXmlLinks(baseDir));
            } else if ("AsciiDoc".equalsIgnoreCase(formatName)) {
                links.addAll(checkAsciidocLinks(baseDir));
            } else if ("Markdown".equalsIgnoreCase(formatName)) {
                links.addAll(checkMarkdownLinks(baseDir));
            } else {
                // Try text-based checks for unknown formats
                String text = currentDocument.getText();
                if (text != null) {
                    links.addAll(checkMarkdownLinks(baseDir));
                    if (links.isEmpty()) {
                        links.addAll(checkAsciidocLinks(baseDir));
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Error checking links", e);
        }

        if (links.isEmpty()) {
            summaryLabel.setText("No links found");
        } else {
            long ok = links.stream().filter(l -> "OK".equals(l.status)).count();
            long notFound = links.stream().filter(l -> "Not Found".equals(l.status)).count();
            long external = links.stream().filter(l -> "External".equals(l.status)).count();
            summaryLabel.setText("Links: " + links.size()
                    + "  OK: " + ok
                    + "  Not Found: " + notFound
                    + "  External: " + external);
        }

        tableModel.setLinks(links);
    }

    // --- XML/DITA link checking ---

    List<LinkEntry> checkXmlLinks(File baseDir) {
        List<LinkEntry> links = new ArrayList<>();

        try {
            XDocument xdoc = currentDocument.getDocument();
            if (xdoc == null) {
                return links;
            }

            // Check href, conref, src, schemaLocation, noNamespaceSchemaLocation
            String[] linkAttrs = {"href", "conref", "src", "schemaLocation",
                    "noNamespaceSchemaLocation"};

            @SuppressWarnings("unchecked")
            List<Node> allNodes = xdoc.selectNodes("//*");
            if (allNodes == null) {
                return links;
            }

            for (Node node : allNodes) {
                if (!(node instanceof Element)) continue;
                Element elem = (Element) node;

                for (String attrName : linkAttrs) {
                    Attribute attr = elem.attribute(attrName);
                    if (attr == null) continue;

                    String value = attr.getValue();
                    if (value == null || value.trim().isEmpty()) continue;

                    // schemaLocation has pairs of namespace+location
                    if ("schemaLocation".equals(attrName)) {
                        String[] parts = value.trim().split("\\s+");
                        for (int i = 1; i < parts.length; i += 2) {
                            links.add(resolveLink(parts[i], attrName, baseDir,
                                    getLineNumber(elem)));
                        }
                    } else {
                        // Strip fragment for conref (file.xml#element)
                        String target = value;
                        if ("conref".equals(attrName) && target.contains("#")) {
                            target = target.substring(0, target.indexOf('#'));
                        }
                        if (!target.isEmpty()) {
                            links.add(resolveLink(target, attrName, baseDir,
                                    getLineNumber(elem)));
                        }
                    }
                }

                // Check XInclude
                String nsUri = elem.getNamespaceURI();
                if ("http://www.w3.org/2001/XInclude".equals(nsUri)
                        && "include".equals(elem.getName())) {
                    Attribute href = elem.attribute("href");
                    if (href != null && href.getValue() != null) {
                        links.add(resolveLink(href.getValue(), "xi:include", baseDir,
                                getLineNumber(elem)));
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Error checking XML links", e);
        }

        return links;
    }

    // --- AsciiDoc link checking ---

    private static final Pattern ASCIIDOC_INCLUDE = Pattern.compile(
            "include::([^\\[]+)\\[");
    private static final Pattern ASCIIDOC_IMAGE_BLOCK = Pattern.compile(
            "image::([^\\[]+)\\[");
    private static final Pattern ASCIIDOC_IMAGE_INLINE = Pattern.compile(
            "image:([^:\\[]+)\\[");
    private static final Pattern ASCIIDOC_LINK = Pattern.compile(
            "link:([^\\[]+)\\[");
    private static final Pattern ASCIIDOC_XREF = Pattern.compile(
            "xref:([^\\[]+)\\[");

    List<LinkEntry> checkAsciidocLinks(File baseDir) {
        List<LinkEntry> links = new ArrayList<>();
        String text = currentDocument.getText();
        if (text == null) return links;

        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;

            checkPatternMatches(ASCIIDOC_INCLUDE, line, "include", baseDir, lineNum, links);
            checkPatternMatches(ASCIIDOC_IMAGE_BLOCK, line, "image", baseDir, lineNum, links);
            checkPatternMatches(ASCIIDOC_IMAGE_INLINE, line, "image", baseDir, lineNum, links);
            checkPatternMatches(ASCIIDOC_LINK, line, "link", baseDir, lineNum, links);
            checkPatternMatches(ASCIIDOC_XREF, line, "xref", baseDir, lineNum, links);
        }

        return links;
    }

    // --- Markdown link checking ---

    private static final Pattern MD_LINK = Pattern.compile(
            "\\[([^\\]]*)\\]\\(([^)]+)\\)");
    private static final Pattern MD_IMAGE = Pattern.compile(
            "!\\[([^\\]]*)\\]\\(([^)]+)\\)");

    List<LinkEntry> checkMarkdownLinks(File baseDir) {
        List<LinkEntry> links = new ArrayList<>();
        String text = currentDocument.getText();
        if (text == null) return links;

        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;

            // Images first (superset match)
            Matcher imgMatcher = MD_IMAGE.matcher(line);
            while (imgMatcher.find()) {
                String target = imgMatcher.group(2).trim();
                links.add(resolveLink(target, "image", baseDir, lineNum));
            }

            // Links (skip images already matched)
            Matcher linkMatcher = MD_LINK.matcher(line);
            while (linkMatcher.find()) {
                // Skip if preceded by '!' (image)
                int start = linkMatcher.start();
                if (start > 0 && line.charAt(start - 1) == '!') continue;
                String target = linkMatcher.group(2).trim();
                links.add(resolveLink(target, "link", baseDir, lineNum));
            }
        }

        return links;
    }

    // --- Resolution helpers ---

    private void checkPatternMatches(Pattern pattern, String line, String type,
            File baseDir, int lineNum, List<LinkEntry> links) {
        Matcher m = pattern.matcher(line);
        while (m.find()) {
            String target = m.group(1).trim();
            links.add(resolveLink(target, type, baseDir, lineNum));
        }
    }

    LinkEntry resolveLink(String target, String type, File baseDir, int line) {
        if (target == null || target.isEmpty()) {
            return new LinkEntry(target, type, "Error", line);
        }

        // External URLs
        if (target.startsWith("http://") || target.startsWith("https://")
                || target.startsWith("ftp://") || target.startsWith("mailto:")) {
            return new LinkEntry(target, type, "External", line);
        }

        // Fragment-only references
        if (target.startsWith("#")) {
            return new LinkEntry(target, type, "Fragment", line);
        }

        // Strip fragment from path
        String path = target;
        if (path.contains("#")) {
            path = path.substring(0, path.indexOf('#'));
        }
        // Strip query string
        if (path.contains("?")) {
            path = path.substring(0, path.indexOf('?'));
        }

        if (baseDir == null) {
            return new LinkEntry(target, type, "Error", line);
        }

        try {
            File resolved = new File(baseDir, path);
            if (resolved.exists()) {
                return new LinkEntry(target, type, "OK", line);
            } else {
                return new LinkEntry(target, type, "Not Found", line);
            }
        } catch (Exception e) {
            return new LinkEntry(target, type, "Error", line);
        }
    }

    private File getBaseDirectory() {
        if (currentDocument == null) return null;

        try {
            URL url = currentDocument.getURL();
            if (url != null && "file".equals(url.getProtocol())) {
                File docFile = new File(url.toURI());
                return docFile.getParentFile();
            }
        } catch (Exception e) {
            LOG.debug("Could not determine base directory", e);
        }

        return null;
    }

    /**
     * Opens the link target file in the editor on double-click.
     */
    private void openLinkTarget(LinkEntry entry) {
        if (entry == null || entry.target == null || entry.target.isEmpty()) return;

        // Don't try to open external URLs, fragments, or error entries
        if ("External".equals(entry.status) || "Fragment".equals(entry.status)
                || "Error".equals(entry.status)) {
            return;
        }

        File baseDir = getBaseDirectory();
        if (baseDir == null) return;

        try {
            // Strip fragment and query
            String path = entry.target;
            if (path.contains("#")) path = path.substring(0, path.indexOf('#'));
            if (path.contains("?")) path = path.substring(0, path.indexOf('?'));

            File resolved = new File(baseDir, path).getCanonicalFile();
            if (resolved.exists() && resolved.isFile()) {
                context.getDocumentManager().open(
                    resolved.toURI().toURL(), null, true);
            }
        } catch (Exception ex) {
            LOG.debug("Could not open link target: {}", entry.target, ex);
        }
    }

    private int getLineNumber(Element elem) {
        // dom4j doesn't reliably track line numbers after parsing;
        // return -1 if unavailable
        return -1;
    }

    // --- Inner classes ---

    /**
     * Represents a single link entry in the table.
     */
    static class LinkEntry {
        final String target;
        final String type;
        final String status;
        final int line;

        LinkEntry(String target, String type, String status, int line) {
            this.target = target;
            this.type = type;
            this.status = status;
            this.line = line;
        }
    }

    /**
     * Table model for the links table.
     */
    static class LinkTableModel extends AbstractTableModel {
        private static final String[] COLUMN_NAMES = {"Target", "Type", "Status", "Line"};
        private List<LinkEntry> links = new ArrayList<>();

        @Override
        public int getRowCount() {
            return links.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            LinkEntry entry = links.get(rowIndex);
            switch (columnIndex) {
                case 0: return entry.target;
                case 1: return entry.type;
                case 2: return entry.status;
                case 3: return entry.line >= 0 ? entry.line : "--";
                default: return null;
            }
        }

        LinkEntry getEntry(int row) {
            return links.get(row);
        }

        void setLinks(List<LinkEntry> links) {
            this.links = new ArrayList<>(links);
            fireTableDataChanged();
        }

        void clear() {
            this.links.clear();
            fireTableDataChanged();
        }
    }

    /**
     * Renders the Status column with color coding.
     */
    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        private static final Color GREEN = new Color(0, 128, 0);
        private static final Color RED = new Color(200, 0, 0);
        private static final Color GREY = Color.GRAY;
        private static final Color ORANGE = new Color(200, 120, 0);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            if (!isSelected && value instanceof String) {
                switch ((String) value) {
                    case "OK":
                        c.setForeground(GREEN);
                        break;
                    case "Not Found":
                        c.setForeground(RED);
                        break;
                    case "External":
                    case "Fragment":
                        c.setForeground(GREY);
                        break;
                    case "Error":
                        c.setForeground(ORANGE);
                        break;
                    default:
                        c.setForeground(table.getForeground());
                }
            }
            return c;
        }
    }
}
