package me.nallar.tickthreading.patcher;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javassist.CannotCompileException;
import javassist.ClassMap;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
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
			requiredAttributes = "class"
	)
	public CtClass replace(CtClass clazz, Map<String, String> attributes) throws NotFoundException, CannotCompileException {
		Log.info("Replacing " + clazz.getName() + " with " + attributes.get("class"));
		String oldName = clazz.getName();
		clazz.setName(oldName + "_old");
		CtClass newClass = classRegistry.getClass(attributes.get("class"));
		newClass.getClassFile2().setSuperclass(null);
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
			requiredAttributes = "code,method"
	)
	public void replaceMethodCall(final CtBehavior ctBehavior, Map<String, String> attributes) throws CannotCompileException {
		String method_ = attributes.get("method");
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

		ctBehavior.instrument(new ExprEditor() {
			private int currentIndex = 0;

			@Override
			public void edit(MethodCall methodCall) throws CannotCompileException {
				if ((className == null || methodCall.getClassName().equals(className)) && methodCall.getMethodName().equals(method) && (index == -1 || currentIndex++ == index)) {
					Log.info("Replaced " + methodCall + " from " + ctBehavior);
					methodCall.replace(code);
				}
			}
		});
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
			Log.info("Added " + ctField);
			ctClass.addField(new CtField(ctField, ctClass));
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
				if ("construct".equals(added.getName())) {
					ctClass.getClassInitializer().insertAfter("this.construct();");
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
	public void makePublic(Object o, Map<String, String> attributes) throws NotFoundException {
		String field = attributes.get("field");
		if (field == null) {
			CtBehavior ctBehavior = (CtBehavior) o;
			ctBehavior.setModifiers(Modifier.setPublic(ctBehavior.getModifiers()));
		} else {
			CtClass ctClass = (CtClass) o;
			CtField ctField = ctClass.getDeclaredField(field);
			ctField.setModifiers(Modifier.setPublic(ctField.getModifiers()));
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
		ctClass.getDeclaredField(field);
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
		ctMethod.setName(ctMethod.getName() + "_nolock");
		replacement.setBody("{ this." + field + ".lock(); try { return " + (attributes.get("methodcall") == null ? "$proceed" : ctMethod.getName()) + "($$); } finally { this." + field + ".unlock(); } }", "this", ctMethod.getName());
		ctClass.addMethod(replacement);
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
			ctMethod.setName(ctMethod.getName() + "_nosynchronize");
			replacement.setBody("synchronized(this." + field + ") { return this." + ctMethod.getName() + "($$); }");
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
