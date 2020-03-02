package org.openntf.openliberty.domino.runtime;

/**
 * Utility interface to denote command interfaces for the runtime and
 * to include shared command names.
 * 
 * @author Jesse Gallagher
 * @since 2.0.0
 */
public interface CLICommands {
	enum Command {
		STATUS, STOP, START, RESTART;
	}
	
	default Command parseCommand(String command) {
		try {
			return Command.valueOf(String.valueOf(command).toUpperCase());
		} catch(IllegalArgumentException e) {
			return null;
		}
	}
}
