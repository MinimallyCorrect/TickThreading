package nallar.tickthreading.util.stringfillers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import nallar.tickthreading.util.ChatFormat;

/**
 * Derived from https://github.com/andfRa/Saga/blob/master/src/org/saga/utility/chat/ChatFiller.java
 */
class ChatStringFiller extends StringFiller {
	private static final double DEFAULT_LENGTH = 3.0 / 2.0;
	private static final double MAX_GAP = 1.25;
	private static final HashMap<Character, Double> SIZE_MAP = new HashMap<Character, Double>() {
		{
			put('i', 0.5);
			put('k', 5.0 / 4.0);
			put('t', 1.0);
			put('f', 5.0 / 4.0);
			put('(', 5.0 / 4.0);
			put(')', 5.0 / 4.0);
			put('<', 5.0 / 4.0);
			put('>', 5.0 / 4.0);
			put('{', 5.0 / 4.0);
			put('}', 5.0 / 4.0);
			put(',', 1.0 / 2.0);
			put('.', 1.0 / 2.0);
			put('[', 1.0);
			put(']', 1.0);
			put('I', 1.0);
			put('|', 1.0 / 2.0);
			put('*', 5.0 / 4.0);
			put('"', 5.0 / 4.0);
			put('|', 0.5);
			put('!', 0.5);
			put(':', 0.5);
			put('l', 3.0 / 4.0);
			put('.', 1.0 / 2.0);
			put('\'', 3.0 / 4.0);
			put(' ', 1.0 / 1.0);
			put('\"', 5.0 / 4.0);
			put('`', 0.5);
			put('\0', 0.0);

			put('\u278A', 0.5);
			put('\u278B', 3.0 / 4.0);
			put(' ', 1.0);
			put('\u278C', 5.0 / 4.0);

			put('\u2500', 5.0 / 4.0);
			put('\u2502', 1.0 / 4.0);
			put('\u250C', 3.0 / 4.0);
			put('\u2510', 3.0 / 4.0);
			put('\u2514', 3.0 / 4.0);
			put('\u2518', 3.0 / 4.0);

			put('\u2550', 5.0 / 4.0);
			put('\u2551', 1.0 / 2.0);

			put('\u2554', 3.0 / 4.0);
			put('\u2560', 3.0 / 4.0);
			put('\u255A', 3.0 / 4.0);

			put('\u2557', 4.0 / 4.0);
			put('\u2563', 4.0 / 4.0);
			put('\u255D', 4.0 / 4.0);

			put('\u2591', 2.0);
		}
	};
	private static final HashSet<Character> FILL_CHARS = new HashSet<Character>() {
		private static final long serialVersionUID = 1L;

		{
			add('\u278A');
			add('\u278B');
			add(' ');
			add('\u278C');
		}
	};

	@Override
	public String fill(String str, double reqLength) {

		char[] chars = str.toCharArray();

		StringBuilder result = new StringBuilder();
		double length = 0.0;

		for (int i = 0; i < chars.length; i++) {

			Double charLength = SIZE_MAP.get(chars[i]);
			if (charLength == null) {
				charLength = DEFAULT_LENGTH;
			}

			if (length + charLength > reqLength) {
				break;
			}

			result.append(chars[i]);

			if (!(chars[i] == ChatFormat.FORMAT_CHAR || (i > 0 && chars[i - 1] == ChatFormat.FORMAT_CHAR))) {
				length += charLength;
			}
		}

		Character fillChar = ' ';
		double fillLength = 1.0;
		while (true) {

			double gapLength = reqLength - length;

			if (gapLength <= 0) {
				break;
			}

			if (gapLength <= MAX_GAP) {

				fillChar = findCustom(gapLength);
				if (fillChar != null) {
					result.append(fillChar);
				}

				break;
			}

			result.append(fillChar);
			length += fillLength;
		}

		return result.toString()
				.replace("\u278A", ChatFormat.DARK_GRAY + "`" + ChatFormat.RESET)
				.replace("\u278B", ChatFormat.DARK_GRAY + String.valueOf(ChatFormat.BOLD) + '`' + ChatFormat.RESET)
				.replace("\u278C", ChatFormat.DARK_GRAY + String.valueOf(ChatFormat.BOLD) + ' ' + ChatFormat.RESET);
	}

	private static Character findCustom(double gapLen) {

		Set<Character> gapStrs = new HashSet<Character>(FILL_CHARS);
		double bestFitLen = -1.0;
		Character bestFitStr = null;

		for (Character gapStr : gapStrs) {

			double gapStrLen = SIZE_MAP.get(gapStr);

			if (gapLen - gapStrLen >= 0 && gapStrLen > bestFitLen) {
				bestFitLen = gapStrLen;
				bestFitStr = gapStr;
			}
		}

		return bestFitStr;
	}

	@Override
	public double getLength(String str) {
		char[] chars = str.toCharArray();

		double length = 0.0;

		for (int i = 0; i < chars.length; i++) {

			Double charLength = SIZE_MAP.get(chars[i]);
			if (charLength == null) {
				charLength = DEFAULT_LENGTH;
			}

			if (!(chars[i] == ChatFormat.FORMAT_CHAR || (i > 0 && chars[i - 1] == ChatFormat.FORMAT_CHAR))) {
				length += charLength;
			}
		}

		return length;
	}
}
