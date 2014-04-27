package ru.tehkode.permissions;

import net.minecraft.entity.player.EntityPlayer;

public interface IPermissions {
	boolean has(EntityPlayer player, String permission);

	boolean has(EntityPlayer player, String permission, String world);

	boolean has(String player, String permission, String world);

	String prefix(String player, String world);

	String suffix(String player, String world);

	IPermissionEntity getUser(EntityPlayer player);

	IPermissionEntity getUser(String name);

	IPermissionEntity getGroup(String name);
}
