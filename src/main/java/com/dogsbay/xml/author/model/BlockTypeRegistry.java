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

package com.dogsbay.xml.author.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dogsbay.xml.author.model.BlockType.Category;

/**
 * The set of block types available to a document, plus the nesting rules
 * between them. Insertions through {@link Transaction} are validated against
 * this registry; the raw-XML fallback type is always present and accepted by
 * any container so that unsupported markup can round-trip losslessly.
 */
import static com.dogsbay.xml.author.model.ContentModel.many;
import static com.dogsbay.xml.author.model.ContentModel.one;
import static com.dogsbay.xml.author.model.ContentModel.optional;
import static com.dogsbay.xml.author.model.ContentModel.some;

public final class BlockTypeRegistry {

    /** The profile name of the built-in DITA registry. */
    public static final String DITA_PROFILE = "dita";

    private final Map<String, BlockType> types;
    private final String profile;

    public BlockTypeRegistry(Collection<BlockType> blockTypes) {
        this(blockTypes, null);
    }

    /** A registry with a profile name; operations that only make sense for one vocabulary check it. */
    public BlockTypeRegistry(Collection<BlockType> blockTypes, String profile) {
        this.profile = profile;
        Map<String, BlockType> map = new LinkedHashMap<>();
        for (BlockType t : blockTypes) {
            if (map.put(t.getName(), t) != null) {
                throw new IllegalArgumentException("duplicate block type: " + t.getName());
            }
        }
        map.computeIfAbsent(BlockType.RAW_NAME,
                n -> BlockType.builder(n, Category.RAW, "XML").preserveSpace().build());
        this.types = Map.copyOf(map);
    }

    public BlockType get(String name) {
        return types.get(name);
    }

    /** Whether this is the DITA profile, whose table and list conventions the block operations know. */
    public boolean isDita() {
        return DITA_PROFILE.equals(profile);
    }

    public BlockType require(String name) {
        BlockType t = types.get(name);
        if (t == null) {
            throw new IllegalArgumentException("unknown block type: " + name);
        }
        return t;
    }

    public BlockType raw() {
        return types.get(BlockType.RAW_NAME);
    }

    public Collection<BlockType> all() {
        return types.values();
    }

    /**
     * Whether {@code child} may be inserted under {@code parent}. Raw blocks
     * are accepted by anything that can have children at all.
     */
    public boolean isValidChild(BlockType parent, BlockType child) {
        if (parent.getCategory() == Category.VOID || parent.getCategory() == Category.RAW) {
            return false;
        }
        if (child.getCategory() == Category.RAW) {
            return true;
        }
        return parent.getAllowedChildren().contains(child.getName());
    }

    /** The insertable child types for {@code parent}, in declaration order. */
    /**
     * The child types that can still be inserted somewhere under {@code parent}
     * given its current children: membership, and for a content model, a
     * position where order and counts allow it.
     */
    public List<BlockType> insertableChildren(AuthorBlock parent) {
        List<BlockType> result = new ArrayList<>();
        ContentModel model = parent.getType().getContentModel();
        List<String> names = new ArrayList<>();
        for (AuthorBlock c : parent.getChildren()) {
            if (!BlockType.RAW_NAME.equals(c.getType().getName())) {
                names.add(c.getType().getName());
            }
        }
        boolean modelled = model != null && model.accepts(names);   // an imported file may already be outside it
        for (BlockType t : allowedChildren(parent.getType())) {
            if (!modelled || model.canInsert(names, t.getName())) {
                result.add(t);
            }
        }
        return result;
    }

    /**
     * Where {@code type} goes under {@code parent} when {@code preferred} is
     * asked for: the same index without a content model, else the nearest
     * valid position, or -1 when the type cannot go anywhere.
     */
    public int insertIndex(AuthorBlock parent, String type, int preferred) {
        ContentModel model = parent.getType().getContentModel();
        int size = parent.getChildren().size();
        int p = Math.max(0, Math.min(preferred, size));
        if (model == null) {
            return p;
        }
        // positions are over the non-raw children; map back and forth around chips
        List<String> names = new ArrayList<>();
        List<Integer> realIndex = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            AuthorBlock c = parent.getChildren().get(i);
            if (!BlockType.RAW_NAME.equals(c.getType().getName())) {
                names.add(c.getType().getName());
                realIndex.add(i);
            }
        }
        int preferredKnown = 0;
        while (preferredKnown < realIndex.size() && realIndex.get(preferredKnown) < p) {
            preferredKnown++;
        }
        if (!model.accepts(names)) {
            return p;   // already outside the model: membership is all that can be asked
        }
        int at = model.insertIndex(names, type, preferredKnown);
        if (at < 0) {
            return -1;
        }
        if (at == preferredKnown) {
            return p;
        }
        return at >= realIndex.size() ? size : realIndex.get(at);
    }

    public List<BlockType> allowedChildren(BlockType parent) {
        List<BlockType> result = new ArrayList<>();
        for (String name : parent.getAllowedChildren()) {
            BlockType t = types.get(name);
            if (t != null) {
                result.add(t);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // DITA 1.3 profile (concept / task / reference / generic topic)
    // ------------------------------------------------------------------

    /** Common block-level content allowed in bodies, sections and similar. */
    private static final String[] FLOW_CONTENT = {
            "p", "ul", "ol", "sl", "dl", "parml", "codeblock", "pre", "lines", "screen", "msgblock", "lq",
            "note", "hazardstatement", "fig", "image", "simpletable", "table", "draft-comment", "required-cleanup"
    };

    private static final String[] BODY_CONTENT = merge(FLOW_CONTENT, "example", "section", "sectiondiv");

    public static BlockTypeRegistry ditaProfile() {
        List<BlockType> t = new ArrayList<>();

        // Topic roots
        t.add(container("concept", "Concept")
                .model(one("title"), optional("shortdesc", "abstract"), optional("prolog"), optional("conbody"),
                        optional("related-links")).build());
        t.add(container("task", "Task")
                .model(one("title"), optional("shortdesc", "abstract"), optional("prolog"), optional("taskbody"),
                        optional("related-links")).build());
        t.add(container("reference", "Reference")
                .model(one("title"), optional("shortdesc", "abstract"), optional("prolog"), optional("refbody"),
                        optional("related-links")).build());
        t.add(container("topic", "Topic")
                .model(one("title"), optional("shortdesc", "abstract"), optional("prolog"), optional("body"),
                        optional("related-links")).build());
        t.add(container("glossentry", "Glossary Entry")
                .model(one("glossterm"), optional("glossdef"), optional("glossBody"), optional("related-links")).build());
        t.add(container("glossgroup", "Glossary Group").model(one("title"), many("glossentry")).build());
        t.add(container("troubleshooting", "Troubleshooting")
                .model(one("title"), optional("shortdesc", "abstract"), optional("prolog"), optional("troublebody"),
                        optional("related-links")).build());

        // Topic-level text
        t.add(text("title", "Title").build());
        t.add(text("shortdesc", "Short Description").build());
        t.add(textWithChildren("abstract", "Abstract", merge(FLOW_CONTENT, "shortdesc")).build());

        // Bodies
        t.add(container("conbody", "Body").children(merge(BODY_CONTENT, "conbodydiv")).build());
        t.add(container("body", "Body").children(merge(BODY_CONTENT, "bodydiv")).build());
        t.add(container("taskbody", "Body")
                .model(many("prereq", "context"), optional("steps", "steps-unordered", "steps-informal"),
                        many("result"), optional("tasktroubleshooting"), many("example"), many("postreq")).build());
        t.add(container("refbody", "Body")
                .children("section", "refsyn", "example", "refbodydiv", "table", "simpletable", "properties").build());
        t.add(container("troublebody", "Body").model(optional("condition"), some("troubleSolution")).build());
        t.add(textWithChildren("condition", "Condition", merge(FLOW_CONTENT, "title")).build());
        t.add(container("troubleSolution", "Solution").children("cause", "remedy").build());
        t.add(textWithChildren("cause", "Cause", merge(FLOW_CONTENT, "title")).build());
        t.add(container("remedy", "Remedy")
                .children("title", "responsibleParty", "steps", "steps-unordered", "steps-informal").build());
        t.add(text("responsibleParty", "Responsible Party").build());
        t.add(container("conbodydiv", "Division").children(merge(FLOW_CONTENT, "example", "section")).build());
        t.add(container("bodydiv", "Division").children(merge(FLOW_CONTENT, "example", "section")).build());
        t.add(container("refbodydiv", "Division")
                .children("section", "refsyn", "example", "table", "simpletable", "properties").build());
        t.add(container("sectiondiv", "Division").children(FLOW_CONTENT).build());
        t.add(textWithChildren("refsyn", "Syntax", merge(FLOW_CONTENT, "title")).build());

        // General content
        t.add(text("p", "Paragraph").splitOnEnter().build());
        t.add(container("section", "Section").children(merge(FLOW_CONTENT, "title", "sectiondiv")).build());
        t.add(textWithChildren("note", "Note", FLOW_CONTENT).build());
        t.add(textWithChildren("hazardstatement", "Hazard Statement", merge(FLOW_CONTENT, "messagepanel")).build());
        t.add(textWithChildren("messagepanel", "Message Panel", FLOW_CONTENT).build());
        t.add(textWithChildren("example", "Example", merge(FLOW_CONTENT, "title")).build());
        t.add(textWithChildren("lq", "Long Quote", FLOW_CONTENT).splitOnEnter().build());
        t.add(textWithChildren("draft-comment", "Draft Comment", FLOW_CONTENT).build());
        t.add(textWithChildren("required-cleanup", "Required Cleanup", FLOW_CONTENT).build());
        t.add(container("ul", "Bulleted List").children("li").build());
        t.add(container("ol", "Numbered List").children("li").build());
        t.add(textWithChildren("li", "List Item", FLOW_CONTENT).splitOnEnter().build());
        t.add(container("sl", "Simple List").children("sli").build());
        t.add(text("sli", "Item").splitOnEnter().build());
        t.add(container("parml", "Parameter List").children("plentry").build());
        t.add(container("plentry", "Parameter").model(some("pt"), some("pd")).build());
        t.add(text("pt", "Parameter Term").build());
        t.add(textWithChildren("pd", "Parameter Description", FLOW_CONTENT).build());
        t.add(text("codeblock", "Code Block").preserveSpace().build());
        t.add(text("pre", "Preformatted").preserveSpace().build());
        t.add(text("lines", "Lines").preserveSpace().build());
        t.add(text("screen", "Screen").preserveSpace().build());
        t.add(text("msgblock", "Message Block").preserveSpace().build());
        t.add(container("fig", "Figure").model(optional("title"), optional("desc"), many("image", "codeblock", "pre", "p",
                "ul", "ol", "simpletable", "table", "lines", "lq", "note", "dl", "sl")).build());
        t.add(text("desc", "Description").build());
        t.add(BlockType.builder("image", Category.VOID, "Image").build());

        // Task structure
        t.add(textWithChildren("prereq", "Prerequisites", FLOW_CONTENT).build());
        t.add(textWithChildren("context", "Context", FLOW_CONTENT).build());
        t.add(textWithChildren("result", "Result", FLOW_CONTENT).build());
        t.add(textWithChildren("postreq", "Postrequisites", FLOW_CONTENT).build());
        t.add(container("steps", "Steps").model(many("stepsection"), some("step", "stepsection")).build());
        t.add(container("steps-unordered", "Unordered Steps").model(many("stepsection"), some("step", "stepsection")).build());
        t.add(textWithChildren("steps-informal", "Informal Steps", FLOW_CONTENT).build());
        t.add(text("stepsection", "Step Section").build());
        t.add(container("step", "Step")
                .model(many("note"), one("cmd"), many("info", "substeps", "tutorialinfo", "stepxmp", "choicetable", "choices"),
                        optional("stepresult"), optional("steptroubleshooting")).build());
        t.add(text("cmd", "Command").build());
        t.add(textWithChildren("info", "Info", FLOW_CONTENT).splitOnEnter().build());
        t.add(textWithChildren("tutorialinfo", "Tutorial Info", FLOW_CONTENT).build());
        t.add(textWithChildren("stepxmp", "Step Example", FLOW_CONTENT).build());
        t.add(textWithChildren("stepresult", "Step Result", FLOW_CONTENT).build());
        t.add(textWithChildren("steptroubleshooting", "Step Troubleshooting", FLOW_CONTENT).build());
        t.add(textWithChildren("tasktroubleshooting", "Troubleshooting", FLOW_CONTENT).build());
        t.add(container("substeps", "Substeps").children("substep").build());
        t.add(container("substep", "Substep")
                .model(many("note"), one("cmd"), many("info", "tutorialinfo", "stepxmp"), optional("stepresult"),
                        optional("steptroubleshooting")).build());
        t.add(container("choices", "Choices").children("choice").build());
        t.add(text("choice", "Choice").splitOnEnter().build());
        t.add(container("choicetable", "Choice Table").model(optional("chhead"), some("chrow")).build());
        t.add(container("chhead", "Header Row").children("choptionhd", "chdeschd").notDeletable().build());
        t.add(text("choptionhd", "Option Heading").notDeletable().build());
        t.add(text("chdeschd", "Description Heading").notDeletable().build());
        t.add(container("chrow", "Row").children("choption", "chdesc").build());
        t.add(text("choption", "Option").notDeletable().build());
        t.add(textWithChildren("chdesc", "Description", FLOW_CONTENT).notDeletable().build());

        // Glossary
        t.add(text("glossterm", "Term").build());
        t.add(textWithChildren("glossdef", "Definition", FLOW_CONTENT).build());
        t.add(container("glossBody", "Glossary Body").children("glossSurfaceForm", "glossUsage", "glossAlt").build());
        t.add(text("glossSurfaceForm", "Surface Form").build());
        t.add(textWithChildren("glossUsage", "Usage", FLOW_CONTENT).build());
        t.add(container("glossAlt", "Alternate").children("glossAcronym", "glossAbbreviation", "glossSynonym",
                "glossShortForm", "glossStatus").build());
        t.add(text("glossAcronym", "Acronym").build());
        t.add(text("glossAbbreviation", "Abbreviation").build());
        t.add(text("glossSynonym", "Synonym").build());
        t.add(text("glossShortForm", "Short Form").build());
        t.add(text("glossStatus", "Status").build());

        // Definition lists
        t.add(container("dl", "Definition List").model(optional("dlhead"), some("dlentry")).build());
        t.add(container("dlhead", "Header").children("dthd", "ddhd").notDeletable().build());
        t.add(text("dthd", "Term Heading").notDeletable().build());
        t.add(text("ddhd", "Description Heading").notDeletable().build());
        t.add(container("dlentry", "Entry").model(some("dt"), some("dd")).build());
        t.add(text("dt", "Term").build());
        t.add(textWithChildren("dd", "Description", FLOW_CONTENT).build());

        // Related links
        t.add(container("related-links", "Related Links").children("link").build());

        // Prolog: topic metadata. The common members are editable; the rest stay raw chips.
        t.add(container("prolog", "Prolog").children("author", "source", "publisher", "copyright", "critdates",
                "permissions", "metadata", "resourceid").build());
        t.add(text("author", "Author").build());
        t.add(text("source", "Source").build());
        t.add(text("publisher", "Publisher").build());
        t.add(container("copyright", "Copyright").children("copyryear", "copyrholder").build());
        t.add(BlockType.builder("copyryear", Category.VOID, "Copyright Year").build());
        t.add(text("copyrholder", "Copyright Holder").build());
        t.add(container("critdates", "Critical Dates").children("created", "revised").build());
        t.add(BlockType.builder("created", Category.VOID, "Created").build());
        t.add(BlockType.builder("revised", Category.VOID, "Revised").build());
        t.add(BlockType.builder("permissions", Category.VOID, "Permissions").build());
        t.add(container("metadata", "Metadata").children("audience", "category", "keywords", "othermeta").build());
        t.add(BlockType.builder("audience", Category.VOID, "Audience").build());
        t.add(text("category", "Category").build());
        t.add(text("keywords", "Keywords").build());
        t.add(BlockType.builder("othermeta", Category.VOID, "Other Metadata").build());
        t.add(BlockType.builder("resourceid", Category.VOID, "Resource Id").build());
        t.add(BlockType.builder("link", Category.VOID, "Link").build());

        // Simple tables
        t.add(container("simpletable", "Table").model(optional("sthead"), some("strow")).build());
        t.add(container("sthead", "Header Row").children("stentry").notDeletable().build());
        t.add(container("strow", "Row").children("stentry").build());
        t.add(textWithChildren("stentry", "Cell", FLOW_CONTENT).notDeletable().build());

        // CALS tables
        t.add(container("table", "CALS Table").model(optional("title"), optional("desc"), some("tgroup")).build());
        t.add(container("tgroup", "Column Group").model(many("colspec"), optional("thead"), one("tbody")).build());
        t.add(BlockType.builder("colspec", Category.VOID, "Column").build());
        t.add(container("thead", "Header Rows").children("row").notDeletable().build());
        t.add(container("tbody", "Body Rows").children("row").notDeletable().build());
        t.add(container("row", "Row").children("entry").build());
        t.add(textWithChildren("entry", "Cell", FLOW_CONTENT).notDeletable().build());

        // Reference properties
        t.add(container("properties", "Properties").children("property").build());
        t.add(container("property", "Property")
                .model(optional("proptype"), optional("propvalue"), optional("propdesc")).build());
        t.add(text("proptype", "Type").notDeletable().build());
        t.add(text("propvalue", "Value").notDeletable().build());
        t.add(textWithChildren("propdesc", "Description", FLOW_CONTENT).notDeletable().build());

        return new BlockTypeRegistry(t, DITA_PROFILE);
    }

    private static BlockType.Builder text(String name, String label) {
        return BlockType.builder(name, Category.TEXT, label);
    }

    private static BlockType.Builder textWithChildren(String name, String label, String... children) {
        return BlockType.builder(name, Category.TEXT, label).children(children);
    }

    private static BlockType.Builder container(String name, String label) {
        return BlockType.builder(name, Category.CONTAINER, label);
    }

    private static String[] merge(String[] base, String... more) {
        String[] out = new String[base.length + more.length];
        System.arraycopy(base, 0, out, 0, base.length);
        System.arraycopy(more, 0, out, base.length, more.length);
        return out;
    }
}
