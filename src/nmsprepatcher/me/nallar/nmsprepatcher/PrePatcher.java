package me.nallar.nmsprepatcher;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Splitter;

// The prepatcher adds method declarations in superclasses,
// so javac can compile the patch classes if they need to use a method/field they
// add on an instance other than this
public class PrePatcher {
	private static final Logger log = Logger.getLogger("PatchLogger");
	private static final Pattern privatePattern = Pattern.compile("^(\\s+?)private", Pattern.MULTILINE);
	private static final Pattern extendsPattern = Pattern.compile("\\s+?extends\\s+?([\\S]+)[^\\{]+?\\{", Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern declareMethodPattern = Pattern.compile("@Declare\\s+?(public\\s+?(\\S*?)?\\s+?(\\S*?)\\s*?\\S+?\\s*?\\([^\\{]*\\)\\s*?\\{)", Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern declareVariablePattern = Pattern.compile("@Declare\\s+?(public [^;\r\n]+?)_;", Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern packageVariablePattern = Pattern.compile("\n    ? ?([^ ]+  ? ?[^ ]+);");

	private static void recursiveSearch(File patchDirectory, File sourceDirectory, Map<File, String> patchClasses) {
		for (File file : patchDirectory.listFiles()) {
			if (file.isDirectory()) {
				recursiveSearch(file, sourceDirectory, patchClasses);
				continue;
			}
			String contents = readFile(file);
			if (contents == null) {
				log.log(Level.SEVERE, "Failed to read " + file);
				continue;
			}
			Matcher extendsMatcher = extendsPattern.matcher(contents);
			if (!extendsMatcher.find()) {
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
			File sourceFile = new File(sourceDirectory, className.replace('.', '/') + ".java");
			if (!sourceFile.exists()) {
				log.severe("Can't find " + sourceFile + " for " + file + ", not patching.");
				continue;
			}
			String current = patchClasses.get(sourceFile);
			patchClasses.put(sourceFile, (current == null ? "" : current) + contents);
		}
	}

	public static void patch(File patchDirectory, File sourceDirectory) {
		if (!patchDirectory.isDirectory()) {
			throw new IllegalArgumentException("Not a directory! " + patchDirectory + ", " + sourceDirectory);
		}
		Map<File, String> patchClasses = new HashMap<File, String>();
		recursiveSearch(patchDirectory, sourceDirectory, patchClasses);

		for (Map.Entry<File, String> classPatchEntry : patchClasses.entrySet()) {
			String contents = classPatchEntry.getValue();
			File sourceFile = classPatchEntry.getKey();
			String shortClassName = sourceFile.getName();
			shortClassName = shortClassName.substring(shortClassName.lastIndexOf('/') + 1);
			shortClassName = shortClassName.substring(0, shortClassName.indexOf('.'));
			String sourceString = readFile(sourceFile).trim();
			int previousIndex = sourceString.indexOf("\n//PREPATCH\n");
			int cutIndex = previousIndex == -1 ? sourceString.lastIndexOf('}') : previousIndex;
			StringBuilder source = new StringBuilder(sourceString.substring(0, cutIndex)).append("\n//PREPATCH\n");
			Matcher matcher = declareMethodPattern.matcher(contents);
			while (matcher.find()) {
				String type = matcher.group(2);
				String ret = "null"; // TODO: Add more types.
				if ("static".equals(type)) {
					type = matcher.group(3);
				}
				if ("boolean".equals(type)) {
					ret = "false";
				} else if ("void".equals(type)) {
					ret = "";
				} else if ("int".equals(type)) {
					ret = "0";
				} else if ("float".equals(type)) {
					ret = "0f";
				} else if ("double".equals(type)) {
					ret = "0.0";
				}
				String decl = matcher.group(1) + "return " + ret + ";}";
				if (source.indexOf(decl) == -1) {
					source.append(decl).append('\n');
				}
			}
			Matcher variableMatcher = declareVariablePattern.matcher(contents);
			while (variableMatcher.find()) {
				String var = variableMatcher.group(1);
				source.append(var).append(";\n");
			}
			source.append("\n}");
			sourceString = source.toString();
			// TODO: Fix package -> public properly later.
			sourceString = sourceString.replace("\nfinal ", " ");
			sourceString = sourceString.replace(" final ", " ");
			sourceString = sourceString.replace("\nclass", "\npublic class");
			sourceString = sourceString.replace("\n    " + shortClassName, "\n    public " + shortClassName);
			sourceString = sourceString.replace("\n    protected " + shortClassName, "\n    public " + shortClassName);
			sourceString = sourceString.replace("private class", "public class");
			sourceString = sourceString.replace("protected class", "public class");
			Matcher privateMatcher = privatePattern.matcher(sourceString);
			sourceString = privateMatcher.replaceAll("$1protected");
			sourceString = sourceString.replace("protected void save(", "public void save(");
			Matcher packageMatcher = packageVariablePattern.matcher(sourceString);
			StringBuffer sb = new StringBuffer();
			while (packageMatcher.find()) {
				packageMatcher.appendReplacement(sb, "\n    public " + packageMatcher.group(1) + ';');
			}
			packageMatcher.appendTail(sb);
			try {
				writeFile(sourceFile, sb.toString());
			} catch (IOException e) {
				log.log(Level.SEVERE, "Failed to save " + sourceFile, e);
			}
		}
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
