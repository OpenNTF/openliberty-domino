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

import static java.text.MessageFormat.format;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openntf.openliberty.domino.config.RuntimeConfigurationProvider;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.runtime.Messages;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;

/**
 * Abstract mplementation of {@link JavaRuntimeProvider} that downloads and references
 * a build from Adoptium's marketplace.
 * 
 * @author Jesse Gallagher
 * @since 4.0.0
 */
public abstract class AbstractAdoptiumJavaRuntimeProvider extends AbstractDownloadingJavaRuntimeProvider {
	private static final Logger log = OpenLibertyLog.instance.log;
	
	// 0 = Java version, 1 = OS, 2 = arch, 3 = jvm impl (hotspot), 4 = vendor
	public static final String API_LATEST = "https://api.adoptium.net/v3/binary/latest/{0}/ga/{1}/{2}/jdk/{3}/normal/{4}?project=jdk"; //$NON-NLS-1$
	
	@Override
	public Path getJavaHome(JVMIdentifier identifier) {
		String version = identifier.getVersion();
		String javaJvm = identifier.getType();
		if(StringUtil.isEmpty(javaJvm)) {
			javaJvm = "HotSpot"; //$NON-NLS-1$
		}
		if(log.isLoggable(Level.FINE)) {
			log.fine(format(Messages.getString("JavaRuntimeProvider.configuredJavaRuntimeWithJVM"), version , javaJvm)); //$NON-NLS-1$
		}	
		
		// Check for an existing JVM
		RuntimeConfigurationProvider config = OpenLibertyUtil.findRequiredExtension(RuntimeConfigurationProvider.class);
		Path execDir = config.getBaseDirectory();
		
		Path jvmDir = execDir.resolve("jvm").resolve(version + "-" + javaJvm); //$NON-NLS-1$ //$NON-NLS-2$
		String execName = "java" + (OpenLibertyUtil.IS_WINDOWS ? ".exe" : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		if(Files.exists(jvmDir.resolve("bin").resolve(execName))) { //$NON-NLS-1$
			if(log.isLoggable(Level.FINE)) {
				log.info(format(Messages.getString("JavaRuntimeProvider.usingDownloadedRuntime"), jvmDir)); //$NON-NLS-1$
			}
			// Assume the whole JVM exists
			return jvmDir;
		}
		
		// Otherwise, look for a release on GitHub
		if("1.8".equals(version)) { //$NON-NLS-1$
			version = "8"; //$NON-NLS-1$
		}
		String latestUrl = format(API_LATEST, version, getOsName(), getOsArch(), getJvmType(), getVendor());
		download(latestUrl, jvmDir);
		
		markExecutables(jvmDir);
		
		return jvmDir;
	}
	
	protected abstract String getVendor();
	protected abstract String getJvmType();

	private static String getOsArch() {
		String arch = System.getProperty("os.arch"); //$NON-NLS-1$
		if("x86_64".equals(arch) || "amd64".equals(arch)) { //$NON-NLS-1$ //$NON-NLS-2$
			return "x64"; //$NON-NLS-1$
		} else {
			return "x86"; //$NON-NLS-1$
		}
	}
}
