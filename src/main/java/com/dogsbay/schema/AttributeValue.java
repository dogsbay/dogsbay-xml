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

/**
 * A cross-grammar container for attribute value related information.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/09/23 10:27:53 $
 * @author Dogsbay
 */
public class AttributeValue {
	public static final int NORMAL_TYPE = 0;
	public static final int DEFAULT_TYPE = 1;
	public static final int FIXED_TYPE = 2;
	
	private String value = null;
	private int type = NORMAL_TYPE;

	public AttributeValue( String value, int type) {
		this.value = value;
		this.type = type;
	}

	public String getValue() {
		return value;
	}

	public boolean isFixed() {
		return type == FIXED_TYPE;
	}

	public boolean isDefault() {
		return type == DEFAULT_TYPE;
	}
	
	public String toString() {
		return value;
	}
} 
