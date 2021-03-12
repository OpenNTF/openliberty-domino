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
package org.openntf.openliberty.domino.log;

import java.io.PrintStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

public class DominoConsoleHandler extends StreamHandler {
	
	private PrintStream out;

	public DominoConsoleHandler(PrintStream out) {
		super();
		
		this.out = out;
		AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
			try {
				setOutputStream(out);
			} catch(Throwable t) {
				t.printStackTrace();
			}
			return null;
		});
	}
	
	@Override
	public synchronized void publish(LogRecord record) {
		if(!isLoggable(record)) {
			return;
		}
		
		String msg;
		try {
			msg = getFormatter().format(record);
		} catch (Exception ex) {
			ex.printStackTrace();
			return;
		}
		
		out.println(msg);
	}

}