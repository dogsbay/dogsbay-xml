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

import org.dom4j.DocumentType;
import org.dom4j.Element;
import org.dom4j.tree.DefaultDocument;

/**
 * The default implementation of the XDocument interface.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:41:32 $
 * @author Dogsbay
 */
public class XDocument extends DefaultDocument {
	private static final String DEFAULT_ENCODING = "UTF-8";
	private static final String DEFAULT_VERSION = "1.0";

	private String encoding = null;
	private String version = null;

    public XDocument() { 
		super();
    }

    public XDocument( String name) { 
        super( name);
    }

    public XDocument( Element root) { 
		super( root);
    }

    public XDocument( DocumentType docType) {
		super( docType);
    }

    public XDocument(Element root, DocumentType docType) {
		super( root, docType);
    }

    public XDocument(String name, Element root, DocumentType docType) {
        super( name, root, docType);
    }

	/**
	 * Gets the encoding for the document.
	 * Returns "UTF-8" by default.
	 *
	 * @return the document encoding.
	 */
	public String getEncoding() {
		String result = DEFAULT_ENCODING;

		if ( encoding != null) {
			result = encoding;
		}
		
		return result;
	}

	/**
	 * Sets the encoding for the document. 
	 * Setting the encoding to null will set the default encoding.
	 *
	 * @param encoding the document encoding.
	 */
	public void setEncoding( String encoding) {
		this.encoding = encoding;
	}

	/**
	 * Gets the xml version of the document.
	 * Returns "1.0" by default.
	 *
	 * @return the document encoding.
	 */
	public String getVersion() {
		String result = DEFAULT_VERSION;

		if ( version != null) {
			result = version;
		}
		
		return result;
	}

	/**
	 * Sets the xml version for the document.
	 * Setting the version to null will set the default version.
	 *
	 * @param version the document xml version.
	 */
	public void setVersion( String version) {
		this.version = version;
	}
	
	public void cleanup() {
		XElement root = (XElement)getRootElement();
		root.cleanup();
	}
} 
