package org.openntf.openliberty.domino.ext;

import java.io.InputStream;
import java.util.List;

/**
 * Defines a service that provides one or more OSGi bundles and a feature manifest
 * to be deployed alongside the servers.
 * 
 * <p>These services should be registered as an IBM Commons extension using the
 * <code>org.openntf.openliberty.domino.ext.ExtensionDeployer</code> extension point.</p>
 * 
 * @author Jesse Gallagher
 * @since 1.18004
 */
public interface ExtensionDeployer {
	public static String SERVICE_ID = ExtensionDeployer.class.getName();
	
	List<InputStream> getBundleData();
	List<String> getBundleFileNames();
	String getSubsystemContent();
	String getFeatureId();
	String getSubsystemVersion();
}
