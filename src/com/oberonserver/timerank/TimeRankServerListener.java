package com.oberonserver.timerank;

import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerListener;
import org.bukkit.plugin.PluginManager;

import com.nijikokun.timerank.register.payment.Methods;

public class TimeRankServerListener extends ServerListener {
	private timerank plugin;
    private Methods Methods = null;
    public TimeRankServerListener(timerank plugin) {
        this.plugin = plugin;
        this.Methods = new Methods();
    }

    @Override
    public void onPluginDisable(PluginDisableEvent event) {
        // Check to see if the plugin thats being disabled is the one we are using
        if (this.Methods != null && this.Methods.hasMethod()) {
            Boolean check = this.Methods.checkDisabled(event.getPlugin());

            if(check) {
                this.plugin.Method = null;
                System.out.println("[TimeRank] Payment method was disabled. No longer accepting payments.");
            }
        }
    }

    @Override
    public void onPluginEnable(PluginEnableEvent event) {
        // Check to see if we need a payment method
        if (!this.Methods.hasMethod()) {
            if(this.Methods.setMethod(event.getPlugin())) {
                // You might want to make this a public variable inside your MAIN class public Method Method = null;
                // then reference it through this.plugin.Method so that way you can use it in the rest of your plugin ;)
                this.plugin.Method = this.Methods.getMethod();
                System.out.println("[TimeRank] Payment method found (" + this.plugin.Method.getName() + " version: " + this.plugin.Method.getVersion() + ")");
            }
        }
                
        if(plugin.permissionHandler == null)
        {
        	PluginManager pm = plugin.getServer().getPluginManager();        	
	        if (pm.getPlugin("Permissions") != null )
	        {	    
	        	if (pm.getPlugin("Permissions").getDescription().getDepend() != null)
	        	{
	        		if (!pm.getPlugin("Permissions").getDescription().getDepend().toString().equalsIgnoreCase("[GroupManager]"))
	        		{//Make sure it's not fakepermissions
	        			System.out.println("[TimeRank] Permissions 3.x found.");
	        			plugin.permissions="Permissions3";
	        			plugin.setupPermissions();	        		
	        		}
	        	}
	        	else
	        	{
	        		System.out.println("[TimeRank] Permissions 3.x found.");
        			plugin.permissions="Permissions3";
        			plugin.setupPermissions();
	        	}
	        }	        
	        if (pm.getPlugin("GroupManager") != null)
	        {        	
	        	System.out.println("[TimeRank] Group Manager found.");
	        	plugin.permissions="GroupManager";
	        	plugin.setupPermissions();
	        }
        }
    }
        
}
