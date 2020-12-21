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
package org.openntf.openliberty.wlp.notesruntime;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import com.darwino.domino.napi.DominoAPI;
import com.darwino.domino.napi.DominoException;

public class RuntimeActivator implements BundleActivator {
	private static final Logger log = Logger.getLogger(RuntimeActivator.class.getName());
	
	private ExecutorService notesExecutor = Executors.newSingleThreadExecutor();
	
	@Override
	public void start(BundleContext context) throws Exception {
		if(log.isLoggable(Level.INFO)) {
			log.info("Initializing Notes runtime");
		}
		
		notesExecutor.submit(() -> {
			try {
				DominoAPI.get().NotesInit();
				DominoAPI.get().NotesInitThread();
				DominoAPI.get().HTMLProcessInitialize();
				
				try {
					while(true) {
						TimeUnit.DAYS.sleep(1);
					}
				} catch (InterruptedException e) {
					// Expected on shutdown
				} finally {
					DominoAPI.get().HTMLProcessTerminate();
					DominoAPI.get().NotesTerm();
					
					if(log.isLoggable(Level.INFO)) {
						log.info("Terminated Notes runtime");
					}
				}
			} catch (DominoException e) {
				e.printStackTrace();
			}	
		});
		
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		notesExecutor.shutdownNow();
		notesExecutor.awaitTermination(1, TimeUnit.MINUTES);
	}

}
