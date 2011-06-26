package com.oberonserver.timerank;

import java.util.LinkedList;
import java.util.List;

import com.oberonserver.perms.PermMethod;

public class Ability {
	public String permission="";
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
	public List<String> Categories=new LinkedList<String>();
	PermMethod perm;	

}
