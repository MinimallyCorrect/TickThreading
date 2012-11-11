package me.nallar.TickThreading.ConcurrentCollections;

import java.util.HashMap;

/**
 * Concurrent HashMap, which extends HashMap.
 * Necessary as ConcurrentHashMap does not, so it's
 * much harder to replace usages of HashMap with it
 */
public class CHashMap extends HashMap {
	private static final long serialVersionUID = 7249069246763182397L;
}
