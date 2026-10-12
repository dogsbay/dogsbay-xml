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

package com.dogsbay.dogsbayaieditor;

/**
 * Implements a custom Exception class for the Editor.
 *
 * @version	$Revision: 1.2 $, $Date: 2004/04/14 16:54:06 $
 * @author Dogs bay
 */
public class DogsBayException extends Exception {

    /**
     * A holder for the original exception
     */
    private Throwable originalException;

    /**
     * String containing additional error information.
     */
    private String info;

    /**
     * Creates a new DogsBayException instance.
     */
    public DogsBayException() {
        super();
    }

    /**	
     * Creates a new DogsBayException instance.
     * @param msg A detailed error message
     */
    public DogsBayException(String msg) {
        super(msg);
    }

    /**
     * Creates a new DogsBayException instance.
     * @param msg A detailed error message
     * @param info Additional error information
     */
    public DogsBayException(String msg, String info) {
        super(msg);
        this.info = info;
    }

    /**
     * Creates a new DogsBayException instance.
     * @param throwable The root cause of the exception. 
     */
    public DogsBayException(Throwable throwable) {
        super("Original  Message - " + throwable.getMessage());
        originalException = throwable;
    }

    /**
     * Creates a new DogsBayException instance.
     * @param msg A detailed error message
     * @param throwable The root cause of the exception. 
     */
    public DogsBayException(String msg, Throwable throwable) {
        super(msg);
        originalException = throwable;
    }

    /**
     * Get the underlying exception that caused this Exception.
     * @return The Throwable that caused this exception
     */
    public Throwable getOriginalException() {
        return originalException;
    }

    /**
     * Returns additional information provided by this exception (or null).
     * @return Additional error information.
     */
    public String getAdditionalInfo() {
        return info;
    }
    
    public Throwable getCause() { 
    	Throwable cause = originalException;
    	
    	while ( cause != null) {
    		Throwable exception = cause.getCause();

    		if ( exception != null) {
    			cause = exception;
    		} else {
    			return cause;
    		}
    	}
    	
    	return null;
    }

    /**
     * Prints a stack trace for this exception and its underlying exception
     * (if defined).
     */
    public void printStackTrace() {
        super.printStackTrace();
        if (originalException != null) originalException.printStackTrace();
    }

}
