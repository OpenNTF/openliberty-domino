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
package org.openntf.openliberty.domino.event;

import java.util.EventObject;

import org.openntf.openliberty.domino.server.ServerInstance;

/**
 * This event signals that a server configuration is deployed to the file system.
 * The server is not guaranteed to be running when this is called.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public class ServerDeployEvent extends EventObject {
	private static final long serialVersionUID = 1L;

	public ServerDeployEvent(ServerInstance<?> instance) {
		super(instance);
	}

	@Override
	public ServerInstance<?> getSource() {
		return (ServerInstance<?>)super.getSource();
	}
}
