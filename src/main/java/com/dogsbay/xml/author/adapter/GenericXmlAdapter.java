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

package com.dogsbay.xml.author.adapter;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.dom4j.Comment;
import org.dom4j.DocumentException;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.ProcessingInstruction;
import org.dom4j.Text;
import org.dom4j.io.SAXReader;

import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.AuthorDocument;
import com.dogsbay.xml.author.model.BlockType;
import com.dogsbay.xml.author.model.BlockTypeRegistry;
import com.dogsbay.xml.author.model.InlineRun;
import com.dogsbay.xml.author.model.XmlAuxNode;

/**
 * Schema-driven (or document-inferred) block conversion for arbitrary XML.
 * No inline vocabulary: text blocks carry plain text only, and any mixed
 * content the model cannot hold falls back to lossless raw-XML blocks.
 * Whitespace collapses unless {@code xml:space="preserve"} is in effect.
 */
public final class GenericXmlAdapter implements BlockAdapter {

    private final BlockTypeRegistry fixedRegistry;
    private BlockTypeRegistry activeRegistry;

    /** Registry inferred from each imported document. */
    public GenericXmlAdapter() {
        this(null);
    }

    /** Registry from a grammar (see {@link GenericRegistryBuilder#fromSchema}). */
    public GenericXmlAdapter(BlockTypeRegistry registry) {
        this.fixedRegistry = registry;
        this.activeRegistry = registry;
    }

    public BlockTypeRegistry getRegistry() {
        return activeRegistry;
    }

    @Override
    public boolean supportsInlineStyles() {
        return false;
    }

    // ------------------------------------------------------------------
    // Import
    // ------------------------------------------------------------------

    @Override
    public AuthorDocument importDocument(Element root) {
        activeRegistry = fixedRegistry != null
                ? fixedRegistry : GenericRegistryBuilder.fromDocument(root);
        AuthorDocument doc = new AuthorDocument(activeRegistry);
        BlockType rootType = activeRegistry.get(root.getQualifiedName());
        AuthorBlock rootBlock = rootType != null && rootType.getCategory() != BlockType.Category.RAW
                ? importElement(doc, root, rootType, false)
                : null;
        doc.setRoot(rootBlock != null ? rootBlock : doc.createRawBlock(root.asXML()));
        return doc;
    }

    @Override
    public AuthorBlock importFragment(AuthorDocument doc, Element element, AuthorBlock parent) {
        if (activeRegistry == null || doc.getRegistry() != activeRegistry) {
            activeRegistry = doc.getRegistry();
        }
        BlockType type = activeRegistry.get(element.getQualifiedName());
        AuthorBlock block = type == null || type.getCategory() == BlockType.Category.RAW
                ? null : importElement(doc, element, type, parent.preservesSpace());
        return block != null ? block : doc.createRawBlock(element.asXML());
    }

    @Override
    public Element exportFragment(AuthorBlock block) {
        return exportBlock(block);
    }

    private AuthorBlock importChild(AuthorDocument doc, Element element,
            BlockType parentType, boolean preserveSpace) {
        BlockType type = activeRegistry.get(element.getQualifiedName());
        if (type == null || !activeRegistry.isValidChild(parentType, type)) {
            return doc.createRawBlock(element.asXML());
        }
        AuthorBlock block = importElement(doc, element, type, preserveSpace);
        return block != null ? block : doc.createRawBlock(element.asXML());
    }

    /** Structured import; null when the content shape is not representable. */
    private AuthorBlock importElement(AuthorDocument doc, Element element,
            BlockType type, boolean inheritedPreserve) {
        AuthorBlock block = doc.createBlock(type.getName());
        boolean preserve = inheritedPreserve;
        for (Object attrObj : element.attributes()) {
            org.dom4j.Attribute attr = (org.dom4j.Attribute) attrObj;
            block.setAttribute(attr.getQualifiedName(), attr.getValue());
            if ("xml:space".equals(attr.getQualifiedName())) {
                preserve = "preserve".equals(attr.getValue());
            }
        }

        List<Node> content = contentOf(element);

        if (type.getCategory() == BlockType.Category.VOID) {
            return content.stream().allMatch(n -> n instanceof Text && n.getText().isBlank())
                    ? block : null;
        }

        // text prefix
        int blockStart = 0;
        if (type.hasText()) {
            StringBuilder text = new StringBuilder();
            while (blockStart < content.size() && content.get(blockStart) instanceof Text) {
                text.append(content.get(blockStart).getText());
                blockStart++;
            }
            String value = preserve ? text.toString() : collapse(text.toString());
            if (!value.isEmpty()) {
                block.setText(List.of(InlineRun.of(value)));
            }
        }

        // remaining content: block children and aux nodes only
        List<XmlAuxNode> pendingAux = new ArrayList<>();
        int childIndex = 0;
        for (int i = blockStart; i < content.size(); i++) {
            Node node = content.get(i);
            if (node instanceof Text) {
                if (!node.getText().isBlank()) {
                    return null; // interleaved mixed content
                }
            } else if (node instanceof Comment c) {
                pendingAux.add(XmlAuxNode.comment(c.getText()));
            } else if (node instanceof ProcessingInstruction pi) {
                pendingAux.add(XmlAuxNode.pi(pi.getTarget(), pi.getText()));
            } else if (node instanceof Element child) {
                AuthorBlock childBlock = importChild(doc, child, type, preserve);
                if (!pendingAux.isEmpty()) {
                    childBlock.setLeadingAux(pendingAux);
                    pendingAux = new ArrayList<>();
                }
                doc.attach(block, childIndex++, childBlock);
            } else {
                return null;
            }
        }
        if (!pendingAux.isEmpty()) {
            block.setTrailingAux(pendingAux);
        }
        return block;
    }

    private String collapse(String s) {
        return s.replaceAll("\\s+", " ").strip();
    }

    @SuppressWarnings("unchecked")
    private static List<Node> contentOf(Element element) {
        return (List<Node>) (List<?>) element.content();
    }

    // ------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------

    @Override
    public Element exportDocument(AuthorDocument doc) {
        return exportBlock(doc.getRoot());
    }

    private Element exportBlock(AuthorBlock block) {
        if (block.getType().getCategory() == BlockType.Category.RAW) {
            return parseRaw(block.getRawXml());
        }
        Element element = DocumentFactory.getInstance().createElement(block.getType().getName());
        for (Map.Entry<String, String> attr : block.getAttributes().entrySet()) {
            element.addAttribute(attr.getKey(), attr.getValue());
        }
        String text = block.getPlainText();
        if (!text.isEmpty()) {
            element.addText(text);
        }
        for (AuthorBlock child : block.getChildren()) {
            for (XmlAuxNode aux : child.getLeadingAux()) {
                emitAux(element, aux);
            }
            element.add(exportBlock(child));
        }
        for (XmlAuxNode aux : block.getTrailingAux()) {
            emitAux(element, aux);
        }
        return element;
    }

    private void emitAux(Element parent, XmlAuxNode aux) {
        DocumentFactory factory = DocumentFactory.getInstance();
        if (aux.kind() == XmlAuxNode.Kind.COMMENT) {
            parent.add(factory.createComment(aux.text()));
        } else {
            parent.add(factory.createProcessingInstruction(aux.target(), aux.text()));
        }
    }

    private Element parseRaw(String rawXml) {
        try {
            SAXReader reader = new SAXReader();
            reader.setValidation(false);
            Element root = reader.read(new StringReader(rawXml)).getRootElement();
            root.detach();
            return root;
        } catch (DocumentException e) {
            throw new IllegalStateException("raw block is no longer well-formed: " + e.getMessage(), e);
        }
    }
}
