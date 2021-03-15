package org.openntf.openliberty.domino.server;

import org.openntf.openliberty.domino.jvm.JVMIdentifier;

/**
 * Represents an abstract configuration for a server that uses a Java runtime.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public abstract class AbstractJavaServerConfiguration implements ServerConfiguration {

	private JVMIdentifier javaVersion;

	public JVMIdentifier getJavaVersion() {
		return javaVersion;
	}

	public void setJavaVersion(JVMIdentifier javaVersion) {
		this.javaVersion = javaVersion;
	}

}
