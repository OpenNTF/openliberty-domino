package org.openntf.openliberty.domino.config;

import java.nio.file.Path;

/**
 * This extension interface specifies a service that can provide global configuration
 * options.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public interface RuntimeConfigurationProvider {
	Path getBaseDirectory();
}
