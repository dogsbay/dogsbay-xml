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

import java.io.IOException;
import java.net.URLDecoder;

import org.xml.sax.SAXParseException;

/**
 * Defines an XMLError.
 *
 * @version	$Revision: 1.2 $, $Date: 2005/04/12 15:45:17 $
 * @author Dogsbay
 */
public class XMLError {
	public static final int WARNING = 0;
	public static final int ERROR = 1;
	public static final int FATAL = 2;

	private Exception exception = null;
	private int type = WARNING;
	private String message = null;
	private int line = -1;
	private int column = -1;
	private String systemId = null;

	public XMLError( IOException e) {
		this.exception = e;
		this.type = ERROR;
		this.message = e.getMessage();
	}

	public XMLError( SAXParseException e, int type) {
		this.exception = e;
		this.type = type;

		this.message = e.getMessage();
		this.line = e.getLineNumber();
		this.column = e.getColumnNumber();
		
		try {
			this.systemId = URLDecoder.decode( e.getSystemId(), "UTF-8");
		} catch (Exception e2) {
			this.systemId = null;
		}
	}
	
	/**
	 * Build an error for a specific file — used by project-wide validation,
	 * where errors come from many files rather than one parse. The systemId
	 * lets the error pane open the right file on click.
	 */
	public XMLError( String systemId, int line, int column, int type, String message) {
		this.systemId = systemId;
		this.line = line;
		this.column = column;
		this.type = type;
		this.message = message;
	}

	public int getType() {
		return type;
	}
	
	public int getLineNumber() {
		return line;
	}
	
	public String getSystemId() {
		return systemId;
	}

	public int getColumnNumber() {
		return column;
	}

	public String getMessage() {
		return message;
	}

	public Exception getException() {
		return exception;
	}
	
	public String toString() {
	    return("Ln "+getLineNumber()+" Col "+getColumnNumber()+" - "+getMessage());
	}
} 
