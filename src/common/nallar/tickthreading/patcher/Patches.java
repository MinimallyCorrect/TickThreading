package nallar.tickthreading.patcher;

import com.google.common.base.Splitter;
import javassist.CannotCompileException;
import javassist.ClassMap;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.CtPrimitiveType;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.ClassFile;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Descriptor;
import javassist.bytecode.DuplicateMemberException;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Mnemonic;
import javassist.bytecode.Opcode;
import javassist.expr.Cast;
import javassist.expr.ConstructorCall;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.Handler;
import javassist.expr.Instanceof;
import javassist.expr.MethodCall;
import javassist.expr.NewArray;
import javassist.expr.NewExpr;
import nallar.insecurity.ThisIsNotAnError;
import nallar.tickthreading.Log;
import nallar.tickthreading.mappings.MethodDescription;
import nallar.tickthreading.util.CollectionsUtil;
import nallar.tickthreading.util.ReflectUtil;
import nallar.unsafe.UnsafeUtil;
import org.omg.CORBA.IntHolder;

import java.io.*;
import java.util.*;

@SuppressWarnings({"MethodMayBeStatic", "ObjectAllocationInLoop"})
public class Patches {
	private final ClassPool classPool;

	public Patches(ClassPool classPool) {
		this.classPool = classPool;
	}

	@SuppressWarnings("EmptyMethod")
	@Patch
	public void markDirty(CtClass ctClass) {
		// A NOOP patch to make sure META-INF is removed
	}

	@Patch
	public void remove(CtMethod ctMethod) {
		ctMethod.setName(ctMethod.getName() + "_rem");
	}

	@Patch(
			requiredAttributes = "code"
	)
	public void newMethod(CtClass ctClass, Map<String, String> attributes) throws CannotCompileException {
		try {
			ctClass.addMethod(CtNewMethod.make(attributes.get("code"), ctClass));
		} catch (DuplicateMemberException e) {
			if (!attributes.containsKey("ignoreDuplicate")) {
				throw e;
			}
		}
	}

	@Patch(
			requiredAttributes = "type,field"
	)
	public void changeFieldType(final CtClass ctClass, Map<String, String> attributes) throws CannotCompileException, NotFoundException {
		final String field = attributes.get("field");
		CtField oldField = ctClass.getDeclaredField(field);
		oldField.setName(field + "_old");
		String newType = attributes.get("type");
		CtField ctField = new CtField(classPool.get(newType), field, ctClass);
		ctField.setModifiers(oldField.getModifiers());
		ctClass.addField(ctField);
		Set<CtBehavior> allBehaviours = new HashSet<CtBehavior>();
		Collections.addAll(allBehaviours, ctClass.getDeclaredConstructors());
		Collections.addAll(allBehaviours, ctClass.getDeclaredMethods());
		CtBehavior initialiser = ctClass.getClassInitializer();
		if (initialiser != null) {
			allBehaviours.add(initialiser);
		}
		final boolean remove = attributes.containsKey("remove");
		for (CtBehavior ctBehavior : allBehaviours) {
			ctBehavior.instrument(new ExprEditor() {
				@Override
				public void edit(FieldAccess fieldAccess) throws CannotCompileException {
					if (fieldAccess.getClassName().equals(ctClass.getName()) && fieldAccess.getFieldName().equals(field)) {
						if (fieldAccess.isReader()) {
							if (remove) {
								fieldAccess.replace("$_ = null;");
							} else {
								fieldAccess.replace("$_ = $0." + field + ';');
							}
						} else if (fieldAccess.isWriter()) {
							if (remove) {
								fieldAccess.replace("$_ = null;");
							} else {
								fieldAccess.replace("$0." + field + " = $1;");
							}
						}
					}
				}
			});
		}
	}

	@Patch(
			requiredAttributes = "from,to"
	)
	public void replaceConstants(CtClass ctClass, Map<String, String> attributes) {
		String from = attributes.get("from");
		String to = attributes.get("to");
		ConstPool constPool = ctClass.getClassFile().getConstPool();
		for (int i = 0; true; i++) {
			String utf8Info;
			try {
				utf8Info = constPool.getUtf8Info(i);
			} catch (ClassCastException ignored) {
				continue;
			} catch (NullPointerException e) {
				break;
			}
			if (utf8Info.equals(from)) {
				Object o = ReflectUtil.call(constPool, "getItem", i);
				try {
					ReflectUtil.getField(Class.forName("javassist.bytecode.ConstPool$Utf8Info"), "string").set(o, to);
				} catch (Exception e) {
					Log.severe("Couldn't set constant value", e);
				}
			}
		}
	}

	@Patch(
			requiredAttributes = "field",
			emptyConstructor = false
	)
	public void replaceInitializer(final Object o, Map<String, String> attributes) throws CannotCompileException, NotFoundException {
		final String field = attributes.get("field");
		CtClass ctClass = o instanceof CtClass ? (CtClass) o : null;
		CtBehavior ctBehavior = null;
		if (ctClass == null) {
			ctBehavior = (CtBehavior) o;
			ctClass = ctBehavior.getDeclaringClass();
		}
		String ctFieldClass = attributes.get("fieldClass");
		if (ctFieldClass != null) {
			if (ctClass == o) {
				Log.info("Must set methods to run on if using fieldClass.");
				return;
			}
			ctClass = classPool.get(ctFieldClass);
		}
		final CtField ctField = ctClass.getDeclaredField(field);
		String code = attributes.get("code");
		String clazz = attributes.get("class");
		if (code == null && clazz == null) {
			throw new NullPointerException("Must give code or class");
		}
		final String newInitialiser = code == null ? "$_ = new " + clazz + "();" : code;
		Set<CtBehavior> allBehaviours = new HashSet<CtBehavior>();
		if (ctBehavior == null) {
			Collections.addAll(allBehaviours, ctClass.getDeclaredConstructors());
			CtBehavior initialiser = ctClass.getClassInitializer();
			if (initialiser != null) {
				allBehaviours.add(initialiser);
			}
		} else {
			allBehaviours.add(ctBehavior);
		}
		final IntHolder replaced = new IntHolder();
		for (CtBehavior ctBehavior_ : allBehaviours) {
			final Map<Integer, String> newExprType = new HashMap<Integer, String>();
			ctBehavior_.instrument(new ExprEditor() {
				NewExpr lastNewExpr;
				int newPos = 0;

				@Override
				public void edit(NewExpr e) {
					lastNewExpr = null;
					newPos++;
					try {
						if (classPool.get(e.getClassName()).subtypeOf(ctField.getType())) {
							lastNewExpr = e;
						}
					} catch (NotFoundException ignored) {
					}
				}

				@Override
				public void edit(FieldAccess e) {
					NewExpr myLastNewExpr = lastNewExpr;
					lastNewExpr = null;
					if (myLastNewExpr != null && e.getFieldName().equals(field)) {
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
			ctBehavior_.instrument(new ExprEditor() {
				int newPos = 0;

				@Override
				public void edit(NewExpr e) throws CannotCompileException {
					newPos++;
					if (newExprType.containsKey(newPos)) {
						String assignedType = newExprType.get(newPos);
						String block = '{' + newInitialiser + '}';
						Log.fine(assignedType + " at " + e.getFileName() + ':' + e.getLineNumber() + " replaced with " + block);
						e.replace(block);
						replaced.value++;
					}
				}
			});
		}
		if (replaced.value == 0 && !attributes.containsKey("silent")) {
			Log.severe("No field initializers found for replacement");
		}
	}

	@Patch(
			requiredAttributes = "oldClass,newClass",
			emptyConstructor = false
	)
	public void replaceNew(Object o, Map<String, String> attributes) throws CannotCompileException, NotFoundException {
		final String type = attributes.get("oldClass");
		final String code = attributes.get("code");
		final String clazz = attributes.get("newClass");
		if (code == null && clazz == null) {
			throw new NullPointerException("Must give code or class");
		}
		final String newInitialiser = code == null ? "$_ = new " + clazz + "();" : code;
		final Set<CtBehavior> allBehaviours = new HashSet<CtBehavior>();
		if (o instanceof CtClass) {
			CtClass ctClass = (CtClass) o;
			Collections.addAll(allBehaviours, ctClass.getDeclaredConstructors());
			final CtBehavior initialiser = ctClass.getClassInitializer();
			if (initialiser != null) {
				allBehaviours.add(initialiser);
			}
		} else {
			allBehaviours.add((CtBehavior) o);
		}
		final IntHolder done = new IntHolder();
		for (CtBehavior ctBehavior : allBehaviours) {
			ctBehavior.instrument(new ExprEditor() {
				@Override
				public void edit(NewExpr e) throws CannotCompileException {
					if (e.getClassName().equals(type)) {
						e.replace(newInitialiser);
						done.value++;
					}
				}
			});
		}
		if (done.value == 0) {
			Log.severe("No new expressions found for replacement.");
		}
	}

	@Patch
	public void profile(CtMethod ctMethod, Map<String, String> attributes) throws CannotCompileException, NotFoundException {
		CtClass ctClass = ctMethod.getDeclaringClass();
		CtMethod replacement = CtNewMethod.copy(ctMethod, ctClass, null);
		int i = 0;

		String deobf = attributes.get("deobf");
		if (deobf == null) {
			deobf = ctMethod.getDeclaringClass().getName() + '/' + ctMethod.getName();
		}
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
			replacement.setBody("{ boolean timings = nallar.tickthreading.minecraft.profiling.Timings.enabled; long st = 0; if (timings) { st = System.nanoTime(); } " + ctMethod.getName() + "($$); if (timings) { nallar.tickthreading.minecraft.profiling.Timings.record(\"" + deobf + "\", System.nanoTime() - st); } }");
		} else {
			replacement.setBody("{ boolean timings = nallar.tickthreading.minecraft.profiling.Timings.enabled; long st = 0; if (timings) { st = System.nanoTime(); } try { return " + ctMethod.getName() + "($$); } finally { if (timings) { nallar.tickthreading.minecraft.profiling.Timings.record(\"" + deobf + "\", System.nanoTime() - st); } } }");
		}
		ctClass.addMethod(replacement);
	}

	@Patch(
			name = "volatile",
			requiredAttributes = "field"
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

	@Patch(
			requiredAttributes = "field"
	)
	public void unvolatile(CtClass ctClass, Map<String, String> attributes) throws NotFoundException {
		String field = attributes.get("field");
		if (field == null) {
			for (CtField ctField : ctClass.getDeclaredFields()) {
				if (ctField.getType().isPrimitive()) {
					ctField.setModifiers(ctField.getModifiers() & ~Modifier.VOLATILE);
				}
			}
		} else {
			CtField ctField = ctClass.getDeclaredField(field);
			ctField.setModifiers(ctField.getModifiers() & ~Modifier.VOLATILE);
		}
	}

	@Patch(
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

	@Patch(
			requiredAttributes = "class"
	)
	public CtClass replace(CtClass clazz, Map<String, String> attributes) throws NotFoundException, CannotCompileException, BadBytecode {
		String fromClass = attributes.get("class");
		String oldName = clazz.getName();
		clazz.setName(oldName + "_old");
		CtClass newClass = classPool.get(fromClass);
		ClassFile classFile = newClass.getClassFile2();
		if (classFile.getSuperclass().equals(oldName)) {
			classFile.setSuperclass(null);
			for (CtConstructor ctBehavior : newClass.getDeclaredConstructors()) {
				javassist.bytecode.MethodInfo methodInfo = ctBehavior.getMethodInfo2();
				CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
				if (codeAttribute != null) {
					CodeIterator iterator = codeAttribute.iterator();
					int pos = iterator.skipSuperConstructor();
					if (pos >= 0) {
						int mref = iterator.u16bitAt(pos + 1);
						ConstPool constPool = codeAttribute.getConstPool();
						iterator.write16bit(constPool.addMethodrefInfo(constPool.addClassInfo("java.lang.Object"), "<init>", "()V"), pos + 1);
						String desc = constPool.getMethodrefType(mref);
						int num = Descriptor.numOfParameters(desc) + 1;
						pos = iterator.insertGapAt(pos, num, false).position;
						Descriptor.Iterator i$ = new Descriptor.Iterator(desc);
						for (i$.next(); i$.isParameter(); i$.next()) {
							iterator.writeByte(i$.is2byte() ? Opcode.POP2 : Opcode.POP, pos++);
						}
					}
					methodInfo.rebuildStackMapIf6(newClass.getClassPool(), newClass.getClassFile2());
				}
			}
		}
		newClass.setName(oldName);
		newClass.setModifiers(newClass.getModifiers() & ~Modifier.ABSTRACT);
		return newClass;
	}

	@Patch
	public void replaceMethod(CtBehavior method, Map<String, String> attributes) throws NotFoundException, CannotCompileException, BadBytecode {
		String fromClass = attributes.get("fromClass");
		String code = attributes.get("code");
		String field = attributes.get("field");
		if (field != null) {
			code = code.replace("$field", field);
		}
		if (fromClass != null) {
			String fromMethod = attributes.get("fromMethod");
			CtMethod replacingMethod = fromMethod == null ?
					classPool.get(fromClass).getDeclaredMethod(method.getName(), method.getParameterTypes())
					: MethodDescription.fromString(fromClass, fromMethod).inClass(classPool.get(fromClass));
			replaceMethod((CtMethod) method, replacingMethod);
		} else if (code != null) {
			method.setBody(code);
		} else {
			Log.severe("Missing required attributes for replaceMethod");
		}
	}

	private void replaceMethod(CtMethod oldMethod, CtMethod newMethod) throws CannotCompileException, BadBytecode {
		ClassMap classMap = new ClassMap();
		classMap.put(newMethod.getDeclaringClass().getName(), oldMethod.getDeclaringClass().getName());
		oldMethod.setBody(newMethod, classMap);
		oldMethod.getMethodInfo().rebuildStackMap(classPool);
		oldMethod.getMethodInfo().rebuildStackMapForME(classPool);
	}

	@Patch(
			requiredAttributes = "field"
	)
	public void replaceFieldUsage(final CtBehavior ctBehavior, Map<String, String> attributes) throws CannotCompileException {
		final String field = attributes.get("field");
		final String readCode = attributes.get("readCode");
		final String writeCode = attributes.get("writeCode");
		final String clazz = attributes.get("class");
		final boolean removeAfter = attributes.containsKey("removeAfter");
		if (readCode == null && writeCode == null) {
			throw new IllegalArgumentException("readCode or writeCode must be set");
		}
		final IntHolder replaced = new IntHolder();
		try {
			ctBehavior.instrument(new ExprEditor() {
				@Override
				public void edit(FieldAccess fieldAccess) throws CannotCompileException {
					String fieldName;
					try {
						fieldName = fieldAccess.getFieldName();
					} catch (ClassCastException e) {
						Log.warning("Can't examine field access at " + fieldAccess.getLineNumber() + " which is a r: " + fieldAccess.isReader() + " w: " + fieldAccess.isWriter());
						return;
					}
					if ((clazz == null || fieldAccess.getClassName().equals(clazz)) && fieldName.equals(field)) {
						if (removeAfter) {
							try {
								removeAfterIndex(ctBehavior, fieldAccess.indexOfBytecode());
							} catch (BadBytecode badBytecode) {
								throw UnsafeUtil.throwIgnoreChecked(badBytecode);
							}
							throw new ThisIsNotAnError();
						}
						if (fieldAccess.isWriter() && writeCode != null) {
							fieldAccess.replace(writeCode);
						} else if (fieldAccess.isReader() && readCode != null) {
							fieldAccess.replace(readCode);
							Log.info("Replaced in " + ctBehavior + ' ' + fieldName + " read with " + readCode);
						}
						replaced.value++;
					}
				}
			});
		} catch (ThisIsNotAnError ignored) {
		}
		if (replaced.value == 0 && !attributes.containsKey("silent")) {
			Log.severe("Didn't replace any field accesses.");
		}
	}

	@Patch
	public void replaceMethodCall(final CtBehavior ctBehavior, Map<String, String> attributes) throws CannotCompileException {
		String method_ = attributes.get("method");
		if (method_ == null) {
			method_ = "";
		}
		String className_ = null;
		int dotIndex = method_.lastIndexOf('.');
		if (dotIndex != -1) {
			className_ = method_.substring(0, dotIndex);
			method_ = method_.substring(dotIndex + 1);
		}
		if ("self".equals(className_)) {
			className_ = ctBehavior.getDeclaringClass().getName();
		}
		String index_ = attributes.get("index");
		if (index_ == null) {
			index_ = "-1";
		}

		final String method = method_;
		final String className = className_;
		final String newMethod = attributes.get("newMethod");
		String code_ = attributes.get("code");
		if (code_ == null) {
			code_ = "$_ = $0." + newMethod + "($$);";
		}
		final String code = code_;
		final IntHolder replaced = new IntHolder();
		final int index = Integer.valueOf(index_);
		final boolean removeAfter = attributes.containsKey("removeAfter");

		try {
			ctBehavior.instrument(new ExprEditor() {
				private int currentIndex = 0;

				@Override
				public void edit(MethodCall methodCall) throws CannotCompileException {
					if ((className == null || methodCall.getClassName().equals(className)) && (method.isEmpty() || methodCall.getMethodName().equals(method)) && (index == -1 || currentIndex++ == index)) {
						if (newMethod != null) {
							try {
								CtMethod oldMethod = methodCall.getMethod();
								oldMethod.getDeclaringClass().getDeclaredMethod(newMethod, oldMethod.getParameterTypes());
							} catch (NotFoundException e) {
								return;
							}
						}
						replaced.value++;
						Log.info("Replaced call to " + methodCall.getClassName() + '/' + methodCall.getMethodName() + " in " + ctBehavior.getLongName());
						if (removeAfter) {
							try {
								removeAfterIndex(ctBehavior, methodCall.indexOfBytecode());
							} catch (BadBytecode badBytecode) {
								throw UnsafeUtil.throwIgnoreChecked(badBytecode);
							}
							throw new ThisIsNotAnError();
						}
						methodCall.replace(code);
					}
				}
			});
		} catch (ThisIsNotAnError ignored) {
		}
		if (replaced.value == 0 && !attributes.containsKey("silent")) {
			Log.warning("Didn't find any method calls to replace");
		}
	}

	@Patch(
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
			parameterList.add(classPool.get(parameterName));
		}
		CtMethod newMethod = new CtMethod(classPool.get(return_), name, parameterList.toArray(new CtClass[parameterList.size()]), ctClass);
		newMethod.setBody('{' + code + '}');
		ctClass.addMethod(newMethod);
	}

	@Patch(
			requiredAttributes = "opcode"
	)
	public void removeUntilOpcode(CtBehavior ctBehavior, Map<String, String> attributes) throws BadBytecode {
		int opcode = Arrays.asList(Mnemonic.OPCODE).indexOf(attributes.get("opcode").toLowerCase());
		String removeIndexString = attributes.get("index");
		int removeIndex = removeIndexString == null ? -1 : Integer.parseInt(removeIndexString);
		int currentIndex = 0;
		Log.info("Removing until " + attributes.get("opcode") + ':' + opcode + " at " + removeIndex);
		CtClass ctClass = ctBehavior.getDeclaringClass();
		MethodInfo methodInfo = ctBehavior.getMethodInfo();
		CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
		if (codeAttribute != null) {
			CodeIterator iterator = codeAttribute.iterator();
			while (iterator.hasNext()) {
				int index = iterator.next();
				int op = iterator.byteAt(index);
				if (op == opcode && (removeIndex < 0 || removeIndex == ++currentIndex)) {
					for (int i = 0; i <= index; i++) {
						iterator.writeByte(Opcode.NOP, i);
					}
					Log.info("Removed until " + index);
					if (removeIndex == -2) {
						break;
					}
				}
			}
			methodInfo.rebuildStackMapIf6(ctClass.getClassPool(), ctClass.getClassFile());
		}
	}

	private void removeAfterIndex(CtBehavior ctBehavior, int index) throws BadBytecode {
		Log.info("Removed after " + index + " in " + ctBehavior.getLongName());
		CtClass ctClass = ctBehavior.getDeclaringClass();
		MethodInfo methodInfo = ctBehavior.getMethodInfo2();
		CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
		if (codeAttribute != null) {
			CodeIterator iterator = codeAttribute.iterator();
			int i, length = iterator.getCodeLength() - 1;
			for (i = index; i < length; i++) {
				iterator.writeByte(Opcode.NOP, i);
			}
			iterator.writeByte(Opcode.RETURN, i);
			methodInfo.rebuildStackMapIf6(ctClass.getClassPool(), ctClass.getClassFile2());
		}
	}

	@Patch(
			requiredAttributes = "fromClass"
	)
	public void addAll(CtClass ctClass, Map<String, String> attributes) throws NotFoundException, CannotCompileException, BadBytecode {
		String fromClass = attributes.get("fromClass");
		CtClass from = classPool.get(fromClass);
		ClassMap classMap = new ClassMap();
		classMap.put(fromClass, ctClass.getName());
		for (CtField ctField : from.getDeclaredFields()) {
			if (!ctField.getName().isEmpty() && ctField.getName().charAt(ctField.getName().length() - 1) == '_') {
				ctField.setName(ctField.getName().substring(0, ctField.getName().length() - 1));
			}
			CtClass expectedType = ctField.getType();
			boolean expectStatic = (ctField.getModifiers() & Modifier.STATIC) == Modifier.STATIC;
			String fieldName = ctField.getName();
			try {
				CtClass type = ctClass.getDeclaredField(fieldName).getType();
				if (type != expectedType) {
					Log.warning("Field " + fieldName + " already exists, but as a different type. Exists: " + type.getName() + ", expected: " + expectedType.getName());
					ctClass.getDeclaredField(fieldName).setType(expectedType);
				}
				boolean isStatic = (ctField.getModifiers() & Modifier.STATIC) == Modifier.STATIC;
				if (isStatic != expectStatic) {
					Log.severe("Can't add field " + fieldName + " as it already exists, but it is static: " + isStatic + " and we expected: " + expectStatic);
				}
			} catch (NotFoundException ignored) {
				ctClass.addField(new CtField(ctField, ctClass));
			}
			if (expectStatic) {
				CtBehavior initializer = ctClass.getClassInitializer();
				if (initializer != null) {
					removeInitializers(initializer, ctField);
				}
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
				if (Modifier.isSynchronized(newMethod.getModifiers())) {
					oldMethod.setModifiers(oldMethod.getModifiers() | Modifier.SYNCHRONIZED);
				}
			} catch (NotFoundException ignored) {
				CtMethod added = CtNewMethod.copy(newMethod, ctClass, classMap);
				ctClass.addMethod(added);
				MethodInfo addedMethodInfo = added.getMethodInfo2();
				String addedDescriptor = addedMethodInfo.getDescriptor();
				String newDescriptor = newMethod.getMethodInfo2().getDescriptor();
				if (!newDescriptor.equals(addedDescriptor)) {
					addedMethodInfo.setDescriptor(newDescriptor);
				}
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
							ctClass.addField(new CtField(classPool.get("boolean"), "isConstructed", ctClass));
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
		CtConstructor initializer = from.getClassInitializer();
		if (initializer != null) {
			ctClass.addMethod(initializer.toMethod("patchStaticInitializer", ctClass));
			ctClass.makeClassInitializer().insertAfter("patchStaticInitializer();");
		}
	}

	@Patch(
			requiredAttributes = "field",
			emptyConstructor = false
	)
	public void removeInitializers(Object o, Map<String, String> attributes) throws NotFoundException, CannotCompileException {
		if (o instanceof CtClass) {
			final CtField ctField = ((CtClass) o).getDeclaredField(attributes.get("field"));
			for (CtBehavior ctBehavior : ((CtClass) o).getDeclaredBehaviors()) {
				removeInitializers(ctBehavior, ctField);
			}
		} else {
			removeInitializers((CtBehavior) o, ((CtBehavior) o).getDeclaringClass().getDeclaredField(attributes.get("field")));
		}
	}

	private void removeInitializers(CtBehavior ctBehavior, final CtField ctField) throws CannotCompileException, NotFoundException {
		replaceInitializer(ctBehavior, CollectionsUtil.<String, String>map(
				"field", ctField.getName(),
				"code", "{ $_ = null; }",
				"silent", "true"));
		replaceFieldUsage(ctBehavior, CollectionsUtil.<String, String>map(
				"field", ctField.getName(),
				"writeCode", "{ }",
				"readCode", "{ $_ = null; }",
				"silent", "true"));
	}

	@Patch(
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

	@Patch(
			requiredAttributes = "field,threadLocalField"
	)
	public void threadLocalBoolean(CtClass ctClass, Map<String, String> attributes) throws CannotCompileException {
		final String field = attributes.get("field");
		final String threadLocalField = attributes.get("threadLocalField");
		Log.info(field + " -> " + threadLocalField);
		for (CtConstructor ctConstructor : ctClass.getDeclaredConstructors()) {
			ctConstructor.instrument(new ExprEditor() {
				@Override
				public void edit(FieldAccess e) throws CannotCompileException {
					if (e.getFieldName().equals(field)) {
						if (e.isWriter()) {
							e.replace("{ }");
						}
					}
				}
			});
		}
		ctClass.instrument(new ExprEditor() {
			@Override
			public void edit(FieldAccess e) throws CannotCompileException {
				if (e.getFieldName().equals(field)) {
					if (e.isReader()) {
						e.replace("{ $_ = ((Boolean) " + threadLocalField + ".get()).booleanValue(); }");
					} else if (e.isWriter()) {
						e.replace("{ " + threadLocalField + ".set(Boolean.valueOf($1)); }");
					}
				}
			}
		});
	}

	@Patch(
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
			List<Object> toPublic = new ArrayList<Object>();
			if (attributes.containsKey("all")) {
				Collections.addAll(toPublic, ctClass.getDeclaredFields());
				Collections.addAll(toPublic, ctClass.getDeclaredBehaviors());
			} else {
				Collections.addAll(toPublic, ctClass.getDeclaredConstructors());
			}
			for (Object o_ : toPublic) {
				public_(o_, Collections.<String, String>emptyMap());
			}
		} else if (o instanceof CtField) {
			CtField ctField = (CtField) o;
			ctField.setModifiers(Modifier.setPublic(ctField.getModifiers()));
		} else {
			CtBehavior ctBehavior = (CtBehavior) o;
			ctBehavior.setModifiers(Modifier.setPublic(ctBehavior.getModifiers()));
		}
	}

	@Patch(
			emptyConstructor = false
	)
	public void noFinal(Object o, Map<String, String> attributes) throws NotFoundException {
		String field = attributes.get("field");
		if (field != null) {
			CtClass ctClass = (CtClass) o;
			CtField ctField = ctClass.getDeclaredField(field);
			ctField.setModifiers(Modifier.clear(ctField.getModifiers(), Modifier.FINAL));
		} else if (o instanceof CtClass) {
			CtClass ctClass = (CtClass) o;
			ctClass.setModifiers(Modifier.setPublic(ctClass.getModifiers()));
			for (CtConstructor ctConstructor : ctClass.getDeclaredConstructors()) {
				public_(ctConstructor, Collections.<String, String>emptyMap());
			}
		} else {
			CtBehavior ctBehavior = (CtBehavior) o;
			ctBehavior.setModifiers(Modifier.clear(ctBehavior.getModifiers(), Modifier.FINAL));
		}
	}

	@Patch(
			requiredAttributes = "field"
	)
	public void newInitializer(CtClass ctClass, Map<String, String> attributes) throws NotFoundException, CannotCompileException, IOException {
		String field = attributes.get("field");
		String clazz = attributes.get("class");
		String initialise = attributes.get("code");
		String arraySize = attributes.get("arraySize");
		initialise = "{ " + field + " = " + (initialise == null ? ("new " + clazz + (arraySize == null ? "()" : '[' + arraySize + ']')) : initialise) + "; }";
		if ((ctClass.getDeclaredField(field).getModifiers() & Modifier.STATIC) == Modifier.STATIC) {
			ctClass.makeClassInitializer().insertAfter(initialise);
		} else {
			CtMethod runConstructors;
			try {
				runConstructors = ctClass.getDeclaredMethod("runConstructors");
			} catch (NotFoundException e) {
				runConstructors = CtNewMethod.make("public void runConstructors() { }", ctClass);
				ctClass.addMethod(runConstructors);
				ctClass.addField(new CtField(classPool.get("boolean"), "isConstructed", ctClass), CtField.Initializer.constant(false));
				for (CtBehavior ctBehavior : ctClass.getDeclaredConstructors()) {
					ctBehavior.insertAfter("{ if(!this.isConstructed) { this.isConstructed = true; this.runConstructors(); } }");
				}
			}
			runConstructors.insertAfter(initialise);
		}
	}

	@Patch(
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
		CtField newField = new CtField(classPool.get(type), field, ctClass);
		newField.setModifiers(oldField.getModifiers());
		ctClass.addField(newField);
		for (CtConstructor ctConstructor : ctClass.getConstructors()) {
			ctConstructor.insertAfter(initialise);
		}
	}

	@Patch(
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
		CtClass newType = classPool.get(clazz);
		CtField ctField = new CtField(newType, field, ctClass);
		if (attributes.get("static") != null) {
			ctField.setModifiers(ctField.getModifiers() | Modifier.STATIC);
		}
		ctField.setModifiers(Modifier.setPublic(ctField.getModifiers()));
		if ("none".equalsIgnoreCase(initialise)) {
			ctClass.addField(ctField);
		} else {
			CtField.Initializer initializer = CtField.Initializer.byExpr(initialise);
			ctClass.addField(ctField, initializer);
		}
	}

	@Patch(
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

	@Patch(
			requiredAttributes = "code"
	)
	public void insertAfter(CtBehavior ctBehavior, Map<String, String> attributes) throws NotFoundException, CannotCompileException, IOException {
		String field = attributes.get("field");
		String code = attributes.get("code");
		if (field != null) {
			code = code.replace("$field", field);
		}
		ctBehavior.insertAfter(code, attributes.containsKey("finally"));
	}

	@Patch
	public void insertSuper(CtBehavior ctBehavior) throws CannotCompileException {
		ctBehavior.insertBefore("super." + ctBehavior.getName() + "($$);");
	}

	@Patch(
			requiredAttributes = "field"
	)
	public void lock(CtMethod ctMethod, Map<String, String> attributes) throws NotFoundException, CannotCompileException, IOException {
		String field = attributes.get("field");
		ctMethod.insertBefore("this." + field + ".lock();");
		ctMethod.insertAfter("this." + field + ".unlock();", true);
	}

	@Patch(
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

	@Patch(
			requiredAttributes = "name,interface"
	)
	public void renameInterfaceMethod(CtMethod ctMethod, Map<String, String> attributes) throws CannotCompileException, NotFoundException {
		CtClass currentClass = ctMethod.getDeclaringClass().getSuperclass();
		final List<String> superClassNames = new ArrayList<String>();
		boolean contains = false;
		do {
			if (!contains) {
				for (CtClass ctClass : currentClass.getInterfaces()) {
					if (ctClass.getName().equals(attributes.get("interface"))) {
						contains = true;
					}
				}
			}
			currentClass = currentClass.getSuperclass();
			superClassNames.add(currentClass.getName());
		} while (currentClass != classPool.get("java.lang.Object"));
		final String newName = attributes.get("name");
		if (!contains) {
			ctMethod.setName(newName);
			return;
		}
		final String methodName = ctMethod.getName();
		ctMethod.instrument(new ExprEditor() {
			@Override
			public void edit(MethodCall methodCall) throws CannotCompileException {
				if (methodName.equals(methodCall.getMethodName()) && superClassNames.contains(methodCall.getClassName())) {
					methodCall.replace("$_ = super." + newName + "($$);");
				}
			}
		});
		ctMethod.setName(newName);
	}

	@Patch(
			requiredAttributes = "name"
	)
	public void rename(CtMethod ctMethod, Map<String, String> attributes) {
		ctMethod.setName(attributes.get("name"));
	}

	@Patch(
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

	@Patch
	public void unsynchronize(CtBehavior ctBehavior) {
		ctBehavior.setModifiers(ctBehavior.getModifiers() & ~Modifier.SYNCHRONIZED);
	}

	@Patch(
			emptyConstructor = false
	)
	public void synchronize(Object o, Map<String, String> attributes) throws CannotCompileException {
		//noinspection StatementWithEmptyBody
		if (o instanceof CtConstructor) {
		} else if (o instanceof CtMethod) {
			synchronize((CtMethod) o, attributes.get("field"));
		} else {
			int synchronized_ = 0;
			boolean static_ = attributes.containsKey("static");
			for (CtMethod ctMethod : ((CtClass) o).getDeclaredMethods()) {
				boolean isStatic = (ctMethod.getModifiers() & Modifier.STATIC) == Modifier.STATIC;
				if (isStatic == static_) {
					synchronize(ctMethod, attributes.get("field"));
					synchronized_++;
				}
			}
			if (synchronized_ == 0) {
				Log.severe("Nothing synchronized - did you forget the 'static' attribute?");
			} else {
				Log.info("Synchronized " + synchronized_ + " methods.");
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
			List<AttributeInfo> attributes = ctMethod.getMethodInfo().getAttributes();
			Iterator<AttributeInfo> attributeInfoIterator = attributes.iterator();
			while (attributeInfoIterator.hasNext()) {
				AttributeInfo attributeInfo = attributeInfoIterator.next();
				if (attributeInfo instanceof AnnotationsAttribute) {
					attributeInfoIterator.remove();
					replacement.getMethodInfo().addAttribute(attributeInfo);
				}
			}
			replacement.setBody("synchronized(" + field + ") { return " + ctMethod.getName() + "($$); }");
			replacement.setModifiers(replacement.getModifiers() & ~Modifier.SYNCHRONIZED);
			ctClass.addMethod(replacement);
		}
	}

	@Patch(
			requiredAttributes = "field"
	)
	public void synchronizeNotNull(CtMethod ctMethod, Map<String, String> attributes) throws CannotCompileException {
		String field = attributes.get("field");
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
		List<AttributeInfo> annotations = ctMethod.getMethodInfo().getAttributes();
		Iterator<AttributeInfo> attributeInfoIterator = annotations.iterator();
		while (attributeInfoIterator.hasNext()) {
			AttributeInfo attributeInfo = attributeInfoIterator.next();
			if (attributeInfo instanceof AnnotationsAttribute) {
				attributeInfoIterator.remove();
				replacement.getMethodInfo().addAttribute(attributeInfo);
			}
		}
		replacement.setBody("Object sync = + " + field + "; if (sync == null) { return " + ctMethod.getName() + "($$); } else { synchronized(sync) { return " + ctMethod.getName() + "($$); }");
		ctClass.addMethod(replacement);
	}

	@Patch
	public void ignoreExceptions(CtMethod ctMethod, Map<String, String> attributes) throws CannotCompileException, NotFoundException {
		String returnCode = attributes.get("code");
		if (returnCode == null) {
			returnCode = "return;";
		}
		String exceptionType = attributes.get("type");
		if (exceptionType == null) {
			exceptionType = "java.lang.Throwable";
		}
		Log.info("Ignoring " + exceptionType + " in " + ctMethod + ", returning with " + returnCode);
		ctMethod.addCatch("{ " + returnCode + '}', classPool.get(exceptionType));
	}

	@Patch
	public void lockToSynchronized(CtBehavior ctBehavior, Map<String, String> attributes) throws BadBytecode {
		CtClass ctClass = ctBehavior.getDeclaringClass();
		MethodInfo methodInfo = ctBehavior.getMethodInfo();
		CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
		CodeIterator iterator = codeAttribute.iterator();
		ConstPool constPool = codeAttribute.getConstPool();
		int done = 0;
		while (iterator.hasNext()) {
			int pos = iterator.next();
			int op = iterator.byteAt(pos);
			if (op == Opcode.INVOKEINTERFACE) {
				int mref = iterator.u16bitAt(pos + 1);
				if (constPool.getInterfaceMethodrefClassName(mref).endsWith("Lock")) {
					String name = constPool.getInterfaceMethodrefName(mref);
					boolean remove = false;
					if ("lock".equals(name)) {
						remove = true;
						iterator.writeByte(Opcode.MONITORENTER, pos);
					} else if ("unlock".equals(name)) {
						remove = true;
						iterator.writeByte(Opcode.MONITOREXIT, pos);
					}
					if (remove) {
						done++;
						iterator.writeByte(Opcode.NOP, pos + 1);
						iterator.writeByte(Opcode.NOP, pos + 2);
						iterator.writeByte(Opcode.NOP, pos + 3);
						iterator.writeByte(Opcode.NOP, pos + 4);
					}
				}
			} else if (op == Opcode.INVOKEVIRTUAL) {
				int mref = iterator.u16bitAt(pos + 1);
				if (constPool.getMethodrefClassName(mref).endsWith("NativeMutex")) {
					String name = constPool.getMethodrefName(mref);
					boolean remove = false;
					if ("lock".equals(name)) {
						remove = true;
						iterator.writeByte(Opcode.MONITORENTER, pos);
					} else if ("unlock".equals(name)) {
						remove = true;
						iterator.writeByte(Opcode.MONITOREXIT, pos);
					}
					if (remove) {
						done++;
						iterator.writeByte(Opcode.NOP, pos + 1);
						iterator.writeByte(Opcode.NOP, pos + 2);
					}
				}
			}
		}
		methodInfo.rebuildStackMapIf6(ctClass.getClassPool(), ctClass.getClassFile2());
		Log.fine("Replaced " + done + " lock/unlock calls.");
	}

	@Patch(
			requiredAttributes = "field"
	)
	public void removeField(CtClass ctClass, Map<String, String> attributes) throws NotFoundException {
		ctClass.removeField(ctClass.getDeclaredField(attributes.get("field")));
	}

	@Patch(
			requiredAttributes = "field"
	)
	public void removeFieldAndInitializers(CtClass ctClass, Map<String, String> attributes) throws CannotCompileException, NotFoundException {
		CtField ctField;
		try {
			ctField = ctClass.getDeclaredField(attributes.get("field"));
		} catch (NotFoundException e) {
			if (!attributes.containsKey("silent")) {
				Log.severe("Couldn't find field " + attributes.get("field"));
			}
			return;
		}
		for (CtBehavior ctBehavior : ctClass.getDeclaredConstructors()) {
			removeInitializers(ctBehavior, ctField);
		}
		CtBehavior ctBehavior = ctClass.getClassInitializer();
		if (ctBehavior != null) {
			removeInitializers(ctBehavior, ctField);
		}
		ctClass.removeField(ctField);
	}

	@Patch
	public void removeMethod(CtMethod ctMethod, Map<String, String> attributes) throws NotFoundException {
		ctMethod.getDeclaringClass().removeMethod(ctMethod);
	}

	private static String classSignatureToName(String signature) {
		//noinspection HardcodedFileSeparator
		return signature.substring(1, signature.length() - 1).replace("/", ".");
	}

	public static void findUnusedFields(CtClass ctClass) {
		final Set<String> readFields = new HashSet<String>();
		final Set<String> writtenFields = new HashSet<String>();
		try {
			ctClass.instrument(new ExprEditor() {
				@Override
				public void edit(FieldAccess fieldAccess) {
					if (fieldAccess.isReader()) {
						readFields.add(fieldAccess.getFieldName());
					} else if (fieldAccess.isWriter()) {
						writtenFields.add(fieldAccess.getFieldName());
					}
				}
			});
			for (CtField ctField : ctClass.getDeclaredFields()) {
				String fieldName = ctField.getName();
				if (fieldName.length() <= 2) {
					continue;
				}
				if (Modifier.isPrivate(ctField.getModifiers())) {
					boolean written = writtenFields.contains(fieldName);
					boolean read = readFields.contains(fieldName);
					if (read && written) {
						continue;
					}
					Log.fine("Field " + fieldName + " in " + ctClass.getName() + " is read: " + read + ", written: " + written);
					if (!written && !read) {
						ctClass.removeField(ctField);
					}
				}
			}
		} catch (Throwable t) {
			throw UnsafeUtil.throwIgnoreChecked(t);
		}
	}
}
