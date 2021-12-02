/*
 * Copyright © 2018-2021 Jesse Gallagher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openntf.openliberty.domino.httpservice;

import java.util.regex.Pattern;

import org.openntf.openliberty.domino.ext.LoggerPrintStream;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;

import com.darwino.domino.napi.DominoAPI;
import com.ibm.commons.util.io.NullOutputStream;

public class DominoLogPrintStream extends LoggerPrintStream {
	
	public static final Pattern ERROR_PATTERN = Pattern.compile("^(\\[ERROR\\s*\\]|ERROR)\\s.*$"); //$NON-NLS-1$
	
	public DominoLogPrintStream() {
		super(new NullOutputStream());
	}
	
	@Override
	protected void _line(String message) {
		String prefix = getPrefix();
		boolean isError = ERROR_PATTERN.matcher(message).matches();
		String msg;
		if(StringUtil.isEmpty(prefix)) {
			msg = StringUtil.toString(message).replace("%", "%%") + '\n'; //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			msg = prefix + ": " + StringUtil.toString(message).replace("%", "%%") + '\n'; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		if(isError) {
			DominoAPI.get().AddInLogErrorText(msg, DominoAPI.NOERROR);
		} else {
			DominoAPI.get().AddInLogMessageText(msg, DominoAPI.NOERROR);
		}
	}
}