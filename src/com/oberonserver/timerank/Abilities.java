package com.oberonserver.timerank;

import java.util.LinkedList;
import java.util.List;

import org.bukkit.entity.Player;

import com.oberonserver.perms.PermMethod;

public class Abilities {
	public String permission="timerank.ab";
	public String desc="";
	public String world="*";
	public long time=-1;
	public int cost=-1;	
	public long minTime=-1;
	public double amount=1;
	public String name="";	
	public String msg="";
	public boolean broadcast=false;
	
	public int rentCost=-1;
	public double rentAmount=-1;
	public long rentTime=-1;
	public long rentMinTime=-1;
	public String rentLostMsg="";
	public String rentGainedMsg="";
	public boolean rentBroadcast=false;
	
	public List<String> Nodes=new LinkedList<String>();
	
	public boolean AddNodes(PermMethod perm,Player p)
	{
		boolean worked=true;
		for(String node : Nodes)
		{
			//make sure all of them worked. If one returns fallse, worked will be false
			if (worked) 
				worked=perm.AddNode(p, node, world);
			else
				perm.AddNode(p, node, world);
		}
		return worked;
	}
	
	public boolean RemoveNodes(PermMethod perm,Player p)
	{
		boolean worked=true;
		for(String node : Nodes)
		{
			//make sure all of them worked. If one returns false, worked will be false
			if (worked) 
				worked=perm.RemoveNode(p, node, world);
			else
				perm.RemoveNode(p, node, world);
		}
		return worked;
	}

}
