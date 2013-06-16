package nallar.patched.forge;

import java.io.BufferedWriter;
import java.io.IOException;

import com.google.common.base.Splitter;

import net.minecraftforge.common.ConfigCategory;
import net.minecraftforge.common.Property;

import static net.minecraftforge.common.Configuration.allowedProperties;

public abstract class PatchConfigCategory extends ConfigCategory {
	private static final Splitter splitter = Splitter.onPattern("\r?\n");

	public PatchConfigCategory(String name) {
		super(name);
	}

	@Override
	public void write(BufferedWriter out, int indent) throws IOException {
		String pad = getIndent(indent);

		out.write(pad);
		out.write("####################");
		out.newLine();
		out.write(pad);
		out.write("# ");
		out.write(name);
		out.newLine();

		if (comment != null) {
			out.write(pad);
			out.write("#===================");
			out.newLine();
			Splitter splitter = Splitter.onPattern("\r?\n");

			for (String line : splitter.split(comment)) {
				out.write(pad);
				out.write("# ");
				out.write(line);
				out.newLine();
			}
		}

		out.write(pad);
		out.write("####################");
		out.newLine();
		out.newLine();

		if (!allowedProperties.matchesAllOf(name)) {
			name = '"' + name + '"';
		}

		out.write(pad);
		out.write(name);
		out.write(" {");
		out.newLine();

		pad = getIndent(indent + 1);

		Property[] props = properties.values().toArray(new Property[properties.size()]);

		for (int x = 0; x < props.length; x++) {
			Property prop = props[x];

			if (prop.comment != null) {
				if (x != 0) {
					out.newLine();
				}

				for (String commentLine : splitter.split(prop.comment)) {
					out.write(pad);
					out.write("# ");
					out.write(commentLine);
					out.newLine();
				}
			}

			String propName = prop.getName();
			propName = !allowedProperties.matchesAllOf(propName) ? '"' + propName + '"' : propName;

			if (prop.isList()) {
				out.write(pad);
				out.write(prop.getType().getID());
				out.write(':');
				out.write(propName);
				out.write(" <");
				out.newLine();
				pad = getIndent(indent + 2);

				for (String line : prop.getStringList()) {
					out.write(pad);
					out.write(line);
					out.newLine();
				}

				out.write(getIndent(indent + 1));
				out.write(" >");
				out.newLine();
				pad = getIndent(indent + 1);
			} else if (prop.getType() == null) {
				out.write(pad);
				out.write(propName);
				out.write('=');
				out.write(prop.getString());
			} else {
				out.write(pad);
				out.write(prop.getType().getID());
				out.write(':');
				out.write(propName);
				out.write('=');
				out.write(prop.getString());
				out.newLine();
			}
		}

		for (ConfigCategory child : children) {
			child.write(out, indent + 1);
		}

		out.write(getIndent(indent));
		out.write('}');
		out.newLine();
		out.newLine();
	}
}
