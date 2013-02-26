package me.nallar.tickthreading.util.stringfillers;

public abstract class StringFiller {
	public static final StringFiller CHAT = new ChatStringFiller();
	public static final StringFiller FIXED_WIDTH = new StringFiller() {
		@Override
		public double getLength(String s) {
			return s.length();
		}

		@Override
		public String fill(String s, double requiredLength) {
			StringBuilder stringBuilder = new StringBuilder();
			double length = getLength(s);
			while (length < requiredLength) {
				stringBuilder.append(' ');
				length++;
			}
			return stringBuilder.append(s).toString();
		}
	};

	public abstract double getLength(String s);

	public abstract String fill(String s, double requiredLength);
}
