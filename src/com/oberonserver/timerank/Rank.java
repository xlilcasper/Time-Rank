package com.oberonserver.timerank;

public class Rank {
	private GenericGroup group;
	private GenericGroup oldgroup;
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
			SetOldGroup(gOldGroup);
		remove=bRemove;
		name=sName;
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
			return SetOldGroup(gGroup);		
		}catch(Exception e){
			e.printStackTrace();
			return false;
		}
		
	}
	public Boolean SetOldGroup(GenericGroup gGroup)
	{
		oldgroup = gGroup;
		return true;
	}
	
	public GenericGroup GetOldGroup()
	{
		return oldgroup;
	}	
}
