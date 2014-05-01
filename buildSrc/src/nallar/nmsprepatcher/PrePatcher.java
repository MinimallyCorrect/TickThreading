package nallar.nmsprepatcher;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The prepatcher adds method declarations in superclasses,
// so javac can compile the patch classes if they need to use a method/field they
// add on an instance other than this
class PrePatcher {
	private static final Logger log = Logger.getLogger("PatchLogger");
	private static final Pattern privatePattern = Pattern.compile("^(\\s+?)private", Pattern.MULTILINE);
	private static final Pattern extendsPattern = Pattern.compile("^public.*?\\s+?extends\\s+?([\\S^<]+?)(?:<(\\S+)>)?[\\s]+?(?:implements [^}]+?)?\\{", Pattern.MULTILINE);
	private static final Pattern declareMethodPattern = Pattern.compile("@Declare\\s+?(public\\s+?(?:(?:synchronized|static) )*(\\S*?)?\\s+?(\\S*?)\\s*?\\S+?\\s*?\\([^\\{]*\\)\\s*?\\{)", Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern declareFieldPattern = Pattern.compile("@Declare\\s+?(public [^;\r\n]+?)_?( = [^;\r\n]+?)?;", Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern packageFieldPattern = Pattern.compile("\n    ? ?([^ ]+  ? ?[^ ]+);");
	private static final Pattern innerClassPattern = Pattern.compile("[^\n]public (?:static )?class ([^ \n]+)[ \n]", Pattern.MULTILINE);
	private static final Pattern importPattern = Pattern.compile("\nimport ([^;]+?);", Pattern.MULTILINE | Pattern.DOTALL);
	private static final Pattern exposeInnerPattern = Pattern.compile("\n@ExposeInner\\(\"([^\"]+)\"\\)", Pattern.MULTILINE | Pattern.DOTALL);
	private static final Splitter spaceSplitter = Splitter.on(' ').omitEmptyStrings();
	private static final Splitter commaSplitter = Splitter.on(',').omitEmptyStrings().trimResults();
	private static final Map<String, PatchInfo> patchClasses = new HashMap<String, PatchInfo>();

	public static void loadPatches(File patchDirectory) {
		recursiveSearch(patchDirectory);
	}

	private static void recursiveSearch(File patchDirectory) {
		for (File file : patchDirectory.listFiles()) {
			if (!file.getName().equals("annotation") && file.isDirectory()) {
				recursiveSearch(file);
				continue;
			}
			if (!file.getName().endsWith(".java")) {
				continue;
			}
			addPatches(file);
		}
	}

	//private static final Pattern methodInfoPattern = Pattern.compile("(?:(public|private|protected) )?(static )?(?:([^ ]+?) )([^\\( ]+?) ?\\((.*?)\\) ?\\{", Pattern.DOTALL);
	private static final Pattern methodInfoPattern = Pattern.compile("^(.+) ?\\(([^\\(]*)\\) ?\\{", Pattern.DOTALL);

	// TODO - clean up this method. It works, but it's hardly pretty...
	private static void addPatches(File file) {
		String contents = readFile(file);
		if (contents == null) {
			log.log(Level.SEVERE, "Failed to read " + file);
			return;
		}
		Matcher extendsMatcher = extendsPattern.matcher(contents);
		if (!extendsMatcher.find()) {
			if (contents.contains(" extends")) {
				log.warning("Didn't match extends matcher for " + file);
			}
			return;
		}
		String shortClassName = extendsMatcher.group(1);
		String className = null;
		Matcher importMatcher = importPattern.matcher(contents);
		List<String> imports = new ArrayList<String>();
		while (importMatcher.find()) {
			imports.add(importMatcher.group(1));
		}
		for (String import_ : imports) {
			if (import_.endsWith('.' + shortClassName)) {
				className = import_;
			}
		}
		if (className == null) {
			log.warning("Unable to find class " + shortClassName + " for " + file);
			return;
		}
		Matcher exposeInnerMatcher = exposeInnerPattern.matcher(contents);
		while (exposeInnerMatcher.find()) {
			log.severe("Inner class name: " + className + "$" + exposeInnerMatcher.group(1));
			getOrMakePatchInfo(className + "$" + exposeInnerMatcher.group(1), shortClassName + "$" + exposeInnerMatcher.group(1)).makePublic = true;
		}
		PatchInfo patchInfo = getOrMakePatchInfo(className, shortClassName);
		Matcher matcher = declareMethodPattern.matcher(contents);
		while (matcher.find()) {
			Matcher methodInfoMatcher = methodInfoPattern.matcher(matcher.group(1));

			if (!methodInfoMatcher.find()) {
				log.warning("Failed to match method info matcher to method declaration " + matcher.group(1));
				continue;
			}


			MethodInfo methodInfo = new MethodInfo();
			patchInfo.methods.add(methodInfo);

			String accessAndNameString = methodInfoMatcher.group(1).replace(", ", ","); // Workaround for multiple argument generics
			String paramString = methodInfoMatcher.group(2);

			for (String parameter : commaSplitter.split(paramString)) {
				Iterator<String> iterator = spaceSplitter.split(parameter).iterator();
				String parameterType = null;
				while (parameterType == null) {
					parameterType = iterator.next();
					if (parameterType.equals("final")) {
						parameterType = null;
					}
				}
				methodInfo.parameterTypes.add(new Type(parameterType, imports));
			}

			LinkedList<String> accessAndNames = Lists.newLinkedList(spaceSplitter.split(accessAndNameString));

			methodInfo.name = accessAndNames.removeLast();
			String rawType = accessAndNames.removeLast();

			while (!accessAndNames.isEmpty()) {
				String thing = accessAndNames.removeLast();
				if (thing.equals("static")) {
					methodInfo.static_ = true;
				} else if (thing.equals("synchronized")) {
					methodInfo.synchronized_ = true;
				} else if (thing.equals("final")) {
					methodInfo.final_ = true;
				} else if (thing.startsWith("<")) {
					methodInfo.genericType = thing;
				} else {
					if (methodInfo.access != null) {
						log.severe("overwriting method access from " + methodInfo.access + " -> " + thing + " in " + matcher.group(1));
					}
					methodInfo.access = thing;
				}
			}

			String ret = "null";
			if ("static".equals(rawType)) {
				rawType = matcher.group(3);
			}
			methodInfo.returnType = new Type(rawType, imports);
			if ("boolean".equals(rawType)) {
				ret = "false";
			} else if ("void".equals(rawType)) {
				ret = "";
			} else if ("long".equals(rawType)) {
				ret = "0L";
			} else if ("int".equals(rawType)) {
				ret = "0";
			} else if ("float".equals(rawType)) {
				ret = "0f";
			} else if ("double".equals(rawType)) {
				ret = "0.0";
			}
			methodInfo.javaCode = matcher.group(1) + "return " + ret + ";}";
		}
		Matcher fieldMatcher = declareFieldPattern.matcher(contents);
		while (fieldMatcher.find()) {
			String var = fieldMatcher.group(1).replace(", ", ","); // Workaround for multiple argument generics
			FieldInfo fieldInfo = new FieldInfo();
			patchInfo.fields.add(fieldInfo);
			LinkedList<String> typeAndName = Lists.newLinkedList(spaceSplitter.split(var));

			fieldInfo.name = typeAndName.removeLast();
			fieldInfo.type = new Type(typeAndName.removeLast(), imports);

			while (!typeAndName.isEmpty()) {
				String thing = typeAndName.removeLast();
				if (thing.equals("static")) {
					fieldInfo.static_ = true;
				} else if (thing.equals("volatile")) {
					fieldInfo.volatile_ = true;
				} else if (thing.equals("final")) {
					fieldInfo.final_ = true;
				} else {
					if (fieldInfo.access != null) {
						log.severe("overwriting field access from " + fieldInfo.access + " -> " + thing + " in " + var);
					}
					fieldInfo.access = thing;
				}
			}
			fieldInfo.javaCode = var + ';';
		}
		if (contents.contains("\n@Public")) {
			patchInfo.makePublic = true;
		}
	}

	private static PatchInfo getOrMakePatchInfo(String className, String shortClassName) {
		PatchInfo patchInfo = patchClasses.get(className);
		if (patchInfo == null) {
			patchInfo = new PatchInfo();
			patchClasses.put(className, patchInfo);
		}
		patchInfo.shortClassName = shortClassName;
		return patchInfo;
	}

	private static int accessStringToInt(String access) {
		int a = 0;
		if (access.isEmpty()) {
			// package-local
		} else if (access.equals("public")) {
			a |= Opcodes.ACC_PUBLIC;
		} else if (access.equals("protected")) {
			a |= Opcodes.ACC_PROTECTED;
		} else if (access.equals("private")) {
			a |= Opcodes.ACC_PRIVATE;
		} else {
			log.severe("Unknown access string " + access);
		}
		return a;
	}

	private static class Type {
		public final String clazz;
		public final int arrayDimensions;
		public boolean noClass = false;
		public final List<Type> generics = new ArrayList<Type>();

		public Type(String raw, List<String> imports) {
			String clazz;
			int arrayLevels = 0;
			while (raw.length() - (arrayLevels * 2) - 2 > 0) {
				int startPos = raw.length() - 2 - arrayLevels * 2;
				if (!raw.substring(startPos, startPos + 2).equals("[]")) {
					break;
				}
				arrayLevels++;
			}
			raw = raw.substring(0, raw.length() - arrayLevels * 2); // THE MORE YOU KNOW: String.substring(begin) special cases begin == 0.
			arrayDimensions = arrayLevels;
			if (raw.contains("<")) {
				String genericRaw = raw.substring(raw.indexOf("<") + 1, raw.length() - 1);
				clazz = raw.substring(0, raw.indexOf("<"));
				if (clazz.isEmpty()) {
					clazz = "java.lang.Object"; // For example, <T> methodName(Class<T> parameter) -> <T> as return type -> erases to object
					noClass = true;
				}
				for (String genericRawSplit : commaSplitter.split(genericRaw)) {
					generics.add(new Type(genericRawSplit, imports));
				}
			} else {
				clazz = raw;
			}
			this.clazz = fullyQualifiedName(clazz, imports);
		}

		private static String[] searchPackages = {
				"java.lang",
				"java.util",
				"java.io",
		};

		private static String fullyQualifiedName(String original, Collection<String> imports) {
			int dots = CharMatcher.is('.').countIn(original);
			if (imports == null || dots > 1) {
				return original;
			}
			if (dots == 1) {
				String start = original.substring(0, original.indexOf('.'));
				String end = original.substring(original.indexOf('.') + 1);
				String qualifiedStart = fullyQualifiedName(start, imports);
				if (!qualifiedStart.equals(start)) {
					return qualifiedStart + '$' + end;
				}
				return original;
			}
			for (String className : imports) {
				String shortClassName = className;
				shortClassName = shortClassName.substring(shortClassName.lastIndexOf('.') + 1);
				if (shortClassName.equals(original)) {
					return className;
				}
			}
			for (String package_ : searchPackages) {
				String packagedName = package_ + "." + original;
				try {
					Class.forName(packagedName, false, PrePatcher.class.getClassLoader());
					return packagedName;
				} catch (ClassNotFoundException ignored) {
				}
			}
			if (primitiveTypeToDescriptor(original) == null) {
				log.severe("Failed to find fully qualified name for '" + original + "'.");
			}
			return original;
		}

		private static String primitiveTypeToDescriptor(String primitive) {
			if (primitive.equals("byte")) {
				return "B";
			} else if (primitive.equals("char")) {
				return "C";
			} else if (primitive.equals("double")) {
				return "D";
			} else if (primitive.equals("float")) {
				return "F";
			} else if (primitive.equals("int")) {
				return "I";
			} else if (primitive.equals("long")) {
				return "J";
			} else if (primitive.equals("short")) {
				return "S";
			} else if (primitive.equals("void")) {
				return "V";
			} else if (primitive.equals("boolean")) {
				return "Z";
			}
			return null;
		}

		public String arrayDimensionsString() {
			return Strings.repeat("[", arrayDimensions);
		}

		public String toString() {
			return arrayDimensionsString() + clazz + (generics.isEmpty() ? "" : '<' + generics.toString() + '>');
		}

		private String genericSignatureIfNeeded(boolean useGenerics) {
			if (generics.isEmpty() || !useGenerics) {
				return "";
			}
			StringBuilder sb = new StringBuilder();
			sb.append('<');
			for (Type generic : generics) {
				sb.append(generic.signature());
			}
			sb.append('>');
			return sb.toString();
		}

		private String javaString(boolean useGenerics) {
			if (clazz.contains("<") || clazz.contains(">")) {
				log.severe("Invalid Type " + this + ", contains broken generics info.");
			} else if (clazz.contains("[") || clazz.contains("]")) {
				log.severe("Invalid Type " + this + ", contains broken array info.");
			} else if (clazz.contains(".")) {
				return arrayDimensionsString() + "L" + clazz.replace(".", "/") + genericSignatureIfNeeded(useGenerics) + ";";
			}
			String primitiveType = primitiveTypeToDescriptor(clazz);
			if (primitiveType != null) {
				return arrayDimensionsString() + primitiveType;
			}
			log.warning("Either generic type or unrecognized type: " + this.toString());
			return arrayDimensionsString() + "T" + clazz + ";";
		}

		public String descriptor() {
			return javaString(false);
		}

		public String signature() {
			return javaString(true);
		}
	}

	private static class MethodInfo {
		public String name;
		public List<Type> parameterTypes = new ArrayList<Type>();
		public Type returnType;
		public String access;
		public boolean static_;
		public boolean synchronized_;
		public boolean final_;
		public String javaCode;

		private static final Joiner parameterJoiner = Joiner.on(", ");
		public String genericType;

		public String toString() {
			return "method: " + access + ' ' + (static_ ? "static " : "") + (final_ ? "final " : "") + (synchronized_ ? "synchronized " : "") + returnType + ' ' + name + " (" + parameterJoiner.join(parameterTypes) + ')';
		}

		public int accessAsInt() {
			int accessInt = 0;
			if (static_) {
				accessInt |= Opcodes.ACC_STATIC;
			}
			if (synchronized_) {
				accessInt |= Opcodes.ACC_SYNCHRONIZED;
			}
			if (final_) {
				accessInt |= Opcodes.ACC_FINAL;
			}
			accessInt |= accessStringToInt(access);
			return accessInt;
		}

		public String descriptor() {
			StringBuilder sb = new StringBuilder();
			sb
					.append('(');
			for (Type type : parameterTypes) {
				sb.append(type.descriptor());
			}
			sb
					.append(')')
					.append(returnType.descriptor());
			return sb.toString();
		}

		public String signature() {
			StringBuilder sb = new StringBuilder();
			String genericType = this.genericType;
			if (genericType != null) {
				sb.append('<');
				genericType = genericType.substring(1, genericType.length() - 1);
				for (String genericTypePart : commaSplitter.split(genericType)) {
					if (genericTypePart.contains(" extends ")) {
						log.severe("Extends unsupported, TODO implement - in " + this.genericType); // TODO
					}
					sb
							.append(genericTypePart)
							.append(":Ljava/lang/Object;");
				}
				sb.append('>');
			}
			sb
					.append('(');
			for (Type type : parameterTypes) {
				sb.append(type.signature());
			}
			sb
					.append(')')
					.append(returnType.signature());
			return sb.toString();
		}
	}

	private static class FieldInfo {
		public String name;
		public Type type;
		public String access;
		public boolean static_;
		public boolean volatile_;
		public boolean final_;
		public String javaCode;

		public String toString() {
			return "field: " + access + ' ' + (static_ ? "static " : "") + (volatile_ ? "volatile " : "") + type + ' ' + name;
		}

		public int accessAsInt() {
			int accessInt = 0;
			if (static_) {
				accessInt |= Opcodes.ACC_STATIC;
			}
			if (volatile_) {
				accessInt |= Opcodes.ACC_VOLATILE;
			}
			if (final_) {
				accessInt |= Opcodes.ACC_FINAL;
			}
			accessInt |= accessStringToInt(access);
			return accessInt;
		}
	}

	private static class PatchInfo {
		List<MethodInfo> methods = new ArrayList<MethodInfo>();
		List<FieldInfo> fields = new ArrayList<FieldInfo>();
		boolean makePublic = false;
		String shortClassName;
	}

	private static PatchInfo patchForClass(String className) {
		return patchClasses.get(className.replace("/", ".").replace(".java", "").replace(".class", ""));
	}

	public static String patchSource(String inputSource, String inputClassName) {
		PatchInfo patchInfo = patchForClass(inputClassName);
		if (patchInfo == null) {
			return inputSource;
		}
		inputSource = inputSource.trim().replace("\t", "    ");
		String shortClassName = patchInfo.shortClassName;
		StringBuilder sourceBuilder = new StringBuilder(inputSource.substring(0, inputSource.lastIndexOf('}')))
				.append("\n// TT Patch Declarations\n");
		for (MethodInfo methodInfo : patchInfo.methods) {
			if (sourceBuilder.indexOf(methodInfo.javaCode) == -1) {
				sourceBuilder.append(methodInfo.javaCode).append('\n');
			}
		}
		for (FieldInfo FieldInfo : patchInfo.fields) {
			if (sourceBuilder.indexOf(FieldInfo.javaCode) == -1) {
				sourceBuilder.append(FieldInfo.javaCode).append('\n');
			}
		}
		sourceBuilder.append("\n}");
		inputSource = sourceBuilder.toString();
		/*Matcher genericMatcher = genericMethodPattern.matcher(contents);
		while (genericMatcher.find()) {
			String original = genericMatcher.group(1);
			String withoutGenerics = original.replace(' ' + generic + ' ', " Object ");
			int index = inputSource.indexOf(withoutGenerics);
			if (index == -1) {
				continue;
			}
			int endIndex = inputSource.indexOf("\n    }", index);
			String body = inputSource.substring(index, endIndex);
			inputSource = inputSource.replace(body, body.replace(withoutGenerics, original).replace("return ", "return (" + generic + ") "));
		}*/
		inputSource = inputSource.replace("\nfinal ", " ");
		inputSource = inputSource.replace(" final ", " ");
		inputSource = inputSource.replace("\nclass", "\npublic class");
		inputSource = inputSource.replace("\n    " + shortClassName, "\n    public " + shortClassName);
		inputSource = inputSource.replace("\n    protected " + shortClassName, "\n    public " + shortClassName);
		inputSource = inputSource.replace("private class", "public class");
		inputSource = inputSource.replace("protected class", "public class");
		inputSource = privatePattern.matcher(inputSource).replaceAll("$1protected");
		if (patchInfo.makePublic) {
			inputSource = inputSource.replace("protected ", "public ");
		}
		Matcher packageMatcher = packageFieldPattern.matcher(inputSource);
		StringBuffer sb = new StringBuffer();
		while (packageMatcher.find()) {
			packageMatcher.appendReplacement(sb, "\n    public " + packageMatcher.group(1) + ';');
		}
		packageMatcher.appendTail(sb);
		inputSource = sb.toString();
		Matcher innerClassMatcher = innerClassPattern.matcher(inputSource);
		while (innerClassMatcher.find()) {
			String name = innerClassMatcher.group(1);
			inputSource = inputSource.replace("    " + name + '(', "    public " + name + '(');
		}
		return inputSource.replace("    ", "\t");
	}

	private static boolean hasFlag(int access, int flag) {
		return (access & flag) != 0;
	}

	private static int replaceFlag(int in, int from, int to) {
		if ((in & from) != 0) {
			in &= ~from;
			in |= to;
		}
		return in;
	}

	private static int makeAccess(int access, boolean makePublic) {
		access = makeAtLeastProtected(access);
		if (makePublic) {
			access = replaceFlag(access, Opcodes.ACC_PROTECTED, Opcodes.ACC_PUBLIC);
		}
		return access;
	}

	/**
	 * Changes access flags to be protected, unless already public.
	 *
	 * @return
	 */
	private static int makeAtLeastProtected(int access) {
		if (hasFlag(access, Opcodes.ACC_PUBLIC) || hasFlag(access, Opcodes.ACC_PROTECTED)) {
			// already protected or public
			return access;
		}
		if (hasFlag(access, Opcodes.ACC_PRIVATE)) {
			// private -> protected
			return replaceFlag(access, Opcodes.ACC_PRIVATE, Opcodes.ACC_PROTECTED);
		}
		// not public, protected or private so must be package-local
		// change to public - protected doesn't include package-local.
		return access | Opcodes.ACC_PUBLIC;
	}

	public static byte[] patchCode(byte[] inputCode, String inputClassName) {
		PatchInfo patchInfo = patchForClass(inputClassName);
		if (inputClassName.contains("Ticket")) {
			log.severe("ticket found: " + inputClassName);
			log.severe(String.valueOf(patchInfo));
		}
		if (patchInfo == null) {
			return inputCode;
		}
		ClassReader classReader = new ClassReader(inputCode);
		ClassNode classNode = new ClassNode();
		classReader.accept(classNode, 0);
		classNode.access = classNode.access & ~Opcodes.ACC_FINAL;
		classNode.access = makeAccess(classNode.access, true);
		for (FieldNode fieldNode : (Iterable<FieldNode>) classNode.fields) {
			fieldNode.access = fieldNode.access & ~Opcodes.ACC_FINAL;
			fieldNode.access = makeAccess(fieldNode.access, patchInfo.makePublic);
		}
		for (MethodNode methodNode : (Iterable<MethodNode>) classNode.methods) {
			methodNode.access = methodNode.access & ~Opcodes.ACC_FINAL;
			methodNode.access = makeAccess(methodNode.access, patchInfo.makePublic);
		}
		for (FieldInfo fieldInfo : patchInfo.fields) {
			classNode.fields.add(new FieldNode(makeAccess(fieldInfo.accessAsInt() & ~Opcodes.ACC_FINAL, patchInfo.makePublic), fieldInfo.name, fieldInfo.type.descriptor(), fieldInfo.type.signature(), null));
		}
		for (MethodInfo methodInfo : patchInfo.methods) {
			classNode.methods.add(new MethodNode(makeAccess(methodInfo.accessAsInt() & ~Opcodes.ACC_FINAL, patchInfo.makePublic), methodInfo.name, methodInfo.descriptor(), methodInfo.signature(), null));
		}
		ClassWriter classWriter = new ClassWriter(classReader, 0);
		classNode.accept(classWriter);
		return classWriter.toByteArray();
	}

	private static String readFile(File file) {
		Scanner fileReader = null;
		try {
			fileReader = new Scanner(file, "UTF-8").useDelimiter("\\A");
			return fileReader.next().replace("\r\n", "\n");
		} catch (FileNotFoundException ignored) {
		} finally {
			if (fileReader != null) {
				fileReader.close();
			}
		}
		return null;
	}
}
