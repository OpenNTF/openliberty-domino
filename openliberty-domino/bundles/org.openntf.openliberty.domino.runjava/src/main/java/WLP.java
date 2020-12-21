/*
 * Copyright © 2018-2020 Jesse Gallagher
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

import static java.text.MessageFormat.format;

import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.runjava.AddInLogBridge;
import org.openntf.openliberty.domino.runjava.AddinLogPrintStream;

import org.openntf.openliberty.domino.runtime.CLIManagerDelegate;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;

import lotus.domino.NotesException;
import lotus.domino.NotesThread;
import lotus.notes.addins.JavaServerAddin;
import lotus.notes.internal.MessageQueue;

/**
 * Entrypoint for loading the Open Liberty runtime using the
 * {@code runjava} server addin system.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public class WLP extends JavaServerAddin implements AddInLogBridge {
	public static final String QUEUE_NAME = JavaServerAddin.MSG_Q_PREFIX + "WLP"; //$NON-NLS-1$
	private static final String BUNDLE_NAME = "org.openntf.openliberty.domino.runjava.messages"; //$NON-NLS-1$

	// MessageQueue Constants
	public static final int MQ_MAX_MSGSIZE = 256;
	
	// MessageQueue errors:
	public static final int PKG_MISC = 0x0400;
	public static final int ERR_MQ_POOLFULL = PKG_MISC+94;
	public static final int ERR_MQ_TIMEOUT = PKG_MISC+95;
	public static final int ERR_MQSCAN_ABORT = PKG_MISC+96;
	public static final int ERR_DUPLICATE_MQ = PKG_MISC+97;
	public static final int ERR_NO_SUCH_MQ = PKG_MISC+98;
	public static final int ERR_MQ_EXCEEDED_QUOTA = PKG_MISC+99;
	public static final int ERR_MQ_EMPTY = PKG_MISC+100;
	public static final int ERR_MQ_BFR_TOO_SMALL = PKG_MISC+101;
	public static final int ERR_MQ_QUITTING = PKG_MISC+102;

	private final CLIManagerDelegate delegate = new CLIManagerDelegate();
	private final ExecutorService commandQueue = Executors.newSingleThreadExecutor(NotesThread::new);
	private ResourceBundle translationBundle;
	
	/**
	 * CLI/generic entrypoint
	 */
	public static void main(String[] args) {
		new WLP().start();
	}
	
	public WLP() {
		setName("WLP"); //$NON-NLS-1$
	}
	
	@Override
	public void runNotes() throws NotesException {
		AddinLogPrintStream.setBridge(this);
		
		setName(translate("WLP.progName")); //$NON-NLS-1$
		int taskId = AddInCreateStatusLine(getName());
		AddInSetStatusLine(taskId, translate("WLP.statusInitializing")); //$NON-NLS-1$
		try {
			delegate.start();
			
			MessageQueue mq = new MessageQueue();
			int status = mq.create(QUEUE_NAME, 0, 0);
			if(status != NOERROR) {
				throw new RuntimeException(format(translate("WLP.errorMqCreate"), status)); //$NON-NLS-1$
			}
			
			status = mq.open(QUEUE_NAME, 0);
			if(status != NOERROR) {
				throw new RuntimeException(format(translate("WLP.errorMqOpen"), status)); //$NON-NLS-1$
			}

			try {
				AddInSetStatusLine(taskId, translate("WLP.statusIdle")); //$NON-NLS-1$
				StringBuffer buf = new StringBuffer();
				while(addInRunning() && status != ERR_MQ_QUITTING) {
					OSPreemptOccasionally();
					
					status = mq.get(buf, MQ_MAX_MSGSIZE, MessageQueue.MQ_WAIT_FOR_MSG, 500);
					switch(status) {
					case NOERROR: {
						String line = buf.toString();
						if(StringUtil.isNotEmpty(line)) {
							
							commandQueue.submit(() -> {
								String result = delegate.processCommand(line);
								if(StringUtil.isNotEmpty(result)) {
									OpenLibertyLog.instance.out.println(result);
								}
							});
						}
						
						break;
					}
					case ERR_MQ_TIMEOUT:
					case ERR_MQ_EMPTY:
					case ERR_MQ_QUITTING:
						break;
					default:
						AddInLogErrorText(translate("WLP.unexpectedCodeWhilePolling", status)); //$NON-NLS-1$
						break;
					}
				}
			} finally {
				mq.close(0);
			}
		} catch (Exception e) {
			AddInLogErrorText(e.getLocalizedMessage());
			e.printStackTrace();
		} finally {
			AddInSetStatusLine(taskId, translate("WLP.statusTerminating")); //$NON-NLS-1$
			commandQueue.shutdownNow();
			try {
				commandQueue.awaitTermination(1, TimeUnit.MINUTES);
			} catch (InterruptedException e) {
				// Ignore
			}
			try {
				delegate.close();
			} catch(Throwable t) {
				t.printStackTrace();
			}
			
			AddInSetStatusLine(taskId, translate("WLP.statusTerm")); //$NON-NLS-1$
			AddInDeleteStatusLine(taskId);
		}
	}
	
	@Override
	public void AddInLogMessageText(String msg) {
		super.AddInLogMessageText(msg);
	}
	
	// *******************************************************************************
	// * Internal utility methods
	// *******************************************************************************
	
	private synchronized String translate(String msg, Object... params) {
		if(this.translationBundle == null) {
			this.translationBundle = ResourceBundle.getBundle(BUNDLE_NAME);
		}
		try {
			return format(translationBundle.getString(msg), params);
		} catch (MissingResourceException e) {
			return '!' + msg + '!';
		}
	}
}
