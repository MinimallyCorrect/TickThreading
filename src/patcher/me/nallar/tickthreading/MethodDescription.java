package me.nallar.tickthreading;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MethodDescription {
	public final String clazz;
	public final String returnType;
	public final String parameters;
	public final String name;

	public MethodDescription(String clazz, String name, String returnType, String parameters) {
		this.clazz = clazz;
		this.returnType = returnType;
		this.parameters = parameters;
		this.name = name;
	}

	public MethodDescription(Method method) {
		this(method.getDeclaringClass().getCanonicalName(), method.getName(), getJVMName(method.getReturnType()), getParameterList(method));
	}

	public MethodDescription(String clazz, String name, String MCPDescription) {
		//MCP style - (Lxv;IIILanw;Ljava/util/List;Llq;)V
		this(clazz, name, MCPDescription.substring(1, MCPDescription.lastIndexOf(')') - 1), MCPDescription.substring(MCPDescription.lastIndexOf(')') + 1));
	}

	public String getFullName() {
		return clazz + "/" + name;
	}

	@Override
	public int hashCode() {
		int hashCode = returnType.hashCode();
		hashCode = 31 * hashCode + parameters.hashCode();
		hashCode = 31 * hashCode + name.hashCode();
		return hashCode;
	}

	@Override
	public boolean equals(Object other) {
		return this == other || (other instanceof MethodDescription && other.hashCode() == this.hashCode());
	}

	private static String getJVMName(Class<?> clazz) {
		if (clazz.isPrimitive()) {
			if (clazz.equals(Boolean.TYPE)) {
				return "Z";
			} else if (clazz.equals(Short.TYPE)) {
				return "S";
			} else if (clazz.equals(Long.TYPE)) {
				return "J";
			} else if (clazz.equals(Integer.TYPE)) {
				return "I";
			} else if (clazz.equals(Float.TYPE)) {
				return "F";
			} else if (clazz.equals(Double.TYPE)) {
				return "D";
			} else if (clazz.equals(Character.TYPE)) {
				return "C";
			}
		}
		return "L" + clazz.getCanonicalName() + ";";
	}

	private static String getParameterList(Method method) {
		List<Class<?>> parameterClasses = new ArrayList<Class<?>>(Arrays.asList(method.getParameterTypes()));
		StringBuilder parameters = new StringBuilder();
		for (Class<?> clazz : parameterClasses) {
			parameters.append(getJVMName(clazz));
		}
		return parameters.toString();
	}
}
