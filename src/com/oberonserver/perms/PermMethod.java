package com.oberonserver.perms;

import org.bukkit.entity.Player;

import com.nijiko.permissions.PermissionHandler;

public interface PermMethod {
	public void AddGroup(Player player, String parentWorld, String parentName);
	public void RemoveGroup(String world, String name, String parentWorld, String parentName);
	public PermissionHandler getHandler();
	public boolean HasPermission(Player p, String PermissionNode);
	public boolean inGroup(Player p, String parentWorld, String parentName);
	public boolean AddNode(Player p, String node, String world);
	public boolean RemoveNode(Player p, String node, String world);
}
