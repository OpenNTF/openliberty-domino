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
package org.openntf.openliberty.domino.adminnsf.config;

import java.util.ResourceBundle;

public enum AdminNSFProperties {
	instance;
	
	private final ResourceBundle bundle;
	
	private AdminNSFProperties() {
		this.bundle = ResourceBundle.getBundle("adminnsf"); //$NON-NLS-1$
	}
	
	public String getNsfPath() {
		return bundle.getString("defaultAdminNsf"); //$NON-NLS-1$
	}
	
	public String getDefaultVersion() {
		return bundle.getString("defaultVersion"); //$NON-NLS-1$
	}
	
	public String getDefaultArtifact() {
		return bundle.getString("defaultArtifact"); //$NON-NLS-1$
	}
	
	public String getDefaultMavenRepo() {
		return bundle.getString("defaultMavenRepo"); //$NON-NLS-1$
	}
}
