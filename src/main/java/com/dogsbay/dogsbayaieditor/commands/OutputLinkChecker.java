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
package com.dogsbay.dogsbayaieditor.commands;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.dogsbay.dogsbayaieditor.commands.results.BrokenRef;

/**
 * Finds links in built output that lead nowhere.
 *
 * <p>Read with expressions rather than an HTML parser, deliberately: built output is HTML, not XML, so a parser strict
 * enough to be worth having would refuse pages a browser reads happily, and every generator writes something that would
 * trip one. What is needed here is the set of things a page points at and the set of anchors it carries, both of which
 * the markup makes plain.
 *
 * <p>The cost of being wrong is asymmetric. A missed broken link is a link a reader finds; a false report is a check
 * nobody runs twice. So where the markup is ambiguous this stays quiet: comments and script bodies are not searched,
 * a page that redirects its own links with {@code <base>} is resolved through it, and an attribute value that cannot be
 * turned into a file name is left alone.
 */
final class OutputLinkChecker {

    /** {@code href="..."} and {@code src="..."}, in either kind of quote. */
    private static final Pattern LINK = Pattern.compile(
            "(?<![-\\w])(href|src)\\s*=\\s*([\"'])(.*?)\\2", Pattern.CASE_INSENSITIVE);

    /** {@code srcset="a.png 1x, b.png 2x"} — several links in one attribute. */
    private static final Pattern SRCSET = Pattern.compile(
            "(?<![-\\w])(srcset)\\s*=\\s*([\"'])(.*?)\\2", Pattern.CASE_INSENSITIVE);

    /**
     * {@code id="..."}, which is what a fragment has to find.
     *
     * <p>The look-behind keeps {@code data-id}, {@code xml:id} and every {@code aria-…-id} out of it: those are not
     * anchors, and treating them as anchors would quietly excuse a fragment that really is broken.
     */
    private static final Pattern ID = Pattern.compile(
            "(?<![-\\w:])id\\s*=\\s*([\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE);

    /**
     * An {@code <a>} start tag.
     *
     * <p>DITA-OT's html5 output still writes footnote anchors as {@code <a name="fntarg_1">} with no id, so a check
     * that looked only at ids would report two broken links for every footnote in an unmodified build.
     */
    private static final Pattern ANCHOR_TAG = Pattern.compile("<a\\b[^>]*>", Pattern.CASE_INSENSITIVE);

    private static final Pattern NAME_ATTR = Pattern.compile(
            "(?<![-\\w])name\\s*=\\s*([\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE);

    /** {@code <base href="...">}, which moves where every relative link on the page points. */
    private static final Pattern BASE_TAG = Pattern.compile("<base\\b[^>]*>", Pattern.CASE_INSENSITIVE);

    private static final Pattern HREF_ATTR = Pattern.compile(
            "(?<![-\\w])href\\s*=\\s*([\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE);

    /** A comment, or a script or style body: markup-shaped text that is not markup. */
    private static final Pattern NOT_MARKUP = Pattern.compile(
            "<!--.*?-->|<script\\b[^>]*>.*?</script\\s*>|<style\\b[^>]*>.*?</style\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** A link that leaves the site, which this does not follow. */
    private static final Pattern ELSEWHERE = Pattern.compile(
            "^(?:[a-z][a-z0-9+.-]*:|//)", Pattern.CASE_INSENSITIVE);

    /** What a folder link is served as, in the order a server would try them. */
    private static final List<String> INDEX_NAMES = List.of("index.html", "index.htm", "index.xhtml");

    private OutputLinkChecker() {
    }

    /** One page, read once: the text with non-markup blanked out, and where its lines begin. */
    private record PageText(String scannable, int[] lineStarts) {

        int lineOf(int index) {
            int at = Arrays.binarySearch(lineStarts, index);
            return at >= 0 ? at + 1 : -at - 1;
        }
    }

    /**
     * What a scan of one output folder found.
     *
     * <p>The page count is part of the answer, not a statistic. A folder with no pages in it — a PDF deliverable, or a
     * build that wrote nothing — yields no broken links, and reporting that as "clean" would be saying a folder nobody
     * looked at holds together.
     *
     * @param pages  how many pages were read.
     * @param broken the links that lead nowhere, in the order the pages were read.
     */
    record Scan(int pages, List<BrokenRef> broken) {
    }

    /**
     * Every link in the built output that leads nowhere.
     *
     * @param dir the folder the build wrote.
     * @return the broken links, in the order the pages were read.
     */
    static List<BrokenRef> check(Path dir) throws IOException {
        return scan(dir).broken();
    }

    /** As {@link #check}, and how many pages it read. */
    static Scan scan(Path dir) throws IOException {
        // Absolute from here on. A relative argument is the documented way to run this, and every path is compared with
        // and relativized against the root, which only works when both are the same kind of path.
        Path root = dir.toAbsolutePath().normalize();
        List<Path> pages = pagesUnder(root);
        Map<Path, Set<String>> anchorsByPage = new HashMap<>(pages.size() * 2);
        for (Path page : pages) {
            // Anchors first and for every page, because a link may point forward to a page not yet read. Only the
            // anchors are kept; the text is let go, so a large output tree costs its largest page, not its total.
            anchorsByPage.put(page, anchorsIn(read(page).scannable()));
        }
        List<BrokenRef> broken = new ArrayList<>();
        Map<Path, Boolean> exists = new HashMap<>();
        for (Path page : pages) {
            checkPage(root, page, read(page), anchorsByPage, exists, broken);
        }
        return new Scan(pages.size(), broken);
    }

    private static List<Path> pagesUnder(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(OutputLinkChecker::isPage)
                    .sorted()
                    .toList();
        }
    }

    /** Whether a file is one of the pages a reader opens. {@code .xhtml} is what {@code args.outext} commonly makes. */
    private static boolean isPage(Path file) {
        String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".xhtml");
    }

    /**
     * A page's text, with anything that is not markup replaced by spaces.
     *
     * <p>Blanking rather than removing keeps every later offset — and so every line number — the offset it had in the
     * file. Bytes that are not UTF-8 become the replacement character rather than an exception: one Latin-1 file copied
     * into the output should not silence the check over the other three thousand pages.
     */
    private static PageText read(Path page) throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        String html = decoder.decode(ByteBuffer.wrap(Files.readAllBytes(page))).toString();

        StringBuilder scannable = new StringBuilder(html);
        Matcher hidden = NOT_MARKUP.matcher(html);
        while (hidden.find()) {
            for (int i = hidden.start(); i < hidden.end(); i++) {
                if (scannable.charAt(i) != '\n') {
                    scannable.setCharAt(i, ' ');
                }
            }
        }
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < html.length(); i++) {
            if (html.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        int[] lineStarts = new int[starts.size()];
        for (int i = 0; i < lineStarts.length; i++) {
            lineStarts[i] = starts.get(i);
        }
        return new PageText(scannable.toString(), lineStarts);
    }

    /** Everything on a page a fragment can land on: {@code id} anywhere, and {@code name} on an {@code <a>}. */
    private static Set<String> anchorsIn(String html) {
        Set<String> anchors = new HashSet<>();
        Matcher m = ID.matcher(html);
        while (m.find()) {
            anchors.add(m.group(2));
        }
        Matcher tag = ANCHOR_TAG.matcher(html);
        while (tag.find()) {
            Matcher name = NAME_ATTR.matcher(tag.group());
            if (name.find()) {
                anchors.add(name.group(2));
            }
        }
        return anchors;
    }

    private static void checkPage(Path root, Path page, PageText text, Map<Path, Set<String>> anchorsByPage,
            Map<Path, Boolean> exists, List<BrokenRef> broken) {
        String html = text.scannable();
        Base base = baseOf(root, page, html);
        if (base.elsewhere()) {
            return; // the page sends its own relative links to another site; nothing here can say where they land
        }
        for (Link link : linksIn(html)) {
            if (base.covers(link.start())) {
                continue; // the <base> tag's own href is not a link to a file
            }
            checkLink(root, page, text, base, link, anchorsByPage, exists, broken);
        }
    }

    private static void checkLink(Path root, Path page, PageText text, Base base, Link link,
            Map<Path, Set<String>> anchorsByPage, Map<Path, Boolean> exists, List<BrokenRef> broken) {
        String value = link.value().trim();
        if (value.isEmpty() || ELSEWHERE.matcher(value).find()) {
            return;
        }
        int hash = value.indexOf('#');
        String pathPart = hash < 0 ? value : value.substring(0, hash);
        String fragment = hash < 0 ? null : decode(value.substring(hash + 1));
        String withoutQuery = pathPart.contains("?") ? pathPart.substring(0, pathPart.indexOf('?')) : pathPart;

        Path target = withoutQuery.isEmpty() ? page
                : resolve(root, base.dir(), decode(withoutQuery), exists);
        int line = text.lineOf(link.start());
        if (target == null) {
            broken.add(new BrokenRef(page.toString(), line, link.attribute(), link.value(),
                    "leads outside the built output"));
            return;
        }
        if (!exists(exists, target)) {
            broken.add(new BrokenRef(page.toString(), line, link.attribute(), link.value(),
                    "there is no " + root.relativize(target)));
            return;
        }
        if (fragment == null || fragment.isEmpty()) {
            return;
        }
        Set<String> anchors = anchorsByPage.get(target);
        if (anchors == null) {
            return; // a fragment of something that is not a page, which is not ours to judge
        }
        if (!anchors.contains(fragment)) {
            broken.add(new BrokenRef(page.toString(), line, link.attribute(), link.value(),
                    "nothing in " + root.relativize(target) + " has id \"" + fragment + "\""));
        }
    }

    /** One link as written: the attribute it came from, its value, and where on the page it was. */
    private record Link(String attribute, String value, int start) {
    }

    /** Every link on a page, in the order they appear. */
    private static List<Link> linksIn(String html) {
        List<Link> links = new ArrayList<>();
        Matcher m = LINK.matcher(html);
        while (m.find()) {
            links.add(new Link(m.group(1).toLowerCase(java.util.Locale.ROOT), m.group(3), m.start()));
        }
        Matcher s = SRCSET.matcher(html);
        while (s.find()) {
            // "a.png 1x, b.png 2x" — each candidate is a link, its descriptor is not
            for (String candidate : s.group(3).split(",")) {
                String url = candidate.trim().split("\\s+")[0];
                if (!url.isEmpty()) {
                    links.add(new Link("srcset", url, s.start()));
                }
            }
        }
        return links;
    }

    /**
     * Where relative links on this page start from, and where its {@code <base>} tag sits.
     *
     * @param dir       the folder relative links resolve against.
     * @param from      the first character of the base tag, or -1 when the page has none.
     * @param to        one past its last character.
     * @param elsewhere whether the base sends relative links off this site altogether.
     */
    private record Base(Path dir, int from, int to, boolean elsewhere) {

        boolean covers(int index) {
            return from >= 0 && index >= from && index < to;
        }
    }

    private static Base baseOf(Path root, Path page, String html) {
        Matcher tag = BASE_TAG.matcher(html);
        if (!tag.find()) {
            return new Base(page.getParent(), -1, -1, false);
        }
        Matcher href = HREF_ATTR.matcher(tag.group());
        if (!href.find()) {
            return new Base(page.getParent(), tag.start(), tag.end(), false);
        }
        String value = href.group(2).trim();
        if (ELSEWHERE.matcher(value).find()) {
            return new Base(page.getParent(), tag.start(), tag.end(), true);
        }
        Path dir = value.startsWith("/")
                ? root.resolve(value.substring(1)).normalize()
                : page.getParent().resolve(decode(value)).normalize();
        if (!Files.isDirectory(dir)) {
            dir = dir.getParent(); // a base that names a file means the folder that file is in
        }
        return new Base(dir == null ? page.getParent() : dir, tag.start(), tag.end(), false);
    }

    /**
     * Where a link points, or null when it points outside the output.
     *
     * <p>A link beginning with a slash is from the site's root, which is the folder the build wrote; anything else is
     * relative to the page — or to its {@code <base>}, when it has one. A link that climbs out of the output with
     * {@code ..} is a fault in itself.
     */
    private static Path resolve(Path root, Path from, String value, Map<Path, Boolean> exists) {
        Path target = value.startsWith("/")
                ? root.resolve(value.substring(1)).normalize()
                : from.resolve(value).normalize();
        if (Files.isDirectory(target)) {
            // a folder is its index, the way a server serves it; name the one that is there, so the report is about
            // what is missing rather than about which index name we happened to try first
            Path found = null;
            for (String index : INDEX_NAMES) {
                if (exists(exists, target.resolve(index))) {
                    found = target.resolve(index);
                    break;
                }
            }
            target = found == null ? target.resolve(INDEX_NAMES.get(0)) : found;
        }
        return target.startsWith(root) ? target : null;
    }

    private static boolean exists(Map<Path, Boolean> exists, Path target) {
        return exists.computeIfAbsent(target, Files::exists);
    }

    /**
     * A percent-encoded name as it is on disk.
     *
     * <p>Written out rather than handed to {@code URLDecoder}, which decodes form data: there a {@code +} means a
     * space, and in a path it means a plus, so {@code a+b.html} would be looked for under a name nothing has.
     */
    private static String decode(String value) {
        if (value.indexOf('%') < 0) {
            return value;
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length()) {
                int hi = Character.digit(value.charAt(i + 1), 16);
                int lo = Character.digit(value.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out.write((hi << 4) + lo);
                    i += 2;
                    continue;
                }
            }
            byte[] raw = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
            out.write(raw, 0, raw.length);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
