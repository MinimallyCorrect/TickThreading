package nallar.tickthreading.util;

import java.util.*;

public class LinkedListThreadLocal extends ThreadLocal {
	public LinkedListThreadLocal() {
	}

	@Override
	protected Object initialValue() {
		return new LinkedList();
	}
}
