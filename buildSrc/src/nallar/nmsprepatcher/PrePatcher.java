package nallar.nmsprepatcher;

import com.google.common.base.Splitter;

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
	private static final Pattern declareVariablePattern = Pattern.compile("@Declare\\s+?(public [^;\r\n]+?)_?( = [^;\r\n]+?)?;", Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern packageVariablePattern = Pattern.compile("\n    ? ?([^ ]+  ? ?[^ ]+);");
	private static final Pattern innerClassPattern = Pattern.compile("[^\n]public (?:static )?class ([^ \n]+)[ \n]", Pattern.MULTILINE);
	private static final Map<String, String> patchClasses = new HashMap<String, String>();
	private static final Map<String, String> generics = new HashMap<String, String>();

	public static void loadPatches(File patchDirectory) {
		recursiveSearch(patchDirectory, patchClasses, generics);
	}

	private static void recursiveSearch(File patchDirectory, Map<String, String> patchClasses, Map<String, String> generics) {
		for (File file : patchDirectory.listFiles()) {
			if (file.isDirectory()) {
				recursiveSearch(file, patchClasses, generics);
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
				log.info("Unable to find class " + shortClassName + " for " + file);
				continue;
			}
			String generic = extendsMatcher.group(2);
			if (generic != null) {
				generics.put(className, generic);
			}
			String current = patchClasses.get(className);
			patchClasses.put(className, (current == null ? "" : current) + contents);
		}
	}

	public static String patchSource(String inputSource, String inputClassName) {
		inputSource = inputSource.trim().replace("\t", "    ");
		String contents = patchClasses.get(inputClassName);
		String shortClassName = inputClassName;
		shortClassName = shortClassName.substring(shortClassName.lastIndexOf('/') + 1);
		shortClassName = shortClassName.substring(0, shortClassName.indexOf('.'));
		int previousIndex = inputSource.indexOf("\n//PREPATCH\n");
		int cutIndex = previousIndex == -1 ? inputSource.lastIndexOf('}') : previousIndex;
		StringBuilder sourceBuilder = new StringBuilder(inputSource.substring(0, cutIndex)).append("\n//PREPATCH\n");
		Matcher matcher = declareMethodPattern.matcher(contents);
		while (matcher.find()) {
			String type = matcher.group(2);
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
			String decl = matcher.group(1) + "return " + ret + ";}";
			if (sourceBuilder.indexOf(decl) == -1) {
				sourceBuilder.append(decl).append('\n');
			}
		}
		Matcher variableMatcher = declareVariablePattern.matcher(contents);
		while (variableMatcher.find()) {
			String var = variableMatcher.group(1);
			sourceBuilder.append(var.replace(" final ", " ")).append(";\n");
		}
		sourceBuilder.append("\n}");
		inputSource = sourceBuilder.toString();
		String generic = generics.get(inputClassName);
		if (generic != null) {
			inputSource = inputSource.replaceAll("class " + shortClassName + "(<[^>]>)?", "class " + shortClassName + '<' + generic + '>');
		}
		Matcher genericMatcher = genericMethodPattern.matcher(contents);
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
		}
		inputSource = inputSource.replace("\nfinal ", " ");
		inputSource = inputSource.replace(" final ", " ");
		inputSource = inputSource.replace("\nclass", "\npublic class");
		inputSource = inputSource.replace("\n    " + shortClassName, "\n    public " + shortClassName);
		inputSource = inputSource.replace("\n    protected " + shortClassName, "\n    public " + shortClassName);
		inputSource = inputSource.replace("private class", "public class");
		inputSource = inputSource.replace("protected class", "public class");
		inputSource = privatePattern.matcher(inputSource).replaceAll("$1protected");
		if (contents.contains("\n@Public")) {
			inputSource = inputSource.replace("protected ", "public ");
		}
		inputSource = inputSource.replace("protected void save(", "public void save(");
		Matcher packageMatcher = packageVariablePattern.matcher(inputSource);
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
		return inputCode;
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
