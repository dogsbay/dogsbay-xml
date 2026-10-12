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

package com.dogsbay.dogsbayaieditor.project;

import javax.swing.Icon;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * The base node class.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:54:53 $
 * @author Dogsbay
 */
public abstract class BaseNode extends DefaultMutableTreeNode implements Comparable {
	public abstract String getName();
	public abstract Icon getIcon();
	public abstract String getDescription();
	public abstract Icon getSelectedIcon();
	public abstract Icon getExpandedIcon();
	public abstract Icon getExpandedSelectedIcon();

	/**
	 * Returns true if the node supplied contains a text or something
	 * that can be used to sort this node.
	 *
	 * @param object the other node.
	 *
	 * @return a positive value if this object is greater than the object supplied.
	 */
	public int compareTo( Object object) {
		int result = getName().compareToIgnoreCase( ((BaseNode)object).getName());
		
		return result;
	}

	/** 
	 * Adds the node to the parent at a sorted location.
	 *
	 * @param the node to be added.
	 */
//	public int add( BaseNode node) {
//		int index = 0;
//		
//		for ( index = 0; index < getChildCount(); index++) {
//			if ( ((BaseNode)node).compareTo( getChildAt( index)) <= 0) {
//				insert( node, index);
//				return index;
//			}
//		}
//		
//		super.add( node);
//		
//		return index;
//	}
} 
