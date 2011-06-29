package com.oberonserver.timerank;

import java.io.Serializable;

public class PurchasedRank implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
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
