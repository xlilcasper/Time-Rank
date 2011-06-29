package com.oberonserver.timerank;

import java.io.Serializable;

public class GenericGroup implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
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
