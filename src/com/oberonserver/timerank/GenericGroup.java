package com.oberonserver.timerank;

public class GenericGroup {
	private String name="";
	private String world="";
	public GenericGroup(String sWorld, String sName)
	{
		name=sName;
		world=sWorld;
	}
	public String getName()
	{
		return name;
	}
	public String getWorld()
	{
		return world;
	}
}
