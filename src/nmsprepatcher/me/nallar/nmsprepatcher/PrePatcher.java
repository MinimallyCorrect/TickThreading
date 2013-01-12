package me.nallar.nmsprepatcher;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The prepatcher adds method declarations in superclasses,
// so javac can compile the patch classes if they need to use a method/field they
// add on an instance other than this
public class PrePatcher {
	private static final Logger log = Logger.getLogger("PatchLogger");
	private static final Pattern privatePattern = Pattern.compile("^(\\s+?)private", Pattern.MULTILINE);
	private static final Pattern extendsPattern = Pattern.compile("\\s+?extends\\s+?([\\S]+)[^\\{]+?\\{", Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern declarePattern = Pattern.compile("@Declare\\s+?(public\\s+?(\\S*?)(?:\\s+?(\\S*?))?\\s*?\\S+?\\s*?\\([^\\{]*\\)\\s*?\\{)", Pattern.DOTALL | Pattern.MULTILINE);

	public static void patch(File patchDirectory, File sourceDirectory) {
		if (!patchDirectory.isDirectory()) {
			throw new IllegalArgumentException("Not a directory! " + patchDirectory + ", " + sourceDirectory);
		}
		for (File file : patchDirectory.listFiles()) {
			String contents = readFile(file);
			if (contents == null) {
				log.log(Level.SEVERE, "Failed to read " + file);
				continue;
			}
			Matcher extendsMatcher = extendsPattern.matcher(contents);
			if (!extendsMatcher.find()) {
				log.info(file + " does not extend an NMS class.");
				continue;
			}
			String shortClassName = extendsMatcher.group(1);
			String className = null;
			for (String line : contents.split("\n")) {
				if (line.endsWith('.' + shortClassName + ';')) {
					className = line.substring(7, line.length() - 1);
				}
			}
			if (className == null) {
				log.info("Unable to find class " + shortClassName);
				continue;
			}
			File sourceFile = new File(sourceDirectory, className.replace('.', '/') + ".java");
			if (!sourceFile.exists()) {
				log.severe("Can't find " + sourceFile + ", not patching.");
				continue;
			}
			String sourceString = readFile(sourceFile).trim();
			int previousIndex = sourceString.indexOf("\n//PREPATCH\n");
			int cutIndex = previousIndex == -1 ? sourceString.lastIndexOf('}') : previousIndex;
			StringBuilder source = new StringBuilder(sourceString.substring(0, cutIndex)).append("\n//PREPATCH\n");
			log.info("Prepatching declarations for " + className);
			Matcher matcher = declarePattern.matcher(contents);
			while (matcher.find()) {
				String type = matcher.group(2);
				String ret = "null"; // TODO: Add more types.
				if ("boolean".equals(type)) {
					ret = "false";
				} else if ("void".equals(type)) {
					ret = "";
				}
				String decl = matcher.group(1) + "return " + ret + ";}";
				log.info("adding " + decl);
				if (source.indexOf(decl) == -1) {
					source.append(decl);
				}
			}
			source.append("\n}");
			sourceString = source.toString();
			Matcher privateMatcher = privatePattern.matcher(sourceString);
			sourceString = privateMatcher.replaceAll("$1protected");
			try {
				writeFile(sourceFile, sourceString);
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
