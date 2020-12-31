/*
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
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openntf.openliberty.domino.ext.LoggerPrintStream;

public enum OpenLibertyLog {
	instance;
	
	public static Logger getLog() {
		return instance.log;
	}

	public final PrintStream out;
	public final Logger log;
	
	private OpenLibertyLog() {
		out = ServiceLoader.load(LoggerPrintStream.class, OpenLibertyLog.class.getClassLoader()).iterator().next();
		log = createLogger(OpenLibertyLog.class.getPackage().getName());
		log.setLevel(Level.INFO);
	}
	
	
	public Logger createLogger(String name) {
		Logger log = Logger.getLogger(name);
		DominoConsoleHandler consoleHandler = new DominoConsoleHandler(out);
		DominoConsoleFormatter consoleFormatter = new DominoConsoleFormatter(null, false);
		consoleHandler.setFormatter(consoleFormatter);
		consoleHandler.setLevel(Level.ALL);
		log.setUseParentHandlers(false);
		log.addHandler(consoleHandler);
		return log;
	}
}
