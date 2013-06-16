package me.nallar.tickthreading.util.contextaccess;

public interface ContextAccess {
	public static final ContextAccess $ = ContextAccessProvider.getContextAccess();

	Class getContext(int depth);

	boolean runningUnder(Class c);
}
