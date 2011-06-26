package com.oberonserver.perms.methods;

import org.anjocaido.groupmanager.GroupManager;
import org.anjocaido.groupmanager.data.Group;
import org.anjocaido.groupmanager.data.User;
import org.anjocaido.groupmanager.dataholder.OverloadedWorldHolder;
import org.anjocaido.groupmanager.dataholder.worlds.WorldsHolder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;
import com.oberonserver.perms.PermMethod;
import com.oberonserver.timerank.timerank;

public class GM implements PermMethod {
	timerank plugin;
	GroupManager gm;	
	
	public GM(timerank instance,Plugin instance2)
	{
		plugin=instance;
		gm=(GroupManager) instance2;
	}
	
	public void AddGroup(Player p, String parentWorld, String parentName)
	{ 				
		String world = p.getWorld().getName();
		String name = p.getName();
		User u;
		Group newGroup;
		WorldsHolder worldsHolder = gm.getWorldsHolder();		
		plugin.DebugPrint("GM trying to promote user " + name + " in " + world);		
		OverloadedWorldHolder dataHolder = null;
		dataHolder = worldsHolder.getWorldData(parentWorld);
		u=dataHolder.getUser(name);
		newGroup = dataHolder.getGroup(parentName);
		if (newGroup == null) {
			plugin.DebugPrint(parentName + " Group not found!");
	          return;
	        }
		else
		{
			plugin.DebugPrint(parentName + " Group found as " + newGroup.getName());
		}
		plugin.DebugPrint("Promoting " + u.getName() + " from " + u.getGroupName());
		u.setGroup(newGroup);
		plugin.DebugPrint( u.getName() + " is now in " + u.getGroupName());		
	}
	public void RemoveGroup(String world, String name, String parentWorld, String parentName)
	{
		
	}
	
	public boolean AddNode(Player p, String node, String World)
	{
		String name = p.getName();
		User u;
		WorldsHolder worldsHolder = gm.getWorldsHolder();				
		OverloadedWorldHolder dataHolder = null;
		dataHolder = worldsHolder.getWorldData(World);
		u=dataHolder.getUser(name);
		u.addPermission(node);
		return true;
		
	}
	
	public boolean RemoveNode(Player p, String node, String World)
	{
		String name = p.getName();
		User u;
		WorldsHolder worldsHolder = gm.getWorldsHolder();				
		OverloadedWorldHolder dataHolder = null;
		dataHolder = worldsHolder.getWorldData(World);
		u=dataHolder.getUser(name);
		u.removePermission(node);
		return true;
		
	}
	
	@SuppressWarnings("deprecation")
	public PermissionHandler getHandler()
	{
		return Permissions.Security;
		
	}
	public boolean HasPermission(Player p, String PermissionNode)
	{		
		return getHandler().has(p,PermissionNode);
		
	}
	public boolean inGroup(Player p, String parentWorld, String parentName)
	{		
		return getHandler().inGroup(parentWorld, p.getName(), parentName);		
	}
}
