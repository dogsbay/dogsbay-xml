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

/*
 * Created on 14-Mar-2005
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */

package com.dogsbay.dogsbayaieditor;

import com.dogsbay.xml.DogsBayDocument;

/**
 * @author DogsBay
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class OpenDocument{
  String name = null;
  DogsBayDocument doc = null;
  
  public OpenDocument(String name, DogsBayDocument doc)
  {
    this.name = name;
    this.doc = doc;
  }
  
  public String toString()
  {
    return name;
  }

  public DogsBayDocument getDocument()
  {
    return doc;
  }

}