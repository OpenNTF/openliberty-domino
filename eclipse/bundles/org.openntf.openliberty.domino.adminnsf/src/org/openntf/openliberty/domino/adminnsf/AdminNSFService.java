/**
 * Copyright © 2018-2019 Jesse Gallagher
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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openntf.openliberty.domino.adminnsf.config.AdminNSFProperties;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.runtime.OpenLibertyRuntime;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;

import com.ibm.commons.util.StringUtil;

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

import static com.ibm.commons.util.StringUtil.format;

/**
 * This task searches the active admin NSF for server documents and deploys them as needed.
 * 
 * @author Jesse Gallagher
 * @since 1.18004.0
 */
public class AdminNSFService implements Runnable {
	private static final Logger log = OpenLibertyLog.LIBERTY_LOG;
	
	public static final String VIEW_SERVERS = "Servers";
	public static final String ITEM_SERVERNAME = "Name";
	public static final String ITEM_SERVERENV = "ServerEnv";
	public static final String ITEM_SERVERXML = "ServerXML";
	public static final String ITEM_APPNAME = "AppName";
	public static final String ITEM_WAR = "WarFile";
	
	private static long lastRun = -1;
	
	private static final Path TEMP_DIR = Paths.get(OpenLibertyUtil.getTempDirectory());

	@Override
	public void run() {
		try {
			Session session = NotesFactory.createSession();
			try {
				String adminNsfPath = AdminNSFProperties.instance.getNsfPath();
				String server, filePath;
				int bangIndex = adminNsfPath.indexOf("!!");
				if(bangIndex > -1) {
					server = adminNsfPath.substring(0, bangIndex);
					filePath = adminNsfPath.substring(bangIndex+2);
				} else {
					server = "";
					filePath = adminNsfPath;
				}
				
				Database adminNsf = session.getDatabase(server, filePath);
				if(!needsUpdate(adminNsf)) {
					// Then we can end now
					if(log.isLoggable(Level.FINER)) {
						log.finer(format("{0}: Admin NSF is unchanged", getClass().getSimpleName()));
					}
					lastRun = System.currentTimeMillis();
					return;
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
								
								OpenLibertyRuntime.instance.createServer(serverName, serverXml, serverEnv);
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
									if(needsUpdate(dropinDoc)) {
										String appName = dropinDoc.getItemValueString(ITEM_APPNAME);
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
														Path dest = TEMP_DIR.resolve(eo.getSource() + System.currentTimeMillis());
														eo.extractFile(dest.toAbsolutePath().toString());
														
														OpenLibertyRuntime.instance.deployDropin(serverName, eo.getSource(), dest, true);
													}
												}
											}
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
				log.log(Level.SEVERE, "Encountered exception in " + getClass().getSimpleName(), t);
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

}
