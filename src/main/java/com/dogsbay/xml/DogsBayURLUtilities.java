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

package com.dogsbay.xml;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

public class DogsBayURLUtilities {

	/**
	 * Converts a File to a URL using the proper URI conversion.
	 * This replaces the deprecated file.toURL() method with file.toURI().toURL()
	 * which properly encodes special characters according to RFC 2396.
	 *
	 * @param file The file to convert to a URL
	 * @return URL representation of the file, or null if file is null
	 * @throws MalformedURLException if the file path cannot be converted to a URL
	 */
	public static URL getURLFromFile(File file) throws MalformedURLException {
		if(file != null) {
			try {
				return file.toURI().toURL();
			} catch (MalformedURLException e) {
				// Re-throw MalformedURLException to maintain API compatibility
				throw e;
			}
		}
		else {
			return(null);
		}
	}
}
