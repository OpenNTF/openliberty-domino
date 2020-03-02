/**
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
package lotus.notes.addins.wlp;

import static java.text.MessageFormat.format;
import static org.openntf.openliberty.domino.runjava.Messages.getString;

import java.io.PrintStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.openntf.openliberty.domino.log.OpenLibertyLog;
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
public class WLP extends JavaServerAddin {
	public static final String PROG_NAME = getString("WLP.progName"); //$NON-NLS-1$
	public static final String QUEUE_NAME = JavaServerAddin.MSG_Q_PREFIX + "WLP"; //$NON-NLS-1$
	
	public static WLP instance;

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
	
	private final PrintStream out = OpenLibertyLog.instance.out;
	private final CLIManagerDelegate delegate = new CLIManagerDelegate();
	private final ExecutorService commandQueue = Executors.newFixedThreadPool(1, NotesThread::new);
	
	/**
	 * CLI/generic entrypoint
	 */
	public static void main(String[] args) {
		new WLP().start();
	}
	
	public WLP() {
		setName(PROG_NAME);
		instance = this;
	}
	
	@Override
	public void runNotes() throws NotesException {
		
		int taskId = AddInCreateStatusLine(PROG_NAME);
		AddInSetStatusLine(taskId, getString("WLP.statusInitializing")); //$NON-NLS-1$
		try {
			MessageQueue mq = new MessageQueue();
			int status = mq.create(QUEUE_NAME, 0, 0);
			if(status != NOERROR) {
				throw new RuntimeException(format(getString("WLP.errorMqCreate"), status)); //$NON-NLS-1$
			}

			AddInSetStatusLine(taskId, getString("WLP.statusIdle")); //$NON-NLS-1$
			StringBuffer buf = new StringBuffer();
			while(status != ERR_MQ_QUITTING) {
				OSPreemptOccasionally();
				
				status = mq.get(buf, MQ_MAX_MSGSIZE, MessageQueue.MQ_WAIT_FOR_MSG, 500);
				if(status == NOERROR) {
					String line = buf.toString();
					if(StringUtil.isNotEmpty(line)) {
						commandQueue.submit(() -> {
							String result = delegate.processCommand(line);
							if(StringUtil.isNotEmpty(result)) {
								out.println(result);
							}
						});
					}
					
					buf.setLength(0);
				}
			}
		} catch (Exception e) {
			AddInLogErrorText(e.getLocalizedMessage());
			e.printStackTrace(out);
		} finally {
			AddInSetStatusLine(taskId, getString("WLP.statusTerminating")); //$NON-NLS-1$
			
			AddInSetStatusLine(taskId, getString("WLP.statusTerm")); //$NON-NLS-1$
			AddInDeleteStatusLine(taskId);
		}
	}
	
	@Override
	public void AddInLogMessageText(String msg) {
		super.AddInLogMessageText(msg);
	}
}
