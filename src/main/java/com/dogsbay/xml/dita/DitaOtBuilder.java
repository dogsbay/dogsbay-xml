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
import java.util.Map;

import org.dita.dost.Processor;
import org.dita.dost.ProcessorFactory;
import org.dita.dost.exception.DITAOTException;

/**
 * Builds a deliverable with DITA-OT to a real output directory, applying its
 * DITAVAL filter(s) and publication parameters, and capturing diagnostics via a
 * {@link CollectingDitaOtLogger} (DITA-OT's logger is an SLF4J {@code Logger}).
 *
 * <p>Unlike {@link DitaOtValidator} (which runs the {@code dita} preprocess and
 * discards output), this runs the deliverable's real {@code transtype} and keeps
 * the generated output. The class is pure (no Swing); run it off the EDT.
 */
public final class DitaOtBuilder {

    private static final String DEFAULT_TRANSTYPE = "html5";

    private final ProcessorFactory processorFactory;

    /**
     * @param ditaOtHomePath path to the DITA-OT installation directory
     * @throws IOException if the directory is missing or not a directory
     */
    public DitaOtBuilder(String ditaOtHomePath) throws IOException {
        File ditaOtHome = new File(ditaOtHomePath);
        if (!ditaOtHome.exists() || !ditaOtHome.isDirectory()) {
            throw new IOException("DITA-OT home directory does not exist: " + ditaOtHomePath);
        }
        System.setProperty("javax.xml.parsers.SAXParserFactory",
                "org.apache.xerces.jaxp.SAXParserFactoryImpl");
        this.processorFactory = ProcessorFactory.newInstance(ditaOtHome);
    }

    /**
     * Build {@code inputMap} to {@code outputDir} with the given transtype, DITAVAL
     * filter(s), and params.
     *
     * @param inputMap   the root map to build
     * @param transtype  the output type (null → {@code html5})
     * @param ditavals   DITAVAL filters to apply (may be empty/null)
     * @param params     DITA-OT parameters, already resolved to absolute where needed
     * @param outputDir  the (kept) output directory
     * @param baseTempDir DITA-OT's working/temp directory (caller cleans up)
     * @return the captured diagnostics, in log order; build is considered failed
     *         when any are ERROR/FATAL
     */
    public List<DitaOtMessage> build(File inputMap, String transtype, List<File> ditavals,
            Map<String, String> params, File outputDir, File baseTempDir) {
        if (!inputMap.exists()) {
            return List.of(new DitaOtMessage(null, "FATAL",
                    "Input map does not exist: " + inputMap, inputMap.getPath(), -1, -1));
        }
        baseTempDir.mkdirs();
        outputDir.mkdirs();
        processorFactory.setBaseTempDir(baseTempDir);

        CollectingDitaOtLogger logger = new CollectingDitaOtLogger();
        try {
            Processor processor = processorFactory.newProcessor(
                            transtype != null && !transtype.isBlank() ? transtype : DEFAULT_TRANSTYPE)
                    .setInput(inputMap)
                    .setOutputDir(outputDir)
                    .setLogger(logger);
            String paramFilter = null;
            if (params != null) {
                for (Map.Entry<String, String> e : params.entrySet()) {
                    if (e.getValue() == null) {
                        continue;
                    }
                    if ("args.filter".equals(e.getKey())) {
                        paramFilter = e.getValue();   // joins the list instead of replacing it
                    } else {
                        processor.setProperty(e.getKey(), e.getValue());
                    }
                }
            }
            String filter = joinFilters(ditavals, paramFilter);
            if (filter != null) {
                processor.setProperty("args.filter", filter);
            }
            processor.run();
        } catch (DITAOTException | RuntimeException e) {
            logger.messages().add(new DitaOtMessage(null, "FATAL",
                    "DITA-OT build aborted: " + describe(e), inputMap.getPath(), -1, -1));
        }
        return logger.messages();
    }

    /** The DITAVAL list, then any {@code args.filter} param, as one path list; null when both are empty. */
    public static String joinFilters(List<File> ditavals, String paramFilter) {
        StringBuilder filter = new StringBuilder();
        if (ditavals != null) {
            for (File dv : ditavals) {
                if (filter.length() > 0) {
                    filter.append(File.pathSeparator);
                }
                filter.append(dv.getAbsolutePath());
            }
        }
        if (paramFilter != null && !paramFilter.isBlank()) {
            if (filter.length() > 0) {
                filter.append(File.pathSeparator);
            }
            filter.append(paramFilter.trim());
        }
        return filter.isEmpty() ? null : filter.toString();
    }

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
