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
package org.openntf.openliberty.domino.adminnsf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openntf.openliberty.domino.adminnsf.util.AdminNSFUtil;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.runtime.OpenLibertyRuntime;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;

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

import static java.text.MessageFormat.format;

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
	/** @since 2.0.0 */
	public static final String ITEM_JVMOPTIONS = "JvmOptions"; //$NON-NLS-1$
	/** @since 2.0.0 */
	public static final String ITEM_BOOTSTRAPPROPS = "BootstrapProperties"; //$NON-NLS-1$
	
	private long lastRun = -1;
	
	private static final Path TEMP_DIR = Paths.get(OpenLibertyUtil.getTempDirectory());

	@Override
	public void run() {
		try {
			Session session = NotesFactory.createSession();
			try {
				Database adminNsf = AdminNSFUtil.getAdminDatabase(session);
				if(!needsUpdate(adminNsf)) {
					// Then we can end now
					if(log.isLoggable(Level.FINER)) {
						log.finer(format("{0}: Admin NSF is unchanged", getClass().getSimpleName()));
					}
					lastRun = System.currentTimeMillis();
					return;
				}
				if(log.isLoggable(Level.FINE)) {
					log.fine(format("{0}: Admin NSF has changed - looking for updates", getClass().getSimpleName()));
				}
				
				View servers = adminNsf.getView(VIEW_SERVERS);
				servers.setAutoUpdate(false);
				
				ViewNavigator nav = servers.createViewNav();
				ViewEntry entry = nav.getFirst();
				while(entry != null) {
					Document serverDoc = entry.getDocument();
					try {
						String serverName = serverDoc.getItemValueString(ITEM_SERVERNAME);
						if(StringUtil.isNotEmpty(serverName)) {
							if(needsUpdate(serverDoc)) {
								if(log.isLoggable(Level.INFO)) {
									log.info(format("{0}: Deploying NSF-defined server \"{1}\"", getClass().getSimpleName(), serverName));
								}
								String serverXml = serverDoc.getItemValueString(ITEM_SERVERXML);
								String serverEnv = serverDoc.getItemValueString(ITEM_SERVERENV);
								String jvmOptions = serverDoc.getItemValueString(ITEM_JVMOPTIONS);
								String bootstrapProperties = serverDoc.getItemValueString(ITEM_BOOTSTRAPPROPS);
								List<Path> additionalZips = new ArrayList<>();
								if(serverDoc.hasItem(ITEM_DEPLOYMENTZIPS)) {
									RichTextItem deploymentItem = (RichTextItem)serverDoc.getFirstItem(ITEM_DEPLOYMENTZIPS);
									@SuppressWarnings("unchecked")
									List<EmbeddedObject> objects = deploymentItem.getEmbeddedObjects();
									for(EmbeddedObject eo : objects) {
										if(eo.getType() == EmbeddedObject.EMBED_ATTACHMENT) {
											Path zip = Files.createTempFile(TEMP_DIR, "nsfdeployment", ".zip"); //$NON-NLS-1$ //$NON-NLS-2$
											Files.deleteIfExists(zip);
											eo.extractFile(zip.toString());
											additionalZips.add(zip);
										}
									}
								}
								
								OpenLibertyRuntime.instance.createServer(serverName, serverXml, serverEnv, jvmOptions, bootstrapProperties, additionalZips);
								OpenLibertyRuntime.instance.startServer(serverName);
							} else {
								if(log.isLoggable(Level.FINER)) {
									log.finer(format("{0}: Skipping unchanged server \"{1}\"", getClass().getSimpleName(), serverName));
								}
							}
							
							// Look for dropin apps to deploy
							ViewEntry dropinEntry = nav.getChild(entry);
							while(dropinEntry != null) {
								Document dropinDoc = dropinEntry.getDocument();
								try {
									String appName = dropinDoc.getItemValueString(ITEM_APPNAME);
									if(needsUpdate(dropinDoc)) {
										if(log.isLoggable(Level.INFO)) {
											log.info(format("{0}: Deploying NSF-defined app \"{1}\"", getClass().getSimpleName(), appName));
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
														Path warFile = TEMP_DIR.resolve(eo.getSource() + System.currentTimeMillis());
														eo.extractFile(warFile.toString());
														
														String warName = appName + ".war"; //$NON-NLS-1$
														OpenLibertyRuntime.instance.deployDropin(serverName, warName, warFile, true);
													}
												}
											}
										}
									} else {
										if(log.isLoggable(Level.FINER)) {
											log.finer(format("{0}: Skipping unchanged dropin app \"{1}\"", getClass().getSimpleName(), appName));
										}
									}
								} finally {
									dropinDoc.recycle();
								}
								
								ViewEntry tempDropin = dropinEntry;
								dropinEntry = nav.getNextSibling(dropinEntry);
								tempDropin.recycle();
							}
						}
					} finally {
						serverDoc.recycle();
					}
					
					ViewEntry tempEntry = entry;
					entry = nav.getNextSibling(entry);
					tempEntry.recycle();
				}
				
				lastRun = System.currentTimeMillis();
			} finally {
				session.recycle();
			}
		} catch(Throwable t) {
			if(log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, MessageFormat.format("Encountered exception in {0}", getClass().getSimpleName()), t);
				t.printStackTrace();
			}
		}
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
