package me.nallar.collections;

import java.util.HashSet;

public class ContainedRemoveSet<T> extends HashSet<T> {
	@Override
	public boolean contains(Object o) {
		return remove(o);
	}
}
