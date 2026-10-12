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

import java.util.EventObject;

/**
 * The event that is fired to a document listener when the document 
 * has been saved, changed or deleted.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:41:32 $
 * @author Dogsbay
 */
public class DogsBayDocumentEvent extends EventObject {
	public final static int MODEL_UPDATED	= 0;
	public final static int TEXT_UPDATED	= 1;
	public final static int CONTENT_UPDATED	= 2;
	public final static int SAVED 			= 3;
	public final static int CHECKED 		= 4;
	public final static int VALIDATED 		= 5;
	public final static int EXTERNAL_UPDATE = 6;
	public final static int DELETED 		= 7;

	private XElement element = null;
	private int type = CONTENT_UPDATED;

 	/**
 	 * The constructor for the event.
	 *
	 * @param document the document that fired the event.
 	 * @param element the element that is the root element for 
	 *        all updated elements.
 	 */	
// 	public DogsBayDocumentEvent( DogsBayDocument document, XElement element) {
//		super( document);
//		
//		this.element = element;
// 	}

 	/**
 	 * The constructor for the event.
 	 *
 	 * @param document the document that fired the event.
 	 * @param element the element that is the root element for 
 	 *        all updated elements.
 	 * @param type the type of change to the document.
 	 */	
 	public DogsBayDocumentEvent( DogsBayDocument document, XElement element, int type) {
 		super( document);
 		
 		this.element = element;
		this.type = type;
 	}

 	/**
 	 * Returns the document that is responsible for firing this event.
 	 *
 	 * @return the document.
 	 */	
 	public DogsBayDocument getDocument() {
		return (DogsBayDocument)super.getSource();
 	}
	
	/**
	 * Returns the type of event.
	 *
	 * @return the event-type.
	 */	
	public int getType() {
		return type;
	}

	/**
	 * Returns the root element that identifies the changed elements 
	 * in the document. 
	 *
	 * @return the root element for all the changed elements.
	 */	
	public XElement getElement() {
		return element;
	}
} 
