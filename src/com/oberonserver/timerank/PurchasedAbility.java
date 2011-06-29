package com.oberonserver.timerank;

import java.io.Serializable;

public class PurchasedAbility  implements Serializable{	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
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
