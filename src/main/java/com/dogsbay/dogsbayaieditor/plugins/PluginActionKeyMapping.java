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

package com.dogsbay.dogsbayaieditor.plugins;


/**
 * 
 *
 * @version	$Revision: 1.0 $, $Date: 15 Mar 2007 17:10:30 $
 */
public class PluginActionKeyMapping {

	private String keystroke_action_name = null;
	private String keystroke_action_description = null;
	private String keystroke_mask = null;
	private String keystroke_value = null;
	
	private PluginAction action = null;
	
	/**
	 * @param keystroke_action_name the keystroke_action_name to set
	 */
	public void setKeystroke_action_name(String keystroke_action_name) {

		this.keystroke_action_name = keystroke_action_name;
	}
	/**
	 * @return the keystroke_action_name
	 */
	public String getKeystroke_action_name() {

		return keystroke_action_name;
	}
	/**
	 * @param keystroke_action_description the keystroke_action_description to set
	 */
	public void setKeystroke_action_description(String keystroke_action_description) {

		this.keystroke_action_description = keystroke_action_description;
	}
	/**
	 * @return the keystroke_action_description
	 */
	public String getKeystroke_action_description() {

		return keystroke_action_description;
	}
	/**
	 * @param keystroke_mask the keystroke_mask to set
	 */
	public void setKeystroke_mask(String keystroke_mask) {

		this.keystroke_mask = keystroke_mask;
	}
	/**
	 * @return the keystroke_mask
	 */
	public String getKeystroke_mask() {

		return keystroke_mask;
	}
	/**
	 * @param keystroke_value the keystroke_value to set
	 */
	public void setKeystroke_value(String keystroke_value) {

		this.keystroke_value = keystroke_value;
	}
	/**
	 * @return the keystroke_value
	 */
	public String getKeystroke_value() {

		return keystroke_value;
	}
	/**
	 * @param action the action to set
	 */
	public void setAction(PluginAction action) {

		this.action = action;
	}
	/**
	 * @return the action
	 */
	public PluginAction getAction() {

		return action;
	}
	
}
