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

package com.dogsbay.xml.dita;

import org.dita.dost.ProcessorFactory;
import org.dita.dost.Processor;
import org.dita.dost.exception.DITAOTException;
import org.dita.dost.util.Configuration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for running DITA-OT transformations programmatically.
 * Uses DITA-OT 4.3.5 ProcessorFactory API to transform DITA maps to various output formats.
 *
 * @version $Revision: 1.0 $, $Date: 2025/01/24 $
 * @author DogsBay Ltd
 */
public class DitaOtTransformer {

    private final File ditaOtHome;
    private final ProcessorFactory processorFactory;
    private File baseTempDir;

    /**
     * Creates a new DITA-OT transformer.
     *
     * @param ditaOtHomePath Path to DITA-OT installation directory
     * @throws IOException if DITA-OT directory is invalid
     */
    public DitaOtTransformer(String ditaOtHomePath) throws IOException {
        this.ditaOtHome = new File(ditaOtHomePath);

        if (!ditaOtHome.exists() || !ditaOtHome.isDirectory()) {
            throw new IOException("DITA-OT home directory does not exist: " + ditaOtHomePath);
        }

        // Configure SAX parser to use Xerces instead of Saxon's AElfred
        // This prevents conflicts with the old Saxon 6.x library in the classpath
        System.setProperty("javax.xml.parsers.SAXParserFactory",
                          "org.apache.xerces.jaxp.SAXParserFactoryImpl");

        // Initialize ProcessorFactory with DITA-OT base directory
        this.processorFactory = ProcessorFactory.newInstance(ditaOtHome);

        // Set default temp directory to system temp
        this.baseTempDir = new File(System.getProperty("java.io.tmpdir"), "dita-ot-temp");
        this.processorFactory.setBaseTempDir(this.baseTempDir);
    }

    /**
     * Sets the base temporary directory for DITA-OT processing.
     *
     * @param tempDir Temporary directory path
     */
    public void setBaseTempDir(File tempDir) {
        this.baseTempDir = tempDir;
    }

    /**
     * Transforms a DITA map to the specified output format.
     *
     * @param inputMap Path to the input DITA map file
     * @param outputDir Path to the output directory
     * @param transtype Transformation type (e.g., "html5", "pdf", "xhtml")
     * @return true if transformation succeeded, false otherwise
     * @throws IOException if there are file access issues
     */
    public boolean transform(String inputMap, String outputDir, String transtype) throws IOException {
        return transform(inputMap, outputDir, transtype, new HashMap<>());
    }

    /**
     * Transforms a DITA map to the specified output format with custom parameters.
     *
     * @param inputMap Path to the input DITA map file
     * @param outputDir Path to the output directory
     * @param transtype Transformation type (e.g., "html5", "pdf", "xhtml")
     * @param parameters Additional transformation parameters (DITA-OT properties)
     * @return true if transformation succeeded, false otherwise
     * @throws IOException if there are file access issues
     */
    public boolean transform(String inputMap, String outputDir, String transtype,
                            Map<String, String> parameters) throws IOException {

        File inputFile = new File(inputMap);
        if (!inputFile.exists()) {
            throw new IOException("Input map file does not exist: " + inputMap);
        }

        File outputDirectory = new File(outputDir);
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        try {
            // Create a processor using the factory and configure the processor
            Processor processor = processorFactory.newProcessor(transtype)
                .setInput(inputFile)
                .setOutputDir(outputDirectory);

            // Set custom properties
            for (Map.Entry<String, String> param : parameters.entrySet()) {
                processor.setProperty(param.getKey(), param.getValue());
            }

            // Run conversion
            processor.run();

            return true;

        } catch (DITAOTException e) {
            System.err.println("DITA-OT transformation failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            System.err.println("DITA-OT transformation failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }


    /**
     * Gets the DITA-OT version.
     *
     * @return DITA-OT version string
     */
    public String getVersion() {
        try {
            return Configuration.class.getPackage().getImplementationVersion();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /**
     * Main method for command-line usage.
     *
     * @param args Command-line arguments: ditaot-home input-map output-dir transtype
     */
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: DitaOtTransformer <ditaot-home> <input-map> <output-dir> <transtype>");
            System.err.println("Example: DitaOtTransformer /opt/dita-ot-4.3.5 input.ditamap output html5");
            System.exit(1);
        }

        try {
            String ditaOtHome = args[0];
            String inputMap = args[1];
            String outputDir = args[2];
            String transtype = args[3];

            DitaOtTransformer transformer = new DitaOtTransformer(ditaOtHome);
            System.out.println("DITA-OT Version: " + transformer.getVersion());
            System.out.println("Transforming: " + inputMap);
            System.out.println("Output: " + outputDir);
            System.out.println("Type: " + transtype);

            boolean success = transformer.transform(inputMap, outputDir, transtype);

            if (success) {
                System.out.println("Transformation completed successfully!");
                System.exit(0);
            } else {
                System.err.println("Transformation failed!");
                System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
