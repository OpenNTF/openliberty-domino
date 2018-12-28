package org.openntf.openliberty.adminnsf;

import java.util.concurrent.TimeUnit;

import org.openntf.openliberty.ext.RuntimeService;
import org.openntf.openliberty.util.DominoThreadFactory;

public class AdminNSFServiceProvider implements RuntimeService {

	@Override
	public void run() {
		DominoThreadFactory.scheduler.scheduleWithFixedDelay(new AdminNSFService(), 0, 30, TimeUnit.SECONDS);
	}

}
