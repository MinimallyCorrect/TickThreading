package nallar.tickthreading.util;

import com.sun.management.UnixOperatingSystemMXBean;
import nallar.tickthreading.Log;

import java.lang.management.*;

public class EnvironmentInfo {
	public static String getJavaVersion() {
		return ManagementFactory.getRuntimeMXBean().getSpecVersion();
	}

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
