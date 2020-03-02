/*
 * © Copyright IBM Corp. 2012-2013
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at:
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or 
 * implied. See the License for the specific language governing 
 * permissions and limitations under the License.
 */

package org.openntf.openliberty.domino.util.commons.ibm;

/**
 * Contains methods extracted from IBM Commons StringUtil.
 */
public class StringUtil {
	/**
     * Test if a string is empty.
     * A string is considered empty if it is either null or has a length of 0 characters.
     * @param s the string to check
     * @return true if the string is empty
     */
    public static boolean isEmpty(String s) {
        return s==null || s.length()==0;
    }
    
    public static boolean isNotEmpty(String s) {
    	return !isEmpty(s);
    }
    
    public static String toString(Object o) {
    	return o == null ? "" : o.toString(); //$NON-NLS-1$
    }
}
