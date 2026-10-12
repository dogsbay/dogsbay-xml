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

package com.dogsbay.xml.editor;

import java.awt.Color;
import java.awt.Font;

import javax.swing.text.DefaultEditorKit;

/**
 * The XML editor kit supports handling of editing XML content.  
 * It supports syntax highlighting, tab replacements and automatic 
 * indents.
 *
 * @version	$Revision: 1.2 $, $Date: 2004/10/19 14:39:44 $
 * @author Dogsbay
 */
public abstract class DogsBayEditorKit extends DefaultEditorKit {

    /**
     * Get the MIME type of the data that this
     * kit represents support for. This kit supports
     * the type <code>text/xml</code>.
	 *
	 * @return the type.
     */
    public abstract String getContentType();

    public abstract void setHighlight( boolean enabled);

    public abstract boolean isHighlight();

    public abstract void setErrorHighlighting( boolean enabled);

    public abstract boolean isErrorHighlighting();

    public abstract void cleanup();
	
    public abstract void setFont( Font font);
    public abstract Font getFont();

    public abstract void setAttributes( int id, Color color, int style);
}







