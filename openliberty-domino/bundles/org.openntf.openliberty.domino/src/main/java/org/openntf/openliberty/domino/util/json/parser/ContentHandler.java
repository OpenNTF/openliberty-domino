package org.openntf.openliberty.domino.util.json.parser;

import java.io.IOException;

/**
 * A simplified and stoppable SAX-like content handler for stream processing of JSON text. 
 * 
 * @see org.xml.sax.ContentHandler
 * 
 * @author FangYidong fangyidong@yahoo.com.cn
 */
public interface ContentHandler {

	void startJSON() throws ParseException, IOException;
	

	void endJSON() throws ParseException, IOException;
	

	boolean startObject() throws ParseException, IOException;
	

	boolean endObject() throws ParseException, IOException;
	

	boolean startObjectEntry(String key) throws ParseException, IOException;
	

	boolean endObjectEntry() throws ParseException, IOException;
	

	boolean startArray() throws ParseException, IOException;
	

	boolean endArray() throws ParseException, IOException;
	

	boolean primitive(Object value) throws ParseException, IOException;
		
}
