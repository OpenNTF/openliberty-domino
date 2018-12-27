package org.openntf.openliberty.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import lotus.domino.NotesThread;

public class DominoThreadFactory implements ThreadFactory {
	private static int spawnCount = 0;
	private static final Object sync = new Object();

	public static final DominoThreadFactory instance = new DominoThreadFactory();

	public static final ExecutorService executor = Executors.newCachedThreadPool(instance);
	public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5, instance);

	@Override
	public Thread newThread(final Runnable runnable) {
		synchronized(sync) {
			spawnCount++;
		}
		return new NotesThread(runnable, "DominoThreadFactory Thread " + spawnCount); //$NON-NLS-1$
	}
}