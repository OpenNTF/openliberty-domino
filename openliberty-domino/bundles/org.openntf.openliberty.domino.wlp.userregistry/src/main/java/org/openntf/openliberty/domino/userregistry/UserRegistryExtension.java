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
package org.openntf.openliberty.domino.userregistry;

import java.io.InputStream;

import org.openntf.openliberty.domino.server.wlp.LibertyExtensionDeployer;

public class UserRegistryExtension implements LibertyExtensionDeployer {
	@Override
	public InputStream getEsaData() {
		return getClass().getResourceAsStream("/ext/dominoUserRegistry-1.0.esa"); //$NON-NLS-1$
	}
	
	@Override
	public String getShortName() {
		return "dominoUserRegistry"; //$NON-NLS-1$
	}
	
	@Override
	public String getFeatureVersion() {
		return "1.0"; //$NON-NLS-1$
	}
}
