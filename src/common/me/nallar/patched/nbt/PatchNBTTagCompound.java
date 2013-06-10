package me.nallar.patched.nbt;

import java.util.Map;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ReportedException;

public abstract class PatchNBTTagCompound extends NBTTagCompound {
	@Override
	public synchronized NBTBase copy() {
		NBTTagCompound var1 = new NBTTagCompound(this.getName());

		for (Map.Entry<String, NBTBase> o : ((Map<String, NBTBase>) this.tagMap).entrySet()) {
			var1.setTag(o.getKey(), o.getValue().copy());
		}

		return var1;
	}

	@Override
	public byte getByte(String tag) {
		try {
			NBTTagByte nbtTagByte = (NBTTagByte) tagMap.get(tag);
			return nbtTagByte == null ? 0 : nbtTagByte.data;
		} catch (ClassCastException e) {
			throw new ReportedException(this.createCrashReport(tag, 1, e));
		}
	}

	@Override
	public short getShort(String tag) {
		try {
			NBTTagShort nbtTagShort = (NBTTagShort) tagMap.get(tag);
			return nbtTagShort == null ? 0 : nbtTagShort.data;
		} catch (ClassCastException e) {
			throw new ReportedException(this.createCrashReport(tag, 2, e));
		}
	}

	@Override
	public int getInteger(String tag) {
		try {
			NBTTagInt nbtTagInt = (NBTTagInt) tagMap.get(tag);
			return nbtTagInt == null ? 0 : nbtTagInt.data;
		} catch (ClassCastException e) {
			throw new ReportedException(this.createCrashReport(tag, 3, e));
		}
	}

	@Override
	public long getLong(String tag) {
		try {
			NBTTagLong nbtTagLong = (NBTTagLong) tagMap.get(tag);
			return nbtTagLong == null ? 0 : nbtTagLong.data;
		} catch (ClassCastException e) {
			throw new ReportedException(this.createCrashReport(tag, 4, e));
		}
	}

	@Override
	public float getFloat(String tag) {
		try {
			NBTTagFloat nbtTagFloat = (NBTTagFloat) tagMap.get(tag);
			return nbtTagFloat == null ? 0 : nbtTagFloat.data;
		} catch (ClassCastException e) {
			throw new ReportedException(this.createCrashReport(tag, 5, e));
		}
	}

	@Override
	public double getDouble(String tag) {
		try {
			NBTTagDouble nbtTagDouble = (NBTTagDouble) tagMap.get(tag);
			return nbtTagDouble == null ? 0 : nbtTagDouble.data;
		} catch (ClassCastException e) {
			throw new ReportedException(this.createCrashReport(tag, 6, e));
		}
	}

	@Override
	public String getString(String tag) {
		try {
			NBTTagString nbtTagString = (NBTTagString) tagMap.get(tag);
			return nbtTagString == null ? "" : nbtTagString.data;
		} catch (ClassCastException e) {
			throw new ReportedException(this.createCrashReport(tag, 8, e));
		}
	}

	@Override
	public byte[] getByteArray(String tag) {
		try {
			NBTTagByteArray nbtTagByteArray = (NBTTagByteArray) tagMap.get(tag);
			return nbtTagByteArray == null ? new byte[0] : nbtTagByteArray.byteArray;
		} catch (ClassCastException e) {
			throw new ReportedException(this.createCrashReport(tag, 7, e));
		}
	}

	@Override
	public int[] getIntArray(String tag) {
		try {
			NBTTagIntArray nbtTagIntArray = (NBTTagIntArray) tagMap.get(tag);
			return nbtTagIntArray == null ? new int[0] : nbtTagIntArray.intArray;
		} catch (ClassCastException e) {
			throw new ReportedException(this.createCrashReport(tag, 11, e));
		}
	}

	@Override
	public NBTTagCompound getCompoundTag(String tag) {
		try {
			NBTTagCompound nbtTagCompound = (NBTTagCompound) tagMap.get(tag);
			return nbtTagCompound == null ? new NBTTagCompound(tag) : nbtTagCompound;
		} catch (ClassCastException e) {
			throw new ReportedException(this.createCrashReport(tag, 10, e));
		}
	}

	@Override
	public NBTTagList getTagList(String tag) {
		try {
			NBTTagList nbtTagList = (NBTTagList) tagMap.get(tag);
			return nbtTagList == null ? new NBTTagList(tag) : nbtTagList;
		} catch (ClassCastException e) {
			throw new ReportedException(this.createCrashReport(tag, 9, e));
		}
	}

	@Override
	@Declare
	public Map getTagMap() {
		return tagMap;
	}

	public boolean equals(Object o) {
		if (o instanceof NBTTagCompound) {
			NBTBase var2 = (NBTBase) o;
			if (this.getId() == var2.getId() && ((this.name != null || var2.getName() == null) && (this.name == null || var2.getName() != null) && (this.name == null || this.name.equals(var2.getName())))) {
				NBTTagCompound nbttagcompound = (NBTTagCompound) o;
				Map map = tagMap;
				Map other = nbttagcompound.getTagMap();
				if (map.size() == other.size()) {
					synchronized (this) {
						for (Map.Entry entry : (Iterable<Map.Entry>) map.entrySet()) {
							if (other.get(entry.getKey()) != entry.getValue()) {
								return false;
							}
						}
					}
					return true;
				}
			}
		}
		return false;
	}
}
