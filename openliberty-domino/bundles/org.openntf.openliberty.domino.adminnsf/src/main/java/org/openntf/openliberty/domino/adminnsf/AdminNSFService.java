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
package org.openntf.openliberty.domino.adminnsf;

import static java.text.MessageFormat.format;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;

import org.openntf.openliberty.domino.adminnsf.util.AdminNSFUtil;
import org.openntf.openliberty.domino.ext.ExtensionDeployer;
import org.openntf.openliberty.domino.jvm.JVMIdentifier;
import org.openntf.openliberty.domino.jvm.RunningJVMJavaRuntimeProvider;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.runtime.LibertyServerConfiguration;
import org.openntf.openliberty.domino.runtime.OpenLibertyRuntime;
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
import lotus.domino.Name;
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
public class AdminNSFService implements Runnable {
	private static final Logger log = OpenLibertyLog.instance.log;
	
	public static final String VIEW_SERVERS = "Servers"; //$NON-NLS-1$
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
	public static final String ITEM_JAVAVERSION = "JavaVersion";
	/** @since 3.0.0 */
	public static final String ITEM_JAVATYPE = "JavaJVM";
	
	private long lastRun = -1;
	
	private static final Path TEMP_DIR = OpenLibertyUtil.getTempDirectory();
	private static final Path APP_DIR = TEMP_DIR.resolve("apps");

	@SuppressWarnings("unchecked")
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
				
				Collection<String> namesList = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
				
				// Do this reflectively from the root classloader to avoid trouble with an intermediary blocking lotus.notes.addins, apparently
				AccessController.doPrivileged((PrivilegedExceptionAction<Void>)() -> {
					Class<?> dominoServerClass = ClassLoader.getSystemClassLoader().loadClass("lotus.notes.addins.DominoServer"); //$NON-NLS-1$
					Method getNamesList = dominoServerClass.getMethod("getNamesList", String.class); //$NON-NLS-1$
					Object dominoServer = dominoServerClass.getConstructor(new Class<?>[0]).newInstance();
					Collection<String> names = (Collection<String>)getNamesList.invoke(dominoServer, session.getUserName());
					namesList.addAll(names);
					return null;
				});
				// The abbreviated name _shouldn't_ make it into the names field, but just in case
				Name nameObj = session.getUserNameObject();
				try {
					namesList.add(nameObj.getAbbreviated());
				} finally {
					nameObj.recycle();
				}
				
				View servers = adminNsf.getView(VIEW_SERVERS);
				servers.setAutoUpdate(false);
				
				ViewNavigator nav = servers.createViewNav();
				ViewEntry entry = nav.getFirst();
				while(entry != null) {
					processServerDocEntry(entry, namesList);
					
					ViewEntry tempEntry = entry;
					entry = nav.getNextSibling(entry);
					tempEntry.recycle();
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
	
	private void processServerDocEntry(ViewEntry entry, Collection<String> namesList) throws NotesException, IOException, SAXException, ParserConfigurationException {
		Document serverDoc = entry.getDocument();
		try {
			@SuppressWarnings("unchecked")
			Collection<String> serverNames = serverDoc.getItemValue(ITEM_DOMINOSERVERS);
			serverNames.remove(""); //$NON-NLS-1$
			serverNames.remove(null);
			boolean shouldRun = true;
			if(!serverNames.isEmpty()) {
				shouldRun = false;
				for(String serverName : serverNames) {
					if(namesList.contains(serverName)) {
						shouldRun = true;
						break;
					}
				}
			}
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
								Path zip = Files.createTempFile(TEMP_DIR, "nsfdeployment", ".zip"); //$NON-NLS-1$ //$NON-NLS-2$
								Files.deleteIfExists(zip);
								eo.extractFile(zip.toString());
								config.addAdditionalZip(zip);
							}
						}
					}
					
					
					OpenLibertyRuntime.instance.createServer(serverName, config);
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
							contextPath = appName;
						}
						
						// Generate a name based on the modification time, to avoid Windows file-locking trouble
						// Shave off the last digit, to avoid trouble with Domino TIMEDATE limitations
						long docMod = dropinDoc.getLastModified().toJavaDate().getTime();
						docMod = docMod / 10 * 10;
						Path appDir = APP_DIR.resolve(dropinDoc.getNoteID());
						if(!Files.exists(appDir)) {
							Files.createDirectories(appDir);
						}
						Path warPath = appDir.resolve(docMod + ".war");
						
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
									List<EmbeddedObject> objects = rtItem.getEmbeddedObjects();
									for(EmbeddedObject eo : objects) {
										// Deploy all attached files
										if(eo.getType() == EmbeddedObject.EMBED_ATTACHMENT) {
											eo.extractFile(warPath.toString());
											break;
										}
									}
								}
							}
						}
						
						// Add a webApplication entry
						
						if(serverXml == null) {
							serverXml = generateServerXml(serverDoc);
						}
						XMLNode webApplication = serverXml.selectSingleNode("/server").addChildElement("webApplication");
						webApplication.setAttribute("contextRoot", contextPath);
						webApplication.setAttribute("id", "app-" + dropinDoc.getNoteID());
						webApplication.setAttribute("location", warPath.toString());
						webApplication.setAttribute("name", appName);
						
					} finally {
						dropinDoc.recycle();
					}
					
					ViewEntry tempDropin = dropinEntry;
					dropinEntry = nav.getNextSibling(dropinEntry);
					tempDropin.recycle();
				}
				
				if(serverXml != null) {
					OpenLibertyRuntime.instance.deployServerXml(serverName, serverXml.toString());
				}
			}
		} finally {
			serverDoc.recycle();
		}
	}
	
	private XMLDocument generateServerXml(Document serverDoc) throws NotesException, SAXException, IOException, ParserConfigurationException {
		String serverXmlString = serverDoc.getItemValueString(ITEM_SERVERXML);
		XMLDocument serverXml = new XMLDocument(serverXmlString);
		
		@SuppressWarnings("unchecked")
		List<String> integrationFeatures = serverDoc.getItemValue(ITEM_INTEGRATIONFEATURES);
		integrationFeatures.remove(null);
		integrationFeatures.remove(""); //$NON-NLS-1$
		if(!integrationFeatures.isEmpty()) {
			List<ExtensionDeployer> extensions = OpenLibertyUtil.findExtensions(ExtensionDeployer.class).collect(Collectors.toList());
			XMLNode featuresElement = serverXml.selectSingleNode("/server/featureManager"); //$NON-NLS-1$
			if(featuresElement == null) {
				featuresElement = serverXml.getDocumentElement().addChildElement("featureManager"); //$NON-NLS-1$
			}
			
			for(String featureName : integrationFeatures) {
				// Map the feature to the right version from the current runtime
				String version = extensions.stream()
					.filter(ext -> featureName.equals(ext.getShortName()))
					.map(ExtensionDeployer::getFeatureVersion)
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
