package org.openntf.openliberty.log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import com.ibm.commons.util.StringUtil;

public class DominoConsoleFormatter extends SimpleFormatter {
	
	private String prefix;
	private boolean includeDate;
	
	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"); //$NON-NLS-1$

	public DominoConsoleFormatter(String prefix, boolean includeDate) {
		this.prefix = prefix;
		this.includeDate = includeDate;
	}

	@Override
	public synchronized String format(LogRecord record) {
		StringBuffer sb = new StringBuffer() ;
		
		if (includeDate) {
			sb.append(dateFormat.format(new Date()) + " ");  //$NON-NLS-1$
		}
		
		if(StringUtil.isNotEmpty(prefix)) {
			sb.append(prefix + ": "); //$NON-NLS-1$
		}
		sb.append(record.getLevel() + " "); //$NON-NLS-1$
		sb.append(record.getMessage());
		sb.append("\n"); //$NON-NLS-1$
		
		return sb.toString();
	}	
}