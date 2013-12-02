package nallar.patched.forge;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.Configuration;

import java.io.*;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public abstract class PatchConfiguration extends Configuration {
	private boolean saveQueued;

	@Override
	public void save() {
		if (PARENT != null && PARENT != this) {
			PARENT.save();
			return;
		}

		Set<Configuration> toSaveConfigurationSet = MinecraftServer.toSaveConfigurationSet;
		if (toSaveConfigurationSet != null) {
			if (!saveQueued) {
				toSaveConfigurationSet.add(this);
			}
			saveQueued = true;
			return;
		}

		try {
			File file = this.file;

			if (!file.exists() && !file.createNewFile() && ((file.getParentFile() == null || !file.getParentFile().mkdirs() || !file.createNewFile()))) {
				return;
			}

			if (file.canWrite()) {
				FileOutputStream fos = new FileOutputStream(file);
				BufferedWriter buffer = new BufferedWriter(new OutputStreamWriter(fos, defaultEncoding));

				buffer.write("# Configuration file" + NEW_LINE);

				if (children.isEmpty()) {
					save(buffer);
				} else {
					for (Map.Entry<String, Configuration> entry : children.entrySet()) {
						buffer.write("START: \"" + entry.getKey() + '"' + NEW_LINE);
						entry.getValue().save(buffer);
						buffer.write("END: \"" + entry.getKey() + '"' + NEW_LINE + NEW_LINE);
					}
				}

				buffer.close();
				fos.close();
			}
		} catch (IOException e) {
			FMLLog.log(Level.WARNING, e, "Error saving configuration file " + file);
		}
	}
}
