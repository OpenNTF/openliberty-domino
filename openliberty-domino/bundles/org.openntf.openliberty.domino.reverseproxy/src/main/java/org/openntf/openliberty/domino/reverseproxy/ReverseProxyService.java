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
package org.openntf.openliberty.domino.reverseproxy;

/**
 * Represents a reverse proxy service able to handle proxying to running application servers.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public interface ReverseProxyService {
	/**
	 * @return an implementation-specific type identifier for the proxy
	 */
	String getProxyType();
}
