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
import javax.swing.ImageIcon;
import javax.swing.tree.DefaultTreeModel;

import org.bounce.image.ImageUtilities;

import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;

/**
 * The default node for a project.
 *
 * @version	$Revision: 1.2 $, $Date: 2005/09/05 09:08:30 $
 * @author Dogsbay
 */
public class ProjectNode extends FolderNode {
	private static final ImageIcon ICON = DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/project/icons/ProjectIcon.gif");
	private static final ImageIcon EXPANDED_ICON = DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/project/icons/SelectedProjectIcon.gif");
	
	/**
	 * The constructor for the project node.
	 *
	 * @param properties the project properties.
	 */
	public ProjectNode( DefaultTreeModel model, ProjectProperties properties, boolean isStartup) {
		super( model, properties, isStartup);
	}
	
	/**
	 * The icon for this node.
	 *
	 * @return the icon for the element.
	 */
	public Icon getSelectedIcon() {
		return ImageUtilities.createDarkerImage( ICON);
	}
	
	/**
	 * Returns the icon that is shown when the node is expanded and selected.
	 *
	 * @return the selected expanded icon.
	 */
	public Icon getExpandedSelectedIcon() {
		return ImageUtilities.createDarkerImage( EXPANDED_ICON);
	}


	/**
	 * Returns the icon that is shown when the node is expanded.
	 *
	 * @return the expanded icon.
	 */
	public Icon getExpandedIcon() {
		return EXPANDED_ICON;
	}

	/**
	 * The icon for this node.
	 *
	 * @return the icon for the element.
	 */
	public Icon getIcon() {
		return ICON;
	}
} 
