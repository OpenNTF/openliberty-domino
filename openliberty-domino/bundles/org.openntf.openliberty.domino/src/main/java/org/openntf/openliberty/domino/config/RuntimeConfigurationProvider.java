package org.openntf.openliberty.domino.config;

import java.nio.file.Path;

import org.openntf.openliberty.domino.jvm.JVMIdentifier;

/**
 * This extension interface specifies a service that can provide global configuration
 * options.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public interface RuntimeConfigurationProvider {
	Path getBaseDirectory();
	
	String getOpenLibertyVersion();
	String getOpenLibertyArtifact();
	String getOpenLibertyMavenRepository();
	
	/**
	 * Returns the configured Java version.
	 * 
	 * @return the desired Java version
	 */
	JVMIdentifier getJavaVersion();
}
