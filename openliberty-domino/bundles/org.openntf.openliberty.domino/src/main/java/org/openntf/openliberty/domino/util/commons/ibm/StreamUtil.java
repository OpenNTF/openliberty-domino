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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * Contains methods extracted from IBM Commons StreamUtil.
 */
public class StreamUtil {
	/**
     * Copy contents of one stream onto another
     * @param is input stream
     * @param os output stream
	 * @return the amount read
	 * @throws IOException if there is an underlying problem
     */
    public static long copyStream(InputStream is, OutputStream os) throws IOException {
        return copyStream(is, os, 8192);
    }

    /**
     * Copy contents of one stream onto another
     * <p>Note: there are cases where InputStream.available() returns &gt; 0 but in
     * actual fact the stream won't be able to read anything, so we need to
     * handle the fact that InputStream.read() may return -1</p>
     * 
     * @param is input stream
     * @param os output stream
     * @param bufferSize size of buffer to use for copy
	 * @return the amount read
	 * @throws IOException if there is an underlying problem
     */
    public static long copyStream(InputStream is, OutputStream os, int bufferSize) throws IOException {
		byte[] buffer = new byte[bufferSize];
		long totalBytes = 0;
		int readBytes;
		while( (readBytes = is.read(buffer))>0 ) {
			os.write(buffer, 0, readBytes);
			totalBytes += readBytes;
		}
		return totalBytes;
    }
    
    /**
     * Read a string from an input stream using the default encoding.
     * @param is the stream to read
     * @return the stream content
	 * @throws IOException if there is an underlying problem
     */
	public static String readString(InputStream is) throws IOException {
		FastStringBuffer sb = new FastStringBuffer();
		sb.load(new InputStreamReader(is));
		return sb.toString();
	}
}
