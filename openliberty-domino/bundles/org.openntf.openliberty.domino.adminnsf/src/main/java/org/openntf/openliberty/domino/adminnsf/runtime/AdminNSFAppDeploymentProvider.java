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
package org.openntf.openliberty.domino.adminnsf.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.Vector;

import org.openntf.openliberty.domino.adminnsf.util.AdminNSFUtil;
import org.openntf.openliberty.domino.runtime.AppDeploymentProvider;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.EmbeddedObject;
import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.RichTextItem;
import lotus.domino.Session;
import lotus.domino.View;
import lotus.domino.ViewEntry;
import lotus.domino.ViewNavigator;

public class AdminNSFAppDeploymentProvider implements AppDeploymentProvider {
	public static final String VIEW_SERVERSANDAPPS = "(ServersAndApps)"; //$NON-NLS-1$
	
	public static final String FORM_APP = "DropinApp"; //$NON-NLS-1$
	public static final String ITEM_APPNAME = "AppName"; //$NON-NLS-1$
	public static final String ITEM_CONTEXTPATH = "ContextRoot"; //$NON-NLS-1$
	public static final String ITEM_FILE = "WarFile"; //$NON-NLS-1$
	public static final String ITEM_REVERSEPROXY = "IncludeInReverseProxy"; //$NON-NLS-1$

	@Override
	public void deployApp(String serverName, String appName, String contextPath, String fileName, Boolean includeInReverseProxy, InputStream appData) {
		if(StringUtil.isEmpty(serverName)) {
			throw new IllegalArgumentException("serverName cannot be empty");
		}
		if(StringUtil.isEmpty(appName)) {
			throw new IllegalArgumentException("appName cannot be empty");
		}
		
		try {
			Session session = NotesFactory.createSession();
			try {
				Database database = AdminNSFUtil.getAdminDatabase(session);
				View serversAndApps = database.getView(VIEW_SERVERSANDAPPS);
				serversAndApps.setAutoUpdate(false);
				
				ViewEntry serverEntry = serversAndApps.getEntryByKey(serverName, true);
				if(serverEntry == null) {
					throw new IllegalArgumentException(MessageFormat.format("Unable to locate server \"{0}\"", serverName));
				}
				ViewNavigator nav = serversAndApps.createViewNavFromChildren(serverEntry);
				ViewEntry appEntry = nav.getFirst();
				while(appEntry != null) {
					Vector<?> columnValues = appEntry.getColumnValues();
					String entryAppName = (String)columnValues.get(0);
					if(appName.equalsIgnoreCase(entryAppName)) {
						break;
					}
					
					appEntry.recycle(columnValues);
					ViewEntry tempEntry = appEntry;
					appEntry = nav.getNextSibling(appEntry);
					tempEntry.recycle();
				}
				
				Document appDoc;
				if(appEntry == null) {
					appDoc = database.createDocument();
					appDoc.replaceItemValue("Form", FORM_APP); //$NON-NLS-1$
					appDoc.makeResponse(serverEntry.getDocument());
					appDoc.replaceItemValue(ITEM_APPNAME, appName);
				} else {
					appDoc = appEntry.getDocument();
				}
				
				String path = contextPath;
				if(StringUtil.isEmpty(path)) {
					// Determine whether to change an existing value
					String existing = appDoc.getItemValueString(ITEM_CONTEXTPATH);
					if(StringUtil.isEmpty(existing)) {
						path = appName;
						appDoc.replaceItemValue(ITEM_CONTEXTPATH, path);
					}
				} else {
					appDoc.replaceItemValue(ITEM_CONTEXTPATH, path);
				}
				appDoc.replaceItemValue(ITEM_REVERSEPROXY, includeInReverseProxy != null && includeInReverseProxy ? "Y" : "N"); //$NON-NLS-1$ //$NON-NLS-2$
				
				if(appDoc.hasItem(ITEM_FILE)) {
					appDoc.removeItem(ITEM_FILE);
				}
				RichTextItem fileItem = appDoc.createRichTextItem(ITEM_FILE);
				
				Path tempDir = Files.createTempDirectory(OpenLibertyUtil.getTempDirectory(), getClass().getName());
				try {
					String fname = fileName;
					if(StringUtil.isEmpty(fname)) {
						// TODO consider a non-WAR default
						fname = appName + ".war"; //$NON-NLS-1$
					}
					Path file = tempDir.resolve(fname);
					Files.copy(appData, file, StandardCopyOption.REPLACE_EXISTING);
					fileItem.embedObject(EmbeddedObject.EMBED_ATTACHMENT, "", file.toString(), null); //$NON-NLS-1$
				} finally {
					OpenLibertyUtil.deltree(tempDir);
				}
				
				appDoc.save();
			} finally {
				session.recycle();
			}
		} catch(NotesException | IOException e) {
			throw new RuntimeException(e);
		}
	}

}
