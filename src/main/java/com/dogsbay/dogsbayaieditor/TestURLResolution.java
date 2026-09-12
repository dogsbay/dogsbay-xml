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

package com.dogsbay.dogsbayaieditor;

import java.io.File;
import java.net.URL;
import com.dogsbay.xml.DogsBayURLUtilities;
import com.dogsbay.dogsbayaieditor.URLUtilities;

public class TestURLResolution {
    public static void main(String[] args) {
        try {
            File projectsDir = new File("projects");
            File allProjectsFile = new File(projectsDir, "all.projects");
            URL baseURL = DogsBayURLUtilities.getURLFromFile(allProjectsFile);

            System.out.println("Base URL: " + baseURL);

            String relativePath = "Browsing Navigation/simple.xml";
            System.out.println("Relative Path: " + relativePath);

            // Mimic Main.java exactly: pass the string directly
            String resolvedURLString = URLUtilities.resolveURL(baseURL, relativePath);
            System.out.println("Resolved URL String: " + resolvedURLString);

            URL resolvedURL = new URL(resolvedURLString);
            System.out.println("Resolved URL: " + resolvedURL);

            File resolvedFile = URLUtilities.toFile(resolvedURL);
            System.out.println("Resolved File: " + resolvedFile);

            if (resolvedFile != null) {
                System.out.println("File exists: " + resolvedFile.exists());
                System.out.println("Is File: " + resolvedFile.isFile());

                // Test DocumentProperties logic
                System.out.println("Testing DocumentProperties logic...");
                URL fileURL = com.dogsbay.xml.DogsBayURLUtilities.getURLFromFile(resolvedFile);
                System.out.println("FileURL from DogsBayURLUtilities: " + fileURL);

                String encrypted = URLUtilities.encrypt(fileURL);
                System.out.println("Encrypted URL: " + encrypted);

                URL decrypted = URLUtilities.decrypt(encrypted);
                System.out.println("Decrypted URL: " + decrypted);

                if (decrypted.equals(fileURL)) {
                    System.out.println("Decryption successful (equals)");
                } else {
                    System.out.println("Decryption failed (not equals)");
                    System.out.println("Expected: " + fileURL);
                    System.out.println("Actual:   " + decrypted);
                }

            } else {
                System.out.println("Resolved File is null");
            }

            // Test XML Parsing
            System.out.println("\nTesting XML Parsing...");
            File projectFile = new File("projects/all.projects");
            if (projectFile.exists()) {
                URL projectURL = com.dogsbay.xml.DogsBayURLUtilities.getURLFromFile(projectFile);
                System.out.println("Loading project file: " + projectURL);

                com.dogsbay.xml.DogsBayDocument doc = new com.dogsbay.xml.DogsBayDocument(projectURL);
                doc.load();
                com.dogsbay.xml.XElement root = doc.getRoot();
                System.out.println("Root element: " + root.getName());

                com.dogsbay.xml.XElement projectsElement = root.getElement("projects");
                if (projectsElement != null) {
                    System.out.println("Found 'projects' element");
                    com.dogsbay.xml.XElement[] projects = projectsElement.getElements("project");
                    System.out.println("Found " + projects.length + " projects");

                    if (projects.length > 0) {
                        com.dogsbay.xml.XElement firstProject = projects[0];
                        String name = firstProject.getAttribute("name");
                        System.out.println("First project name: " + name);

                        com.dogsbay.xml.XElement[] documents = firstProject.getElements("document");
                        System.out.println("First project has " + documents.length + " documents");

                        if (documents.length > 0) {
                            String src = documents[0].getAttribute("src");
                            System.out.println("First document src: " + src);

                            // Test resolution with this src
                            String resolved = URLUtilities.resolveURL(projectURL, src);
                            System.out.println("Resolved src: " + resolved);
                        }
                    }
                } else {
                    System.out.println("'projects' element not found");
                }

            } else {
                System.out.println("projects/all.projects not found");
            }

            // Test Base URL with spaces
            System.out.println("\nTesting Base URL with spaces...");
            try {
                URL spaceBase = new URL("file:/tmp/Space%20Dir/projects/all.projects");
                String relative = "Browsing Navigation/simple.xml";
                String resolved = URLUtilities.resolveURL(spaceBase, relative);
                System.out.println("Base: " + spaceBase);
                System.out.println("Relative: " + relative);
                System.out.println("Resolved: " + resolved);

                URL resolvedURLSpace = new URL(resolved);
                System.out.println("Resolved URL object: " + resolvedURLSpace);

                File fileFromSpaceURL = URLUtilities.toFile(resolvedURLSpace);
                System.out.println("File from space URL: " + fileFromSpaceURL);
                if (fileFromSpaceURL != null) {
                    System.out.println("File path: " + fileFromSpaceURL.getAbsolutePath());
                    if (fileFromSpaceURL.getAbsolutePath().contains("%20")) {
                        System.out.println("FAIL: File path contains %20");
                    } else {
                        System.out.println("SUCCESS: File path decoded correctly");
                    }
                }

                // Test URLDecoder
                String path = resolvedURLSpace.getFile();
                System.out.println("Raw path: " + path);
                String decodedPath = java.net.URLDecoder.decode(path, "UTF-8");
                System.out.println("Decoded path: " + decodedPath);
                File decodedFile = new File(decodedPath);
                System.out.println("Decoded File path: " + decodedFile.getAbsolutePath());
                if (decodedFile.getAbsolutePath().contains("%20")) {
                    System.out.println("FAIL: Decoded path still contains %20");
                } else {
                    System.out.println("SUCCESS: Decoded path is clean");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Test XPath Project specifically
            System.out.println("\nTesting XPath Project...");
            String xpathRelative = "XPath/Contacts.xml";
            String xpathResolved = URLUtilities.resolveURL(baseURL, xpathRelative);
            System.out.println("XPath Resolved: " + xpathResolved);
            File xpathFile = URLUtilities.toFile(new URL(xpathResolved));
            System.out.println("XPath File: " + xpathFile);
            if (xpathFile != null) {
                System.out.println("XPath File exists: " + xpathFile.exists());
                System.out.println("XPath File isFile: " + xpathFile.isFile());
            } else {
                System.out.println("XPath File is NULL");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
