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
import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;

import org.openntf.openliberty.domino.adminnsf.util.AdminNSFUtil;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyConfig;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyConfigProvider;
import org.openntf.openliberty.domino.reverseproxy.event.ReverseProxyConfigChangedEvent;
import org.openntf.openliberty.domino.runtime.OpenLibertyRuntime;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;
import org.xml.sax.SAXException;

import lotus.domino.Database;
import lotus.domino.DateTime;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
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
	/** @since 2.1.0 */
	public static final String ITEM_CONTEXTPATH = "ContextRoot"; //$NON-NLS-1$
	/** @since 2.0.0 */
	public static final String ITEM_DOMINOSERVERS = "DominoServers"; //$NON-NLS-1$
	
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
	/**
	 * @since 4.0.0
	 */
	private List<ServerDocumentHandler> serverDocumentHandlers;

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
				
				// TODO also see if this can check for deleted docs
				// Check the last-mod time for server docs and their count
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
		this.serverDocumentHandlers = null;
	}
	
	// *******************************************************************************
	// * Internal implementation methods
	// *******************************************************************************
	
	private List<ServerDocumentHandler> getHandlers() {
		if(this.serverDocumentHandlers == null) {
			this.serverDocumentHandlers = OpenLibertyUtil.findExtensions(ServerDocumentHandler.class).collect(Collectors.toList());
		}
		return this.serverDocumentHandlers;
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
				if(needsUpdate(serverDoc)) {
					for(ServerDocumentHandler handler : getHandlers()) {
						if(handler.canHandle(serverDoc)) {
							handler.handle(serverDoc);
							return;
						} else {
							if(log.isLoggable(Level.WARNING)) {
								log.warning(MessageFormat.format("No handler found for server document UNID {0}", serverDoc.getUniversalID()));
							}
						}
					}
				} else {
					if(log.isLoggable(Level.FINER)) {
						log.finer(format(Messages.getString("AdminNSFService.skippingUnchangedServer"), getClass().getSimpleName(), serverName)); //$NON-NLS-1$
					}
				}
			}
		} finally {
			serverDoc.recycle();
		}
	}
	
	// *******************************************************************************
	// * Internal implementation methods
	// *******************************************************************************
	
	
	
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
