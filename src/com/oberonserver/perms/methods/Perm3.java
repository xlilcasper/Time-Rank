package com.oberonserver.perms.methods;

import org.bukkit.entity.Player;

import com.nijiko.permissions.Entry;
import com.nijiko.permissions.Group;
import com.nijiko.permissions.PermissionHandler;

import com.oberonserver.perms.PermMethod;
import com.oberonserver.timerank.timerank;


public class Perm3 implements PermMethod{	
	timerank plugin;
	public Perm3(timerank instance)
	{
		plugin=instance;
	}
	public boolean HasPermission(Player p, String PermissionNode) {
       	return plugin.permissionHandler.has(p,PermissionNode);       
    }
	public boolean HasPermission(Player p, String PermissionNode, String world) {
       	return plugin.permissionHandler.has(world,p.getName(),PermissionNode);
    }
	
	public PermissionHandler getHandler() {
        return plugin.permissionHandler;
    }
	
	public void AddGroup(Player p, String parentWorld, String parentName)
	{
		String world = p.getWorld().getName();
		String name = p.getName();
		
		Entry entry = getHandler().getUserObject(world, name);
		if (entry == null)
		{
			plugin.DebugPrint("Error in AddGroup. Could not create user: " + name + " in world " + world);
			return;
		}
		Group parent = getHandler().getGroupObject(parentWorld, parentName);
		if (parent == null)
		{
			plugin.DebugPrint("Error in AddGroup. Could not create group: " + parentName + " in world " + parentWorld);
			return;
		}
		plugin.DebugPrint("Adding '" + name + "' in world '" + world + "' to group '" + parentName + "' in world '" + parentWorld+"'");		
		entry.addParent(parent);		
	}	
	
	public void RemoveGroup(String world, String name, String parentWorld, String parentName)
	{
		Entry entry = getHandler().getUserObject(world, name);
		if (entry == null)
			plugin.DebugPrint("Error in RemoveGroup. Could not create user: " + name + " in world " + world);
		Group parent = getHandler().getGroupObject(parentWorld, parentName);
		if (parent == null)
			plugin.DebugPrint("Error in RemoveGroup. Could not create group: " + parentName + " in world " + parentWorld);
		entry.removeParent(parent);
		if (inGroup(plugin.getServer().getPlayer(name),parentWorld,parentName))
		{
			plugin.DebugPrint("Something went wrong adding '" + name + "' in world '" + world + "' to group '" + parentName + "' in world '" + parentWorld+"'");
		}
	}
	
	public boolean AddNode(Player p, String node, String World)
	{
		getHandler().addUserPermission(World, p.getName(), node);
		return true;
		
	}
	
	public boolean RemoveNode(Player p, String node, String World)
	{
		getHandler().removeUserPermission(World, p.getName(), node);
		return true;
		
	}
	
	public boolean inGroup(Player p, String parentWorld, String parentName)
	{		
		return getHandler().inGroup(p.getWorld().getName(), p.getName(),parentWorld, parentName);
		
	}
	
	public boolean isGroup(String world, String name)
	{
		Group parent = getHandler().getGroupObject(world, name);
		if (parent == null)
			return false;
		return true;
	}
}
