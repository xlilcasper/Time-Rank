package com.oberonserver.timerank;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.entity.Player;

import com.oberonserver.perms.PermMethod;

public class Rank implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private GenericGroup group;
	private List<GenericGroup> oldgroup;
	public String desc="";
	public long time=-1;
	public int cost=-1;	
	public long minTime=-1;
	public double amount=1;
	public boolean remove=false;
	public String name="";	
	public String msg="";
	public boolean broadcast=false;
	
	public int rentCost=-1;
	public double rentAmount=-1;
	public long rentTime=-1;
	public long rentMinTime=-1;
	public boolean rentReturn=true;
	public String rentLostMsg="";
	public String rentGainedMsg="";
	public boolean rentBroadcast=false;
	
	public Rank(String sName, String sWorld, String sGroup,  String sOldGroup, boolean bRemove)
	{
		SetGroup(sWorld,sGroup);
		if (sOldGroup != null)
			SetOldGroup(sWorld,sOldGroup);		
		remove=bRemove;
		name=sName;
	}
	
	public Rank(String sName, GenericGroup gGroup, GenericGroup gOldGroup, boolean bRemove)
	{
		SetGroup(gGroup);
		if (gOldGroup != null)
			AddOldGroup(gOldGroup);
		remove=bRemove;
		name=sName;
	}
	
	public List<String> GetOldGroupNames()
	{
		List<String> names = new LinkedList<String>();
		for(GenericGroup curGroup: oldgroup )
		{
			names.add(curGroup.getName());
		}
		return names;
	}	
	
	public String strOldGroups()
	{
		List<String> names = new ArrayList<String>();
		for(GenericGroup curGroup: oldgroup )
		{
			names.add(curGroup.getName());
		}
		return arrayToString((String[]) names.toArray(),",");
	}
	
	public Boolean SetGroup(String sWorld, String sGroup)
	{
		try
		{
			GenericGroup gGroup = new GenericGroup(sWorld,sGroup);
			return SetGroup(gGroup);		
		}catch(Exception e){
			e.printStackTrace();
			return false;
		}
		
	}
	
	public Boolean SetGroup(GenericGroup gGroup)
	{
		group = gGroup;
		return true;
	}
	
	public GenericGroup GetGroup()
	{
		return group;
	}
	
	public Boolean SetOldGroup(String sWorld, String sGroup)
	{
		try
		{
			GenericGroup gGroup =  new GenericGroup(sWorld,sGroup);
			return AddOldGroup(gGroup);		
		}catch(Exception e){
			e.printStackTrace();
			return false;
		}
		
	}
	public Boolean AddOldGroup(GenericGroup gGroup)
	{
		oldgroup.add(gGroup);
		return true;
	}
	public Boolean RemoveOldGroup(GenericGroup gGroup)
	{
		oldgroup.remove(gGroup);
		return true;
	}
	
	public List<GenericGroup> GetOldGroup()
	{
		return oldgroup;
	}	
	
	public boolean hasOldGroup()
	{
		return !oldgroup.isEmpty();
	}
	
	public boolean inOldGroup(PermMethod perms, Player player)
	{
		for(GenericGroup curGroup: oldgroup )
		{
			if (perms.inGroup(player,curGroup.getWorld(), curGroup.getName()))
			{
				return true;
			}
		}
		return false;
		
	}
	
	public static String arrayToString(String[] a, String separator) {
		StringBuffer result = new StringBuffer();
		if (a.length > 0) {
			result.append(a[0]);
			for (int i=1; i<a.length; i++) {
				result.append(separator);
				result.append(a[i]);
			}
		}
		return result.toString();
	}
}
