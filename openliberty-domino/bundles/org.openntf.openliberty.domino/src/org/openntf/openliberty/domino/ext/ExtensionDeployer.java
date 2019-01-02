/**
 * Copyright © 2018-2019 Jesse Gallagher
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
