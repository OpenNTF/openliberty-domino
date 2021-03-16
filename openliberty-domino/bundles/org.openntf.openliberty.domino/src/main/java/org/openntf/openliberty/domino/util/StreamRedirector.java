package org.openntf.openliberty.domino.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.openntf.openliberty.domino.log.OpenLibertyLog;

public class StreamRedirector implements Runnable {
	private final InputStream is;
	
	public StreamRedirector(InputStream is) {
		this.is = is;
	}
	
	@Override
	public void run() {
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			String line = null;
			while((line = reader.readLine()) != null) {
				OpenLibertyLog.instance.out.println(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}