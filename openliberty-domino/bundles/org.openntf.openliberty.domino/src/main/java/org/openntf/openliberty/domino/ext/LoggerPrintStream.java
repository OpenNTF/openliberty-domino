package org.openntf.openliberty.domino.ext;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Extension point class for different environments to provide their own
 * {@link PrintStream} implementations to display well on the Domino
 * console.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public abstract class LoggerPrintStream extends PrintStream {
	public LoggerPrintStream(OutputStream out) {
		super(out);
	}
}
