package me.nallar.tickthreading.patcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Splitter;

import javassist.CannotCompileException;
import javassist.ClassMap;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.CtPrimitiveType;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.ClassFile;
import javassist.expr.Cast;
import javassist.expr.ConstructorCall;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.Handler;
import javassist.expr.Instanceof;
import javassist.expr.MethodCall;
import javassist.expr.NewArray;
import javassist.expr.NewExpr;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.mappings.MethodDescription;

@SuppressWarnings ("MethodMayBeStatic")
public class Patches {
	private final ClassRegistry classRegistry;

	public Patches(ClassRegistry classRegistry) {
		this.classRegistry = classRegistry;
	}

	@Patch
	public void markDirty(CtClass ctClass) {
		// A NOOP patch to make sure META-INF is removed
	}

	@Patch
	public void remove(CtMethod ctMethod) {
		ctMethod.setName(ctMethod.getName() + "_rem");
	}

	@Patch (
			requiredAttributes = "code"
	)
	public void newMethod(CtClass ctClass, Map<String, String> attributes) throws CannotCompileException {
		ctClass.addMethod(CtNewMethod.make(attributes.get("code"), ctClass));
	}

	@Patch
	public void profile(CtMethod ctMethod, Map<String, String> attributes) throws CannotCompileException, NotFoundException {
		CtClass ctClass = ctMethod.getDeclaringClass();
		CtMethod replacement = CtNewMethod.copy(ctMethod, ctClass, null);
		int i = 0;

		String deobf = attributes.get("deobf");
		String suffix = '_' + deobf.replace('/', '_').replace('.', '_') + "_p";
		try {
			//noinspection InfiniteLoopStatement
			for (; true; i++) {
				ctClass.getDeclaredMethod(ctMethod.getName() + suffix + i, ctMethod.getParameterTypes());
			}
		} catch (NotFoundException ignored) {
		}
		ctMethod.setName(ctMethod.getName() + suffix + i);
		if (ctMethod.getReturnType() == CtPrimitiveType.voidType) {
			replacement.setBody("{ boolean timings = javassist.is.faulty.Timings.enabled; long st = 0; if (timings) { st = System.nanoTime(); } " + ctMethod.getName() + "($$); if (timings) { javassist.is.faulty.Timings.record(\"" + deobf + "\", System.nanoTime() - st); } }");
		} else {
			replacement.setBody("{ boolean timings = javassist.is.faulty.Timings.enabled; long st = 0; if (timings) { st = System.nanoTime(); } try { return " + ctMethod.getName() + "($$); } finally { if (timings) { javassist.is.faulty.Timings.record(\"" + deobf + "\", System.nanoTime() - st); } } }");
		}
		ctClass.addMethod(replacement);
	}

	@Patch (
			name = "volatile"
	)
	public void volatile_(CtClass ctClass, Map<String, String> attributes) throws NotFoundException {
		String field = attributes.get("field");
		if (field == null) {
			for (CtField ctField : ctClass.getDeclaredFields()) {
				if (ctField.getType().isPrimitive()) {
					ctField.setModifiers(ctField.getModifiers() | Modifier.VOLATILE);
				}
			}
		} else {
			CtField ctField = ctClass.getDeclaredField(field);
			ctField.setModifiers(ctField.getModifiers() | Modifier.VOLATILE);
		}
	}

	@Patch (
			name = "final"
	)
	public void final_(CtClass ctClass, Map<String, String> attributes) throws NotFoundException {
		String field = attributes.get("field");
		if (field == null) {
			for (CtField ctField : ctClass.getDeclaredFields()) {
				if (ctField.getType().isPrimitive()) {
					ctField.setModifiers(ctField.getModifiers() | Modifier.FINAL);
				}
			}
		} else {
			CtField ctField = ctClass.getDeclaredField(field);
			ctField.setModifiers(ctField.getModifiers() | Modifier.FINAL);
		}
	}

	@Patch
	public void disable(CtMethod ctMethod, Map<String, String> attributes) throws NotFoundException, CannotCompileException {
		ctMethod.setBody("{ }");
	}

	@Patch (
			requiredAttributes = "class"
	)
	public CtClass replace(CtClass clazz, Map<String, String> attributes) throws NotFoundException, CannotCompileException {
		Log.info("Replacing " + clazz.getName() + " with " + attributes.get("class"));
		String oldName = clazz.getName();
		clazz.setName(oldName + "_old");
		CtClass newClass = classRegistry.getClass(attributes.get("class"));
		ClassFile classFile = newClass.getClassFile2();
		if (classFile.getSuperclass().equals(oldName)) {
			classFile.setSuperclass(null);
		}
		newClass.setName(oldName);
		newClass.setModifiers(newClass.getModifiers() & ~Modifier.ABSTRACT);
		return newClass;
	}

	@Patch
	public void replaceMethod(CtMethod method, Map<String, String> attributes) throws NotFoundException, CannotCompileException, BadBytecode {
		String fromClass = attributes.get("fromClass");
		String code = attributes.get("code");
		if (fromClass != null) {
			String fromMethod = attributes.get("fromMethod");
			CtMethod replacingMethod = fromMethod == null ?
					classRegistry.getClass(fromClass).getDeclaredMethod(method.getName(), method.getParameterTypes())
					: MethodDescription.fromString(fromClass, fromMethod).inClass(classRegistry.getClass(fromClass));
			replaceMethod(method, replacingMethod);
		} else if (code != null) {
			Log.info("Replacing " + new MethodDescription(method).getMCPName() + " with " + code);
			method.setBody(code);
		} else {
			Log.severe("Missing required attributes for replaceMethod");
		}
	}

	private void replaceMethod(CtMethod oldMethod, CtMethod newMethod) throws CannotCompileException, BadBytecode {
		Log.info("Replacing " + new MethodDescription(oldMethod).getMCPName() + " with " + new MethodDescription(newMethod).getMCPName());
		ClassMap classMap = new ClassMap();
		classMap.put(newMethod.getDeclaringClass().getName(), oldMethod.getDeclaringClass().getName());
		oldMethod.setBody(newMethod, classMap);
		oldMethod.getMethodInfo().rebuildStackMap(classRegistry.classes);
		oldMethod.getMethodInfo().rebuildStackMapForME(classRegistry.classes);
	}

	@Patch (
			requiredAttributes = "field"
	)
	public void replaceFieldUsage(CtBehavior ctBehavior, Map<String, String> attributes) throws CannotCompileException {
		final String field = attributes.get("field");
		final String readCode = attributes.get("readCode");
		final String writeCode = attributes.get("writeCode");
		if (readCode == null && writeCode == null) {
			throw new IllegalArgumentException("readCode or writeCode must be set");
		}
		ctBehavior.instrument(new ExprEditor() {
			@Override
			public void edit(FieldAccess fieldAccess) throws CannotCompileException {
				if (fieldAccess.getFieldName().equals(field)) {
					if (fieldAccess.isWriter() && writeCode != null) {
						fieldAccess.replace(writeCode);
					} else if (fieldAccess.isReader() && readCode != null) {
						fieldAccess.replace(readCode);
					}
				}
			}
		});
	}

	@Patch (
			requiredAttributes = "code"
	)
	public void replaceMethodCall(final CtBehavior ctBehavior, Map<String, String> attributes) throws CannotCompileException {
		String method_ = attributes.get("method");
		if (method_ == null) {
			method_ = "";
		}
		String className_ = null;
		int dotIndex = method_.indexOf('.');
		if (dotIndex != -1) {
			className_ = method_.substring(0, dotIndex);
			method_ = method_.substring(dotIndex + 1);
		}
		String index_ = attributes.get("index");
		if (index_ == null) {
			index_ = "-1";
		}

		final String method = method_;
		final String className = className_;
		final String code = attributes.get("code");
		final int index = Integer.valueOf(index_);

		Log.fine("method: " + method + ", class: " + className + ", code" + code + ", index: " + index);

		ctBehavior.instrument(new ExprEditor() {
			private int currentIndex = 0;

			@Override
			public void edit(MethodCall methodCall) throws CannotCompileException {
				if ((className == null || methodCall.getClassName().equals(className)) && (method.isEmpty() || methodCall.getMethodName().equals(method)) && (index == -1 || currentIndex++ == index)) {
					Log.info("Replaced " + methodCall + " from " + ctBehavior);
					methodCall.replace(code);
				}
			}
		});
	}

	@Patch (
			requiredAttributes = "code,return,name"
	)
	public void addMethod(CtClass ctClass, Map<String, String> attributes) throws NotFoundException, CannotCompileException {
		String name = attributes.get("name");
		String return_ = attributes.get("return");
		String code = attributes.get("code");
		String parameterNamesList = attributes.get("parameters");
		parameterNamesList = parameterNamesList == null ? "" : parameterNamesList;
		List<CtClass> parameterList = new ArrayList<CtClass>();
		for (String parameterName : Splitter.on(',').trimResults().omitEmptyStrings().split(parameterNamesList)) {
			parameterList.add(classRegistry.getClass(parameterName));
		}
		CtMethod newMethod = new CtMethod(classRegistry.getClass(return_), name, parameterList.toArray(new CtClass[parameterList.size()]), ctClass);
		newMethod.setBody('{' + code + '}');
		ctClass.addMethod(newMethod);
	}

	@Patch (
			requiredAttributes = "fromClass"
	)
	public void addAll(CtClass ctClass, Map<String, String> attributes) throws NotFoundException, CannotCompileException, BadBytecode {
		String fromClass = attributes.get("fromClass");
		CtClass from = classRegistry.getClass(fromClass);
		ClassMap classMap = new ClassMap();
		classMap.put(fromClass, ctClass.getName());
		for (CtField ctField : from.getDeclaredFields()) {
			if (!ctField.getName().isEmpty() && ctField.getName().charAt(ctField.getName().length() - 1) == '_') {
				ctField.setName(ctField.getName().substring(0, ctField.getName().length() - 1));
			}
			try {
				ctClass.getDeclaredField(ctField.getName());
			} catch (NotFoundException ignored) {
				Log.info("Added " + ctField);
				ctClass.addField(new CtField(ctField, ctClass));
			}
		}
		for (CtMethod newMethod : from.getDeclaredMethods()) {
			if ((newMethod.getName().startsWith("construct") || newMethod.getName().startsWith("staticConstruct"))) {
				try {
					ctClass.getDeclaredMethod(newMethod.getName());
					boolean found = true;
					int i = 0;
					String name = newMethod.getName();
					while (found) {
						i++;
						try {
							ctClass.getDeclaredMethod(name + i);
						} catch (NotFoundException e2) {
							found = false;
						}
					}
					newMethod.setName(name + i);
				} catch (NotFoundException ignored) {
					// Not found - no need to change the name
				}
			}
		}
		for (CtMethod newMethod : from.getDeclaredMethods()) {
			try {
				CtMethod oldMethod = ctClass.getDeclaredMethod(newMethod.getName(), newMethod.getParameterTypes());
				replaceMethod(oldMethod, newMethod);
				Log.info("Replaced " + newMethod);
			} catch (NotFoundException ignored) {
				CtMethod added = CtNewMethod.copy(newMethod, ctClass, classMap);
				Log.info("Adding " + added);
				ctClass.addMethod(added);
				replaceMethod(added, newMethod);
				if (added.getName().startsWith("construct")) {
					try {
						insertSuper(added);
					} catch (CannotCompileException ignore) {
					}
					CtMethod runConstructors;
					try {
						runConstructors = ctClass.getMethod("runConstructors", "()V");
					} catch (NotFoundException e) {
						runConstructors = CtNewMethod.make("public void runConstructors() { }", ctClass);
						ctClass.addMethod(runConstructors);
						try {
							ctClass.getField("isConstructed");
						} catch (NotFoundException ignore) {
							ctClass.addField(new CtField(classRegistry.getClass("boolean"), "isConstructed", ctClass));
						}
						for (CtBehavior ctBehavior : ctClass.getDeclaredConstructors()) {
							ctBehavior.insertAfter("{ if(!this.isConstructed) { this.isConstructed = true; this.runConstructors(); } }");
						}
					}
					try {
						ctClass.getSuperclass().getMethod(added.getName(), "()V");
					} catch (NotFoundException ignore) {
						runConstructors.insertAfter(added.getName() + "();");
					}
				}
				if (added.getName().startsWith("staticConstruct")) {
					ctClass.makeClassInitializer().insertAfter("{ " + added.getName() + "(); }");
				}
			}
		}
		for (CtClass CtInterface : from.getInterfaces()) {
			ctClass.addInterface(CtInterface);
		}
	}

	@Patch (
			requiredAttributes = "field,threadLocalField,type"
	)
	public void threadLocal(CtClass ctClass, Map<String, String> attributes) throws CannotCompileException {
		final String field = attributes.get("field");
		final String threadLocalField = attributes.get("threadLocalField");
		final String type = attributes.get("type");
		String setExpression_ = attributes.get("setExpression");
		final String setExpression = setExpression_ == null ? '(' + type + ") $1" : setExpression_;
		Log.info(field + " -> " + threadLocalField);
		ctClass.instrument(new ExprEditor() {
			@Override
			public void edit(FieldAccess e) throws CannotCompileException {
				if (e.getFieldName().equals(field)) {
					if (e.isReader()) {
						e.replace("{ $_ = (" + type + ") " + threadLocalField + ".get(); }");
					} else if (e.isWriter()) {
						e.replace("{ " + threadLocalField + ".set(" + setExpression + "); }");
					}
				}
			}
		});
	}

	@Patch (
			name = "public",
			emptyConstructor = false
	)
	public void public_(Object o, Map<String, String> attributes) throws NotFoundException {
		String field = attributes.get("field");
		if (field != null) {
			CtClass ctClass = (CtClass) o;
			CtField ctField = ctClass.getDeclaredField(field);
			ctField.setModifiers(Modifier.setPublic(ctField.getModifiers()));
		} else if (o instanceof CtClass) {
			CtClass ctClass = (CtClass) o;
			ctClass.setModifiers(Modifier.setPublic(ctClass.getModifiers()));
			for (CtConstructor ctConstructor : ctClass.getDeclaredConstructors()) {
				public_(ctConstructor, Collections.<String, String>emptyMap());
			}
		} else {
			CtBehavior ctBehavior = (CtBehavior) o;
			ctBehavior.setModifiers(Modifier.setPublic(ctBehavior.getModifiers()));
		}
	}

	@Patch (
			requiredAttributes = "field"
	)
	public void newInitializer(CtClass ctClass, Map<String, String> attributes) throws NotFoundException, CannotCompileException, IOException {
		String field = attributes.get("field");
		String clazz = attributes.get("class");
		String initialise = attributes.get("code");
		String arraySize = attributes.get("arraySize");
		initialise = "{ " + field + " = " + (initialise == null ? ("new " + clazz + (arraySize == null ? "()" : '[' + arraySize + ']')) : initialise) + "; }";
		// Return value ignored - just used to cause a NotFoundException if the field doesn't exist.
		if ((ctClass.getDeclaredField(field).getModifiers() & Modifier.STATIC) == Modifier.STATIC) {
			ctClass.makeClassInitializer().insertAfter(initialise);
		} else {
			CtMethod runConstructors;
			try {
				runConstructors = ctClass.getDeclaredMethod("runConstructors");
			} catch (NotFoundException e) {
				runConstructors = CtNewMethod.make("public void runConstructors() { }", ctClass);
				ctClass.addMethod(runConstructors);
				ctClass.addField(new CtField(classRegistry.getClass("boolean"), "isConstructed", ctClass), CtField.Initializer.constant(false));
				for (CtBehavior ctBehavior : ctClass.getDeclaredConstructors()) {
					ctBehavior.insertAfter("{ if(!this.isConstructed) { this.isConstructed = true; this.runConstructors(); } }");
				}
			}
			runConstructors.insertAfter(initialise);
		}
		if (clazz != null) {
			classRegistry.add(ctClass, clazz);
		}
	}

	@Patch (
			requiredAttributes = "field"
	)
	public void replaceField(CtClass ctClass, Map<String, String> attributes) throws NotFoundException, CannotCompileException, IOException {
		String field = attributes.get("field");
		String clazz = attributes.get("class");
		String type = attributes.get("type");
		if (type == null) {
			type = clazz;
		}
		String initialise = attributes.get("code");
		String arraySize = attributes.get("arraySize");
		initialise = "{ " + field + " = " + (initialise == null ? ("new " + clazz + (arraySize == null ? "()" : '[' + arraySize + ']')) : initialise) + "; }";
		CtField oldField = ctClass.getDeclaredField(field);
		oldField.setName(oldField.getName() + "_rem");
		CtField newField = new CtField(classRegistry.getClass(type), field, ctClass);
		newField.setModifiers(oldField.getModifiers());
		ctClass.addField(newField);
		for (CtConstructor ctConstructor : ctClass.getConstructors()) {
			ctConstructor.insertAfter(initialise);
		}
		if (clazz != null) {
			classRegistry.add(ctClass, clazz);
		}
	}

	@Patch (
			requiredAttributes = "field,class"
	)
	public void newField(CtClass ctClass, Map<String, String> attributes) throws NotFoundException, CannotCompileException, IOException {
		String field = attributes.get("field");
		String clazz = attributes.get("class");
		String initialise = attributes.get("code");
		if (initialise == null) {
			initialise = "new " + clazz + "();";
		}
		try {
			CtField ctField = ctClass.getDeclaredField(field);
			Log.warning(field + " already exists as " + ctField);
			return;
		} catch (NotFoundException ignored) {
		}
		CtClass newType = classRegistry.getClass(clazz);
		CtField ctField = new CtField(newType, field, ctClass);
		if (attributes.get("static") != null) {
			ctField.setModifiers(ctField.getModifiers() | Modifier.STATIC | Modifier.PUBLIC);
		}
		CtField.Initializer initializer = CtField.Initializer.byExpr(initialise);
		ctClass.addField(ctField, initializer);
		classRegistry.add(ctClass, clazz);
	}

	@Patch (
			requiredAttributes = "code"
	)
	public void insertBefore(CtBehavior ctBehavior, Map<String, String> attributes) throws NotFoundException, CannotCompileException, IOException {
		String field = attributes.get("field");
		String code = attributes.get("code");
		if (field != null) {
			code = code.replace("$field", field);
		}
		ctBehavior.insertBefore(code);
	}

	@Patch (
			requiredAttributes = "code"
	)
	public void insertAfter(CtBehavior ctBehavior, Map<String, String> attributes) throws NotFoundException, CannotCompileException, IOException {
		String field = attributes.get("field");
		String code = attributes.get("code");
		if (field != null) {
			code = code.replace("$field", field);
		}
		ctBehavior.insertAfter(code);
	}

	@Patch
	public void insertSuper(CtBehavior ctBehavior) throws CannotCompileException {
		ctBehavior.insertBefore("super." + ctBehavior.getName() + "($$);");
	}

	@Patch (
			requiredAttributes = "field"
	)
	public void lock(CtMethod ctMethod, Map<String, String> attributes) throws NotFoundException, CannotCompileException, IOException {
		String field = attributes.get("field");
		CtClass ctClass = ctMethod.getDeclaringClass();
		CtMethod replacement = CtNewMethod.copy(ctMethod, ctClass, null);
		ctMethod.setName(ctMethod.getName() + "_lock");
		replacement.setBody("{ this." + field + ".lock(); try { return " + (attributes.get("methodcall") == null ? "$proceed" : ctMethod.getName()) + "($$); } finally { this." + field + ".unlock(); } }", "this", ctMethod.getName());
		ctClass.addMethod(replacement);
	}

	@Patch (
			requiredAttributes = "field"
	)
	public void lockMethodCall(final CtBehavior ctBehavior, Map<String, String> attributes) throws CannotCompileException {
		String method_ = attributes.get("method");
		if (method_ == null) {
			method_ = "";
		}
		String className_ = null;
		int dotIndex = method_.indexOf('.');
		if (dotIndex != -1) {
			className_ = method_.substring(0, dotIndex);
			method_ = method_.substring(dotIndex + 1);
		}
		String index_ = attributes.get("index");
		if (index_ == null) {
			index_ = "-1";
		}

		final String method = method_;
		final String className = className_;
		final String field = attributes.get("field");
		final int index = Integer.valueOf(index_);

		ctBehavior.instrument(new ExprEditor() {
			private int currentIndex = 0;

			@Override
			public void edit(MethodCall methodCall) throws CannotCompileException {
				if ((className == null || methodCall.getClassName().equals(className)) && (method.isEmpty() || methodCall.getMethodName().equals(method)) && (index == -1 || currentIndex++ == index)) {
					Log.info("Replaced " + methodCall + " from " + ctBehavior);
					methodCall.replace("{ " + field + ".lock(); try { $_ =  $proceed($$); } finally { " + field + ".unlock(); } }");
				}
			}
		});
	}

	@Patch (
			requiredAttributes = "field"
	)
	public void synchronizeMethodCall(final CtBehavior ctBehavior, Map<String, String> attributes) throws CannotCompileException {
		String method_ = attributes.get("method");
		if (method_ == null) {
			method_ = "";
		}
		String className_ = null;
		int dotIndex = method_.indexOf('.');
		if (dotIndex != -1) {
			className_ = method_.substring(0, dotIndex);
			method_ = method_.substring(dotIndex + 1);
		}
		String index_ = attributes.get("index");
		if (index_ == null) {
			index_ = "-1";
		}

		final String method = method_;
		final String className = className_;
		final String field = attributes.get("field");
		final int index = Integer.valueOf(index_);

		ctBehavior.instrument(new ExprEditor() {
			private int currentIndex = 0;

			@Override
			public void edit(MethodCall methodCall) throws CannotCompileException {
				if ((className == null || methodCall.getClassName().equals(className)) && (method.isEmpty() || methodCall.getMethodName().equals(method)) && (index == -1 || currentIndex++ == index)) {
					Log.info("Replaced " + methodCall + " from " + ctBehavior);
					methodCall.replace("synchronized(" + field + ") { $_ =  $0.$proceed($$); }");
				}
			}
		});
	}

	@Patch (
			emptyConstructor = false
	)
	public void synchronize(Object o, Map<String, String> attributes) throws CannotCompileException {
		if (o instanceof CtMethod) {
			synchronize((CtMethod) o, attributes.get("field"));
		} else {
			boolean static_ = attributes.containsKey("static");
			for (CtMethod ctMethod : ((CtClass) o).getDeclaredMethods()) {
				boolean isStatic = (ctMethod.getModifiers() & Modifier.STATIC) == Modifier.STATIC;
				if (isStatic == static_) {
					synchronize(ctMethod, attributes.get("field"));
				}
			}
		}
	}

	private void synchronize(CtMethod ctMethod, String field) throws CannotCompileException {
		if (field == null) {
			int currentModifiers = ctMethod.getModifiers();
			if (Modifier.isSynchronized(currentModifiers)) {
				Log.warning("Method: " + ctMethod.getLongName() + " is already synchronized");
			} else {
				ctMethod.setModifiers(currentModifiers | Modifier.SYNCHRONIZED);
			}
		} else {
			CtClass ctClass = ctMethod.getDeclaringClass();
			CtMethod replacement = CtNewMethod.copy(ctMethod, ctClass, null);
			int i = 0;
			try {
				//noinspection InfiniteLoopStatement
				for (; true; i++) {
					ctClass.getDeclaredMethod(ctMethod.getName() + "_sync" + i);
				}
			} catch (NotFoundException ignored) {
			}
			ctMethod.setName(ctMethod.getName() + "_sync" + i);
			replacement.setBody("synchronized(" + field + ") { return " + ctMethod.getName() + "($$); }");
			ctClass.addMethod(replacement);
		}
	}

	@Patch
	public void ignoreExceptions(CtMethod ctMethod, Map<String, String> attributes) throws CannotCompileException, NotFoundException {
		String returnCode = attributes.get("code");
		if (returnCode == null) {
			returnCode = "return;";
		}
		String exceptionType = attributes.get("type");
		if (exceptionType == null) {
			exceptionType = "java.lang.Exception";
		}
		Log.info("Ignoring " + exceptionType + " in " + ctMethod + ", returning with " + returnCode);
		ctMethod.addCatch("{ " + returnCode + '}', classRegistry.getClass(exceptionType));
	}

	@Patch (
			requiredAttributes = "class"
	)
	public void addClass(CtClass ctClass, Map<String, String> attributes) throws IOException, CannotCompileException, NotFoundException {
		classRegistry.add(ctClass, attributes.get("class"));
	}

	@Patch
	public void replaceInstantiations(CtBehavior object, Map<String, String> attributes) {
		// TODO: Implement this, change some newInitializer patches to use this
	}

	public void replaceInstantiationsImplementation(CtBehavior ctBehavior, final Map<String, List<String>> replacementClasses) throws CannotCompileException {
		// TODO: Learn to use ASM, javassist isn't nice for some things. :(
		final Map<Integer, String> newExprType = new HashMap<Integer, String>();
		ctBehavior.instrument(new ExprEditor() {
			NewExpr lastNewExpr;
			int newPos = 0;

			@Override
			public void edit(NewExpr e) {
				lastNewExpr = null;
				newPos++;
				if (replacementClasses.containsKey(e.getClassName())) {
					lastNewExpr = e;
				}
			}

			@Override
			public void edit(FieldAccess e) {
				NewExpr myLastNewExpr = lastNewExpr;
				lastNewExpr = null;
				if (myLastNewExpr != null) {
					Log.fine('(' + myLastNewExpr.getSignature() + ") " + myLastNewExpr.getClassName() + " at " + myLastNewExpr.getFileName() + ':' + myLastNewExpr.getLineNumber() + ':' + newPos);
					newExprType.put(newPos, classSignatureToName(e.getSignature()));
				}
			}

			@Override
			public void edit(MethodCall e) {
				lastNewExpr = null;
			}

			@Override
			public void edit(NewArray e) {
				lastNewExpr = null;
			}

			@Override
			public void edit(Cast e) {
				lastNewExpr = null;
			}

			@Override
			public void edit(Instanceof e) {
				lastNewExpr = null;
			}

			@Override
			public void edit(Handler e) {
				lastNewExpr = null;
			}

			@Override
			public void edit(ConstructorCall e) {
				lastNewExpr = null;
			}
		});
		ctBehavior.instrument(new ExprEditor() {
			int newPos = 0;

			@Override
			public void edit(NewExpr e) throws CannotCompileException {
				newPos++;
				try {
					Log.fine(e.getFileName() + ':' + e.getLineNumber() + ", pos: " + newPos);
					if (newExprType.containsKey(newPos)) {
						String replacementType = null, assignedType = newExprType.get(newPos);
						Log.fine(assignedType + " at " + e.getFileName() + ':' + e.getLineNumber());
						Class<?> assignTo = Class.forName(assignedType);
						for (String replacementClass : replacementClasses.get(e.getClassName())) {
							if (assignTo.isAssignableFrom(Class.forName(replacementClass))) {
								replacementType = replacementClass;
								break;
							}
						}
						if (replacementType == null) {
							return;
						}
						String block = "{$_=new " + replacementType + "($$);}";
						Log.fine("Replaced with " + block + ", " + replacementType.length() + ':' + assignedType.length());
						e.replace(block);
					}
				} catch (ClassNotFoundException el) {
					Log.severe("Could not replace instantiation, class not found.", el);
				}
			}
		});
	}

	private static String classSignatureToName(String signature) {
		//noinspection HardcodedFileSeparator
		return signature.substring(1, signature.length() - 1).replace("/", ".");
	}
}
