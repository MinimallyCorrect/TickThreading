package nallar.patched.entity;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import nallar.patched.annotation.FakeExtend;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.WatchableObject;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.ChunkCoordinates;

@FakeExtend
public abstract class PatchDataWatcher extends DataWatcher {
	private boolean isBlank = true;
	private static final HashMap<Class, Integer> dataTypes = new HashMap<Class, Integer>();
	private final WatchableObject[] watchedObjects = new WatchableObject[31];
	/**
	 * true if one or more object was changed
	 */
	private boolean objectChanged;

	static {
		dataTypes.put(Byte.class, 0);
		dataTypes.put(Short.class, 1);
		dataTypes.put(Integer.class, 2);
		dataTypes.put(Float.class, 3);
		dataTypes.put(String.class, 4);
		dataTypes.put(ItemStack.class, 5);
		dataTypes.put(ChunkCoordinates.class, 6);
	}

	/**
	 * adds a new object to dataWatcher to watch, to update an already existing object see updateObject. Arguments: data
	 * Value Id, Object to add
	 */
	@Override
	public void addObject(int par1, Object par2Obj) {
		Integer var3 = dataTypes.get(par2Obj.getClass());

		if (var3 == null) {
			throw new IllegalArgumentException("Unknown data type: " + par2Obj.getClass());
		} else if (par1 > 31) {
			throw new IllegalArgumentException("Data value id is too big with " + par1 + "! (Max is " + 31 + ')');
		} else if (watchedObjects[par1] != null) {
			throw new IllegalArgumentException("Duplicate id value for " + par1 + '!');
		}
		watchedObjects[par1] = new WatchableObject(var3, par1, par2Obj);
		isBlank = false;
	}

	/**
	 * Add a new object for the DataWatcher to watch, using the specified data type.
	 */
	@Override
	public void addObjectByDataType(int par1, int par2) {
		WatchableObject var3 = new WatchableObject(par2, par1, null);
		watchedObjects[par1] = var3;
		isBlank = false;
	}

	/**
	 * gets the bytevalue of a watchable object
	 */
	@Override
	public byte getWatchableObjectByte(int par1) {
		return (Byte) watchedObjects[par1].getObject();
	}

	@Override
	public short getWatchableObjectShort(int par1) {
		return (Short) watchedObjects[par1].getObject();
	}

	/**
	 * gets a watchable object and returns it as a Integer
	 */
	@Override
	public int getWatchableObjectInt(int par1) {
		return (Integer) watchedObjects[par1].getObject();
	}

	/**
	 * gets a watchable object and returns it as a String
	 */
	@Override
	public String getWatchableObjectString(int par1) {
		return (String) watchedObjects[par1].getObject();
	}

	/**
	 * Get a watchable object as an ItemStack.
	 */
	@Override
	public ItemStack getWatchableObjectItemStack(int par1) {
		return (ItemStack) watchedObjects[par1].getObject();
	}

	/**
	 * updates an already existing object
	 */
	@Override
	public void updateObject(int par1, Object par2Obj) {
		WatchableObject var3 = watchedObjects[par1];

		if (!par2Obj.equals(var3.getObject())) {
			var3.setObject(par2Obj);
			var3.setWatched(true);
			objectChanged = true;
		}
	}

	@Override
	public void setObjectWatched(int par1) {
		watchedObjects[par1].watched = true;
		objectChanged = true;
	}

	@Override
	public boolean hasChanges() {
		return objectChanged;
	}

	/**
	 * writes every object in passed list to dataoutputstream, terminated by 0x7F
	 */
	public static void a(List par0List, DataOutputStream par1DataOutputStream) throws IOException {
		if (par0List != null) {
			for (final Object aPar0List : par0List) {
				WatchableObject watchableobject = (WatchableObject) aPar0List;
				a(par1DataOutputStream, watchableobject);
			}
		}

		par1DataOutputStream.writeByte(127);
	}

	@Override
	public List unwatchAndReturnAllWatched() {
		ArrayList arraylist = null;

		if (this.objectChanged) {
			for (WatchableObject watchableobject : watchedObjects) {
				if (watchableobject != null && watchableobject.isWatched()) {
					watchableobject.setWatched(false);

					if (arraylist == null) {
						arraylist = new ArrayList();
					}

					arraylist.add(watchableobject);
				}
			}
		}

		this.objectChanged = false;
		return arraylist;
	}

	@Override
	public void writeWatchableObjects(DataOutput dataOutput) throws IOException {
		for (WatchableObject watchableobject : watchedObjects) {
			if (watchableobject != null) {
				a(dataOutput, watchableobject);
			}
		}
		dataOutput.writeByte(127);
	}

	@Override
	public List getAllWatched() {
		ArrayList arraylist = null;

		for (WatchableObject watchableobject : watchedObjects) {
			if (watchableobject == null) {
				continue;
			}
			if (arraylist == null) {
				arraylist = new ArrayList();
			}
			arraylist.add(watchableobject);
		}
		return arraylist;
	}

	protected static void a(DataOutput dataOutput, WatchableObject par1WatchableObject) throws IOException {
		int i = (par1WatchableObject.getObjectType() << 5 | par1WatchableObject.getDataValueId() & 31) & 255;
		dataOutput.writeByte(i);

		switch (par1WatchableObject.getObjectType()) {
			case 0:
				dataOutput.writeByte((Byte) par1WatchableObject.getObject());
				break;
			case 1:
				dataOutput.writeShort((Short) par1WatchableObject.getObject());
				break;
			case 2:
				dataOutput.writeInt((Integer) par1WatchableObject.getObject());
				break;
			case 3:
				dataOutput.writeFloat((Float) par1WatchableObject.getObject());
				break;
			case 4:
				Packet.writeString((String) par1WatchableObject.getObject(), dataOutput);
				break;
			case 5:
				ItemStack itemstack = (ItemStack) par1WatchableObject.getObject();
				Packet.writeItemStack(itemstack, dataOutput);
				break;
			case 6:
				ChunkCoordinates chunkcoordinates = (ChunkCoordinates) par1WatchableObject.getObject();
				dataOutput.writeInt(chunkcoordinates.posX);
				dataOutput.writeInt(chunkcoordinates.posY);
				dataOutput.writeInt(chunkcoordinates.posZ);
		}
	}

	public static List a(DataInput dataInput) throws IOException {
		ArrayList arraylist = null;

		for (byte b0 = dataInput.readByte(); b0 != 127; b0 = dataInput.readByte()) {
			if (arraylist == null) {
				arraylist = new ArrayList();
			}

			int i = (b0 & 224) >> 5;
			int j = b0 & 31;
			WatchableObject watchableobject = null;

			switch (i) {
				case 0:
					watchableobject = new WatchableObject(i, j, dataInput.readByte());
					break;
				case 1:
					watchableobject = new WatchableObject(i, j, dataInput.readShort());
					break;
				case 2:
					watchableobject = new WatchableObject(i, j, dataInput.readInt());
					break;
				case 3:
					watchableobject = new WatchableObject(i, j, dataInput.readFloat());
					break;
				case 4:
					watchableobject = new WatchableObject(i, j, Packet.readString(dataInput, 64));
					break;
				case 5:
					watchableobject = new WatchableObject(i, j, Packet.readItemStack(dataInput));
					break;
				case 6:
					int k = dataInput.readInt();
					int l = dataInput.readInt();
					int i1 = dataInput.readInt();
					watchableobject = new WatchableObject(i, j, new ChunkCoordinates(k, l, i1));
			}

			arraylist.add(watchableobject);
		}

		return arraylist;
	}

	@Override
	@SideOnly (Side.CLIENT)
	public void updateWatchedObjectsFromList(List par1List) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean getIsBlank() {
		return this.isBlank;
	}
}
