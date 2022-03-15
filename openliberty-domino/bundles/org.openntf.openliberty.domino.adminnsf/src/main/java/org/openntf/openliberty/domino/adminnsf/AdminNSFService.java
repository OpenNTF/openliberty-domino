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

import static java.text.MessageFormat.format;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;

import org.openntf.openliberty.domino.adminnsf.util.AdminNSFUtil;
import org.openntf.openliberty.domino.jvm.JVMIdentifier;
import org.openntf.openliberty.domino.jvm.RunningJVMJavaRuntimeProvider;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyConfig;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyConfigProvider;
import org.openntf.openliberty.domino.reverseproxy.event.ReverseProxyConfigChangedEvent;
import org.openntf.openliberty.domino.runtime.OpenLibertyRuntime;
import org.openntf.openliberty.domino.server.wlp.LibertyExtensionDeployer;
import org.openntf.openliberty.domino.server.wlp.LibertyServerConfiguration;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;
import org.openntf.openliberty.domino.util.xml.XMLDocument;
import org.openntf.openliberty.domino.util.xml.XMLNode;
import org.openntf.openliberty.domino.util.xml.XMLNodeList;
import org.xml.sax.SAXException;

import lotus.domino.Database;
import lotus.domino.DateTime;
import lotus.domino.Document;
import lotus.domino.EmbeddedObject;
import lotus.domino.Item;
import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.RichTextItem;
import lotus.domino.Session;
import lotus.domino.View;
import lotus.domino.ViewEntry;
import lotus.domino.ViewNavigator;

/**
 * This task searches the active admin NSF for server documents and deploys them as needed.
 * 
 * @author Jesse Gallagher
 * @since 1.18004.0
 */
public enum AdminNSFService implements Runnable {
	instance;
	
	private static final Logger log = OpenLibertyLog.instance.log;
	
	public static final String VIEW_SERVERS = "Servers"; //$NON-NLS-1$
	public static final String VIEW_CONFIGURATION = "ConfigurationLookup"; //$NON-NLS-1$
	public static final String VIEW_SERVERSMODIFIED = "ServersModified"; //$NON-NLS-1$
	
	public static final String ITEM_SERVERNAME = "Name"; //$NON-NLS-1$
	public static final String ITEM_SERVERENV = "ServerEnv"; //$NON-NLS-1$
	public static final String ITEM_SERVERXML = "ServerXML"; //$NON-NLS-1$
	public static final String ITEM_DEPLOYMENTZIPS = "DeploymentZIPs"; //$NON-NLS-1$
	public static final String ITEM_APPNAME = "AppName"; //$NON-NLS-1$
	public static final String ITEM_WAR = "WarFile"; //$NON-NLS-1$
	/** @since 2.1.0 */
	public static final String ITEM_CONTEXTPATH = "ContextRoot"; //$NON-NLS-1$
	/** @since 2.0.0 */
	public static final String ITEM_JVMOPTIONS = "JvmOptions"; //$NON-NLS-1$
	/** @since 2.0.0 */
	public static final String ITEM_BOOTSTRAPPROPS = "BootstrapProperties"; //$NON-NLS-1$
	/** @since 2.0.0 */
	public static final String ITEM_DOMINOSERVERS = "DominoServers"; //$NON-NLS-1$
	/** @since 2.0.0 */
	public static final String ITEM_INTEGRATIONFEATURES = "IntegrationFeatures"; //$NON-NLS-1$
	
	/** @since 3.0.0 */
	public static final String ITEM_JAVAVERSION = "JavaVersion"; //$NON-NLS-1$
	/** @since 3.0.0 */
	public static final String ITEM_JAVATYPE = "JavaJVM"; //$NON-NLS-1$
	/** @since 3.0.0 */
	public static final String ITEM_LIBERTYVERSION = "LibertyVersion"; //$NON-NLS-1$
	/** @since 3.0.0 */
	public static final String ITEM_LIBERTYARTIFACT = "LibertyArtifact"; //$NON-NLS-1$
	/** @since 3.0.0 */
	public static final String ITEM_LIBERTYMAVENREPO = "LibertyMavenRepo"; //$NON-NLS-1$
	
	private long lastRun = -1;
	/**
	 * Contains the modification time of the main configuration document from the last run
	 */
	private long lastRunConfigMod;
	/**
	 * Contains the latest modification time of server and app config documents from the last run
	 */
	private long lastRunServerConfigMod;
	/**
	 * Contains the count of server and app config documents from the last run
	 */
	private int lastRunServerConfigCount;

	@Override
	public void run() {
		try {
			Session session = NotesFactory.createSession();
			try {
				Database adminNsf = AdminNSFUtil.getAdminDatabase(session);
				if(!needsUpdate(adminNsf)) {
					// Then we can end now
					if(log.isLoggable(Level.FINER)) {
						log.finer(format(Messages.getString("AdminNSFService.adminNSFUnchanged"), getClass().getSimpleName())); //$NON-NLS-1$
					}
					return;
				}
				if(log.isLoggable(Level.FINE)) {
					log.fine(format(Messages.getString("AdminNSFService.adminNSFChanged"), getClass().getSimpleName())); //$NON-NLS-1$
				}
				
				Collection<String> namesList = AdminNSFUtil.getCurrentServerNamesList();
				
				long configurationModTime = getConfigurationModTime(adminNsf);
				boolean configChanged = configurationModTime != this.lastRunConfigMod;
				this.lastRunConfigMod = configurationModTime;
				
				// Check the last-mod time for server/app docs and their count
				long serverConfigModTime;
				int serverConfigCount;
				{
					View serversModView = adminNsf.getView(VIEW_SERVERSMODIFIED);
					try {
						serversModView.setAutoUpdate(false);
						
						ViewNavigator nav = serversModView.createViewNav();
						serverConfigCount = nav.getCount();
						ViewEntry latestEntry = nav.getFirst();
						if(latestEntry != null) {
							Vector<?> columnValues = latestEntry.getColumnValues();
							try {
								DateTime mod = (DateTime)columnValues.get(0);
								serverConfigModTime = mod.toJavaDate().getTime();
							} finally {
								latestEntry.recycle(columnValues);
								latestEntry.recycle();
							}
						} else {
							serverConfigModTime = 0;
						}
					} finally {
						serversModView.recycle();
					}
				}
				boolean serverConfigChanged = serverConfigModTime != this.lastRunServerConfigMod || serverConfigCount != this.lastRunServerConfigCount;
				this.lastRunConfigMod = serverConfigModTime;
				this.lastRunServerConfigCount = serverConfigCount;
				
				
				if(serverConfigChanged) {
					// Update the servers themselves
					if(log.isLoggable(Level.INFO)) {
						log.info("Server/app configuration changed; refreshing");
					}
					
					View servers = adminNsf.getView(VIEW_SERVERS);
					servers.setAutoUpdate(false);
					servers.refresh();
					
					ViewNavigator nav = servers.createViewNav();
					ViewEntry entry = nav.getFirst();
					while(entry != null) {
						processServerDocEntry(entry, namesList);
						
						ViewEntry tempEntry = entry;
						entry = nav.getNextSibling(entry);
						tempEntry.recycle();
					}
				}
				
				if(configChanged || serverConfigChanged) {
					if(log.isLoggable(Level.INFO)) {
						log.info("Configuration changed; refreshing reverse proxy");
					}
					// Update the reverse proxy config
					ReverseProxyConfigProvider configProvider = OpenLibertyUtil.findRequiredExtension(ReverseProxyConfigProvider.class);
					ReverseProxyConfig reverseProxyConfig = configProvider.createConfiguration();
					OpenLibertyRuntime.instance.broadcastMessage(new ReverseProxyConfigChangedEvent(reverseProxyConfig));
				}
				
			} finally {
				session.recycle();
			}
		} catch(Throwable t) {
			if(log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, format(Messages.getString("AdminNSFService.encounteredExceptionIn"), getClass().getSimpleName()), t); //$NON-NLS-1$
				t.printStackTrace();
			}
		} finally {
			lastRun = System.currentTimeMillis();
		}
	}
	
	/**
	 * Resets the service's internal state to its pre-run configuration.
	 * 
	 * @since 3.0.0
	 */
	public void reset() {
		this.lastRun = -1;
		this.lastRunConfigMod = 0;
		this.lastRunServerConfigCount = 0;
		this.lastRunServerConfigMod = 0;
	}
	
	private long getConfigurationModTime(Database adminNsf) throws NotesException {
		View configuration = adminNsf.getView(VIEW_CONFIGURATION);
		try {
			configuration.setAutoUpdate(false);
			configuration.refresh();
			ViewEntry configEntry = configuration.getEntryByKey(adminNsf.getParent().getUserName(), true);
			if(configEntry == null) {
				configuration.getEntryByKey("", true); //$NON-NLS-1$
			}
			if(configEntry == null) {
				return 0;
			}
			Vector<?> columnValues = configEntry.getColumnValues();
			try {
				DateTime mod = (DateTime)columnValues.get(1);
				return mod.toJavaDate().getTime();
			} finally {
				configEntry.recycle(columnValues);
				configEntry.recycle();
			}
		} finally {
			if(configuration != null) {
				configuration.recycle();
			}
		}
	}
	
	private void processServerDocEntry(ViewEntry entry, Collection<String> namesList) throws NotesException, IOException, SAXException, ParserConfigurationException {
		Document serverDoc = entry.getDocument();
		try {
			
			
			@SuppressWarnings("unchecked")
			Collection<String> serverNames = serverDoc.getItemValue(ITEM_DOMINOSERVERS);
			boolean shouldRun = AdminNSFUtil.isNamesListMatch(namesList, serverNames);
			if(!shouldRun) {
				return;
			}
			
			String serverName = serverDoc.getItemValueString(ITEM_SERVERNAME);
			if(StringUtil.isNotEmpty(serverName)) {
				XMLDocument serverXml = null;
				
				if(needsUpdate(serverDoc)) {
					if(log.isLoggable(Level.INFO)) {
						log.info(format(Messages.getString("AdminNSFService.deployingDefinedServer"), getClass().getSimpleName(), serverName)); //$NON-NLS-1$
					}

					LibertyServerConfiguration config = new LibertyServerConfiguration();
					
					String javaVersion = serverDoc.getItemValueString(ITEM_JAVAVERSION);
					String javaType = serverDoc.getItemValueString(ITEM_JAVATYPE);
					if(StringUtil.isEmpty(javaType)) {
						javaType = RunningJVMJavaRuntimeProvider.TYPE_RUNNINGJVM;
					}
					config.setJavaVersion(new JVMIdentifier(javaVersion, javaType));
					
					config.setServerXml(generateServerXml(serverDoc));
					config.setServerEnv(serverDoc.getItemValueString(ITEM_SERVERENV));
					config.setJvmOptions(serverDoc.getItemValueString(ITEM_JVMOPTIONS));
					config.setBootstrapProperties(serverDoc.getItemValueString(ITEM_BOOTSTRAPPROPS));
					if(serverDoc.hasItem(ITEM_DEPLOYMENTZIPS)) {
						RichTextItem deploymentItem = (RichTextItem)serverDoc.getFirstItem(ITEM_DEPLOYMENTZIPS);
						@SuppressWarnings("unchecked")
						List<EmbeddedObject> objects = deploymentItem.getEmbeddedObjects();
						for(EmbeddedObject eo : objects) {
							if(eo.getType() == EmbeddedObject.EMBED_ATTACHMENT) {
								Path zip = Files.createTempFile(OpenLibertyUtil.getTempDirectory(), "nsfdeployment", ".zip"); //$NON-NLS-1$ //$NON-NLS-2$
								Files.deleteIfExists(zip);
								eo.extractFile(zip.toString());
								config.addAdditionalZip(zip);
							}
						}
					}
					
					config.setLibertyVersion(serverDoc.getItemValueString(ITEM_LIBERTYVERSION));
					config.setLibertyArtifact(serverDoc.getItemValueString(ITEM_LIBERTYARTIFACT));
					config.setLibertyMavenRepo(serverDoc.getItemValueString(ITEM_LIBERTYMAVENREPO));
					
					
					OpenLibertyRuntime.instance.registerServer(serverName, config);
					OpenLibertyRuntime.instance.createServer(serverName);
					OpenLibertyRuntime.instance.startServer(serverName);
				} else {
					if(log.isLoggable(Level.FINER)) {
						log.finer(format(Messages.getString("AdminNSFService.skippingUnchangedServer"), getClass().getSimpleName(), serverName)); //$NON-NLS-1$
					}
				}
				
				// Look for dropin apps to deploy
				ViewNavigator nav = (ViewNavigator)entry.getParent();
				ViewEntry dropinEntry = nav.getChild(entry);
				while(dropinEntry != null) {
					Document dropinDoc = dropinEntry.getDocument();
					try {
						String appName = dropinDoc.getItemValueString(ITEM_APPNAME);
						String contextPath = dropinDoc.getItemValueString(ITEM_CONTEXTPATH);
						if(StringUtil.isEmpty(contextPath)) {
							contextPath = "/" + appName; //$NON-NLS-1$
						}
						
						// Generate a name based on the modification time, to avoid Windows file-locking trouble
						// Shave off the last digit, to avoid trouble with Domino TIMEDATE limitations
						long docMod = dropinDoc.getLastModified().toJavaDate().getTime();
						docMod = docMod / 10 * 10;
						Path appsRoot = OpenLibertyUtil.getTempDirectory().resolve("apps"); //$NON-NLS-1$
						Path appDir = appsRoot.resolve(dropinDoc.getNoteID() + '-' + docMod);
						Files.createDirectories(appDir);
						Path warPath = appDir.resolve("app.war"); //$NON-NLS-1$
						
						// See if we need to deploy the file
						if(!Files.exists(warPath)) {
							if(log.isLoggable(Level.INFO)) {
								log.info(format(Messages.getString("AdminNSFService.deployingDefinedApp"), getClass().getSimpleName(), appName, contextPath)); //$NON-NLS-1$
							}
							
							if(dropinDoc.hasItem(ITEM_WAR)) {
								Item warItem = dropinDoc.getFirstItem(ITEM_WAR);
								if(warItem.getType() == Item.RICHTEXT) {
									RichTextItem rtItem = (RichTextItem)warItem;
									@SuppressWarnings("unchecked")
									Vector<EmbeddedObject> objects = rtItem.getEmbeddedObjects();
									try {
										for(EmbeddedObject eo : objects) {
											// Deploy all attached files
											if(eo.getType() == EmbeddedObject.EMBED_ATTACHMENT) {
												eo.extractFile(warPath.toString());
												break;
											}
										}
									} finally {
										rtItem.recycle(objects);
									}
								}
							}
						}
						
						// Add a webApplication entry
						if(serverXml == null) {
							serverXml = generateServerXml(serverDoc);
						}
						XMLNode webApplication = serverXml.selectSingleNode("/server").addChildElement("webApplication"); //$NON-NLS-1$ //$NON-NLS-2$
						webApplication.setAttribute("contextRoot", contextPath); //$NON-NLS-1$
						webApplication.setAttribute("id", "app-" + dropinDoc.getNoteID()); //$NON-NLS-1$ //$NON-NLS-2$
						webApplication.setAttribute("location", warPath.toString()); //$NON-NLS-1$
						webApplication.setAttribute("name", appName); //$NON-NLS-1$
						
					} finally {
						dropinDoc.recycle();
					}
					
					ViewEntry tempDropin = dropinEntry;
					dropinEntry = nav.getNextSibling(dropinEntry);
					tempDropin.recycle();
				}
				
				if(serverXml != null) {
					// TODO create a full configuration
					LibertyServerConfiguration newConfig = new LibertyServerConfiguration();
					newConfig.setServerXml(serverXml);
					OpenLibertyRuntime.instance.updateConfiguration(serverName, newConfig);
				}
			}
		} finally {
			serverDoc.recycle();
		}
	}
	
	// *******************************************************************************
	// * Internal implementation methods
	// *******************************************************************************
	
	private XMLDocument generateServerXml(Document serverDoc) throws NotesException, SAXException, IOException, ParserConfigurationException {
		String serverXmlString = serverDoc.getItemValueString(ITEM_SERVERXML);
		XMLDocument serverXml = new XMLDocument(serverXmlString);
		
		@SuppressWarnings("unchecked")
		List<String> integrationFeatures = serverDoc.getItemValue(ITEM_INTEGRATIONFEATURES);
		integrationFeatures.remove(null);
		integrationFeatures.remove(""); //$NON-NLS-1$
		if(!integrationFeatures.isEmpty()) {
			List<LibertyExtensionDeployer> extensions = OpenLibertyUtil.findExtensions(LibertyExtensionDeployer.class).collect(Collectors.toList());
			XMLNode featuresElement = serverXml.selectSingleNode("/server/featureManager"); //$NON-NLS-1$
			if(featuresElement == null) {
				featuresElement = serverXml.getDocumentElement().addChildElement("featureManager"); //$NON-NLS-1$
			}
			
			for(String featureName : integrationFeatures) {
				// Map the feature to the right version from the current runtime
				String version = extensions.stream()
					.filter(ext -> featureName.equals(ext.getShortName()))
					.map(LibertyExtensionDeployer::getFeatureVersion)
					.findFirst()
					.orElse(""); //$NON-NLS-1$
				if(!version.isEmpty()) {
					String feature = format("usr:{0}-{1}", featureName, version); //$NON-NLS-1$
					XMLNodeList result = featuresElement.selectNodes(format("./feature[starts-with(text(), ''usr:{0}-'')]", featureName)); //$NON-NLS-1$
					if(result.isEmpty()) {
						featuresElement.addChildElement("feature").setTextContent(feature); //$NON-NLS-1$
					}
				}
			}
		}
		
		return serverXml;
	}
	
	private boolean needsUpdate(Database adminNsf) throws NotesException {
		if(lastRun > -1) {
			// Check if we need to do anything
			DateTime mod = adminNsf.getLastModified();
			try {
				long modTime = mod.toJavaDate().getTime();
				if(modTime < lastRun) {
					// Then we can end now
					lastRun = System.currentTimeMillis();
					return false;
				}
			} finally {
				mod.recycle();
			}
		}
		return true;
	}
	
	private boolean needsUpdate(Document doc) throws NotesException {
		if(lastRun > -1) {
			// Check if we need to do anything
			DateTime mod = doc.getLastModified();
			try {
				long modTime = mod.toJavaDate().getTime();
				if(modTime < lastRun) {
					return false;
				}
			} finally {
				mod.recycle();
			}
		}
		return true;
	}

}
