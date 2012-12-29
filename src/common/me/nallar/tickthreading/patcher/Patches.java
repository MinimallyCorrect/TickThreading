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

public class Patches {
	private final ClassRegistry classRegistry;

	public Patches(ClassRegistry classRegistry) {
		this.classRegistry = classRegistry;
	}

	@Patch (
			requiredAttributes = "fromClass"
	)
	public void replaceMethod(CtMethod method, Map<String, String> attributes) throws NotFoundException, CannotCompileException, BadBytecode {
		String fromClass = attributes.get("fromClass");
		String fromMethod = attributes.get("fromMethod");
		CtMethod replacingMethod = fromMethod == null ?
				classRegistry.getClass(fromClass).getDeclaredMethod(method.getName(), method.getParameterTypes())
				: MethodDescription.fromString(fromClass, fromMethod).inClass(classRegistry.getClass(fromClass));
		Log.info("Replacing " + new MethodDescription(method).getMCPName() + " with " + new MethodDescription(replacingMethod).getMCPName());
		ClassMap classMap = new ClassMap();
		classMap.put(fromClass, method.getDeclaringClass().getName());
		method.setBody(replacingMethod, classMap);
		method.getMethodInfo().rebuildStackMap(classRegistry.classes);
		method.getMethodInfo().rebuildStackMapForME(classRegistry.classes);
	}

	@Patch (
			name = "public"
	)
	public void makePublic(CtMethod ctMethod) {
		ctMethod.setModifiers(Modifier.setPublic(ctMethod.getModifiers()));
	}

	@Patch (
			requiredAttributes = "field,class"
	)
	public void newInitializer(CtClass ctClass, Map<String, String> attributes) throws NotFoundException, CannotCompileException, IOException {
		String field = attributes.get("field");
		String clazz = attributes.get("class");
		String initialise = "{ " + field + " = new " + clazz + "(); }";
		ctClass.getDeclaredField(field);
		// Return value ignored - just used to cause a NotFoundException if the field doestn't exist.
		for (CtConstructor ctConstructor : ctClass.getConstructors()) {
			ctConstructor.insertAfter(initialise);
		}
		classRegistry.add(ctClass, clazz);
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
		CtField.Initializer initializer = CtField.Initializer.byExpr(initialise);
		ctClass.addField(ctField, initializer);
		classRegistry.add(ctClass, clazz);
	}

	@Patch (
			requiredAttributes = "code"
	)
	public void insertBefore(CtMethod ctMethod, Map<String, String> attributes) throws NotFoundException, CannotCompileException, IOException {
		String clazz = attributes.get("class");
		String code = attributes.get("code");
		if (clazz != null) {
			classRegistry.add(ctMethod, clazz);
		}
		ctMethod.insertBefore(code);
	}

	@Patch (
			requiredAttributes = "field"
	)
	public void lock(CtMethod ctMethod, Map<String, String> attributes) throws NotFoundException, CannotCompileException, IOException {
		String field = attributes.get("field");
		CtClass ctClass = ctMethod.getDeclaringClass();
		CtMethod replacement = CtNewMethod.copy(ctMethod, ctClass, null);
		ctMethod.setName(ctMethod.getName() + "_nolock");
		replacement.setBody("{ this." + field + ".lock(); try { return " + ctMethod.getName() + "($$); } finally { this." + field + ".unlock(); } }");
		ctClass.addMethod(replacement);
	}

	@Patch
	public void synchronize(CtMethod method) {
		int currentModifiers = method.getModifiers();
		if (Modifier.isSynchronized(currentModifiers)) {
			Log.warning("Method: " + method.getLongName() + " is already synchronized");
		} else {
			method.setModifiers(currentModifiers | Modifier.SYNCHRONIZED);
		}
	}

	@Patch
	public void ignoreExceptions(CtMethod ctMethod, Map<String, String> attributes) throws CannotCompileException, NotFoundException {
		String returnCode = attributes.get("code");
		if (returnCode == null) {
			returnCode = "return;";
		}
		ctMethod.addCatch("{ " + returnCode + "}", classRegistry.getClass("java.lang.Exception"));
	}

	@Patch (
			requiredAttributes = "code"
	)
	public void replaceBody(CtMethod ctMethod, Map<String, String> attributes) throws CannotCompileException, BadBytecode {
		String body = attributes.get("code");
		ctMethod.setBody(body);
	}

	@Patch
	public void replaceInstantiations(Object object, Map<String, String> attributes) {
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
					Log.fine("(" + myLastNewExpr.getSignature() + ") " + myLastNewExpr.getClassName() + " at " + myLastNewExpr.getFileName() + ":" + myLastNewExpr.getLineNumber() + ":" + newPos);
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
					Log.fine(e.getFileName() + ":" + e.getLineNumber() + ", pos: " + newPos);
					if (newExprType.containsKey(newPos)) {
						String replacementType = null, assignedType = newExprType.get(newPos);
						Log.fine(assignedType + " at " + e.getFileName() + ":" + e.getLineNumber());
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
						Log.fine("Replaced with " + block + ", " + replacementType.length() + ":" + assignedType.length());
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
