package org.openntf.openliberty.domino.adminnsf;

import lotus.domino.Document;
import lotus.domino.NotesException;

/**
 * This extension interface specifies a service that can handle a server
 * document within the admin NSF.
 * 
 * @author Jesse Gallagher
 * @since 4.0.0
 */
public interface ServerDocumentHandler {
	boolean canHandle(Document serverDoc) throws NotesException;
	
	void handle(Document serverDoc) throws NotesException;
}
