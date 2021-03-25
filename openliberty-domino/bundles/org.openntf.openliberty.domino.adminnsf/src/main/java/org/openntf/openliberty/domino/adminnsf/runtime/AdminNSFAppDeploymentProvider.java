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
	public void deployApp(String serverName, String appName, String contextPath, String fileName, boolean includeInReverseProxy, InputStream appData) {
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
				if(path.startsWith("/")) { //$NON-NLS-1$
					path = path.substring(1);
				}
				appDoc.replaceItemValue(ITEM_CONTEXTPATH, path);
				appDoc.replaceItemValue(ITEM_REVERSEPROXY, includeInReverseProxy ? "Y" : "N"); //$NON-NLS-1$ //$NON-NLS-2$
				
				if(appDoc.hasItem(ITEM_FILE)) {
					appDoc.removeItem(ITEM_FILE);
				}
				RichTextItem fileItem = appDoc.createRichTextItem(ITEM_FILE);
				
				Path tempDir = Files.createTempDirectory(OpenLibertyUtil.getTempDirectory(), getClass().getName());
				try {
					Path file = tempDir.resolve(fileName);
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
