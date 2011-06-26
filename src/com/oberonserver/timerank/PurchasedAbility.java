package com.oberonserver.timerank;

public class PurchasedAbility {	
	public String playername;
	public Ability ability;
	public long durationTicks=0;	
	public PurchasedAbility(String p, Ability a)
	{
		playername=p;
		ability=a;
		durationTicks=a.rentTime/1000*20;
	}

}
