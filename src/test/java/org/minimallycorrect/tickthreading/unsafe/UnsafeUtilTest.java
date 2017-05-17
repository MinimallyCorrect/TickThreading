package org.minimallycorrect.tickthreading.unsafe;

import org.junit.Test;
import org.minimallycorrect.tickthreading.util.unsafe.UnsafeUtil;

import java.security.*;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class UnsafeUtilTest {
	private static void installAnnoyingSecurityManager() {
		System.setSecurityManager(new SecurityManager() {
			@Override
			public void checkPermission(Permission perm) {
				String permName = perm.getName() != null ? perm.getName() : "missing";
				if (permName.startsWith("exitVM") || "setSecurityManager".equals(permName))
					throw new SecurityException("Cannot " + permName);
			}
		});
	}

	@Test
	public void testRemoveSecurityManager() throws Exception {
		installAnnoyingSecurityManager();

		assertNotNull(System.getSecurityManager());

		UnsafeUtil.removeSecurityManager();

		assertNull(System.getSecurityManager());
	}
}
