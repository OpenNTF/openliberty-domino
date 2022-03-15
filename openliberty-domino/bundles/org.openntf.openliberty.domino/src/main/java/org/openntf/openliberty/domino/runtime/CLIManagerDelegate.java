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
package org.openntf.openliberty.domino.runtime;

import java.text.MessageFormat;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.util.DominoThreadFactory;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;

/**
 * This class handles shared code for CLI-based runtime managers.
 * 
 * @author Jesse Gallagher
 * @since 2.0.0
 */
public class CLIManagerDelegate implements AutoCloseable {
	private Future<?> runner;
	
	/**
	 * Initializes the delegate and the Open Liberty thread provider.
	 */
	public void start() {
		if(this.runner == null) {
			this.runner = DominoThreadFactory.getExecutor().submit(OpenLibertyRuntime.instance);
		}
	}
	
	/**
	 * Terminates the delegate and the thread manager.
	 * 
	 * <p>This method should be called during full shutdown of the runtime.
	 */
	@Override
	public void close() {
		if(this.runner != null) {
			this.runner.cancel(true);
			this.runner = null;
		}
		OpenLibertyRuntime.instance.stop();
		OpenLibertyUtil.performShutdownCleanup();
	}
	
	/**
	 * Evaluates and executes the provided command.
	 * 
	 * @param line the command to execute
	 * @return immediate result text, if available.
	 */
	public String processCommand(String line) {
		String[] argv = line.split("\\s+"); //$NON-NLS-1$
		if(argv.length > 0) {
			Command command = parseCommand(argv[0]);
			if(command == null) {
				return MessageFormat.format(Messages.getString("CLIManagerDelegate.unknownCommand"), argv[0]); //$NON-NLS-1$
			}
			switch(command) {
			case STATUS:
				OpenLibertyRuntime.instance.showStatus();
				return Messages.getString("CLIManagerDelegate.statusOfRunningServers"); //$NON-NLS-1$
			case START:
				if(this.runner != null) {
					return Messages.getString("CLIManagerDelegate.serverAlreadyRunning"); //$NON-NLS-1$
				} else {
					start();
					return Messages.getString("CLIManagerDelegate.startingServer"); //$NON-NLS-1$
				}
			case STOP:
				if(this.runner == null) {
					return Messages.getString("CLIManagerDelegate.serverIsNotRunning"); //$NON-NLS-1$
				} else {
					close();
					return Messages.getString("CLIManagerDelegate.stoppedServer"); //$NON-NLS-1$
				}
			case RESTART:
				if(this.runner != null) {
					close();
					try {
						TimeUnit.SECONDS.sleep(3);
					} catch (InterruptedException e) {
					}
					start();
					return Messages.getString("CLIManagerDelegate.restartedServer"); //$NON-NLS-1$
				} else {
					start();
					return Messages.getString("CLIManagerDelegate.startingServer"); //$NON-NLS-1$
				}
			case HELP:
				emitHelp();
				return ""; //$NON-NLS-1$
			default:
				return MessageFormat.format(Messages.getString("CLIManagerDelegate.commandNotYetImplemented"), command); //$NON-NLS-1$
			}
		} else {
			emitHelp();
			
			return ""; //$NON-NLS-1$
		}
	}
	
	private void emitHelp() {
		OpenLibertyLog.instance.out.println(Messages.getString("CLIManagerDelegate.availableCommands")); //$NON-NLS-1$
		for(Command command : Command.values()) {
			OpenLibertyLog.instance.out.println("- " + command.toString()); //$NON-NLS-1$
		}
	}
	
	// *******************************************************************************
	// * Internal implementation
	// *******************************************************************************

	private enum Command {
		STATUS, STOP, START, RESTART, HELP;
	}
	
	private Command parseCommand(String command) {
		try {
			return Command.valueOf(String.valueOf(command).toUpperCase());
		} catch(IllegalArgumentException e) {
			return null;
		}
	}
}
