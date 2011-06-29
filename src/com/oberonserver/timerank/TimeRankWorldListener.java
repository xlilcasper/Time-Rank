package com.oberonserver.timerank;

import org.bukkit.event.world.WorldListener;
import org.bukkit.event.world.WorldSaveEvent;

public class TimeRankWorldListener extends WorldListener {
	private final timerank plugin;
	
	public TimeRankWorldListener(timerank instance) {
        plugin = instance;
    }

    @Override
    public void onWorldSave(WorldSaveEvent event) {
    	plugin.savePlaytime();
    }
}
