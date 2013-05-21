package me.nallar.tickthreading.util;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

import com.sun.management.UnixOperatingSystemMXBean;

import me.nallar.tickthreading.Log;

public class EnvironmentInfo {
	public static String getOpenFileHandles() {
		OperatingSystemMXBean osMxBean = ManagementFactory.getOperatingSystemMXBean();
		if (osMxBean instanceof UnixOperatingSystemMXBean) {
			UnixOperatingSystemMXBean unixOsMxBean = (UnixOperatingSystemMXBean) osMxBean;
			return unixOsMxBean.getOpenFileDescriptorCount() + " / " + unixOsMxBean.getMaxFileDescriptorCount();
		}
		return "unknown";
	}

	public static void checkOpenFileHandles() {
		OperatingSystemMXBean osMxBean = ManagementFactory.getOperatingSystemMXBean();
		if (osMxBean instanceof UnixOperatingSystemMXBean) {
			UnixOperatingSystemMXBean unixOsMxBean = (UnixOperatingSystemMXBean) osMxBean;
			long used = unixOsMxBean.getOpenFileDescriptorCount();
			long available = unixOsMxBean.getMaxFileDescriptorCount();
			if (used != 0 && available != 0 && (used > (available * 17) / 20 || (available - used) < 3)) {
				Log.severe("Used >= 85% of available file handles: " + getOpenFileHandles());
			}
		}
	}
}
