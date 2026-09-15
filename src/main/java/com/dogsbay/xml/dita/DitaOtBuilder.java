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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.dita.dost.Processor;
import org.dita.dost.ProcessorFactory;
import org.dita.dost.exception.DITAOTException;
import org.dita.dost.log.LoggerListener;
import org.dita.dost.util.Configuration;

/**
 * Builds a deliverable with DITA-OT to a real output directory, applying its
 * DITAVAL filter(s) and publication parameters, and capturing diagnostics via a
 * {@link CollectingDitaOtLogger} (DITA-OT's logger is an SLF4J {@code Logger}).
 *
 * <p>Unlike {@link DitaOtValidator} (which runs the {@code dita} preprocess and
 * discards output), this runs the deliverable's real {@code transtype} and keeps
 * the generated output. The class is pure (no Swing); run it off the EDT.
 *
 * <p><b>Keeping temporary files.</b> DITA-OT's Java {@link Processor} deletes its
 * temporary folder after every successful run, whatever {@code clean.temp} says:
 * {@code clean.temp} only controls DITA-OT's own clean-up step, and the processor
 * then removes the folder itself. So when the params ask for
 * {@code clean.temp=no}, the build runs DITA-OT's Ant project directly, exactly as
 * the processor does but without that final delete, the way the {@code dita}
 * command honours {@code --clean.temp=no}.
 */
public final class DitaOtBuilder {

    private static final String DEFAULT_TRANSTYPE = "html5";
    /** The DITA-OT parameter that keeps the temporary files. */
    public static final String CLEAN_TEMP = "clean.temp";

    private final File ditaOtHome;
    private final ProcessorFactory processorFactory;

    /**
     * @param ditaOtHomePath path to the DITA-OT installation directory
     * @throws IOException if the directory is missing or not a directory
     */
    public DitaOtBuilder(String ditaOtHomePath) throws IOException {
        File home = new File(ditaOtHomePath);
        if (!home.exists() || !home.isDirectory()) {
            throw new IOException("DITA-OT home directory does not exist: " + ditaOtHomePath);
        }
        System.setProperty("javax.xml.parsers.SAXParserFactory",
                "org.apache.xerces.jaxp.SAXParserFactoryImpl");
        this.ditaOtHome = home.getAbsoluteFile();
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
     * @param baseTempDir DITA-OT's working/temp directory (caller cleans up). When the
     *                   params keep temporary files ({@link #keepsTemp}), DITA-OT
     *                   writes its temporary files directly into this folder and
     *                   leaves them there; otherwise it works in a subfolder it
     *                   deletes when the build succeeds.
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
        String type = transtype != null && !transtype.isBlank() ? transtype : DEFAULT_TRANSTYPE;
        Map<String, String> properties = properties(params, ditavals);
        if (keepsTemp(params)) {
            return buildKeepingTemp(inputMap, type, properties, outputDir, baseTempDir);
        }
        processorFactory.setBaseTempDir(baseTempDir);

        CollectingDitaOtLogger logger = new CollectingDitaOtLogger();
        try {
            Processor processor = processorFactory.newProcessor(type)
                    .setInput(inputMap)
                    .setOutputDir(outputDir)
                    .setLogger(logger);
            properties.forEach(processor::setProperty);
            processor.run();
        } catch (DITAOTException | RuntimeException e) {
            logger.messages().add(new DitaOtMessage(null, "FATAL",
                    "DITA-OT build aborted: " + describe(e), inputMap.getPath(), -1, -1));
        }
        return logger.messages();
    }

    /** True when the params ask DITA-OT to keep its temporary files ({@code clean.temp} of no or false). */
    public static boolean keepsTemp(Map<String, String> params) {
        String value = params == null ? null : params.get(CLEAN_TEMP);
        return value != null && (value.trim().equalsIgnoreCase("no") || value.trim().equalsIgnoreCase("false"));
    }

    /** The params (without nulls) plus the joined filter, as DITA-OT properties. */
    private static Map<String, String> properties(Map<String, String> params, List<File> ditavals) {
        Map<String, String> properties = new LinkedHashMap<>();
        String paramFilter = null;
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (e.getValue() == null) {
                    continue;
                }
                if ("args.filter".equals(e.getKey())) {
                    paramFilter = e.getValue();   // joins the list instead of replacing it
                } else {
                    properties.put(e.getKey(), e.getValue());
                }
            }
        }
        String filter = joinFilters(ditavals, paramFilter);
        if (filter != null) {
            properties.put("args.filter", filter);
        }
        return properties;
    }

    /**
     * Run DITA-OT's Ant project as {@link Processor#run} does (same build file, base
     * directory, target and properties), with {@code tempDir} as the temporary folder
     * and without deleting it afterwards.
     */
    private List<DitaOtMessage> buildKeepingTemp(File inputMap, String transtype, Map<String, String> properties,
            File outputDir, File tempDir) {
        CollectingDitaOtLogger logger = new CollectingDitaOtLogger();
        try {
            Project project = new Project();
            project.setCoreLoader(getClass().getClassLoader());
            project.addBuildListener(new LoggerListener(logger));
            // Processor.run fires build-started but never build-finished (which clears
            // Ant's JVM-wide introspection cache); this matches it.
            project.fireBuildStarted();
            project.init();
            project.setBaseDir(ditaOtHome);
            project.setKeepGoingMode(false);
            project.setUserProperty("dita.dir", ditaOtHome.getAbsolutePath());
            project.setUserProperty("transtype", transtype);
            project.setUserProperty("args.input", inputMap.getAbsoluteFile().toURI().toString());
            project.setUserProperty("output.dir", outputDir.getAbsolutePath());
            File parent = tempDir.getAbsoluteFile().getParentFile();
            if (parent != null) {
                project.setUserProperty("base.temp.dir", parent.getAbsolutePath());
            }
            properties.forEach(project::setUserProperty);
            // Set last so a param cannot move or clean the folder the caller owns.
            project.setUserProperty("dita.temp.dir", tempDir.getAbsolutePath());
            project.setUserProperty(CLEAN_TEMP, "no");
            ProjectHelper.configureProject(project, buildFile());
            project.executeTargets(new Vector<>(List.of("dita2" + transtype)));
        } catch (RuntimeException e) {
            logger.messages().add(new DitaOtMessage(null, "FATAL",
                    "DITA-OT build aborted: " + describe(e), inputMap.getPath(), -1, -1));
        }
        return logger.messages();
    }

    /** The build file the processor uses: {@code org.dita.base}'s, else the home's. */
    private File buildFile() {
        File base = Configuration.pluginResourceDirs.get("org.dita.base");
        File file = base == null ? null : new File(new File(ditaOtHome, base.getPath()), "build.xml");
        return file != null && file.isFile() ? file : new File(ditaOtHome, "build.xml");
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
