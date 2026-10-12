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

package com.dogsbay.dogsbayaieditor.cli;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import com.dogsbay.dogsbayaieditor.commands.results.*;

/**
 * Formats command results for terminal output.
 */
class OutputFormatter {

    static void printValidation(ValidationResult r, PrintStream out) {
        out.println(r.valid() ? "VALID" : "INVALID");
        for (var err : r.errors()) {
            out.printf("  %d:%d [%s] %s%n",
                err.line(), err.column(), err.severity(), err.message());
        }
    }

    static void printParse(ParseResult r, PrintStream out) {
        out.println("Well-formed: " + r.wellFormed());
        out.println("Root: " + r.rootElement());
        out.println("Namespace: " + r.rootNamespace());
        out.println("Encoding: " + r.encoding());
        for (var err : r.errors()) {
            out.printf("  %d:%d [%s] %s%n",
                err.line(), err.column(), err.severity(), err.message());
        }
    }

    static void printInfo(DocumentInfo r, PrintStream out) {
        out.println("File: " + r.file());
        out.println("Root: " + r.rootElement());
        out.println("Namespace: " + r.rootNamespace());
        out.println("Encoding: " + r.encoding());
        out.println("Grammar: " + r.grammarType());
        if (r.grammarLocation() != null) {
            out.println("Grammar location: " + r.grammarLocation());
        }
        out.println("Size: " + r.sizeBytes() + " bytes");
        out.println("Lines: " + r.lineCount());
    }

    static void printQuery(List<QueryResult> results, PrintStream out) {
        if (results.isEmpty()) {
            out.println("No matches.");
            return;
        }
        for (var qr : results) {
            out.println("File: " + qr.file());
            for (var match : qr.matches()) {
                out.printf("  <%s> [%s] %s%n",
                    match.nodeName(), match.nodeType(),
                    truncate(match.textContent(), 80));
            }
        }
    }

    static void printTransform(TransformResult r, PrintStream out) {
        if (r.content() != null) {
            out.println(r.content());
        } else {
            out.println("Output written to: " + r.outputFile());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        s = s.strip().replaceAll("\\s+", " ");
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
