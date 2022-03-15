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
package org.openntf.openliberty.domino.adminnsf;

import java.util.EventObject;
import java.util.concurrent.TimeUnit;

import org.openntf.openliberty.domino.ext.RuntimeService;
import org.openntf.openliberty.domino.util.DominoThreadFactory;

public class AdminNSFServiceProvider implements RuntimeService {

	@Override
	public void run() {
		DominoThreadFactory.getScheduler().scheduleWithFixedDelay(AdminNSFService.instance, 0, 30, TimeUnit.SECONDS);
	}
	
	@Override
	public void notifyMessage(EventObject event) {
		// NOP
	}
	
	@Override
	public void close() {
		AdminNSFService.instance.reset();
	}

}
