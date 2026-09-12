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

import org.dom4j.io.OutputFormat;

/**
 * This DogsBayOutputFormat is used to ...
 *
 * @version $Revision: 1.1 $, $Date: 2004/05/28 09:14:00 $
 * @author Dogsbay
 */
public class DogsBayOutputFormat extends OutputFormat {
	private String version = "1.0";
	private boolean omitStandalone = true;
	private String standalone = null;

	public DogsBayOutputFormat() {
		super();
	}

	public DogsBayOutputFormat( String arg0) {
		super(arg0);
	}

	public DogsBayOutputFormat( String arg0, boolean arg1) {
		super(arg0, arg1);
	}

	public DogsBayOutputFormat(String arg0, boolean arg1, String arg2) {
		super(arg0, arg1, arg2);
	}
	
	public String getVersion() {
		return version;
	}

	public void setVersion( String version) {
		this.version = version;
	}

	public String getStandalone() {
		return standalone;
	}

	public void setStandalone( String standalone) {
		this.standalone = standalone;
	}
	
	public boolean isOmitStandalone() {
		return omitStandalone;
	}

	public void setOmitStandalone( boolean omit) {
		omitStandalone = omit;
	}
}
