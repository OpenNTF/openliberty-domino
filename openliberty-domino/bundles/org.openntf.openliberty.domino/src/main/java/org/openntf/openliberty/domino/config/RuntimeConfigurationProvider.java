package org.openntf.openliberty.domino.config;

import java.nio.file.Path;

import org.openntf.openliberty.domino.jvm.JavaRuntimeProvider;

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
	 * Returns the configured Java version, generally in the form of
	 * {@code "1.8"} or {@code "11"}.
	 * 
	 * @return the desired Java version
	 */
	String getJavaVersion();
	
	/**
	 * Returns the type of runtime, which will be interpreted by {@link JavaRuntimeProvider}
	 * instances.
	 * 
	 * @return the type of runtime
	 */
	String getJavaType();
}
