package me.nallar.tickthreading.mappings;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javassist.CtClass;
import javassist.CtMethod;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.util.CollectionsUtil;

public class MethodDescription {
	public final String clazz;
	public final String returnType;
	public final String parameters;
	public final String name;
	private Integer cachedHashCode = null;

	private MethodDescription(String clazz, String name, String returnType, String parameters) {
		this.clazz = clazz;
		this.returnType = returnType;
		this.parameters = parameters;
		this.name = name;
	}

	private MethodDescription(Method method) {
		this(method.getDeclaringClass().getCanonicalName(), method.getName(), getJVMName(method.getReturnType()), getParameterList(method));
	}

	private MethodDescription(CtMethod ctMethod) {
		this(ctMethod.getDeclaringClass().getName(), ctMethod.getName(), ctMethod.getSignature());
	}

	public MethodDescription(String clazz, String name, String MCPDescription) {
		//MCP style - (Lxv;IIILanw;Ljava/util/List;Llq;)V
		this(clazz, name, MCPDescription.substring(MCPDescription.lastIndexOf(')') + 1), MCPDescription.substring(1, MCPDescription.indexOf(')')));
	}

	public String getShortName() {
		return clazz + '/' + name;
	}

	String getMCPName() {
		return '(' + parameters + ')' + returnType;
	}

	@Override
	public String toString() {
		if (!name.isEmpty() && name.charAt(name.length() - 1) == '^') {
			return name;
		}
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
		return this == other ||
				(other instanceof MethodDescription &&
						((MethodDescription) other).clazz.equals(this.clazz) &&
						((MethodDescription) other).returnType.equals(this.returnType) &&
						((MethodDescription) other).parameters.equals(this.parameters) &&
						((MethodDescription) other).name.equals(this.name))
				|| (other instanceof Method && new MethodDescription((Method) other).equals(this));
	}

	boolean similar(Object other) {
		return this == other || (other instanceof MethodDescription && ((MethodDescription) other).getShortName().equals(this.getShortName())) || (other instanceof Method && new MethodDescription((Method) other).similar(this));
	}

	public boolean isExact() {
		return !this.parameters.isEmpty() || !this.returnType.isEmpty();
	}

	public CtMethod inClass(CtClass ctClass) {
		CtMethod possible = null;
		for (CtMethod ctMethod : ctClass.getDeclaredMethods()) {
			MethodDescription methodDescription = new MethodDescription(ctMethod);
			if (methodDescription.equals(this)) {
				return ctMethod;
			}
			if (methodDescription.name.equals(this.name) && (!this.parameters.isEmpty() && methodDescription.getParameterList().size() == this.getParameterList().size())) {
				possible = ctMethod;
			}
		}
		if (isExact()) {
			Log.warning("Failed to find exact match for " + this.getMCPName() + ", trying to find similar methods.");
		}
		if (possible != null) {
			return possible;
		}
		for (CtMethod ctMethod : ctClass.getDeclaredMethods()) {
			if (new MethodDescription(ctMethod).similar(this)) {
				return ctMethod;
			}
		}
		throw new RuntimeException("Method not found: " + this + " was not found in " + ctClass.getName());
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
		return 'L' + clazz.getCanonicalName() + ';';
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
		if (methodString.contains("(")) {
			try {
				String methodName = methodString.substring(0, methodString.indexOf('('));
				methodString = methodString.replace('.', '/');
				return new MethodDescription(clazz, methodName, methodString.substring(methodString.indexOf('(')));
			} catch (Exception e) {
				Log.severe("Failed to parse " + methodString, e);
			}
		}
		return new MethodDescription(clazz, methodString, "", "");
	}

	public static List<MethodDescription> fromListString(String clazz, String methodList) {
		ArrayList<MethodDescription> methodDescriptions = new ArrayList<MethodDescription>();
		for (String methodString : CollectionsUtil.split(methodList)) {
			methodDescriptions.add(fromString(clazz, methodString));
		}
		return methodDescriptions;
	}

	public static String toListString(List<MethodDescription> methodDescriptions) {
		if (methodDescriptions.isEmpty()) {
			return "";
		}
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(methodDescriptions.remove(0));
		for (MethodDescription methodDescription : methodDescriptions) {
			stringBuilder.append(',').append(methodDescription);
		}
		return stringBuilder.toString();
	}

	public void obfuscateClasses() {
		// TODO: obfuscate parameters and return type
	}

	public String getParameters() {
		return parameters;
	}

	public List<String> getParameterList() {
		String parameters = this.parameters;
		List<String> parameterList = new ArrayList<String>();
		while (!parameters.isEmpty()) {
			String p = parameters.substring(0, 1);
			parameters = parameters.substring(1);
			if ("L".equals(p)) {
				int cIndex = parameters.indexOf(';');
				p = parameters.substring(0, cIndex).replace('/', '.');
				parameters = parameters.substring(cIndex + 1);
			}
			parameterList.add(p);
		}
		return parameterList;
	}
}
