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
package com.xagent.tool;

/**
 * Utility for truncating tool output to stay within reasonable limits.
 * Mirrors pi's truncation behavior in tools.
 */
public final class TruncationUtil {

	public static final int MAX_LINES = 2000;
	public static final int MAX_BYTES = 30_000;

	private TruncationUtil() {}

	/**
	 * Truncate from the end (keep first N lines). Used for file reads.
	 */
	public static String truncateHead(String text, int maxLines, int maxBytes) {
		if (text.length() <= maxBytes && countLines(text) <= maxLines) {
			return text;
		}

		String[] lines = text.split("\n", -1);
		var sb = new StringBuilder();
		int lineCount = 0;

		for (String line : lines) {
			if (lineCount >= maxLines || sb.length() + line.length() + 1 > maxBytes) {
				sb.append("\n... truncated (").append(lines.length - lineCount).append(" lines remaining)");
				break;
			}
			if (lineCount > 0) sb.append("\n");
			sb.append(line);
			lineCount++;
		}

		return sb.toString();
	}

	/**
	 * Truncate from the beginning (keep last N lines). Used for bash output.
	 */
	public static String truncateTail(String text, int maxLines, int maxBytes) {
		if (text.length() <= maxBytes && countLines(text) <= maxLines) {
			return text;
		}

		String[] lines = text.split("\n", -1);
		int startLine = Math.max(0, lines.length - maxLines);

		var sb = new StringBuilder();
		if (startLine > 0) {
			sb.append("... truncated (").append(startLine).append(" lines omitted)\n");
		}

		int byteCount = sb.length();
		for (int i = startLine; i < lines.length; i++) {
			String line = lines[i];
			if (byteCount + line.length() + 1 > maxBytes) {
				break;
			}
			if (i > startLine) sb.append("\n");
			sb.append(line);
			byteCount += line.length() + 1;
		}

		return sb.toString();
	}

	/**
	 * Add line numbers to text (like cat -n).
	 */
	public static String addLineNumbers(String text, int startLine) {
		String[] lines = text.split("\n", -1);
		var sb = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			if (i > 0) sb.append("\n");
			sb.append(String.format("%6d\t%s", startLine + i, lines[i]));
		}
		return sb.toString();
	}

	private static int countLines(String text) {
		if (text.isEmpty()) return 0;
		int count = 1;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '\n') count++;
		}
		return count;
	}
}
