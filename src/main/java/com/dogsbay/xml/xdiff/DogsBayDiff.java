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

package com.dogsbay.xml.xdiff;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;

/**
 * A wrapper for external (non-gui) calls to the diff
 * methods
 * 
 *
 */
public class DogsBayDiff {
	
	public static final String START_DIFF_RESULT = "<diff_result>";
	public static final String END_DIFF_RESULT = "</diff_result>";	

	private String baseFile = null;
	private String modFile = null;
	private DogsBayDocument diffResult = null;
	private boolean different = false;
	
	public DogsBayDiff() {
		
	}
	
	public void diff() {
		if(baseFile != null) {
			
			if(modFile != null) {
				
				try {
					XDiff xdiff = new XDiff(baseFile,modFile);
					String resultDiff = xdiff.getDiff();
					
					if (resultDiff.equals(XDiff.NO_DIFF))
					{
						//MessageHandler.showMessage("The two files are identical!","XML Diff");
						different = true;
						return;
					}
					
					String cleanResultDiff = cleanUpResult(resultDiff);
				
					StringBuffer buf = new StringBuffer();
					buf.append(START_DIFF_RESULT);
					buf.append(cleanResultDiff);
					buf.append(END_DIFF_RESULT);
					
	//				java.io.File file = new java.io.File("C:/testdocs/diffout.xml");
	//				java.io.FileWriter writer = new java.io.FileWriter(file);
	//				java.io.BufferedWriter bw = new java.io.BufferedWriter(writer);
	//				bw.write(buf.toString());
	//				bw.flush();
	//				bw.close();
				
					// test the result for a change in the root
					DogsBayDocument testDoc = new DogsBayDocument(buf.toString(),true);
								
					if (!rootChanged(testDoc))
					{
						// show the xml tree with differences
						DogsBayDocument doc = new DogsBayDocument(cleanResultDiff, true);
						//dialogTree.show(doc,getBaseFile(),getModFile(),parent);
						//return;
						this.setDiffResult(doc);
					}
					else
					{
						System.out.println( "The root element of these files are different!");
						return;
					}
				} catch(Exception e) {
					System.out.println("Error: DogsBayDiff - diff - " + e.getMessage());
				}
			}
			else {
				System.out.println("Error: DogsBayDiff - diff - modFile is null");	
			}
		}
		else {
			System.out.println("Error: DogsBayDiff - diff - baseFile is null");
		}
	}
	
	public DogsBayDocument getDiffResult() {
		return(diffResult);
	}
	
	public boolean isDifferent() {
		return(different);
	}

	public void setBaseFile(String baseFile) {
		this.baseFile = baseFile;
	}

	public String getBaseFile() {
		return baseFile;
	}

	public void setModFile(String modFile) {
		this.modFile = modFile;
	}

	public String getModFile() {
		return modFile;
	}

	private void setDiffResult(DogsBayDocument diffResult) {
		this.diffResult = diffResult;
	}
	
	// checks that the root are not the same (2 child elements)
 	public static boolean rootChanged(DogsBayDocument doc)
 	{
 		XElement root = doc.getRoot();
 		
 		if (root == null)
 		{
 			return false;
 		}
 			
 		if (root.elements().size() > 1)
 		{
 			return true;
 		}
 		else
 			return false;
 		
 	} 
 	
 	// cleans out XML declarations and DOCTYPE 
 	public static String cleanUpResult(String resultDiff)
 	{
 		resultDiff = resultDiff.trim();
 		
 		//int xmlDecl;
 		if (resultDiff.startsWith("<?xml") && ! resultDiff.startsWith("<?xml-stylesheet"))
 		{
 			int xmlDeclStart = resultDiff.indexOf("<?xml");
 			int xmlDeclEnd = resultDiff.indexOf("?>",xmlDeclStart);
 			
 			resultDiff = resultDiff.substring(xmlDeclEnd+2,resultDiff.length());
 		}
 		
 		resultDiff = resultDiff.trim();
 		if (resultDiff.startsWith("<!DOCTYPE"))
 		{
 			resultDiff = stripOutDoctype(resultDiff);
 		}
 		
 		return resultDiff;
 	}
 	
 	public static String stripOutDoctype(String resultDiff)
 	{
		
 		int doctypeStart = resultDiff.indexOf("<!DOCTYPE");
 		int endBracket = resultDiff.indexOf(">",doctypeStart);
 		
 		// make sure end bracket is not part of content
 		int quote = resultDiff.indexOf("\"");
 		int secondQuote = -1;
 		if (quote == -1)
 		{
 			quote = resultDiff.indexOf("'");
 			if (quote != -1)
 			{
 				secondQuote = resultDiff.indexOf("'",quote);
 			}
 		}
 		else
 		{
 			secondQuote = resultDiff.indexOf("\"",quote);
 		}
 		
 		if (endBracket < secondQuote)
 		{
 			endBracket = resultDiff.indexOf(">",secondQuote);	
 		}
 	
 		int internalDoctypeEnd = resultDiff.indexOf("]>");
 		
 		if (internalDoctypeEnd == -1)
 		{
 			// can use the endBracket
 			resultDiff = resultDiff.substring(endBracket+1,resultDiff.length());
 		}
 		else
 		{
 			int internalDoctypeStart = resultDiff.indexOf("[");
 			
 			if (endBracket < internalDoctypeStart)
 			{
 				// can use endbracket
 				resultDiff = resultDiff.substring(endBracket+1,resultDiff.length());
 			}
 			else
 			{
 				resultDiff = resultDiff.substring(internalDoctypeEnd+2,resultDiff.length());
 			}
 			
 		}
 		
 		return resultDiff;
 	}
	
}
