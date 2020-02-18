/**
 * Copyright © 2018-2020 Jesse Gallagher
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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openntf.openliberty.domino.Activator;
import org.openntf.openliberty.domino.config.RuntimeProperties;

import com.ibm.commons.util.StringUtil;

public enum OpenLibertyLog {
	;
	
	private static final String PREFIX = RuntimeProperties.instance.getPrefix();
	public final static Logger LIBERTY_LOG = createLogger(Activator.class.getPackage().getName(), PREFIX);
	
	static {
		LIBERTY_LOG.setLevel(Level.INFO);
	}
	
	public static final PrintStream out = new DominoLogPrintStream(System.err, PREFIX + ": "); //$NON-NLS-1$
	
	public static Logger createLogger(String name, String label) {
		Logger log = Logger.getLogger(name);
		String prefix = StringUtil.isEmpty(label) ? null : (label + ": "); //$NON-NLS-1$
		DominoConsoleHandler consoleHandler = new DominoConsoleHandler(prefix);
		DominoConsoleFormatter consoleFormatter = new DominoConsoleFormatter(null, false);
		consoleHandler.setFormatter(consoleFormatter);
		consoleHandler.setLevel(Level.ALL);
		log.setUseParentHandlers(false);
		log.addHandler(consoleHandler);
		return log;
	}
}
