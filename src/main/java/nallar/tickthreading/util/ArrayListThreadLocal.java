package nallar.tickthreading.util;

import java.util.*;

public class ArrayListThreadLocal extends ThreadLocal {
	public ArrayListThreadLocal() {
	}

	@Override
	protected Object initialValue() {
		return new ArrayList();
	}
}
