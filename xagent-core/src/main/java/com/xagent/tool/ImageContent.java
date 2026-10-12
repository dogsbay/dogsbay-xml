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
 * Base64-encoded image returned by a tool.
 */
public record ImageContent(
	String base64Data,
	String mimeType,
	String fileName
) {
	public int approximateSizeBytes() {
		return base64Data != null ? (int) (base64Data.length() * 0.75) : 0;
	}

	public String formatSizeLabel() {
		int bytes = approximateSizeBytes();
		if (bytes < 1024) return bytes + "B";
		if (bytes < 1024 * 1024) return (bytes / 1024) + "KB";
		return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
	}

	public static String mimeTypeForExtension(String ext) {
		return switch (ext.toLowerCase()) {
			case "png" -> "image/png";
			case "jpg", "jpeg" -> "image/jpeg";
			case "gif" -> "image/gif";
			case "webp" -> "image/webp";
			case "svg" -> "image/svg+xml";
			case "bmp" -> "image/bmp";
			case "ico" -> "image/x-icon";
			default -> null;
		};
	}

	public static boolean isImageExtension(String ext) {
		return mimeTypeForExtension(ext) != null;
	}
}
