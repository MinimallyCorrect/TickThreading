package nallar.nmsprepatcher;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.util.regex.*;

// The prepatcher adds method declarations in superclasses,
// so javac can compile the patch classes if they need to use a method/field they
// add on an instance other than this
class PrePatcher {
	private static final Logger log = Logger.getLogger("PatchLogger");
	private static final Pattern privatePattern = Pattern.compile("^(\\s+?)private", Pattern.MULTILINE);
	private static final Pattern extendsPattern = Pattern.compile("^public.*?\\s+?extends\\s+?([\\S^<]+?)(?:<(\\S+)>)?[\\s]+?(?:implements [^}]+?)?\\{", Pattern.MULTILINE);
	private static final Pattern genericMethodPattern = Pattern.compile("@Generic\\s+?(public\\s+?(\\S*?)?\\s+?(\\S*?)\\s*?\\S+?\\s*?\\()[^\\{]*\\)\\s*?\\{", Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern declareMethodPattern = Pattern.compile("@Declare\\s+?(public\\s+?(?:(?:synchronized|static) )*(\\S*?)?\\s+?(\\S*?)\\s*?\\S+?\\s*?\\([^\\{]*\\)\\s*?\\{)", Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern declareFieldPattern = Pattern.compile("@Declare\\s+?(public [^;\r\n]+?)_?( = [^;\r\n]+?)?;", Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern packageFieldPattern = Pattern.compile("\n    ? ?([^ ]+  ? ?[^ ]+);");
	private static final Pattern innerClassPattern = Pattern.compile("[^\n]public (?:static )?class ([^ \n]+)[ \n]", Pattern.MULTILINE);
	private static final Splitter spaceSplitter = Splitter.on(' ').omitEmptyStrings();
	private static final Splitter commaSplitter = Splitter.on(',').omitEmptyStrings().trimResults();
	private static final Map<String, PatchInfo> patchClasses = new HashMap<String, PatchInfo>();
	private static final Map<String, String> generics = new HashMap<String, String>();

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
			String contents = readFile(file);
			if (contents == null) {
				log.log(Level.SEVERE, "Failed to read " + file);
				continue;
			}
			Matcher extendsMatcher = extendsPattern.matcher(contents);
			if (!extendsMatcher.find()) {
				if (contents.contains(" extends")) {
					log.warning("Didn't match extends matcher for " + file);
				}
				continue;
			}
			String shortClassName = extendsMatcher.group(1);
			String className = null;
			for (String line : Splitter.on('\n').split(contents)) {
				if (line.endsWith('.' + shortClassName + ';')) {
					className = line.substring(7, line.length() - 1);
				}
			}
			if (className == null) {
				log.warning("Unable to find class " + shortClassName + " for " + file);
				continue;
			}
			String generic = extendsMatcher.group(2);
			if (generic != null) {
				generics.put(className, generic);
			}
			addPatches(className, contents);
			log.warning("Loaded patch for " + className);
		}
	}

	//private static final Pattern methodInfoPattern = Pattern.compile("(?:(public|private|protected) )?(static )?(?:([^ ]+?) )([^\\( ]+?) ?\\((.*?)\\) ?\\{", Pattern.DOTALL);
	private static final Pattern methodInfoPattern = Pattern.compile("^(.+) ?\\(([^\\(]*)\\) ?\\{", Pattern.DOTALL);

	private static void addPatches(String className, String contents) {
		PatchInfo patchInfo = patchClasses.get(className);
		if (patchInfo == null) {
			patchInfo = new PatchInfo();
			patchClasses.put(className, patchInfo);
		}
		String shortClassName = className;
		shortClassName = shortClassName.substring(shortClassName.lastIndexOf('/') + 1);
		shortClassName = shortClassName.substring(0, shortClassName.indexOf('.'));
		patchInfo.shortClassName = shortClassName;
		Matcher matcher = declareMethodPattern.matcher(contents);
		while (matcher.find()) {
			Matcher methodInfoMatcher = methodInfoPattern.matcher(matcher.group(1));

			if (!methodInfoMatcher.find()) {
				log.warning("Failed to match method info matcher to method declaration " + matcher.group(1));
				continue;
			}


			MethodInfo methodInfo = new MethodInfo();
			patchInfo.methods.add(methodInfo);

			String accessAndNameString = methodInfoMatcher.group(1);
			String paramString = methodInfoMatcher.group(2);

			for (String parameter : commaSplitter.split(paramString)) {
				String parameterType = spaceSplitter.split(parameter).iterator().next();
				methodInfo.parameterTypes.add(parameterType);
			}

			LinkedList<String> accessAndNames = Lists.newLinkedList(spaceSplitter.split(accessAndNameString));

			methodInfo.name = accessAndNames.removeLast();
			String type = methodInfo.returnType = accessAndNames.removeLast();

			while (!accessAndNames.isEmpty()) {
				String thing = accessAndNames.removeLast();
				if (thing.equals("static")) {
					methodInfo.static_ = true;
				} else if (thing.equals("synchronized")) {
					methodInfo.synchronized_ = true;
				} else {
					if (methodInfo.access != null) {
						log.warning("overwriting access from " + methodInfo.access + " -> " + thing + " in " + matcher.group(1));
					}
					methodInfo.access = thing;
				}
			}

			//methodInfo.parameterTypes.add();

			String ret = "null";
			if ("static".equals(type)) {
				type = matcher.group(3);
			}
			if ("boolean".equals(type)) {
				ret = "false";
			} else if ("void".equals(type)) {
				ret = "";
			} else if ("long".equals(type)) {
				ret = "0L";
			} else if ("int".equals(type)) {
				ret = "0";
			} else if ("float".equals(type)) {
				ret = "0f";
			} else if ("double".equals(type)) {
				ret = "0.0";
			}
			methodInfo.javaCode = matcher.group(1) + "return " + ret + ";}";
			log.warning(methodInfo.toString());
		}
		Matcher FieldMatcher = declareFieldPattern.matcher(contents);
		while (FieldMatcher.find()) {
			String var = FieldMatcher.group(1);
			FieldInfo FieldInfo = new FieldInfo();
			patchInfo.Fields.add(FieldInfo);
			LinkedList<String> typeAndName = Lists.newLinkedList(spaceSplitter.split(var));

			FieldInfo.name = typeAndName.removeLast();
			FieldInfo.type = typeAndName.removeLast();

			while (!typeAndName.isEmpty()) {
				String thing = typeAndName.removeLast();
				if (thing.equals("static")) {
					FieldInfo.static_ = true;
				} else if (thing.equals("volatile")) {
					FieldInfo.volatile_ = true;
				} else {
					if (FieldInfo.access != null) {
						log.severe("overwriting access from " + FieldInfo.access + " -> " + thing + " in " + var);
					}
					FieldInfo.access = thing;
				}
			}
			FieldInfo.javaCode = var + ';';
			log.warning(FieldInfo.toString());
		}
		String generic = generics.get(className);
		Matcher genericMatcher = genericMethodPattern.matcher(contents);
		while (genericMatcher.find()) {
			String original = genericMatcher.group(1);
			String withoutGenerics = original.replace(' ' + generic + ' ', " Object ");
			log.warning(original + " -> " + withoutGenerics);
		}
		if (contents.contains("\n@Public")) {
			patchInfo.makeAllPublic = true;
		}
	}

	private static class MethodInfo {
		public String name;
		public List<String> parameterTypes = new ArrayList<String>();
		public String returnType;
		public String access;
		public boolean static_;
		public boolean synchronized_;
		public String javaCode;

		private static final Joiner parameterJoiner = Joiner.on(", ");

		public String toString() {
			return "method: " + access + ' ' + (static_ ? "static " : "") + (synchronized_ ? "synchronized " : "") + returnType + ' ' + name + " (" + parameterJoiner.join(parameterTypes) + ')';
		}
	}

	private static class FieldInfo {
		public String name;
		public String type;
		public String access;
		public boolean static_;
		public boolean volatile_;
		public String javaCode;

		public String toString() {
			return "field: " + access + ' ' + (static_ ? "static " : "") + (volatile_ ? "volatile " : "") + type + ' ' + name;
		}
	}

	private static class PatchInfo {
		List<MethodInfo> methods = new ArrayList<MethodInfo>();
		List<FieldInfo> Fields = new ArrayList<FieldInfo>();
		boolean makeAllPublic = false;
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
		log.warning("Prepatching source for " + inputClassName);
		inputSource = inputSource.trim().replace("\t", "    ");
		String shortClassName = patchInfo.shortClassName;
		StringBuilder sourceBuilder = new StringBuilder(inputSource.substring(0, inputSource.lastIndexOf('}')))
				.append("\n// TT Patch Declarations\n");
		for (MethodInfo methodInfo : patchInfo.methods) {
			if (sourceBuilder.indexOf(methodInfo.javaCode) == -1) {
				sourceBuilder.append(methodInfo.javaCode).append('\n');
			}
		}
		for (FieldInfo FieldInfo : patchInfo.Fields) {
			if (sourceBuilder.indexOf(FieldInfo.javaCode) == -1) {
				sourceBuilder.append(FieldInfo.javaCode).append('\n');
			}
		}
		sourceBuilder.append("\n}");
		inputSource = sourceBuilder.toString();
		String generic = generics.get(inputClassName);
		if (generic != null) {
			inputSource = inputSource.replaceAll("class " + shortClassName + "(<[^>]>)?", "class " + shortClassName + '<' + generic + '>');
		}
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
		if (patchInfo.makeAllPublic) {
			inputSource = inputSource.replace("protected ", "public ");
		}
		inputSource = inputSource.replace("protected void save(", "public void save(");
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

	public static byte[] patchCode(byte[] inputCode, String inputClassName) {
		PatchInfo patchInfo = patchForClass(inputClassName);
		if (patchInfo == null) {
			return inputCode;
		}
		log.warning("Prepatching code for " + inputClassName);
		ClassReader classReader = new ClassReader(inputCode);
		ClassNode classNode = new ClassNode();
		classReader.accept(classNode, 0);
		classNode.access = classNode.access & ~Opcodes.ACC_FINAL;
		for (FieldNode fieldNode : (Iterable<FieldNode>) classNode.fields) {
			fieldNode.access = fieldNode.access & ~Opcodes.ACC_FINAL;
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

	private static void writeFile(File file, String contents) throws IOException {
		FileWriter fileWriter = new FileWriter(file);
		try {
			fileWriter.write(contents);
		} finally {
			fileWriter.close();
		}
	}
}
