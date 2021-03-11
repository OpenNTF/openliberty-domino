package org.openntf.openliberty.domino.reverseproxy;

import org.openntf.openliberty.domino.util.DominoThreadFactory;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesFactory;
import lotus.domino.Session;

import org.openntf.openliberty.domino.adminnsf.util.AdminNSFUtil;
import org.openntf.openliberty.domino.ext.RuntimeService;
import org.openntf.openliberty.domino.reverseproxy.undertow.UndertowReverseProxy;

public class ReverseProxyService implements RuntimeService {
	@Override
	public void run() {
		System.out.println("gonna submit");
		try {
			Session session = NotesFactory.createSession();
			try {
				Database adminNsf = AdminNSFUtil.getAdminDatabase(session);
				Document config = AdminNSFUtil.getConfigurationDocument(adminNsf);
				
				UndertowReverseProxy proxy = new UndertowReverseProxy();
				DominoThreadFactory.executor.submit(proxy);
			} finally {
				session.recycle();
			}
		} catch(Throwable t) {
			t.printStackTrace();
		}
	}
}
