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
package org.openntf.openliberty.domino.jvm;

/**
 * Implementation of {@link JavaRuntimeProvider} that downloads and references
 * a build from Adoptium's Temurin distribution.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public class AdoptiumJavaRuntimeProvider extends AbstractAdoptiumJavaRuntimeProvider {
	// 0 = Java version, 1 = OS, 2 = arch, 3 = jvm impl (hotspot), 4 = vendor
	public static final String API_LATEST = "https://api.adoptium.net/v3/binary/latest/{0}/ga/{1}/{2}/jdk/{3}/normal/{4}?project=jdk"; //$NON-NLS-1$
	
	public static final String TYPE_HOTSPOT = "HotSpot"; //$NON-NLS-1$
	
	public static final String PROVIDER_NAME = "Adoptium Temurin"; //$NON-NLS-1$
	public static final String VENDOR = "eclipse"; //$NON-NLS-1$
	
	@Override
	public boolean canProvide(JVMIdentifier identifier) {
		return TYPE_HOTSPOT.equals(identifier.getType());
	}
	
	@Override
	protected String getVendor() {
		return VENDOR;
	}
	
	@Override
	protected String getJvmType() {
		return TYPE_HOTSPOT.toLowerCase();
	}
}
