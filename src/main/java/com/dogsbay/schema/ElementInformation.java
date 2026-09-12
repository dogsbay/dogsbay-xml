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

package com.dogsbay.schema;

import java.util.Vector;

/**
 * A cross-grammar container for element related information.
 *
 * @version	$Revision: 1.2 $, $Date: 2004/09/23 10:36:17 $
 * @author Dogsbay
 */
public interface ElementInformation {
	public Vector getChildElements();
	public Vector getAttributes();
	public String getName();
	public String getParentName();
	public String getQualifiedName();
	public String getNamespace();
	public String getType();
	public String getPrefix();
	public String getAnnotations();
	public boolean isEmpty();
	public void setPrefix( String prefix);
}