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

import java.io.IOException;
import java.io.InputStream;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Segment;

/**
 * A XML input stream, for a XML Document.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:44:46 $
 * @author Dogsbay
 */
class XmlInputStream extends InputStream {

    private Segment segment = null;
	private Document document = null;

    private int end		= 0; // end position
    private int pos		= 0; // pos in document
    private int index	= 0; // index into array of the segment

	/**
	 * Constructs a stream for the document.
	 *
	 * @param doc the document with Xml Information.
	 */
    public XmlInputStream( Document doc) {
        this.segment = new Segment();
		this.document = doc;
    	
        end = document.getLength();
        pos	= 0;

        try {
    		loadSegment();
        } catch ( IOException ioe) {
    		throw new Error( "unexpected: "+ioe);
        }
    }

	/**
	 * Sets the new range to be scanned for the stream and
	 * loads the necessary information.
	 *
	 * @param start the start of the segment.
	 * @param end the end of the segment.
	 */
	public void setRange( int start, int end) {
		this.end = end;
		pos	= start;
	
		try {
			loadSegment();
		} catch ( IOException ioe) {
			throw new Error( "unexpected: "+ioe);
		}
	}

	/**
	 * Reads the next byte of data from this input stream. The value 
	 * byte is returned as an <code>int</code> in the range 
	 * <code>0</code> to <code>255</code>. If no byte is available 
	 * because the end of the stream has been reached, the value 
	 * <code>-1</code> is returned. 
	 *
	 * @return     the next byte of data, or <code>-1</code> if the end of the
	 *             stream is reached.
	 */
	public int read() throws IOException {
	    if ( index >= segment.offset + segment.count) {
			if ( pos >= end) {
			    // no more data
			    return -1;
			}

			loadSegment();
	    }

	    return segment.array[ index++];
	}

	// Loads the segment with new information if necessary...
	private void loadSegment() throws IOException {
	    try {
			int n = Math.min( 1024, end - pos);

			document.getText( pos, n, segment);
			pos += n;

			index = segment.offset;
	    } catch ( BadLocationException e) {
			throw new IOException("Bad location");
	    }
	}
	
	public void cleanup() {
		finalize();
	}
	
	protected void finalize() {
		segment = null;
		document = null;
	}
}
