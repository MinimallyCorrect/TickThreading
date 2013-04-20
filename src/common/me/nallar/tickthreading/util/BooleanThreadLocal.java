package me.nallar.tickthreading.util;

public class BooleanThreadLocal extends ThreadLocal<Boolean> {
	@Override
	public Boolean initialValue() {
		return false;
	}
}
