/*
 * Copyright © 2018-2020 Jesse Gallagher
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

import java.nio.file.Path;

/**
 * Defines a service that will be loaded and run asynchronously after the core
 * runtime has been deployed.
 * 
 * <p>These services should be registered as {@code ServiceLoader} service using the
 * <code>org.openntf.openliberty.domino.ext.RuntimeService</code> name.</p>
 * 
 * @author Jesse Gallagher
 * @since 1.18004
 */
public interface RuntimeService extends Runnable {
	public static final String SERVICE_ID = RuntimeService.class.getName();
	
	/**
	 * This method is called asynchronously by the runtime whenever a new server is started.
	 * 
	 * @param wlp the path to the context Open Liberty runtime
	 * @param serverName the name of the server that has been started
	 * @since 2.0.0
	 */
	default void notifyServerStart(Path wlp, String serverName) {}
	
	/**
	 * This method is called asynchronously by the runtime whenever a running server is stopped.
	 * 
	 * @param wlp the path to the context Open Liberty runtime
	 * @param serverName the name of the server that has been stopped
	 * @since 2.0.0
	 */
	default void notifyServerStop(Path wlp, String serverName) {}
	
	/**
	 * This method is called asynchronously by the runtime whenever a server configuration is
	 * deployed to the file system. The server is not guaranteed to be running when this is
	 * called.
	 * 
	 * @param wlp the path to the context Open Liberty runtime
	 * @param serverName the name of the server that has been deployed
	 * @since 2.0.0
	 */
	default void notifyServerDeploy(Path wlp, String serverName) {}
}
