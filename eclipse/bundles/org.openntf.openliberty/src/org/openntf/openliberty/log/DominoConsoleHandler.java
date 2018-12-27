package org.openntf.openliberty.log;

import java.io.PrintStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

public class DominoConsoleHandler extends StreamHandler {
	
	private PrintStream out;

	public DominoConsoleHandler(String prefix) {
		super();
		
		out = new DominoLogPrintStream(System.err, prefix);
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