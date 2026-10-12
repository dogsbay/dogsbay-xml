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

package com.dogsbay.xml.edit;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.xml.sax.InputSource;

import com.dogsbay.xml.review.Marks;
import com.dogsbay.xml.review.ProposalOps;
import com.dogsbay.xml.review.XmlTokens;
import com.dogsbay.xml.review.XmlTokens.Kind;
import com.dogsbay.xml.review.XmlTokens.Token;

/**
 * Structural edits to an XML document as an ordered list of steps: insert, delete, replace, move,
 * wrap, unwrap, rename, set or remove an attribute, set an element's text, and test a precondition.
 * The vocabulary follows the DOM and jQuery ({@code before}, {@code after}, {@code wrap}), addressing
 * follows XML Patch (RFC 5261: an XPath that must select exactly one element), and the batch follows
 * JSON Patch (RFC 6902): steps apply in order, each seeing the result of the ones before, and the whole
 * list applies or none of it does. See {@code plans/structural-edits.md}.
 *
 * <p>Edits are made to the text at the selected element's exact offsets, so everything outside them
 * keeps its formatting byte for byte; content is indented to where it goes only where its line breaks
 * are formatting (never inside text, code or {@code xml:space="preserve"}), with the document's own line
 * ending and indentation. With an author, the result also carries review marks made from the steps
 * rather than inferred from a diff: what a step inserts is marked {@code status="new"}, what it removes
 * stays in place marked {@code status="deleted"}, and a step that changes an element in place keeps the
 * old element as deleted beside the new one. Marks are added to a tag as text, not by rewriting it. An
 * element inserted by an earlier step of the same batch is changed in place, since it is new anyway.
 * Callers that need the marks to be exact check them ({@code StructuralEditPolicy} does), since a few
 * shapes cannot be marked so that accepting and rejecting restore both sides byte for byte.
 *
 * <p>During the batch every element carries a temporary identity attribute (a name the document does
 * not use), so that a step can select an element by XPath on the document as it stands (without what
 * the batch marked deleted, and without the marks) and the edit lands on that element's text. The
 * attributes are removed from the result.
 */
public final class StructuralEdit {

    /** The operations. */
    public enum Op {
        INSERT, DELETE, REPLACE, MOVE, WRAP, UNWRAP, RENAME, SET_ATTRIBUTE, REMOVE_ATTRIBUTE, SET_TEXT, TEST;

        static Op of(String name) {
            if (name == null) {
                throw new IllegalArgumentException("a step needs an op");
            }
            try {
                return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unknown op \"" + name + "\"; the ops are insert, delete, replace, "
                        + "move, wrap, unwrap, rename, set-attribute, remove-attribute, set-text and test");
            }
        }
    }

    /** Where an insert or a move puts its content, relative to the element it names. */
    public enum Position {
        BEFORE, AFTER, FIRST_CHILD, LAST_CHILD;

        static Position of(String name) {
            if (name == null) {
                throw new IllegalArgumentException("position is required: before, after, first-child or last-child");
            }
            try {
                return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unknown position \"" + name
                        + "\"; use before, after, first-child or last-child");
            }
        }
    }

    /**
     * One step.
     *
     * @param op       the operation
     * @param select   XPath selecting exactly one element, or {@code #id} for the element with that id
     * @param position for insert and move: before, after, first-child or last-child
     * @param xml      for insert and replace: the XML to put in; for wrap: the wrapping element, empty
     * @param name     for rename: the new name; for the attribute ops and test: the attribute
     * @param value    for set-attribute: its value; for test: the attribute value expected
     * @param text     for set-text: the element's new text; for test: the element's text expected
     * @param to       for move: XPath or {@code #id} of the element the moved one goes relative to
     */
    public record Step(String op, String select, String position, String xml, String name, String value,
            String text, String to) {
    }

    /** A step that could not apply: which one, and why. Nothing of the batch is applied. */
    public static final class StepException extends Exception {
        private static final long serialVersionUID = 1L;
        private final int step;

        StepException(int step, String op, String message) {
            super("Step " + step + " (" + op + "): " + message);
            this.step = step;
        }

        /** The step's number, from 1. */
        public int step() {
            return step;
        }
    }

    /** The result: the new text, and a line for each step saying what it did. */
    public record Result(String text, List<String> changes) {
    }

    /** Elements whose text is not reindented: line breaks and spaces in them are content. */
    private static final Set<String> PREFORMATTED = Set.of("pre", "codeblock", "screen", "msgblock", "lines");
    private static final Set<String> REVIEW_ATTRIBUTES = Set.of("status", "rev", "otherprops");

    private final String author;
    private final String idName;
    private final Pattern idPattern;
    private final String unit;
    private final String newline;
    private final StringBuilder doc;
    private int nextId = 1;
    /** Elements this batch marked deleted: gone from the document as later steps see it. */
    private final Set<String> deleted = new HashSet<>();
    /** Elements this batch inserted, and their descendants: changed in place, since they are new anyway. */
    private final Set<String> created = new HashSet<>();
    private final List<String> changes = new ArrayList<>();

    private StructuralEdit(String source, String author) {
        this.author = author;
        String name = "dogsbay-sx";
        for (int n = 2; source.contains(name); n++) {
            name = "dogsbay-sx" + n; // a name the document does not use, so no element is mistaken for another
        }
        this.idName = name;
        this.idPattern = Pattern.compile(" " + Pattern.quote(name) + "=\"\\d+\"");
        this.unit = indentUnit(source);
        this.newline = source.contains("\r\n") ? "\r\n" : "\n";
        this.doc = new StringBuilder(source);
    }

    /**
     * Apply {@code steps} to {@code source} in order, all or none.
     *
     * @param author the review author ({@code ai:claude-acp}) to mark the changes as proposals, or null
     *               for a plain edit
     */
    public static Result apply(String source, List<Step> steps, String author) throws StepException {
        if (author != null && !Marks.isAuthorId(author)) {
            throw new IllegalArgumentException("author must be an id like ai:claude-acp, not '" + author + "'");
        }
        StructuralEdit edit = new StructuralEdit(source, author);
        edit.doc.replace(0, edit.doc.length(), edit.identify(source, false));
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            String op = step.op() == null ? "?" : step.op();
            try {
                edit.run(step);
            } catch (StepFailure | IllegalArgumentException | IllegalStateException e) {
                throw new StepException(i + 1, op, e.getMessage());
            } catch (RuntimeException e) {
                throw new StepException(i + 1, op, "could not apply (" + e + ")");
            }
        }
        return new Result(edit.idPattern.matcher(edit.doc).replaceAll(""), List.copyOf(edit.changes));
    }

    /** A step's own failure, as distinct from a defect. */
    private static final class StepFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        StepFailure(String message) {
            super(message);
        }
    }

    // ── steps ───────────────────────────────────────────────────────────

    private void run(Step s) {
        Op op = Op.of(s.op());
        Target t = select(s.select(), "select");
        switch (op) {
            case INSERT -> {
                String content = fragment(s.xml(), "xml");
                Position position = Position.of(s.position());
                if ((position == Position.BEFORE || position == Position.AFTER) && t.parentless()) {
                    throw new StepFailure("the document element has no siblings; insert inside it");
                }
                int at = insert(t, position, content, true);
                changes.add("inserted " + describe(content) + " " + name(position) + " " + t.describe() + " (line "
                        + line(at) + ")");
            }
            case DELETE -> {
                requireParent(t);
                String what = t.describe();
                remove(t);
                changes.add("deleted " + what);
            }
            case REPLACE -> {
                requireParent(t);
                String content = fragment(s.xml(), "xml");
                String what = t.describe();
                int at = replaceElement(t, content);
                changes.add("replaced " + what + " with " + describe(content) + " (line " + line(at) + ")");
            }
            case MOVE -> {
                requireParent(t);
                Position position = Position.of(s.position());
                Target dest = select(s.to(), "to");
                if (dest.start >= t.start && dest.end <= t.end) {
                    throw new StepFailure("cannot move an element into itself");
                }
                if ((position == Position.BEFORE || position == Position.AFTER) && dest.parentless()) {
                    throw new StepFailure("the document element has no siblings; move inside it");
                }
                String what = t.describe();
                boolean alreadyNew = markedNewByThisBatch(t);
                String content = copy(t);
                String destId = dest.id;
                remove(t);
                Target destNow = byId(destId);
                // an element this batch inserted already carries its mark; marking it again would stack marks
                int at = insert(destNow, position, content, !alreadyNew);
                changes.add("moved " + what + " " + name(position) + " " + destNow.describe() + " (line " + line(at) + ")");
            }
            case WRAP -> {
                requireParent(t);
                Token wrapper = wrapper(s.xml());
                String inner = copy(t);
                String open = XmlTokens.renderTag(wrapper.name(), wrapper.attrs(), false);
                String close = "</" + wrapper.name() + ">";
                String indent = indentAt(t.start);
                boolean block = indent != null && restOfLineBlank(t.end) && reindentable(inner);
                String wrapped = block
                        ? open + newline + indent + unit + reindent(inner, indent + unit) + newline + indent + close
                        : open + inner + close;
                String what = t.describe();
                int at = replaceElement(t, wrapped);
                changes.add("wrapped " + what + " in <" + wrapper.name() + "> (line " + line(at) + ")");
            }
            case UNWRAP -> {
                requireParent(t);
                String inner = t.empty ? "" : withoutDeleted(doc.substring(t.startTagEnd, t.endTagStart));
                boolean block = indentAt(t.start) != null && restOfLineBlank(t.end);
                String content = stripIds(block && reindentable(inner) ? dedent(inner.strip()) : inner);
                String what = t.describe();
                if (content.isBlank() && block) {
                    remove(t);
                } else if (content.isEmpty()) {
                    remove(t);
                } else {
                    replaceElement(t, content);
                }
                changes.add("unwrapped " + what);
            }
            case RENAME -> {
                String name = required(s.name(), "name");
                requireName(name);
                String what = t.describe();
                changeInPlace(t, rename(t, name));
                changes.add("renamed " + what + " to <" + name + ">");
            }
            case SET_ATTRIBUTE -> {
                String name = required(s.name(), "name");
                requireName(name);
                refuseReviewAttribute(t, name);
                String value = s.value() == null ? "" : s.value();
                changeInPlace(t, withAttribute(t, name, value));
                changes.add("set @" + name + "=\"" + value + "\" on " + t.describe());
            }
            case REMOVE_ATTRIBUTE -> {
                String name = required(s.name(), "name");
                refuseReviewAttribute(t, name);
                if (!t.token.attrs().containsKey(name)) {
                    throw new StepFailure(t.describe() + " has no attribute " + name);
                }
                changeInPlace(t, withoutAttribute(t, name));
                changes.add("removed @" + name + " from " + t.describe());
            }
            case SET_TEXT -> {
                if (t.hasChildElements) {
                    throw new StepFailure(t.describe() + " holds elements, which set-text would "
                            + "remove; select the element that holds the words, or use replace");
                }
                String text = s.text() == null ? "" : s.text();
                String element = t.empty
                        ? doc.substring(t.start, t.end).replaceFirst("\\s*/>$", ">") + XmlTokens.escapeText(text)
                                + "</" + t.token.name() + ">"
                        : doc.substring(t.start, t.startTagEnd) + XmlTokens.escapeText(text)
                                + doc.substring(t.endTagStart, t.end);
                changeInPlace(t, element);
                changes.add("set the text of " + t.describe());
            }
            case TEST -> {
                if (s.name() != null) {
                    String actual = t.token.attrs().get(s.name());
                    if (actual == null || (s.value() != null && !actual.equals(s.value()))) {
                        throw new StepFailure("test failed: " + t.describe() + " has @" + s.name()
                                + (actual == null ? " missing" : "=\"" + actual + "\"") + ", expected "
                                + (s.value() == null ? "it present" : "\"" + s.value() + "\""));
                    }
                }
                if (s.text() != null) {
                    String actual = normalize(XmlTokens.unescape(t.empty ? ""
                            : withoutDeleted(doc.substring(t.startTagEnd, t.endTagStart)).replaceAll("<[^>]*>", "")));
                    if (!actual.equals(normalize(s.text()))) {
                        throw new StepFailure("test failed: the text of " + t.describe() + " is \""
                                + actual + "\", expected \"" + normalize(s.text()) + "\"");
                    }
                }
                changes.add("tested " + t.describe());
            }
        }
    }

    // ── edits ───────────────────────────────────────────────────────────

    /** Put content relative to an element; returns where it starts. Marked new when reviewing and {@code mark}. */
    private int insert(Target t, Position position, String content, boolean mark) {
        switch (position) {
            case BEFORE -> {
                String indent = indentAt(t.start);
                String text = indent == null ? prepare(content, mark, null)
                        : prepare(content.strip(), mark, indent) + newline + indent;
                doc.insert(t.start, text);
                return t.start;
            }
            case AFTER -> {
                String indent = indentAt(t.start);
                boolean block = indent != null && restOfLineBlank(t.end);
                String text = block ? newline + indent + prepare(content.strip(), mark, indent)
                        : prepare(content, mark, null);
                doc.insert(t.end, text);
                return t.end + (block ? newline.length() + indent.length() : 0);
            }
            case FIRST_CHILD, LAST_CHILD -> {
                boolean first = position == Position.FIRST_CHILD;
                List<Target> kids = children(t);
                if (!kids.isEmpty() && !t.hasText) {
                    return insert(first ? kids.get(0) : kids.get(kids.size() - 1),
                            first ? Position.BEFORE : Position.AFTER, content, mark);
                }
                String parentIndent = indentAt(t.start);
                boolean blank = !t.hasText && kids.isEmpty();
                if (t.empty) {
                    String open = doc.substring(t.start, t.end).replaceFirst("\\s*/>$", ">");
                    String body = parentIndent != null
                            ? newline + parentIndent + unit + prepare(content.strip(), mark, parentIndent + unit) + newline
                                    + parentIndent
                            : prepare(content, mark, null);
                    doc.replace(t.start, t.end, open + body + "</" + t.token.name() + ">");
                    return t.start + open.length();
                }
                if (blank && parentIndent != null && doc.substring(t.startTagEnd, t.endTagStart).isBlank()) {
                    String body = newline + parentIndent + unit + prepare(content.strip(), mark, parentIndent + unit)
                            + newline + parentIndent;
                    doc.replace(t.startTagEnd, t.endTagStart, body);
                    return t.startTagEnd + newline.length();
                }
                int at = first ? t.startTagEnd : t.endTagStart;
                doc.insert(at, prepare(content, mark, null));
                return at;
            }
            default -> throw new IllegalStateException();
        }
    }

    /** Content ready to go in: marked new when reviewing, indented to {@code indent} when that is safe, identified. */
    private String prepare(String content, boolean mark, String indent) {
        // Inside content marked new, the author's own marks (from copying what this batch made) are settled:
        // it is all new, and a mark inside a mark would not come off exactly
        String text = mark && author != null ? markNew(ProposalOps.acceptAll(content, author)) : content;
        if (indent != null && reindentable(text)) {
            text = reindent(inUnit(text, unit), indent);
        }
        return identify(text.replace("\r\n", "\n").replace("\n", newline), true);
    }

    /** Take an element out: marked deleted in place when reviewing, unless this batch made it. */
    private void remove(Target t) {
        if (author != null && !created.contains(t.id)) {
            doc.replace(t.start, t.startTagEnd, marked(t.token, doc.substring(t.start, t.startTagEnd), "deleted"));
            deleted.add(t.id);
            return;
        }
        int from = t.start;
        int to = t.end;
        if (indentAt(t.start) != null && restOfLineBlank(t.end) && lineStart(t.start) > 0) {
            from = lineStart(t.start);
            int nl = doc.indexOf("\n", t.end);
            to = nl < 0 ? doc.length() : nl + 1;
        }
        doc.delete(from, to);
    }

    /** Put new content where an element is: beside it, the old one marked deleted, when reviewing. */
    private int replaceElement(Target t, String content) {
        if (author != null && !created.contains(t.id)) {
            int at = insert(t, Position.AFTER, content, true);
            remove(byId(t.id));
            return at;
        }
        // Made by this batch: replaced in place, and marked new unless an element around it carries the mark
        boolean mark = author != null && markedNewByThisBatch(t);
        String indent = indentAt(t.start);
        String text = prepare(indent == null ? content : content.strip(), mark, indent);
        doc.replace(t.start, t.end, text);
        return t.start;
    }

    /** An element changed in place (renamed, an attribute, its text): a new copy beside the old, when reviewing. */
    private void changeInPlace(Target t, String newElement) {
        if (author != null && !created.contains(t.id)) {
            insert(t, Position.AFTER, stripIds(withoutDeleted(newElement)), true);
            remove(byId(t.id));
            return;
        }
        doc.replace(t.start, t.end, newElement);
    }

    /** The element's text for a copy: without what the batch deleted inside it, and without identities. */
    private String copy(Target t) {
        return stripIds(withoutDeleted(doc.substring(t.start, t.end)));
    }

    // ── the element texts ───────────────────────────────────────────────

    private String rename(Target t, String name) {
        String old = t.token.name();
        String start = "<" + name + doc.substring(t.start + 1 + old.length(), t.startTagEnd);
        if (t.empty) {
            return start;
        }
        // the end tag rebuilt, whatever whitespace it had (</cmd >)
        return start + doc.substring(t.startTagEnd, t.endTagStart) + "</" + name + ">";
    }

    private String withAttribute(Target t, String name, String value) {
        String tag = doc.substring(t.start, t.startTagEnd);
        String escaped = XmlTokens.escapeAttr(value);
        Matcher m = Pattern.compile("(\\s" + Pattern.quote(name) + "\\s*=\\s*)(\"[^\"]*\"|'[^']*')").matcher(tag);
        String newTag;
        if (m.find()) {
            newTag = tag.substring(0, m.start(2)) + "\"" + escaped + "\"" + tag.substring(m.end(2));
        } else {
            int close = tag.endsWith("/>") ? tag.length() - 2 : tag.length() - 1;
            newTag = tag.substring(0, close) + " " + name + "=\"" + escaped + "\"" + tag.substring(close);
        }
        return newTag + doc.substring(t.startTagEnd, t.end);
    }

    private String withoutAttribute(Target t, String name) {
        String tag = doc.substring(t.start, t.startTagEnd);
        String newTag = tag.replaceFirst("\\s" + Pattern.quote(name) + "\\s*=\\s*(\"[^\"]*\"|'[^']*')", "");
        return newTag + doc.substring(t.startTagEnd, t.end);
    }

    private void refuseReviewAttribute(Target t, String name) {
        if (author != null && REVIEW_ATTRIBUTES.contains(name) && created.contains(t.id)) {
            throw new StepFailure("@" + name + " of an element this batch inserted holds its review mark; leave it "
                    + "to the editor");
        }
    }

    // ── review marks ────────────────────────────────────────────────────

    /**
     * A start tag with a review mark. The mark's attributes are added to the tag's text, so the rest of the
     * tag stays as written; a tag that already carries status, rev or otherprops goes through
     * {@link Marks#marked}, which records what it had for rejecting.
     */
    private String marked(Token token, String tagText, String status) {
        boolean plain = token.attrs().keySet().stream().noneMatch(REVIEW_ATTRIBUTES::contains);
        if (!plain) {
            List<String> props = "deleted".equals(status) ? List.of(Marks.DELETED) : List.of();
            return Marks.retag(token, Marks.marked(token, status, author, props));
        }
        String attrs = " status=\"" + status + "\" rev=\"" + author + "\""
                + ("deleted".equals(status) ? " otherprops=\"" + Marks.DELETED + "\"" : "");
        int close = token.kind() == Kind.EMPTY ? tagText.lastIndexOf("/>") : tagText.length() - 1;
        return tagText.substring(0, close) + attrs + tagText.substring(close);
    }

    /**
     * Content marked new: each top-level element marked, or, when the content has words of its own at the
     * top level, all of it in one marked phrase (so rejecting takes it back whole, spaces included).
     */
    private String markNew(String content) {
        List<Token> tokens = XmlTokens.tokenize(content);
        boolean words = false;
        for (Token tok : tokens) {
            if (tok.parent() < 0 && tok.kind() == Kind.TEXT && !tok.text(content).isBlank()) {
                words = true;
            }
        }
        if (words) {
            int lead = 0;
            while (Character.isWhitespace(content.charAt(lead))) {
                lead++;
            }
            int trail = content.length();
            while (Character.isWhitespace(content.charAt(trail - 1))) {
                trail--;
            }
            Map<String, String> ph = new LinkedHashMap<>();
            ph.put("status", "new");
            ph.put("rev", author + " " + Marks.MARK);
            return content.substring(0, lead) + XmlTokens.renderTag("ph", ph, false) + content.substring(lead, trail)
                    + "</ph>" + content.substring(trail);
        }
        StringBuilder out = new StringBuilder();
        for (Token tok : tokens) {
            String text = tok.text(content);
            out.append(tok.parent() < 0 && (tok.kind() == Kind.START || tok.kind() == Kind.EMPTY)
                    ? marked(tok, text, "new") : text);
        }
        return out.toString();
    }

    /** Whether this batch inserted the element and marked it itself (rather than something around it). */
    private boolean markedNewByThisBatch(Target t) {
        return author != null && created.contains(t.id) && "new".equals(t.token.attrs().get("status"))
                && Marks.authorOf(t.token.attrs().get("rev")) != null
                && author.equals(Marks.authorOf(t.token.attrs().get("rev")));
    }

    // ── selection ───────────────────────────────────────────────────────

    /** An element in the working text. */
    private final class Target {
        final String id;
        final Token token;
        final int start;
        final int end;
        final int startTagEnd;
        final int endTagStart;
        final boolean empty;
        final boolean hasText;
        final boolean hasChildElements;
        final int parent;

        Target(String id, String source, List<Token> tokens, int index) {
            this.id = id;
            this.token = tokens.get(index);
            this.start = token.start();
            this.empty = token.kind() == Kind.EMPTY;
            this.startTagEnd = token.end();
            this.end = XmlTokens.endOf(tokens, index);
            int close = empty ? -1 : XmlTokens.closeOf(tokens, index);
            this.endTagStart = empty ? end : tokens.get(close).start();
            this.parent = token.parent();
            boolean text = false;
            boolean kids = false;
            for (int i = index + 1; !empty && i < close; i++) {
                Token c = tokens.get(i);
                if (c.parent() != index) {
                    continue;
                }
                if (c.kind() == Kind.TEXT && !c.text(source).isBlank()) {
                    text = true;
                } else if ((c.kind() == Kind.START || c.kind() == Kind.EMPTY) && !deleted.contains(c.attrs().get(idName))) {
                    kids = true; // what the batch deleted is gone for later steps
                }
            }
            this.hasText = text;
            this.hasChildElements = kids;
        }

        boolean parentless() {
            return parent < 0;
        }

        String describe() {
            Map<String, String> shown = new LinkedHashMap<>(token.attrs());
            shown.remove(idName);
            shown.keySet().removeAll(REVIEW_ATTRIBUTES);
            String attrs = shown.entrySet().stream().limit(2).map(e -> " " + e.getKey() + "=\"" + e.getValue() + "\"")
                    .reduce("", String::concat);
            return "<" + token.name() + attrs + "> at line " + line(start);
        }
    }

    private Target select(String select, String field) {
        String path = required(select, field);
        if (path.startsWith("#")) {
            path = "//*[@id='" + path.substring(1).replace("'", "") + "']";
        }
        Document view = view();
        Object result;
        try {
            result = view.selectObject(path);
        } catch (RuntimeException e) {
            throw new StepFailure(field + " is not a valid XPath: " + path + " (" + e.getMessage() + ")");
        }
        List<?> nodes = result instanceof List<?> list ? list : result instanceof Node n ? List.of(n) : null;
        if (nodes == null) {
            throw new StepFailure(field + " must select an element; " + path + " is a value (" + result + ")");
        }
        List<Element> elements = new ArrayList<>();
        for (Object n : nodes) {
            if (!(n instanceof Element e)) {
                String kind = n instanceof Node node ? node.getNodeTypeName().toLowerCase() : "value";
                throw new StepFailure(field + " must select an element; " + path + " selects a " + kind
                        + " (select its element instead)");
            }
            elements.add(e);
        }
        if (elements.size() != 1) {
            String hint = "";
            if (elements.isEmpty() && view.getRootElement() != null
                    && !view.getRootElement().getNamespaceURI().isEmpty()) {
                hint = " (the document has a default namespace: use *[local-name()='name'], or #id)";
            } else if (!elements.isEmpty()) {
                hint = " (add a condition such as [@id='...'] or [2])";
            }
            throw new StepFailure(field + " " + path + " matches " + elements.size()
                    + " elements; it must match exactly one" + hint);
        }
        String id = elements.get(0).attributeValue(idName);
        if (id == null) {
            throw new StepFailure(field + " " + path + " selects an element that comes from an entity, not from the "
                    + "document's text; select the element that holds the entity reference");
        }
        return byId(id);
    }

    private Target byId(String id) {
        String source = doc.toString();
        List<Token> tokens = XmlTokens.tokenize(source);
        for (int i = 0; i < tokens.size(); i++) {
            Token tok = tokens.get(i);
            if ((tok.kind() == Kind.START || tok.kind() == Kind.EMPTY) && id.equals(tok.attrs().get(idName))) {
                return new Target(id, source, tokens, i);
            }
        }
        throw new IllegalStateException("the selected element is gone");
    }

    private List<Target> children(Target t) {
        String source = doc.toString();
        List<Token> tokens = XmlTokens.tokenize(source);
        int index = -1;
        for (int i = 0; i < tokens.size(); i++) {
            if (t.id.equals(tokens.get(i).attrs().get(idName))) {
                index = i;
                break;
            }
        }
        List<Target> out = new ArrayList<>();
        for (int i = index + 1; i < tokens.size() && tokens.get(i).start() < t.end; i++) {
            Token tok = tokens.get(i);
            if (tok.parent() == index && (tok.kind() == Kind.START || tok.kind() == Kind.EMPTY)
                    && !deleted.contains(tok.attrs().get(idName))) {
                out.add(new Target(tok.attrs().get(idName), source, tokens, i));
            }
        }
        return out;
    }

    /**
     * The document as the steps see it: without what this batch marked deleted, without the phrases that
     * mark inserted text, without the batch's review attributes, and parsed without its DTD.
     */
    private Document view() {
        String visible = withoutDeleted(doc.toString());
        if (author != null) {
            visible = unmarkedView(visible);
        }
        try {
            SAXReader reader = new SAXReader();
            reader.setValidation(false);
            reader.setIncludeExternalDTDDeclarations(false);
            reader.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            return reader.read(new StringReader(visible));
        } catch (org.dom4j.DocumentException e) {
            throw new IllegalStateException("the document is not well-formed: " + e.getMessage());
        }
    }

    /** The text without the batch's marks on the elements it created: they are for the writer, not for steps. */
    private String unmarkedView(String text) {
        List<Token> tokens = XmlTokens.tokenize(text);
        StringBuilder out = new StringBuilder(text);
        for (int i = tokens.size() - 1; i >= 0; i--) {
            Token tok = tokens.get(i);
            if ((tok.kind() != Kind.START && tok.kind() != Kind.EMPTY) || !created.contains(tok.attrs().get(idName))) {
                continue;
            }
            String rev = tok.attrs().get("rev");
            if ("ph".equals(tok.name()) && rev != null && Marks.hasToken(rev, Marks.MARK) && tok.kind() == Kind.START) {
                int close = XmlTokens.closeOf(tokens, i);
                out.delete(tokens.get(close).start(), tokens.get(close).end()); // the phrase that marks inserted text
                out.delete(tok.start(), tok.end());
                continue;
            }
            String tag = tok.text(text);
            String bare = tag.replaceFirst(" status=\"new\" rev=\"" + Pattern.quote(author) + "\"", "");
            out.replace(tok.start(), tok.end(), bare);
        }
        return out.toString();
    }

    /** Text without the elements this batch marked deleted. */
    private String withoutDeleted(String text) {
        if (deleted.isEmpty()) {
            return text;
        }
        List<Token> tokens = XmlTokens.tokenize(text);
        StringBuilder out = new StringBuilder(text);
        List<int[]> cut = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            Token tok = tokens.get(i);
            if ((tok.kind() == Kind.START || tok.kind() == Kind.EMPTY) && deleted.contains(tok.attrs().get(idName))) {
                int end = XmlTokens.endOf(tokens, i);
                int from = tok.start();
                int to = end;
                // alone on its lines, it takes them with it, as accepting its deletion does
                int ls = text.lastIndexOf('\n', from - 1) + 1;
                int nl = text.indexOf('\n', end);
                int le = nl < 0 ? text.length() : nl;
                if (ls > 0 && text.substring(ls, from).isBlank() && text.substring(end, le).isBlank()
                        && !inPreformatted(tokens, i)) {
                    from = ls;
                    to = nl < 0 ? le : nl + 1;
                }
                cut.add(new int[] {from, to});
                while (i + 1 < tokens.size() && tokens.get(i + 1).start() < end) {
                    i++;
                }
            }
        }
        for (int k = cut.size() - 1; k >= 0; k--) {
            out.delete(cut.get(k)[0], cut.get(k)[1]);
        }
        return out.toString();
    }

    private static boolean inPreformatted(List<Token> tokens, int index) {
        for (int p = tokens.get(index).parent(); p >= 0; p = tokens.get(p).parent()) {
            Token t = tokens.get(p);
            if (PREFORMATTED.contains(t.name()) || "preserve".equals(t.attrs().get("xml:space"))) {
                return true;
            }
        }
        return false;
    }

    // ── identities ──────────────────────────────────────────────────────

    /**
     * {@code text} with an identity attribute on each element that has none (just before the tag's close,
     * so removing it later restores the tag exactly); {@code isNew} records them as made by this batch.
     */
    private String identify(String text, boolean isNew) {
        List<Token> tokens = XmlTokens.tokenize(text);
        StringBuilder out = new StringBuilder(text);
        for (int i = tokens.size() - 1; i >= 0; i--) {
            Token tok = tokens.get(i);
            if ((tok.kind() != Kind.START && tok.kind() != Kind.EMPTY) || tok.attrs().containsKey(idName)) {
                continue;
            }
            int close = tok.kind() == Kind.EMPTY ? tok.text(text).lastIndexOf("/>") + tok.start() : tok.end() - 1;
            String id = Integer.toString(nextId++);
            out.insert(close, " " + idName + "=\"" + id + "\"");
            if (isNew) {
                created.add(id);
            }
        }
        return out.toString();
    }

    private String stripIds(String text) {
        return idPattern.matcher(text).replaceAll("");
    }

    // ── fragments and formatting ────────────────────────────────────────

    private String fragment(String xml, String field) {
        String content = required(xml, field);
        List<Token> tokens = XmlTokens.tokenize(content);
        if (!XmlTokens.isBalanced(tokens)) {
            throw new StepFailure(field + " is not well-formed XML: " + content);
        }
        try {
            SAXReader reader = new SAXReader();
            reader.setEntityResolver((p, s) -> new InputSource(new StringReader("")));
            reader.read(new StringReader("<dogsbay-fragment>" + content + "</dogsbay-fragment>"));
        } catch (org.dom4j.DocumentException e) {
            throw new StepFailure(field + " is not well-formed XML: " + e.getMessage());
        }
        return content;
    }

    private Token wrapper(String xml) {
        String content = fragment(xml, "xml").strip();
        List<Token> tokens = XmlTokens.tokenize(content);
        Token first = tokens.get(0);
        boolean single = (first.kind() == Kind.EMPTY && tokens.size() == 1)
                || (first.kind() == Kind.START && tokens.size() == 2 && tokens.get(1).kind() == Kind.END);
        if (!single) {
            throw new StepFailure("xml for wrap must be one empty element, such as <note type=\"tip\"/>");
        }
        return first;
    }

    private static String describe(String content) {
        List<Token> tokens = XmlTokens.tokenize(content);
        List<String> names = new ArrayList<>();
        int depth = 0;
        for (Token t : tokens) {
            if (depth == 0 && (t.kind() == Kind.START || t.kind() == Kind.EMPTY)) {
                names.add("<" + t.name() + ">");
            }
            if (t.kind() == Kind.START) {
                depth++;
            } else if (t.kind() == Kind.END) {
                depth--;
            }
        }
        return names.isEmpty() ? "text" : String.join(" ", names);
    }

    private static String name(Position p) {
        return p.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    /**
     * Whether content's line breaks are only formatting: every one in whitespace between tags, none in text,
     * and nothing preformatted or {@code xml:space="preserve"}. Only then is it reindented.
     */
    private static boolean reindentable(String content) {
        if (!content.contains("\n")) {
            return true;
        }
        for (Token t : XmlTokens.tokenize(content)) {
            if (t.kind() == Kind.TEXT && t.text(content).contains("\n") && !t.text(content).isBlank()) {
                return false;
            }
            if ((t.kind() == Kind.CDATA || t.kind() == Kind.COMMENT) && t.text(content).contains("\n")) {
                return false;
            }
            if ((t.kind() == Kind.START || t.kind() == Kind.EMPTY)
                    && (PREFORMATTED.contains(t.name()) || "preserve".equals(t.attrs().get("xml:space")))) {
                return false;
            }
        }
        return true;
    }

    /** The whitespace before {@code pos} on its line, when only whitespace precedes it there; else null. */
    private String indentAt(int pos) {
        int ls = lineStart(pos);
        String before = doc.substring(ls, pos);
        return before.isBlank() && !before.contains("\r") ? before : null;
    }

    private int lineStart(int pos) {
        int nl = doc.lastIndexOf("\n", pos - 1);
        return nl < 0 ? 0 : nl + 1;
    }

    private boolean restOfLineBlank(int pos) {
        int nl = doc.indexOf("\n", pos);
        return doc.substring(pos, nl < 0 ? doc.length() : nl).isBlank();
    }

    private int line(int pos) {
        int n = 1;
        for (int i = 0; i < pos && i < doc.length(); i++) {
            if (doc.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    /** Indent every line after the first of a fragment to {@code indent}, keeping its own relative indentation. */
    private static String reindent(String content, String indent) {
        String[] lines = dedent(content.replace("\r\n", "\n")).split("\n", -1);
        StringBuilder out = new StringBuilder(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            out.append('\n');
            if (!lines[i].isBlank()) {
                out.append(indent).append(lines[i]);
            }
        }
        return out.toString();
    }

    /** A fragment's lines indented in {@code unit} (the document's) rather than the fragment's own step. */
    private static String inUnit(String content, String unit) {
        String own = indentUnit(dedent(content.replace("\r\n", "\n")));
        if (own.equals(unit) || !content.contains("\n")) {
            return content;
        }
        String[] lines = dedent(content.replace("\r\n", "\n")).split("\n", -1);
        StringBuilder out = new StringBuilder(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int steps = 0;
            int at = 0;
            while (line.startsWith(own, at)) {
                steps++;
                at += own.length();
            }
            out.append('\n').append(unit.repeat(steps)).append(line.substring(at));
        }
        return out.toString();
    }

    /** Remove the indentation common to the lines after the first. */
    private static String dedent(String content) {
        String[] lines = content.split("\n", -1);
        int common = Integer.MAX_VALUE;
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                int lead = 0;
                while (lead < lines[i].length() && (lines[i].charAt(lead) == ' ' || lines[i].charAt(lead) == '\t')) {
                    lead++;
                }
                common = Math.min(common, lead);
            }
        }
        if (common == Integer.MAX_VALUE || common == 0) {
            return content;
        }
        StringBuilder out = new StringBuilder(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            out.append('\n').append(lines[i].isBlank() ? "" : lines[i].substring(common));
        }
        return out.toString();
    }

    /**
     * The document's indentation step: the smallest increase in leading whitespace between nested lines,
     * as written (tabs stay tabs), else two spaces.
     */
    private static String indentUnit(String source) {
        String best = null;
        String previous = null;
        for (String line : source.replace("\r\n", "\n").split("\n")) {
            if (line.isBlank() || !line.stripLeading().startsWith("<")) {
                continue;
            }
            String lead = line.substring(0, line.length() - line.stripLeading().length());
            if (previous != null && lead.length() > previous.length() && lead.startsWith(previous)) {
                String step = lead.substring(previous.length());
                if (best == null || step.length() < best.length()) {
                    best = step;
                }
            }
            previous = lead;
        }
        return best == null || best.length() > 8 ? "  " : best;
    }

    private static String normalize(String s) {
        return s.replaceAll("\\s+", " ").strip();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new StepFailure(field + " is required");
        }
        return value;
    }

    private static void requireName(String name) {
        if (!name.matches("[A-Za-z_][A-Za-z0-9_.:-]*")) {
            throw new StepFailure("\"" + name + "\" is not an XML name");
        }
    }

    private static void requireParent(Target t) {
        if (t.parentless()) {
            throw new StepFailure("the document element cannot be deleted, replaced, moved or wrapped");
        }
    }
}
