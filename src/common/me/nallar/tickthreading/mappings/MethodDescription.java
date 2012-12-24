package me.nallar.tickthreading.mappings;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javassist.CtClass;
import javassist.CtMethod;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.util.ListUtil;

public class MethodDescription {
	public final String clazz;
	public final ClassDescription classDescription;
	public final String returnType;
	public final String parameters;
	public final String name;
	private Integer cachedHashCode = null;

	public MethodDescription(String clazz, String name, String returnType, String parameters) {
		this.clazz = clazz;
		this.classDescription = new ClassDescription(clazz);
		this.returnType = returnType;
		this.parameters = parameters;
		this.name = name;
	}

	public MethodDescription(Method method) {
		this(method.getDeclaringClass().getCanonicalName(), method.getName(), getJVMName(method.getReturnType()), getParameterList(method));
	}

	public MethodDescription(CtMethod ctMethod) {
		this(ctMethod.getDeclaringClass().getName(), ctMethod.getName(), ctMethod.getSignature());
	}

	public MethodDescription(String clazz, String name, String MCPDescription) {
		//MCP style - (Lxv;IIILanw;Ljava/util/List;Llq;)V
		this(clazz, name, MCPDescription.substring(MCPDescription.lastIndexOf(')') + 1), MCPDescription.substring(1, MCPDescription.indexOf(')')));
	}

	public String getShortName() {
		return clazz + "/" + name;
	}

	public String getMCPName() {
		return "(" + parameters + ")" + returnType;
	}

	@Override
	public String toString() {
		return name + getMCPName();
	}

	@Override
	public int hashCode() {
		if (cachedHashCode != null) {
			return cachedHashCode;
		}
		int hashCode = returnType.hashCode();
		hashCode = 31 * hashCode + parameters.hashCode();
		hashCode = 31 * hashCode + name.hashCode();
		hashCode = 31 * hashCode + clazz.hashCode();
		return (cachedHashCode = hashCode);
	}

	@Override
	public boolean equals(Object other) {
		return this == other || (other instanceof MethodDescription && other.hashCode() == this.hashCode()) || (other instanceof Method && new MethodDescription((Method) other).equals(this));
	}

	public boolean similar(Object other) {
		return this == other || (other instanceof MethodDescription && ((MethodDescription) other).getShortName() == this.getShortName()) || (other instanceof Method && new MethodDescription((Method) other).similar(this));
	}

	public CtMethod inClass(CtClass ctClass) {
		for (CtMethod ctMethod : ctClass.getDeclaredMethods()) {
			if (new MethodDescription(ctMethod).equals(this)) {
				return ctMethod;
			}
		}
		if (!this.parameters.isEmpty() || !this.returnType.isEmpty()) {
			Log.warning("Failed to find exact match for " + this.getMCPName() + ", trying to find similar methods.");
		}
		for (CtMethod ctMethod : ctClass.getDeclaredMethods()) {
			if (new MethodDescription(ctMethod).similar(this)) {
				return ctMethod;
			}
		}
		throw new RuntimeException("Method not found");
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

	public static MethodDescription fromString(String clazz, String methodString) {
		try {
			String methodName = methodString.substring(0, methodString.indexOf('('));
			return new MethodDescription(clazz, methodName, methodString.substring(methodString.indexOf('(')));
		} catch (Exception ignored) {

		}
		return new MethodDescription(clazz, methodString, "", "");
	}

	public static List<MethodDescription> fromListString(String clazz, String methodList) {
		ArrayList<MethodDescription> methodDescriptions = new ArrayList<MethodDescription>();
		for (String methodString : ListUtil.split(methodList)) {
			methodDescriptions.add(fromString(clazz, methodString));
		}
		return methodDescriptions;
	}

	public static String toListString(List<MethodDescription> methodDescriptions) {
		if (methodDescriptions.size() == 0) {
			return "";
		}
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(methodDescriptions.remove(0));
		for (MethodDescription methodDescription : methodDescriptions) {
			stringBuilder.append(',').append(methodDescription);
		}
		return stringBuilder.toString();
	}
}
