package com.oberonserver.timerank;

//import java.util.ArrayList;
//import java.util.Collections;

import org.bukkit.ChatColor;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerQuitEvent;



public class TimeRankPlayerListener extends PlayerListener {

	private final timerank plugin;
	
	public TimeRankPlayerListener(timerank instance) 
	{
		plugin = instance;
	}
	
	@Override
	public void onPlayerJoin(PlayerJoinEvent e)
	{
		long now = System.currentTimeMillis();	
		String playername = e.getPlayer().getName();
		plugin.StartTime.put(playername,now);
		plugin.loadPlaytime(playername);
		e.getPlayer().sendMessage(ChatColor.YELLOW + "Your current play time is " + plugin.Mills2Time(plugin.GetPlaytime(playername)));		
		plugin.CheckRanks(e.getPlayer());		
	}
	
	@Override
	public void onPlayerQuit(PlayerQuitEvent e) {
		String playername =e.getPlayer().getName();	
		
		plugin.savePlaytime(playername);
		plugin.StartTime.remove(playername);
		plugin.PlayTime.remove(playername);
		//plugin.CheckRanks(plugin.getServer().getOnlinePlayers());
	}
	
}
