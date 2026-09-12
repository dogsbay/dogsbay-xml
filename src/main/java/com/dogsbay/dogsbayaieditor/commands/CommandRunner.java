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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.dogsbay.dogsbayaieditor.commands.results.*;

/**
 * Simple CLI runner for testing headless commands.
 *
 * Usage:
 *   ./gradlew run -PmainClass=com.dogsbay.dogsbayaieditor.commands.CommandRunner \
 *       --args="validate src/test/resources/commands/valid.xml"
 *
 * Or after building:
 *   java -cp build/classes/java/main:lib/* \
 *       com.dogsbay.dogsbayaieditor.commands.CommandRunner validate path/to/file.xml
 *
 * Commands:
 *   validate <file> [schema] [--catalog catalog.xml]  - Validate XML (XSD, DTD, RNG)
 *   parse <file>                 - Parse and show document info
 *   info <file>                  - Show document metadata
 *   format <file>                - Pretty-print XML
 *   transform <input> <xslt>    - Apply XSLT transformation
 *   query <file> <xpath>         - Run XPath query
 */
public class CommandRunner {

    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }

        String command = args[0];
        HeadlessExecutor executor = new HeadlessExecutor();

        try {
            switch (command) {
                case "validate" -> {
                    // Parse: validate <file> [schema] [--catalog cat1.xml [--catalog cat2.xml]]
                    Path file = Path.of(args[1]);
                    Path schema = null;
                    List<Path> catalogs = new ArrayList<>();
                    int i = 2;
                    while (i < args.length) {
                        if ("--catalog".equals(args[i]) && i + 1 < args.length) {
                            catalogs.add(Path.of(args[++i]));
                        } else if (schema == null && !args[i].startsWith("--")) {
                            schema = Path.of(args[i]);
                        }
                        i++;
                    }
                    var result = executor.execute(new ValidateCommand(file, schema, catalogs));
                    System.out.println(result.valid() ? "VALID" : "INVALID");
                    for (var err : result.errors()) {
                        System.out.printf("  %d:%d [%s] %s%n",
                            err.line(), err.column(), err.severity(), err.message());
                    }
                }
                case "parse" -> {
                    var result = executor.execute(new ParseCommand(Path.of(args[1])));
                    System.out.println("Well-formed: " + result.wellFormed());
                    System.out.println("Root: " + result.rootElement());
                    System.out.println("Namespace: " + result.rootNamespace());
                    System.out.println("Encoding: " + result.encoding());
                    for (var err : result.errors()) {
                        System.out.printf("  %d:%d [%s] %s%n",
                            err.line(), err.column(), err.severity(), err.message());
                    }
                }
                case "info" -> {
                    var result = executor.execute(new InfoCommand(Path.of(args[1])));
                    System.out.println("File: " + result.file());
                    System.out.println("Root: " + result.rootElement());
                    System.out.println("Namespace: " + result.rootNamespace());
                    System.out.println("Encoding: " + result.encoding());
                    System.out.println("Grammar: " + result.grammarType());
                    System.out.println("Grammar location: " + result.grammarLocation());
                    System.out.println("Size: " + result.sizeBytes() + " bytes");
                    System.out.println("Lines: " + result.lineCount());
                }
                case "format" -> {
                    var result = executor.execute(new FormatCommand(Path.of(args[1]), null, null));
                    System.out.println(result.content());
                }
                case "transform" -> {
                    if (args.length < 3) {
                        System.err.println("Usage: transform <input> <xslt>");
                        System.exit(1);
                    }
                    var result = executor.execute(new TransformCommand(
                        Path.of(args[1]), Path.of(args[2]), null, Map.of()
                    ));
                    System.out.println(result.content());
                }
                case "query" -> {
                    if (args.length < 3) {
                        System.err.println("Usage: query <file> <xpath>");
                        System.exit(1);
                    }
                    var results = executor.execute(new QueryCommand(args[1], args[2]));
                    for (var qr : results) {
                        System.out.println("File: " + qr.file());
                        for (var match : qr.matches()) {
                            System.out.printf("  <%s> [%s] %s%n",
                                match.nodeName(), match.nodeType(),
                                truncate(match.textContent(), 80));
                        }
                    }
                    if (results.isEmpty()) {
                        System.out.println("No matches.");
                    }
                }
                default -> {
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
                }
            }
        } catch (CommandException e) {
            System.err.println("Error [" + e.getCode() + "]: " + e.getMessage());
            System.exit(2);
        }
    }

    private static void printUsage() {
        System.err.println("""
            Usage: CommandRunner <command> <args...>

            Commands:
              validate <file> [schema] [--catalog file]  Validate XML (XSD/DTD/RNG + catalogs)
              parse <file>                 Parse and show root element info
              info <file>                  Show document metadata
              format <file>                Pretty-print XML to stdout
              transform <input> <xslt>     Apply XSLT transformation
              query <file-or-glob> <xpath> Run XPath query
            """);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        s = s.strip().replaceAll("\\s+", " ");
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
