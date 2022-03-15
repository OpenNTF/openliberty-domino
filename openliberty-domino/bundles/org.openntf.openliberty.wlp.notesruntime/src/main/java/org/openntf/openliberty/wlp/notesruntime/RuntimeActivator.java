/*
 * Copyright © 2018-2022 Jesse Gallagher
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import com.darwino.domino.napi.DominoAPI;
import com.darwino.domino.napi.DominoException;
import com.ibm.commons.util.StringUtil;

public class RuntimeActivator implements BundleActivator {
	private static final Logger log = Logger.getLogger(RuntimeActivator.class.getName());
	
	private ExecutorService notesExecutor = Executors.newSingleThreadExecutor();
	
	@Override
	public void start(BundleContext context) throws Exception {
		if(log.isLoggable(Level.INFO)) {
			log.info(Messages.getString("RuntimeActivator.initializingRuntime")); //$NON-NLS-1$
		}
		
		AccessController.doPrivileged((PrivilegedAction<Void>)() -> {
			System.setProperty("notesruntime.init", "true"); //$NON-NLS-1$ //$NON-NLS-2$
			return null;
		});
		
		String notesProgramDir = System.getenv("Notes_ExecDirectory"); //$NON-NLS-1$
		
		if(StringUtil.isNotEmpty(notesProgramDir)) {
			// Attempt to load lsxbe and jnotes
			
			String lsxbeFileName = System.mapLibraryName("lsxbe"); //$NON-NLS-1$
			Path lsxbe = Paths.get(notesProgramDir).resolve(lsxbeFileName);
			if(Files.isRegularFile(lsxbe)) {
				System.load(lsxbe.toString());
			}
			
			String jnotesFileName = System.mapLibraryName("jnotes"); //$NON-NLS-1$
			Path jnotes = Paths.get(notesProgramDir).resolve(jnotesFileName);
			if(Files.isRegularFile(jnotes)) {
				System.load(jnotes.toString());
			}
		}
		
		
		notesExecutor.submit(() -> {
			try {
				String notesIniPath = System.getenv("NotesINI"); //$NON-NLS-1$ 
				if (StringUtil.isNotEmpty(notesProgramDir)) {
					String[] initArgs = new String[] {
							notesProgramDir,
							StringUtil.isEmpty(notesIniPath) ? "" : ("=" + notesIniPath) //$NON-NLS-1$ //$NON-NLS-2$ 
					};
					
					if(log.isLoggable(Level.INFO)) {
						log.info(StringUtil.format(Messages.getString("RuntimeActivator.initializingRuntimeWithArgs"), Arrays.toString(initArgs))); //$NON-NLS-1$
					}
					try {
						DominoAPI.get().NotesInitExtended(initArgs);
					} catch (DominoException e) {
						throw new RuntimeException(e);
					}
				} else {
					// For Windows specifically and Domino generally
					DominoAPI.get().NotesInit();
				}
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
					DominoAPI.get().NotesTermThread();
					DominoAPI.get().NotesTerm();
					
					if(log.isLoggable(Level.INFO)) {
						log.info(Messages.getString("RuntimeActivator.terminatedRuntime")); //$NON-NLS-1$
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
