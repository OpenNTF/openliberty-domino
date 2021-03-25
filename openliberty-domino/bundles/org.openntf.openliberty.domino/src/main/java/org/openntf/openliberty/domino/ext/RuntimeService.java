/*
 * Copyright © 2018-2021 Jesse Gallagher
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

import java.util.EventObject;

import org.openntf.openliberty.domino.event.EventRecipient;

/**
 * Defines a service that will be loaded and run asynchronously after the core
 * runtime has been deployed.
 * 
 * <p>These services should be registered as {@code ServiceLoader} service using the
 * <code>org.openntf.openliberty.domino.ext.RuntimeService</code> name.</p>
 * 
 * <p>These objects are also implicitly added to the runtime's message-recipient queue
 * to receive {@link EventObject}s for runtime events.</p>
 * 
 * @author Jesse Gallagher
 * @since 1.18004
 */
public interface RuntimeService extends Runnable, EventRecipient {
	public static final String SERVICE_ID = RuntimeService.class.getName();
}
