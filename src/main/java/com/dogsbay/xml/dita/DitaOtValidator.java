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

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.dita.dost.Processor;
import org.dita.dost.ProcessorFactory;
import org.dita.dost.exception.DITAOTException;

/**
 * Runs DITA-OT preprocessing against a map and returns the diagnostics it
 * produced — the engine (T3/T4) tier of validation. It runs the {@code dita}
 * transtype, whose preprocessing pipeline (gen-list → mapref → keyref → conref →
 * filter, with DTD/grammar parsing) is exactly the resolution work we want to
 * check; the normalized DITA output is discarded. This catches DTD/grammar
 * errors, keyref/conref resolution failures, broken links, circular maprefs, and
 * filtered-content errors that no static crawl can.
 *
 * <p>Stock DITA-OT has no {@code validate} transtype (it is not a base plugin),
 * so {@code dita} is the lightest transtype that still runs the full preprocess.
 *
 * <p>Diagnostics are captured via a {@link CollectingDitaOtLogger} attached to the
 * {@code Processor} — DITA-OT's logger is just an SLF4J {@code Logger}. The class
 * is pure (no Swing); callers run it off the EDT.
 */
public final class DitaOtValidator {

    private final File ditaOtHome;
    private final ProcessorFactory processorFactory;

    /**
     * @param ditaOtHomePath path to the DITA-OT installation directory
     * @throws IOException if the directory is missing or not a directory
     */
    public DitaOtValidator(String ditaOtHomePath) throws IOException {
        this.ditaOtHome = new File(ditaOtHomePath);
        if (!ditaOtHome.exists() || !ditaOtHome.isDirectory()) {
            throw new IOException("DITA-OT home directory does not exist: " + ditaOtHomePath);
        }
        // Use Xerces (not Saxon 6.x AElfred) for SAX, matching DitaOtTransformer.
        System.setProperty("javax.xml.parsers.SAXParserFactory",
                "org.apache.xerces.jaxp.SAXParserFactoryImpl");
        this.processorFactory = ProcessorFactory.newInstance(ditaOtHome);
    }

    /** The transtype used for validation — the lightest full-preprocess output. */
    private static final String PREPROCESS_TRANSTYPE = "dita";

    /**
     * Validate {@code inputMap} by running the {@code dita} preprocess, applying
     * the given DITAVAL filter(s) so only the content that would ship is checked.
     * Passing all of a deliverable's ditavals (context + publication profiles)
     * keeps deep-validate in agreement with publish.
     *
     * @param inputMap the root map to validate
     * @param ditavals DITAVAL filters to apply (may be empty/null for no filtering)
     * @param tempDir  a working directory for DITA-OT's temp + (discarded) output
     * @return the captured diagnostics (errors and warnings), in log order; empty
     *         when the map validates clean
     */
    public List<DitaOtMessage> validate(File inputMap, java.util.List<File> ditavals,
            File tempDir) {
        if (!inputMap.exists()) {
            return List.of(new DitaOtMessage(null, "FATAL",
                    "Input map does not exist: " + inputMap, inputMap.getPath(), -1, -1));
        }

        File baseTempDir = new File(tempDir, "dita-ot-temp");
        File outputDir = new File(tempDir, "validate-out");
        baseTempDir.mkdirs();
        outputDir.mkdirs();
        processorFactory.setBaseTempDir(baseTempDir);

        CollectingDitaOtLogger logger = new CollectingDitaOtLogger();
        try {
            Processor processor = processorFactory.newProcessor(PREPROCESS_TRANSTYPE)
                    .setInput(inputMap)
                    .setOutputDir(outputDir)
                    .setLogger(logger);
            if (ditavals != null && !ditavals.isEmpty()) {
                StringBuilder filter = new StringBuilder();
                for (File dv : ditavals) {
                    if (filter.length() > 0) {
                        filter.append(File.pathSeparator);
                    }
                    filter.append(dv.getAbsolutePath());
                }
                processor.setProperty("args.filter", filter.toString());
            }
            processor.run();
        } catch (DITAOTException | RuntimeException e) {
            // The run aborted (often on the first fatal error, after the logger has
            // already captured it). Record the cause too — DITA-OT's own message is
            // sometimes null, in which case the class name is the only signal.
            logger.messages().add(new DitaOtMessage(null, "FATAL",
                    "DITA-OT preprocess aborted: " + describe(e),
                    inputMap.getPath(), -1, -1));
        }
        return logger.messages();
    }

    /** A non-null description of a throwable, falling back to its type when the
     * message is null (DITA-OT's exceptions sometimes carry no message). */
    private static String describe(Throwable e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        Throwable cause = e.getCause();
        if (cause != null && cause != e && cause.getMessage() != null) {
            sb.append(" (").append(cause.getMessage()).append(')');
        }
        return sb.toString();
    }
}
