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

import java.nio.file.Path;
import java.nio.file.Paths;

import org.openntf.openliberty.domino.adminnsf.util.AdminNSFUtil;
import org.openntf.openliberty.domino.config.RuntimeConfigurationProvider;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyConfig;
import org.openntf.openliberty.domino.util.DominoThreadFactory;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.domino.View;

public class AdminNSFRuntimeConfigurationProvider implements RuntimeConfigurationProvider {
	
	public static final String ITEM_BASEDIRECTORY = "BaseDirectory"; //$NON-NLS-1$
	
	private Path baseDirectory;
	private String dominoHostName;
	private int dominoPort;
	private boolean dominoHttps;
	private boolean dominoConnectorHeaders;
	private String dominoVersion;
	private Path dominoProgramDirectory;

	@Override
	public Path getBaseDirectory() {
		if(this.baseDirectory == null) { loadData(); }
		return this.baseDirectory;
	}
	
	@Override
	public String getDominoHostName() {
		if(this.baseDirectory == null) { loadData(); }
		return this.dominoHostName;
	}
	
	@Override
	public int getDominoPort() {
		if(this.baseDirectory == null) { loadData(); }
		return this.dominoPort;
	}
	
	@Override
	public boolean isDominoHttps() {
		if(this.baseDirectory == null) { loadData(); }
		return this.dominoHttps;
	}
	
	@Override
	public boolean isUseDominoConnectorHeaders() {
		if(this.baseDirectory == null) { loadData(); }
		return this.dominoConnectorHeaders;
	}
	
	@Override
	public String getDominoVersion() {
		if(this.dominoVersion == null) { loadData(); }
		return this.dominoVersion;
	}
	
	@Override
	public Path getDominoProgramDirectory() {
		if(this.dominoProgramDirectory == null) { loadData(); }
		return this.dominoProgramDirectory;
	}

	private synchronized void loadData() {
		try {
			DominoThreadFactory.getExecutor().submit(() -> {
				Session session = NotesFactory.createSession();
				try {
					this.dominoVersion = StringUtil.toString(session.evaluate(" @Version ").get(0)); //$NON-NLS-1$
					this.dominoProgramDirectory = Paths.get(OpenLibertyUtil.getDominoProgramDirectory());
					
					// Read configuration from the Runtime configuration NSF
					Database adminNsf = AdminNSFUtil.getAdminDatabase(session);
					Document config = AdminNSFUtil.getConfigurationDocument(adminNsf);
					String execDirName = config.getItemValueString(ITEM_BASEDIRECTORY);
					Path execDir;
					if(StringUtil.isEmpty(execDirName)) {
						if(OpenLibertyUtil.IS_WINDOWS) {
							execDir = this.dominoProgramDirectory.resolve("wlp"); //$NON-NLS-1$
						} else {
							execDir = Paths.get(OpenLibertyUtil.getDominoDataDirectory()).resolve("wlp"); //$NON-NLS-1$
						}
					} else {
						execDir = Paths.get(execDirName);
					}
					this.baseDirectory = execDir;
					
					// Read Domino server config from names.nsf
					Database names = session.getDatabase("", "names.nsf"); //$NON-NLS-1$ //$NON-NLS-2$
					View servers = names.getView("$Servers"); //$NON-NLS-1$
					Document serverDoc = servers.getDocumentByKey(session.getUserName());
					
					boolean httpEnabled = "1".equals(serverDoc.getItemValueString("HTTP_NormalMode")); //$NON-NLS-1$ //$NON-NLS-2$
					boolean httpsEnabled = "1".equals(serverDoc.getItemValueString("HTTP_SSLMode")); //$NON-NLS-1$ //$NON-NLS-2$
					if(!httpEnabled && !httpsEnabled) {
						// Then HTTP is effectively off - end early
						this.dominoPort = ReverseProxyConfig.PORT_DISABLED;
						return null;
					}
					
					if(httpEnabled) {
						this.dominoPort = serverDoc.getItemValueInteger("HTTP_Port"); //$NON-NLS-1$
					} else {
						this.dominoPort = serverDoc.getItemValueInteger("HTTP_SSLPort"); //$NON-NLS-1$
						this.dominoHttps = true;
					}
					
					boolean bindToHostName = "1".equals(serverDoc.getItemValueString("HTTP_BindToHostName")); //$NON-NLS-1$ //$NON-NLS-2$
					if(bindToHostName) {
						String hostName = serverDoc.getItemValueString("HTTP_HostName"); //$NON-NLS-1$
						if(hostName != null && !hostName.isEmpty()) {
							this.dominoHostName = hostName;
						}
					}
					if(StringUtil.isEmpty(this.dominoHostName)) {
						this.dominoHostName = "localhost"; //$NON-NLS-1$
					}
					
					String connectorHeadersParam = session.getEnvironmentString("HTTPEnableConnectorHeaders", true); //$NON-NLS-1$
					this.dominoConnectorHeaders = "1".equals(connectorHeadersParam); //$NON-NLS-1$
				} finally {
					session.recycle();
				}
				
				return null;
			}).get();
		} catch(RuntimeException e) {
			throw e;
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}
}
