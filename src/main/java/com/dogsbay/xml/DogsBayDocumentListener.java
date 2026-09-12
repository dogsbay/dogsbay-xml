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

import java.util.EventListener;

/**
 * This interface needs to be implemented to be able to listen 
 * to specific Document events.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:41:32 $
 * @author Dogsbay
 */
public interface DogsBayDocumentListener extends EventListener {

 	/**
	 * This method is called when the document has been informed
	 * by an internal process that the document has been updated 
	 * calling the <code>update( XElement element)</code> method.
 	 *
 	 * @param event the document event fired.
 	 */	
 	public void documentUpdated( DogsBayDocumentEvent event);
} 
