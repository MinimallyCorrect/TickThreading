package me.nallar.tickthreading.util.contextaccess;

public interface ContextAccess {
	public static final ContextAccess $ = ContextAccessProvider.getContextAccess();

	public Class getContext(int depth);
}
