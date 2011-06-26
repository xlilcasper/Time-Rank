package com.oberonserver.timerank;

public class PurchasedRank {
	public String playername;
	public Rank rank;
	public long durationTicks=0;	
	public PurchasedRank(String p, Rank r)
	{
		playername=p;
		rank=r;
		durationTicks=r.rentTime/1000*20;
	}
}
