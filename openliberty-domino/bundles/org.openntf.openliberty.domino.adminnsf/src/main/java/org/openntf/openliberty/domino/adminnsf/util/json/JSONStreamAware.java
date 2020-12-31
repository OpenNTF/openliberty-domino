package org.openntf.openliberty.domino.adminnsf.util.json;

import java.io.IOException;
import java.io.Writer;

/**
 * Beans that support customized output of JSON text to a writer shall implement this interface.  
 * @author FangYidong fangyidong@yahoo.com.cn
 */
public interface JSONStreamAware {

	void writeJSONString(Writer out) throws IOException;
}
