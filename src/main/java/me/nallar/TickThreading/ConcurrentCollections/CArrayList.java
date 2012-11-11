package me.nallar.TickThreading.ConcurrentCollections;

import java.util.ArrayList;

/**
 * Semi-concurrent arraylist.
 * Ignores comodification exceptions, some synchronisation.
 */
public class CArrayList extends ArrayList {
	private static final long serialVersionUID = 8683452581122892189L;
}
