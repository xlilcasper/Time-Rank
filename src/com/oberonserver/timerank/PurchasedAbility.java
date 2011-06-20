package com.oberonserver.timerank;

public class PurchasedAbility {
	public String playername;
	public Rank rank;
	public long durationTicks=0;	
	public PurchasedAbility(String p, Rank r)
	{
		playername=p;
		rank=r;
		durationTicks=r.rentTime/1000*20;
	}
}
