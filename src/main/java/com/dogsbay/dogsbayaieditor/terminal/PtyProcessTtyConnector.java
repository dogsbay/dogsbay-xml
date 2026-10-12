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

package com.dogsbay.dogsbayaieditor.terminal;

import com.jediterm.terminal.Questioner;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;

import java.awt.Dimension;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Custom TtyConnector implementation adapting PtyProcess to JediTerm.
 * Replaces jediterm-pty dependency to avoid version conflicts.
 */
public class PtyProcessTtyConnector implements TtyConnector {
    private final PtyProcess process;
    private final InputStreamReader reader;
    private final OutputStream output;
    private final Charset charset;

    public PtyProcessTtyConnector(PtyProcess process, Charset charset) {
        this.process = process;
        this.charset = charset;
        this.reader = new InputStreamReader(process.getInputStream(), charset);
        this.output = process.getOutputStream();
    }

    @Override
    public boolean init(Questioner questioner) {
        return true;
    }

    @Override
    public void close() {
        process.destroy();
    }

    @Override
    public String getName() {
        return "Terminal";
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        return reader.read(buf, offset, length);
    }

    @Override
    public boolean ready() throws IOException {
        return reader.ready();
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        output.write(bytes);
        output.flush();
    }

    @Override
    public boolean isConnected() {
        return process.isAlive();
    }

    @Override
    public void write(String string) throws IOException {
        write(string.getBytes(charset));
    }

    @Override
    public int waitFor() throws InterruptedException {
        return process.waitFor();
    }

    // Note: older JediTerm versions used resize(TermSize), newer might use
    // resize(Dimension)
    // We'll implement resize(Dimension) as it's common in newer APIs,
    // but we might need to adjust based on compilation errors.
    public void resize(Dimension termSize, Dimension pixelSize) {
        if (process.isAlive()) {
            process.setWinSize(new WinSize(termSize.width, termSize.height));
        }
    }

    // For compatibility if interface expects this signature
    public void resize(Dimension termSize) {
        resize(termSize, null);
    }
}
