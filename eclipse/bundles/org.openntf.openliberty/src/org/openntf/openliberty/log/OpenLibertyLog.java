package org.openntf.openliberty.log;

import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openntf.openliberty.Activator;

import com.ibm.commons.util.StringUtil;

public enum OpenLibertyLog {
	;
	
	private static final String PREFIX = "OpenLiberty"; //$NON-NLS-1$
	public final static Logger LIBERTY_LOG = createLogger(Activator.class.getPackage().getName(), "OpenLiberty"); //$NON-NLS-1$
	
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
