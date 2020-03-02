package org.openntf.openliberty.domino.runjava;

import java.io.PrintStream;
import java.util.regex.Pattern;

import org.openntf.openliberty.domino.config.RuntimeProperties;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;

import lotus.notes.addins.wlp.WLP;

public class AddinLogPrintStream extends PrintStream {
	private final String prefix;
	private final WLP context;
	
	public AddinLogPrintStream(WLP context) {
		super(System.out);
		this.prefix = RuntimeProperties.instance.getPrefix();
		this.context = context;
	}
	
	private ThreadLocal<StringBuilder> buffer = ThreadLocal.withInitial(StringBuilder::new);
	private static final Pattern LINE_BREAK = Pattern.compile("[\\r\\n]+"); //$NON-NLS-1$
	
	@Override
	public void println(String paramString) {
		if(StringUtil.isEmpty(paramString)) {
			println();
			return;
		}
		
		String message = null;
		if(buffer.get().length() > 0) {
			message = buffer.get().toString() + StringUtil.toString(paramString);
			buffer.get().setLength(0);
		} else {
			message = StringUtil.toString(paramString);
		}
		
		_lines(message);
	}
	
	@Override
	public void println() {
		flushBuffer();
	}
	
	@Override
	public void println(Object paramObject) {
		println(StringUtil.toString(paramObject));
	}
	@Override
	public void println(boolean paramBoolean) {
		println(StringUtil.toString(paramBoolean));
	}
	@Override
	public void println(char paramChar) {			
		println(StringUtil.toString(paramChar));
	}
	@Override
	public void println(char[] paramArrayOfChar) {
		println(new String(paramArrayOfChar));
	}
	@Override
	public void println(double paramDouble) {
		println(StringUtil.toString(paramDouble));
	}
	@Override
	public void println(float paramFloat) {
		println(StringUtil.toString(paramFloat));
	}
	@Override
	public void println(int paramInt) {
		println(StringUtil.toString(paramInt));
	}
	@Override
	public void println(long paramLong) {
		println(StringUtil.toString(paramLong));
	}
	

	@Override
	public void print(String paramString) {
		if(StringUtil.isEmpty(paramString)) {
			return;
		}
		
		String[] pieces = LINE_BREAK.split(paramString);
		if(pieces.length > 0) {
			// Print out any whole strings, then append a remainder to the buffer
			for(int i = 0; i < pieces.length-1; i++) {
				_line(pieces[i]);
			}
			
			buffer.get().append(pieces[pieces.length-1]);
		}
	}
	@Override
	public void print(boolean paramBoolean) {
		buffer.get().append(paramBoolean);
	}
	@Override
	public void print(char[] paramArrayOfChar) {
		buffer.get().append(paramArrayOfChar);
	}
	@Override
	public void print(long paramLong) {
		buffer.get().append(paramLong);
	}
	@Override
	public void print(int paramInt) {
		buffer.get().append(paramInt);
	}
	@Override
	public void print(char paramChar) {
		if(paramChar == '\n' || paramChar == '\r') {
			flushBuffer();
		} else {
			buffer.get().append(paramChar);
		}
	}
	@Override
	public void print(double paramDouble) {
		buffer.get().append(paramDouble);
	}
	@Override
	public void print(float paramFloat) {
		buffer.get().append(paramFloat);
	}
	@Override
	public void print(Object paramObject) {
		print(StringUtil.toString(paramObject));
	}
	
	/* (non-Javadoc)
	 * @see java.io.PrintStream#flush()
	 */
	@Override
	public void flush() {
		flushBuffer();
	}
	
	/* ******************************************************************************
	 * Internal utility methods
	 ********************************************************************************/
	
	private void _lines(String message) {
		String[] pieces = LINE_BREAK.split(message);
		if(pieces.length > 0) {
			for(int i = 0; i < pieces.length; i++) {
				_line(pieces[i]);
			}
		}
	}
	
	private void _line(String message) {
		if(StringUtil.isEmpty(prefix)) {
			context.AddInLogMessageText(StringUtil.toString(message).replace("%", "%%") + '\n'); //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			context.AddInLogMessageText(prefix + StringUtil.toString(message).replace("%", "%%") + '\n'); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
	private void flushBuffer() {
		if(buffer.get().length() > 0) {
			_lines(buffer.get().toString());
			buffer.get().setLength(0);
		}
	}
}