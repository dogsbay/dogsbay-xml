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

package com.dogsbay.dogsbayaieditor.dita.live;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import com.dogsbay.dogsbayaieditor.commands.BuildStepReport;
import com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild;
import com.dogsbay.dogsbayaieditor.ditaproject.Deliverable;

/**
 * The Live Preview window: the preview's state and address, the last republish's step
 * times, and the dev server's output, with Open in Browser, Republish Now and Stop.
 * Closing the window stops the preview.
 */
public final class LivePreviewWindow extends JFrame implements LivePreview.Listener {

    private static final long serialVersionUID = 1L;
    private static final int MAX_OUTPUT_LINES = 400;
    private static final Map<LivePreview, LivePreviewWindow> OPEN = new HashMap<>();

    private final transient LivePreview preview;
    private final JLabel status = new JLabel();
    private final JLabel address = new JLabel();
    private final JTextArea publishReport = new JTextArea();
    private final JTextArea output = new JTextArea();
    private final JButton open = new JButton("Open in Browser");
    private final JButton republish = new JButton("Republish Now");
    private final JButton stop = new JButton("Stop");
    private boolean opened;

    /**
     * Show the live preview of a deliverable: the running one's window, or a new preview that opens the
     * browser when it is ready. Call on the event dispatch thread.
     */
    public static LivePreview show(Window owner, Path root, Deliverable deliverable, String ditaOtHome) {
        return show(owner, root, deliverable, ditaOtHome, true);
    }

    /**
     * As {@link #show(Window, Path, Deliverable, String)}; {@code openBrowser} false leaves the browser alone
     * (an agent's start: it reads the address from the result). Returns the preview shown.
     */
    public static LivePreview show(Window owner, Path root, Deliverable deliverable, String ditaOtHome, boolean openBrowser) {
        LivePreview preview = LivePreview.running(root, deliverable.name()).orElse(null);
        if (preview != null && preview.state() == LivePreview.State.FAILED) {
            close(preview); // start again rather than hand back the failure
            preview = null;
        }
        if (preview != null && OPEN.containsKey(preview)) {
            LivePreviewWindow window = OPEN.get(preview);
            window.setVisible(true);
            window.toFront();
            return preview;
        }
        if (preview != null) {
            return preview;
        }
        preview = new LivePreview(root, deliverable, ditaOtHome);
        LivePreviewWindow window = new LivePreviewWindow(preview);
        window.opened = !openBrowser;
        OPEN.put(preview, window);
        window.setLocationRelativeTo(owner);
        window.setVisible(true);
        preview.addListener(window);
        preview.start();
        return preview;
    }

    /** Close a preview's window, which stops it; a preview without a window is stopped. Call on the event dispatch thread. */
    public static void close(LivePreview preview) {
        // Stopped here, not in the window's closed event, which AWT runs later: a caller that stops and
        // then starts again must not find the preview it has just stopped
        preview.stop();
        LivePreviewWindow window = OPEN.remove(preview);
        if (window != null) {
            window.dispose();
        }
    }

    private LivePreviewWindow(LivePreview preview) {
        super("Live Preview: " + preview.deliverable().name());
        this.preview = preview;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                OPEN.remove(preview);
                preview.stop();
            }
        });

        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        top.add(status, BorderLayout.NORTH);
        address.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        address.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openInBrowser();
            }
        });
        top.add(address, BorderLayout.CENTER);
        JLabel hint = new JLabel("<html>Edits in the project republish the deliverable; the page reloads when the site has been rebuilt. "
                + "Search is not available in the preview.</html>");
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN));
        top.add(hint, BorderLayout.SOUTH);

        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, publishReport.getFont().getSize());
        publishReport.setEditable(false);
        publishReport.setFont(mono);
        output.setEditable(false);
        output.setFont(mono);
        JScrollPane reportScroll = new JScrollPane(publishReport);
        reportScroll.setBorder(BorderFactory.createTitledBorder("Last publish"));
        JScrollPane outputScroll = new JScrollPane(output);
        outputScroll.setBorder(BorderFactory.createTitledBorder("Dev server"));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, reportScroll, outputScroll);
        split.setResizeWeight(0.45);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        open.addActionListener(e -> openInBrowser());
        republish.addActionListener(e -> preview.republish());
        stop.addActionListener(e -> dispose());
        buttons.add(open);
        buttons.add(republish);
        buttons.add(stop);

        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(split, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        setSize(720, 560);
        refresh();
    }

    @Override
    public void changed(LivePreview p) {
        SwingUtilities.invokeLater(this::refresh);
    }

    @Override
    public void output(String line) {
        SwingUtilities.invokeLater(() -> {
            output.append(line + "\n");
            int lines = output.getLineCount();
            if (lines > MAX_OUTPUT_LINES) {
                try {
                    output.replaceRange("", 0, output.getLineStartOffset(lines - MAX_OUTPUT_LINES));
                } catch (javax.swing.text.BadLocationException e) {
                    // keep everything
                }
            }
            output.setCaretPosition(output.getDocument().getLength());
        });
    }

    private void refresh() {
        LivePreview.State state = preview.state();
        status.setText("<html><b>" + label(state) + "</b> " + escape(preview.message()) + "</html>");
        String url = preview.url();
        address.setText(url == null ? " " : "<html><a href=''>" + escape(url) + "</a></html>");
        DeliverableBuild build = preview.lastBuild();
        if (build != null) {
            long errors = build.messages().stream().filter(com.dogsbay.xml.dita.DitaOtMessage::isError).count();
            publishReport.setText((errors > 0 ? errors + " error(s): see the Project Validation tab of a Build Deliverables run.\n" : "")
                    + BuildStepReport.format(build, ""));
            publishReport.setCaretPosition(0);
        }
        open.setEnabled(url != null);
        republish.setEnabled(state == LivePreview.State.READY || state == LivePreview.State.READY_WITH_ERRORS);
        // Open the browser the first time the preview is ready
        if ((state == LivePreview.State.READY || state == LivePreview.State.READY_WITH_ERRORS) && url != null && !opened) {
            opened = true;
            openInBrowser();
        }
    }

    private void openInBrowser() {
        String url = preview.url();
        if (url == null) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception e) {
            output(" (could not open a browser: " + e.getMessage() + "; open " + url + ")");
        }
    }

    private static String label(LivePreview.State state) {
        return switch (state) {
            case STARTING -> "Starting:";
            case PUBLISHING -> "Publishing:";
            case READY -> "Ready:";
            case READY_WITH_ERRORS -> "Ready, with errors:";
            case FAILED -> "Failed:";
            case STOPPED -> "Stopped:";
        };
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
