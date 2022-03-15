/*
 * Copyright © 2018-2022 Jesse Gallagher
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
package org.openntf.openliberty.domino.runtime;

import java.io.InputStream;

/**
 * This service interface represents an object capable of deploying new and updated
 * apps to their designated servers.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public interface AppDeploymentProvider {
	/**
	 * 
	 * @param serverName the name of the existing server; cannot be empty
	 * @param appName the name of the app to deploy; cannot be empty
	 * @param contextPath the context path for the app; when empty, this defaults to deriving from {@code appName}
	 * 		or keeping an existing path
	 * @param fileName a file name hint for the attachment; when empty, this defaults to deriving from {@code fileName}
	 * @param includeInReverseProxy whether to include the app in the reverse proxy; when {@code null}, this defaults to
	 * 		{@code false} or keeping an existing setting 
	 * @param appData the app file data; cannot be {@code null}
	 */
	void deployApp(String serverName, String appName, String contextPath, String fileName, Boolean includeInReverseProxy, InputStream appData);
}
