package org.openntf.openliberty.ext;

/**
 * Defines a service that will be loaded and run asynchronously after the core
 * runtime has been deployed.
 * 
 * <p>These services should be registered as an IBM Commons extension using the
 * <code>org.openntf.openliberty.ext.RuntimeService</code> extension point.</p>
 * 
 * @author Jesse Gallagher
 * @since 1.18004
 */
public interface RuntimeService extends Runnable {
	public static final String SERVICE_ID = RuntimeService.class.getName();
}
