package me.nallar.tickthreading.mcp;

import java.lang.reflect.Method;

import me.nallar.tickthreading.MethodDescription;

public abstract class Mappings {
	public MethodDescription obfuscate(Method method) {
		return obfuscate(new MethodDescription(method));
	}

	public abstract MethodDescription obfuscate(MethodDescription methodDescription);
}
