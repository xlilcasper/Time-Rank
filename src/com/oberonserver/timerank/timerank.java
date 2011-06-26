package com.oberonserver.timerank;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;
import com.nijikokun.timerank.register.payment.Method;
import com.oberonserver.perms.PermMethod;

public class timerank extends JavaPlugin
{	
	private boolean debug = false;
	static String mainDirectory = "plugins/TimeRank";
	static File times = new File(mainDirectory+File.separator+"Time.dat");
	public Logger log = Logger.getLogger("Minecraft");
	public PermissionHandler permissionHandler;
	boolean UsePermissions = false; 
	private final TimeRankPlayerListener playerListener = new TimeRankPlayerListener(this);
	private final TimeRankWorldListener worldListener = new TimeRankWorldListener(this);
	private final TimeRankServerListener serverListener = new TimeRankServerListener(this);	
	public Map<String, Long> StartTime = new HashMap<String, Long>();
	public Map<String, Long> PlayTime = new HashMap<String, Long>();
	public List<PurchasedAbility> RentedAbilities;
	public List<PurchasedRank> RentedRanks;
	public Map<Rank, Long> Ranks = new LinkedHashMap<Rank, Long>();	
	public Map<Ability, Long> Abilities = new LinkedHashMap<Ability,Long>();
	public Method Method = null;
	public PermMethod perms;
	public String permissions = "Permissions3"; 
	private boolean hideUnavaible=false;

	private TimeRankChecker checker;
	private int checkDelay=5*20;
	private int checkInterval=10*20;

	public Configuration config;

	public void onEnable(){
		//Get the plugin managor
		PluginManager pm = this.getServer().getPluginManager();

		//register our events
		pm.registerEvent(Event.Type.PLAYER_JOIN , playerListener, Event.Priority.Monitor , this);
		pm.registerEvent(Event.Type.PLAYER_QUIT , playerListener, Event.Priority.Monitor , this);
		pm.registerEvent(Event.Type.WORLD_SAVE, worldListener, Event.Priority.Monitor, this);

		pm.registerEvent(Event.Type.PLUGIN_ENABLE, serverListener, Event.Priority.Monitor, this);
		pm.registerEvent(Event.Type.PLUGIN_DISABLE, serverListener, Event.Priority.Monitor, this);

		//Make our data folder and try and prepare the config file.
		new File(mainDirectory).mkdir();
		new File(mainDirectory+File.separator+"data").mkdir();		
		//loadPlaytime();
		config = getConfiguration();

		loadConfig();		
		//setupPermissions();

		//Get all players online and give them a start time. This is in case we are disabled/enabled after the server starts
		for(Player p : getServer().getOnlinePlayers())
		{
			long now = System.currentTimeMillis();
			StartTime.put(p.getName(),now);
			loadPlaytime(p.getName());
		}

		//Schedule our check event 
		checker = new TimeRankChecker(this, checkInterval);
		getServer().getScheduler().scheduleSyncRepeatingTask(this, checker, checkDelay, checkInterval);

		//Load what abilites were rented last time we were closed
		RentedAbilities = new LinkedList<PurchasedAbility>();
		RentedRanks = new LinkedList<PurchasedRank>();
		loadRent();

		//All went well.
		log.info("[Time Rank] Version " + this.getDescription().getVersion() + " Enabled.");
	} 

	public void onDisable(){
		//Save play time and clear variables
		savePlaytime();
		saveRent();
		permissionHandler=null;
		Ranks.clear();
		StartTime.clear();
		PlayTime.clear();
		RentedAbilities.clear();

		//stop our scheduled rank checker
		getServer().getScheduler().cancelTasks(this);
		log.info("[Time Rank] Disabled.");		
	}

	private void loadConfig()
	{
		try
		{						
			config.load();
			//check for old config.
			int cv=config.getInt("settings.configVersion", 1);
			if (cv==1)
			{
				log.info("[TimeRank] Old config version " + cv + " detected. Updating to new config format.");				
				updateConfig();
				config.load();
				Ranks.clear();
			}
			debug = config.getBoolean("settings.debug",false);			
			hideUnavaible = config.getBoolean("settings.hideUnavaible",false);
			List<String> keys = config.getKeys("ranks");
			DebugPrint("Ranks key size "+Integer.toString(keys.size()));
			//load config
			for(String key : keys)
			{		
				String node="ranks."+key;
				String sGroup = config.getString(node+".group");
				String sOldGroup = config.getString(node+".oldgroup","");
				String sWorld = config.getString(node+".world","*");
				boolean remove = config.getBoolean(node+".remove", false);
				int iTime = config.getInt(node+".time",-1);
				long lTime = (long)iTime * 60 * 1000;		
				GenericGroup group =  new GenericGroup(sWorld,sGroup);;
				GenericGroup gOldGroup=null;
				if (sOldGroup != "")
					gOldGroup =  new GenericGroup(sWorld,sOldGroup);		
				Rank rank = new Rank(key, group, gOldGroup, remove);
				rank.name=key;
				rank.time = lTime;
				if (config.getString(node+".buy.cost","").equalsIgnoreCase("money"))
					rank.cost=0;
				else
					rank.cost	= config.getInt(node+".buy.cost", -1);
				if (rank.cost != -1)
				{
					rank.amount		= config.getDouble(node+".buy.amount", 1);
					rank.minTime		= config.getInt(node+".buy.minTime", -1)*60*1000;				
					rank.broadcast	= config.getBoolean(node+".buy.broadcast", true);
					rank.msg		= config.getString(node+".buy.msg", "&B<player.name> &Ehas been promoted to &B<rank.group>");
				} else {
					config.removeProperty(node+".buy.cost");
					config.removeProperty(node+".buy");
				}

				//Rent stuff
				if (config.getString(node+".rent.cost","").equalsIgnoreCase("money"))
					rank.rentCost=0;
				else
					rank.rentCost	= config.getInt(node+".rent.cost", -1);
				if (rank.rentCost != -1)
				{
					rank.rentMinTime	= config.getInt(node+".rent.minTime", -1)*60*1000;
					rank.rentAmount		= config.getDouble(node+".rent.amount", 1);
					rank.rentBroadcast	= config.getBoolean(node+".rent.broadcast", true);
					rank.rentGainedMsg	= config.getString(node+".rent.gainedMsg", "&B<player.name> &Ehas been promoted to &B<rank.group>");
					rank.rentLostMsg	= config.getString(node+".rent.lostMsg", "&B<player.name> &Ehas been demoted from &B<rank.group> &Eto &B<rank.oldgroup>.");

					iTime = config.getInt(node+".rent.time",-1);
					lTime = (long)iTime * 60 * 1000;	
					rank.rentTime		= lTime;
				} else {
					config.removeProperty(node+".rent.cost");
					config.removeProperty(node+".rent");
				}

				rank.desc			= config.getString(node+".description", "");
				Ranks.put(rank, lTime);	
				DebugPrint("Loaded " + rank.name + " with group " + rank.GetGroup().getName() + " in world " + rank.GetGroup().getWorld());
			}						

			//Load abilities node
			keys = config.getKeys("abilities");
			if (keys != null)
			{
				DebugPrint("Abilities key size "+Integer.toString(keys.size()));
				List<String> emptyNodes = new LinkedList<String>();
				emptyNodes.add("timerank.none");
				List<String> emptyCat = new LinkedList<String>();
				emptyCat.add("Uncategorized");
				for(String key : keys)
				{		
					String node="abilities."+key;				
					String sWorld = config.getString(node+".world","*");				
					int iTime = config.getInt(node+".time",-1);
					long lTime = (long)iTime * 60 * 1000;		

					Ability ability = new Ability();
					ability.world=sWorld;				
					ability.name=key;
					ability.permission = config.getString(node+".permission", "timerank.ab");				
					ability.Nodes = config.getStringList(node+".nodes", emptyNodes);
					ability.Categories = config.getStringList(node+".categories", emptyCat);

					ability.time = lTime;
					if (config.getString(node+".buy.cost","").equalsIgnoreCase("money"))
						ability.cost=0;
					else
						ability.cost	= config.getInt(node+".buy.cost", -1);
					if (ability.cost != -1)
					{
						ability.amount		= config.getDouble(node+".buy.amount", 1);
						ability.minTime		= config.getInt(node+".buy.minTime", -1)*60*1000;				
						ability.broadcast	= config.getBoolean(node+".buy.broadcast", true);
						ability.msg		= config.getString(node+".buy.msg", "&B<player.name> &Ehas been promoted to &B<rank.group>");
					} else {
						config.removeProperty(node+".buy.cost");
						config.removeProperty(node+".buy");
					}

					//Rent stuff
					if (config.getString(node+".rent.cost","").equalsIgnoreCase("money"))
						ability.rentCost=0;
					else
						ability.rentCost	= config.getInt(node+".rent.cost", -1);
					if (ability.rentCost != -1)
					{
						ability.rentMinTime	= config.getInt(node+".rent.minTime", -1)*60*1000;
						ability.rentAmount		= config.getDouble(node+".rent.amount", 1);
						ability.rentBroadcast	= config.getBoolean(node+".rent.broadcast", true);
						ability.rentGainedMsg	= config.getString(node+".rent.gainedMsg", "&B<player.name> &Ehas been promoted to &B<rank.group>");
						ability.rentLostMsg	= config.getString(node+".rent.lostMsg", "&B<player.name> &Ehas been demoted from &B<rank.group> &Eto &B<rank.oldgroup>.");

						iTime = config.getInt(node+".rent.time",-1);
						lTime = (long)iTime * 60 * 1000;	
						ability.rentTime		= lTime;
					} else {
						config.removeProperty(node+".rent.cost");
						config.removeProperty(node+".rent");
					}

					ability.desc			= config.getString(node+".description", "");
					Abilities.put(ability, lTime);	
					DebugPrint("Loaded " + ability.name + " with permissions " + "" + " in world " + ability.world);
				}
			}else
			{
				DebugPrint("No abilities found.");
			}
		}catch(Exception e){
			ThrowSimpleError(e);

		}

	}

	private void updateConfig()
	{//read in the old config and output a new one.
		config.load();
		debug = config.getBoolean("settings.debug",false);				
		hideUnavaible = config.getBoolean("settings.hideUnavaible",false);
		List<String> keys = config.getKeys("ranks");
		DebugPrint("Keys size "+Integer.toString(keys.size()));
		//load old config
		for(String key : keys)
		{		
			String node="ranks."+key;
			String sGroup = config.getString(node+".group");
			String sOldGroup = config.getString(node+".oldgroup","");
			String sWorld = config.getString(node+".world","*");
			boolean remove = config.getBoolean(node+".remove", false);
			int iTime = config.getInt(node+".time",-1);
			long lTime = (long)iTime * 60 * 1000;		
			GenericGroup group =  new GenericGroup(sWorld,sGroup);;
			GenericGroup gOldGroup=null;
			if (sOldGroup != "")
				gOldGroup =  new GenericGroup(sWorld,sOldGroup);		
			Rank rank = new Rank(key, group, gOldGroup, remove);
			rank.name=key;
			rank.time = lTime;
			if (config.getString(node+".cost","").equalsIgnoreCase("money"))
				rank.cost=0;
			else
				rank.cost	= config.getInt(node+".cost", -1);
			if (rank.cost != -1)
			{
				rank.amount		= config.getDouble(node+".amount", 1);
				rank.minTime		= config.getInt(node+".minTime", -1)*60*1000;				
				rank.broadcast	= config.getBoolean(node+".broadcast", true);
				rank.msg		= config.getString(node+".msg", "&B<player.name> &Ehas been promoted to &B<rank.group>");				
			} else {
				config.removeProperty(node+".cost");
			}
			//Rent stuff
			if (config.getString(node+".rentCost","").equalsIgnoreCase("money"))
				rank.rentCost=0;
			else
				rank.rentCost	= config.getInt(node+".rentCost", -1);
			if (rank.rentCost != -1)
			{
				rank.rentMinTime	= config.getInt(node+".rentMinTime", -1)*60*1000;
				rank.rentAmount		= config.getDouble(node+".rentAmount", 1);
				rank.rentBroadcast	= config.getBoolean(node+".rentBroadcast", true);
				rank.rentGainedMsg	= config.getString(node+".rentGainedMsg", "&B<player.name> &Ehas been promoted to &B<rank.group>");
				rank.rentLostMsg	= config.getString(node+".rentLostMsg", "&B<player.name> &Ehas been demoted from &B<rank.group> &Eto &B<rank.oldgroup>.");
			}
			else
			{
				config.removeProperty(node+".rentCost");
			}


			iTime = config.getInt(node+".rentTime",-1);
			lTime = (long)iTime * 60 * 1000;	
			rank.rentTime		= lTime;

			rank.desc			= config.getString(node+".description", "");
			Ranks.put(rank, lTime);	
			DebugPrint("Loaded " + rank.name + " with group " + rank.GetGroup().getName() + " in world " + rank.GetGroup().getWorld());

			//Remove old nods
			for(String remNode : config.getKeys(node))
			{//loop though all the nodes.
				DebugPrint("Removing old node: "+node+"."+remNode);
				config.removeProperty(node+"."+remNode);
			}
		}
		saveConfig();
	}

	private void saveConfig()
	{//save all our settings to the config file.
		config.setProperty("settings.debug", debug);
		config.setProperty("settings.hideUnavaible", hideUnavaible);		
		config.setProperty("settings.configVersion", 2);

		for(Rank rank : Ranks.keySet())
		{
			String node="ranks."+rank.name;			

			config.setProperty(node+".group", rank.GetGroup().getName());
			config.setProperty(node+".world", rank.GetGroup().getWorld());
			config.setProperty(node+".oldgroup", rank.GetOldGroup().getName());

			config.setProperty(node+".time", rank.time/60/1000);
			if (rank.cost > -1)
			{
				if (rank.cost>0)
					config.setProperty(node+".buy.cost", rank.cost);
				else if (rank.cost==0)
					config.setProperty(node+".buy.cost", "Money");					

				config.setProperty(node+".buy.amount", rank.amount);
				if (rank.minTime>0) config.setProperty(node+".buy.minTime", rank.minTime/60/1000);
				config.setProperty(node+".buy.broadcast", rank.broadcast);
				config.setProperty(node+".buy.msg", rank.msg);
			}

			if (rank.rentCost > -1)
			{
				DebugPrint("rentCost is "+rank.rentCost+" for "+rank.name);
				if (rank.rentCost>0)
					config.setProperty(node+".rent.cost", rank.rentCost);
				else if (rank.rentCost==0)
					config.setProperty(node+".rent.cost", "Money");
				if (rank.rentMinTime>0) config.setProperty(node+".rent.minTime", rank.rentMinTime/60/1000);
				config.setProperty(node+".rent.amount", rank.rentAmount);
				config.setProperty(node+".rent.broadcast", rank.rentBroadcast);
				config.setProperty(node+".rent.gainedMessage", rank.rentGainedMsg);
				config.setProperty(node+".rent.lostMessage", rank.rentLostMsg);
				config.setProperty(node+".rent.time", rank.rentTime/60/1000);
			}

			config.setProperty(node+".description", rank.desc);

		}
		config.save();
	}
	public void setupPermissions()
	{
		//Get the permissions plugin.
		Plugin permissionsPlugin = this.getServer().getPluginManager().getPlugin("Permissions");

		if (permissionHandler == null) {//check if we already have a handler
			if (permissionsPlugin != null) {//Make sure we found a permissions plugin
				permissionHandler = ((Permissions) permissionsPlugin).getHandler();
				UsePermissions = true;
				if (permissions.equalsIgnoreCase("Permissions3"))
				{//Use permissions 3.x
					try
					{
						perms = new com.oberonserver.perms.methods.Perm3(this);		            	
						log.info("[Time Rank] Using Permissions 3.x");
					}catch(Exception e){
						PluginManager pm = this.getServer().getPluginManager();

						Map<String,String>ErrorInfo = new LinkedHashMap<String,String>();
						ErrorInfo.put("Error message:", "Set to use Permissions 3.x but something went wrong.");
						ErrorInfo.put("Depend", pm.getPlugin("Permissions").getDescription().getDepend().toString());
						ErrorInfo.put("Permissions version", pm.getPlugin("Permissions").getDescription().getVersion().toString());
						ErrorLog(ErrorInfo);

					}
				}
				else if (permissions.equalsIgnoreCase("GroupManager"))
				{//Use group manager
					Plugin gm = this.getServer().getPluginManager().getPlugin("GroupManager");
					perms = new com.oberonserver.perms.methods.GM(this,gm);
					log.info("[Time Rank] Using permissions GroupManger");
				}

			} else {
				System.out.println("[Time Rank] Permission system not detected. Something went wrong.");
				System.out.println("[Time Rank] Make sure you are using Permisions 3.x or GroupManager.");
			}
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args)
	{
		try
		{
			Player player = null;
			//String[] split = args;
			if(sender instanceof Player)
			{
				player = (Player) sender;
				//Check permissions. Node should be timerank.<command name>
				if(!perms.HasPermission(player,"timerank."+cmd.getName()))
				{
					if(sender instanceof Player)
						sender.sendMessage("§4You do not have permission to run this command");
					return true;
				}
			}
			//We have permission to run the command.

			if (cmd.getName().equalsIgnoreCase("playtime")) //Check which command we ran.
			{
				String playername="";
				if(sender instanceof Player)
					playername = player.getDisplayName(); 

				if (args.length > 0)
					playername = args[0];

				for(String p : PlayTime.keySet())
				{
					if (p.equalsIgnoreCase(playername))
					{//Name or display name matches
						if(sender instanceof Player)
							player.sendMessage(p + " has been playing for " + Mills2Time(GetPlaytime(p)));
						else
							log.info(p + " has been playing for " + Mills2Time(GetPlaytime(p)));
						return true;
					}
				}			
				//Player not found in list see if it is there first connect.
				for(String p : StartTime.keySet())
				{
					if (p.equalsIgnoreCase(playername))
					{//Name or display name matches
						sender.sendMessage(p + " has been playing for " + Mills2Time(GetPlaytime(p)));
						return true;
					}
				}

				//see if they are just not loaded yet.
				File path = new File(mainDirectory+File.separator+"data"+File.separator+playername);
				if (path.exists())
				{
					loadPlaytime(playername);				
					sender.sendMessage(playername + " has been playing for " + Mills2Time(GetPlaytime(playername)));				
				}
				sender.sendMessage(playername + " could not be found");
			}		
			else if (cmd.getName().equalsIgnoreCase("checkranks"))
			{
				sender.sendMessage("Promoted " + CheckRanks(getServer().getOnlinePlayers()) + " people.");
				return true;
			}		
			else if (cmd.getName().equalsIgnoreCase("buyrank"))
			{
				//Make sure a player is running the command.
				if(!(sender instanceof Player))
				{
					log.info("This command must be run in game.");
					return false;
				}

				if (args.length < 1)
					return false;			
				//Buy the rank
				String rankname = args[0];
				DebugPrint(player.getName() + " is trying to buy "+rankname);
				BuyRank(player,rankname);
				return true;
			}
			else if (cmd.getName().equalsIgnoreCase("rentrank"))
			{
				//Make sure it is a player
				if(!(sender instanceof Player))
				{
					log.info("This command must be run in game.");
					return false;
				}

				if (args.length < 1)
					return false;	
				//Rent out the rank
				String rankname = args[0];
				DebugPrint(player.getName() + " is trying to rent "+rankname);
				RentRank(player,rankname);
				return true;
			}
			else if (cmd.getName().equalsIgnoreCase("listranks"))
			{
				//Set up the filter and page info
				String sCmd ="";
				int iPage=-1;
				if (args.length > 0)
					if (!isParsableToInt(args[0]))					
						sCmd = args[0];
					else
						iPage = Integer.parseInt(args[0]);
				if ((args.length > 1) && (isParsableToInt(args[1])))
					iPage = Integer.parseInt(args[1]);
				//iPage -= 1;
				int perPage = 5;
				int curItem = -1;
				int startItem = ((iPage-1) * perPage);			

				sender.sendMessage("§B---------------Rank List---------------");
				for(Rank r : Ranks.keySet())
				{		

					//check to see if we hide groups we can't get.
					if ( hideUnavaible )
						if (!perms.inGroup(player,r.GetOldGroup().getWorld(), r.GetOldGroup().getName())&& !r.GetOldGroup().getName().equals(""))
						{
							DebugPrint("Hidding " + r.name + " from " + player.getName());
							continue;
						}

					//Check filters
					if (sCmd.equalsIgnoreCase("time"))
						if (r.time<=0)
							continue;

					if (sCmd.equalsIgnoreCase("buy"))
						if (r.cost<0)
							continue;

					if (sCmd.equalsIgnoreCase("rent"))
						if (r.rentCost<0)
							continue;
					//We are showing this one, so update page info
					curItem +=1;
					if (iPage >= 0)
					{
						if (curItem <= startItem)
							continue;
						if (curItem > iPage * perPage)
							continue;
					}


					//Build the rank info string and display it
					String msg="§A"+r.name + " - ";

					if (r.time>0)
						msg+="§BTime: §A" + Mills2Time(r.time) + " ";
					if (r.cost>0)
						msg+="§BBuy: §A" +  r.amount+ " " + Material.getMaterial(r.cost)+ " ";
					if (r.cost==0)
						msg+="§BBuy: §A" +  Method.format(r.amount)+ " ";					
					if (r.rentCost>0)
						msg+="§BRent: §A" +  r.rentAmount+ " " + Material.getMaterial(r.rentCost)+ " ";
					if (r.rentCost==0)
						msg+="§BRent: §A" +  Method.format(r.rentAmount)+ " ";
					if (r.GetOldGroup() != null)
						msg+="§BRequires group: §A" +  r.GetOldGroup().getName()+ " ";
					sender.sendMessage(msg);				
					if (r.desc != "")
						sender.sendMessage("§BDescription: §A"+r.desc);
				}
				sender.sendMessage("§B-----------------------------------------");
				return true;
			}
			else if (cmd.getName().equalsIgnoreCase("listabs"))
			{
				//Set up the filter and page info
				String sCmd ="";
				int iPage=-1;
				if (args.length > 0)
					if (!isParsableToInt(args[0]))					
						sCmd = args[0];
					else
						iPage = Integer.parseInt(args[0]);
				if ((args.length > 1) && (isParsableToInt(args[1])))
					iPage = Integer.parseInt(args[1]);
				//iPage -= 1;
				int perPage = 5;
				int curItem = -1;
				int startItem = ((iPage-1) * perPage);			

				sender.sendMessage("§B---------------Abilities List---------------");
				for(Ability ab : Abilities.keySet())
				{		

					//check to see if we hide groups we can't get.
					if ( hideUnavaible )
						if (!perms.HasPermission(player, ab.permission, player.getWorld().getName()))
						{
							DebugPrint("Hidding " + ab.name + " from " + player.getName());
							continue;
						}

					//Check filters
					if (sCmd.equalsIgnoreCase("time"))
						if (ab.time<=0)
							continue;

					if (sCmd.equalsIgnoreCase("buy"))
						if (ab.cost<0)
							continue;

					if (sCmd.equalsIgnoreCase("rent"))
						if (ab.rentCost<0)
							continue;
					//We are showing this one, so update page info
					curItem +=1;
					if (iPage >= 0)
					{
						if (curItem <= startItem)
							continue;
						if (curItem > iPage * perPage)
							continue;
					}


					//Build the rank info string and display it
					String msg="§A"+ab.name + " - ";

					if (ab.time>0)
						msg+="§BTime: §A" + Mills2Time(ab.time) + " ";
					if (ab.cost>0)
						msg+="§BBuy: §A" +  ab.amount+ " " + Material.getMaterial(ab.cost)+ " ";
					if (ab.cost==0)
						msg+="§BBuy: §A" +  Method.format(ab.amount)+ " ";					
					if (ab.rentCost>0)
						msg+="§BRent: §A" +  ab.rentAmount+ " " + Material.getMaterial(ab.rentCost)+ " ";
					if (ab.rentCost==0)
						msg+="§BRent: §A" +  Method.format(ab.rentAmount)+ " ";
					if (ab.permission != "")
						msg+="§BRequires perm: §A" +  ab.permission+ " ";
					sender.sendMessage(msg);				
					if (ab.desc != "")
						sender.sendMessage("§BDescription: §A"+ab.desc);
				}
				sender.sendMessage("§B-----------------------------------------");
				return true;
			}
			else if (cmd.getName().equalsIgnoreCase("timerank"))
			{
				//Check for no args. If no args, display basic info
				if (args.length<1)
				{
					sender.sendMessage("§B---------------Time Rank---------------");
					sender.sendMessage("§BVersion: §A"+this.getDescription().getVersion());
					sender.sendMessage("§BDebug: §A"+this.debug);
					sender.sendMessage("§BPermissions: §A"+this.permissions);
					sender.sendMessage("§BHide Unavaible: §A"+this.hideUnavaible );				
					sender.sendMessage("§B-----------------------------------------");				
					return true;
				}
				else if (args[0].equalsIgnoreCase("reload"))
				{//Reload the config file.
					Ranks.clear();
					Ranks = new HashMap<Rank, Long>();
					loadConfig();
					sender.sendMessage("§B[TimeRank] Timerank has been reloaded.");
					return true;
				}
				else if (args[0].equalsIgnoreCase("groups"))
				{//Show advanced listing of the groups.
					String sCmd ="";
					if (args.length > 1)
						sCmd = args[1];	
					sender.sendMessage("§B-----------Advanced Rank List-----------");				
					for(Rank r : Ranks.keySet())
					{						
						if (sCmd.equalsIgnoreCase("time"))
							if (r.time<=0)
								continue;

						if (sCmd.equalsIgnoreCase("buy"))
							if (r.cost<0)
								continue;

						if (sCmd.equalsIgnoreCase("rent"))
							if (r.rentCost<0)
								continue;

						String msg="§A"+r.name + " - ";
						if (r.time>0)
							msg+="§BTime: §A" +Mills2Time(r.time) + " ";
						if (r.cost>0)
							msg+="§BCost: §A" + r.amount+ " " + Material.getMaterial(r.cost)+ " ";
						if (r.cost==0)
							msg+="§BCost: §A" + Method.format(r.amount)+ " ";
						msg+="§BGroup: §A" + r.GetGroup().getName()+ " ";
						if (r.GetOldGroup() != null)
							msg+="§BRequires group: §A" +  r.GetOldGroup().getName()+ " ";				
						sender.sendMessage(msg);				
					}
					sender.sendMessage("§B-----------------------------------------");
					return true;
				}
				else if (args[0].equalsIgnoreCase("group"))
				{//show a lot of info about 1 group.
					if (args.length < 2)
					{
						sender.sendMessage("§CUseage: /timerank group <name>");
						return true;
					}
					sender.sendMessage("§B-----------Advanced Rank List-----------");
					for(Rank r : Ranks.keySet())
					{				
						if (r.name.equalsIgnoreCase(args[1]))
						{
							String msg="§A"+r.name + " - ";
							if (r.time>0)
								msg+="§BTime: §A" + Mills2Time(r.time) + " ";
							if (r.cost>0)
								msg+="§BCost: §A" +  r.amount+ " " + Material.getMaterial(r.cost)+ " ";
							if (r.cost==0)
								msg+="§BCost: §A" +  Method.format(r.amount)+ " ";
							msg+="§BGroup: §A" +  r.GetGroup().getName()+ " ";
							if (r.GetOldGroup() != null)
								msg+="§BRequires group: §A" +  r.GetOldGroup().getName()+ " ";					
							sender.sendMessage(msg);									
						}
						sender.sendMessage("§B-----------------------------------------");
						return true;
					}
				}
				else if (args[0].equalsIgnoreCase("set"))
				{//change a config variable
					if (args.length < 3)
					{//make sure we have at least 3 args
						sender.sendMessage("§CUseage: /timerank set <setting> <value>");
						return true;
					}
					if (args[1].equalsIgnoreCase("debug"))
					{//Set debug value
						if (args[2].equalsIgnoreCase("true"))						
							debug=true;							
						else						
							debug=false;
						sender.sendMessage("§AConfig file updated.");
						saveConfig();
					}
					return true;
				}
				else if (args[0].equalsIgnoreCase("test"))
				{//Test command. This changes A LOT and can/will do whatever I'm trying to figure out at the moment.
					Class<Rank> rClass = Rank.class;
					Field[] methods = rClass.getFields();
					for(Field f : methods)
					{
						for(Rank r : Ranks.keySet())
						{
							try {
								DebugPrint(r.name + ":" + f.getName() + " = " + f.get(r));
							} catch (IllegalArgumentException e) {
								ThrowSimpleError(e);
							} catch (IllegalAccessException e) {
								ThrowSimpleError(e);
							}
						}
					}

					return true;
				}


			}
		}catch(Exception e)
		{//Oops. Dump a bunch of data so we can figure out what caused the error.
			Map<String,String>ErrorInfo = new LinkedHashMap<String,String>();
			//CommandSender sender, Command cmd, String commandLabel, String[] args
			ErrorInfo.put("Msg", "Error running command.");
			ErrorInfo.put("CMD", cmd.getName());
			ErrorInfo.put("Label", commandLabel);;
			ErrorInfo.put("Arguments",Integer.toString(args.length));
			ErrorInfo.put("Args",arrayToString(args, " "));
			ErrorInfo.put("Trace",StracktraceToString(e));
			ErrorLog(ErrorInfo);
		}
		return false;
	}			

	public long GetPlaytime(String player)
	{//Get the play time of a single player and return it in milliseconds
		long now = System.currentTimeMillis();
		long login=0;
		long total=0;
		if (StartTime.containsKey(player))
			login=now - StartTime.get(player);
		if (PlayTime.containsKey(player))
			total=PlayTime.get(player);
		if (total==0)
		{
			loadPlaytime(player);
			if (PlayTime.containsKey(player))
				total=PlayTime.get(player);
		}
		return login + total;
	}	

	public void saveRent()
	{//Save our rented abilites to disk so we can load them later.
		try {			
			File path = new File(mainDirectory+File.separator+"data"+File.separator+"rent.data");
			ObjectOutputStream obj = new ObjectOutputStream(new FileOutputStream(path.getPath()));
			obj.writeObject(RentedAbilities);
			obj.close();
		} catch (FileNotFoundException e) {
			ThrowSimpleError(e);
		} catch (IOException e) {
			ThrowSimpleError(e);
		}
	}

	@SuppressWarnings("unchecked")
	public void loadRent()
	{//Load who has rented what.
		File path = new File(mainDirectory+File.separator+"data"+File.separator+"rent.data");
		if (path.exists())
		{
			try {
				ObjectInputStream obj = new ObjectInputStream(new FileInputStream(path.getPath()));
				RentedRanks = (List<PurchasedRank>)obj.readObject();			
			} catch (FileNotFoundException e) {
				ThrowSimpleError(e);
			} catch (IOException e) {
				ThrowSimpleError(e);
			} catch (ClassNotFoundException e) {
				ThrowSimpleError(e);
			}
		}
	}

	public void savePlaytime()
	{//Save play time for everyone
		for(String p : PlayTime.keySet())
		{					
			savePlaytime(p);
		}		
	}

	public void savePlaytime(Player p)
	{//Save playtime for a single player.
		savePlaytime(p.getName());
	}

	public void savePlaytime(String name)
	{//Save play time for a single players name.
		String path;
		Properties prop = new Properties(); //creates a new properties file	
		try {
			//Update play time
			long playtime =GetPlaytime(name);
			PlayTime.put(name,playtime);
			long now = System.currentTimeMillis();
			StartTime.put(name, now);

			path = mainDirectory+File.separator+"data"+File.separator+name;
			ObjectOutputStream hashfile = new ObjectOutputStream(new FileOutputStream(path));
			prop.put("time", PlayTime.get(name).toString());
			prop.store(hashfile, "Do not edit");
			hashfile.flush();
			hashfile.close();
			//Something went wrong
		}catch(Exception e){
			ThrowSimpleError(e);
		}
	}

	public void loadPlaytime(Player p)
	{//Load play time for a player.
		loadPlaytime(p.getName());
	}

	public void loadPlaytime(String name)
	{ 	//Load play time for a single players name	
		Properties prop = new Properties(); //creates a new properties file
		File path = new File(mainDirectory+File.separator+"data"+File.separator+name);
		if (path.exists())
		{
			try{
				ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path.getPath()));
				prop.load(ois);
				if (prop.getProperty("time") != null)
					PlayTime.put(name,Long.parseLong(prop.getProperty("time")) );
				else
				{
					DebugPrint("Problem loading playtime, returned null for "+name+". Setting it to 0");
					DebugPrint("Problem loading playtime, returned null for "+name+". Setting it to 0");
				}
			}catch(Exception e){
				DebugPrint("Error loading playtime. Setting it to 0");
				PlayTime.put(name,(long) 0);
				ThrowSimpleError(e);

			}
		}
		else
		{
			DebugPrint("Playtime not found for "+name+". Setting it to 0.");
			PlayTime.put(name,(long) 0);
		}
	}

	public void ThrowSimpleError(Exception e,String msg)
	{//Throw a simple error message using our nice formated error system with a single bit of txt to help describe it.
		Map<String, String>ErrorInfo= new LinkedHashMap<String,String>();
		ErrorInfo.put("Msg", msg);
		ErrorInfo.put("Trace", StracktraceToString(e));		
		ErrorLog(ErrorInfo);
	}

	public void ThrowSimpleError(Exception e)
	{//throw a basic error in our nice formated error system.
		Map<String, String>ErrorInfo = new LinkedHashMap<String,String>();
		ErrorInfo.put("Trace", StracktraceToString(e));
		ErrorLog(ErrorInfo);
	}

	public String StracktraceToString(Exception e)	
	{//Take a stack trace and return it in a string we can work with.
		StringWriter sw = new StringWriter();
		e.printStackTrace(new PrintWriter(sw));
		return sw.toString();
	}

	public void DebugPrint(String msg)
	{//Print debug messages if debug is turned on.
		if (debug)
			log.info("[Time Rank] " + msg);
	}	

	public void ErrorLog(Map<String, String> ErrorList)
	{//A nicely formated error message for the console.
		log.severe("===================================================");
		log.severe("=              ERROR REPORT START                 =");
		log.severe("===================================================");
		log.severe("=               TIME RANK ERROR                   =");
		log.severe("=         INCLUDE WHEN ASKING FOR HELP            =");
		log.severe("===================================================");
		log.severe("Version: "+this.getDescription().getVersion());
		log.severe("Permissions: "+this.permissions);
		log.severe("Ranks Loaded: "+Ranks.size());
		if (ErrorList != null)
		{
			log.severe("===================ERROR INFO===================");
			for (String key:ErrorList.keySet())
			{
				log.severe(key + ": " + ErrorList.get(key));
			}
		}	    
		log.severe("===================================================");
		log.severe("=              ERROR REPORT ENDED                 =");
		log.severe("===================================================");
	}

	public static String arrayToString(String[] a, String separator) {
		StringBuffer result = new StringBuffer();
		if (a.length > 0) {
			result.append(a[0]);
			for (int i=1; i<a.length; i++) {
				result.append(separator);
				result.append(a[i]);
			}
		}
		return result.toString();
	}

	public void BuyRank(Player player, String rankname)
	{	//Given a player and a rank. Try to have that player buy that rank.	
		DebugPrint("Looking for " + rankname);
		for(Rank r : Ranks.keySet())
		{
			DebugPrint("Checking "+rankname + "=" + r.name);
			if (r.name.equalsIgnoreCase(rankname))
			{//found the rank we are looking for. See if it is for sale		
				DebugPrint(rankname + " found. Checking cost: " +r.cost);
				//Check if we have passed the minimum time				
				if (r.cost>=0 && r.minTime < GetPlaytime(player.getName()))
				{
					if (r.cost==0)
					{//use money
						DebugPrint(rankname + " Using money for cost");
						if (Method.getAccount(player.getName()).hasEnough(r.amount))
						{
							DebugPrint("You have the required money");															
							switch(PromotePlayer(player,r))
							{
							case 0://Everything went fine
								Method.getAccount(player.getName()).subtract(r.amount); //Consume money.
								Map<String, String> replace = new HashMap<String, String>();				
								replace.putAll(ProcessMsgVars(player));
								replace.putAll(ProcessMsgVars(r));
								String msg = ProcessMsg(r.msg, replace);
								if (r.broadcast)				
									getServer().broadcastMessage(msg);
								else
									player.sendMessage(msg);
								break;
							case 1://Not in old group
								player.sendMessage("You need to be in " + r.GetOldGroup().getName() + " to be buy the rank "+r.name);
								break;
							case 2:
								player.sendMessage("You are already in " + r.GetGroup().getName() +" which " + r.name + " grants.");
								break;	
							}//end switch
						}
						else
						{
							player.sendMessage("You don't have enough items. You need at least " + Method.format(r.amount));
						}
					}

					else
					{//use block id				
						DebugPrint(rankname + " Using block "+ r.cost +" for cost");
						ItemStack item = new ItemStack(r.cost, (int) r.amount);	
						//right now the player must have exactly that item.
						//Example: a stack of 5 gold bars instead of a stack of 10 gold bars.
						if (CheckItems(player,item))
						{
							DebugPrint("You have the required items");															
							switch(PromotePlayer(player,r))
							{
							case 0://Everything went fine	
								ConsumeItems(player,item);
								Map<String, String> replace = new HashMap<String, String>();				
								replace.putAll(ProcessMsgVars(player));
								replace.putAll(ProcessMsgVars(r));
								String msg = ProcessMsg(r.msg, replace);
								if (r.broadcast)				
									getServer().broadcastMessage(msg);
								else
									player.sendMessage(msg);
								break;
							case 1://Not in old group
								player.sendMessage("You need to be in " + r.GetOldGroup().getName() + " to be buy the rank "+r.name);
								break;
							case 2:
								player.sendMessage("You are already in " + r.GetGroup().getName() +" which " + r.name + " grants.");
								break;	
							}//end switch
						}//end player has items check
						else
						{							
							player.sendMessage("You don't have enough items. You need at least " + r.amount + " of " + Material.getMaterial(r.cost));
						}
					}//end check to see if we are using money/block
				}
			}//end check to see if we can buy this
		}//end check of rank name
	}

	public void RentRank(Player player,String rankname)
	{//RentedGroups
		DebugPrint("Looking for " + rankname);
		for(Rank r : Ranks.keySet())
		{
			DebugPrint("Checking "+rankname + "=" + r.name);
			if (r.name.equalsIgnoreCase(rankname))
			{//found the rank we are looking for. See if it is for sale		
				DebugPrint(rankname + " found. Checking cost: " +r.rentCost);
				if (r.rentCost>=0)
				{
					if (r.rentCost==0)
					{//use money
						DebugPrint(rankname + " Using money for cost");
						if (Method.getAccount(player.getName()).hasEnough(r.rentAmount))
						{
							DebugPrint("You have the required items");															
							switch(PromotePlayer(player,r))
							{
							case 0://Everything went fine
								Method.getAccount(player.getName()).subtract(r.rentAmount); //Consume money.								
								RentedRanks.add(new PurchasedRank(player.getName(), r));
								Map<String, String> replace = new HashMap<String, String>();				
								replace.putAll(ProcessMsgVars(player));
								replace.putAll(ProcessMsgVars(r));
								String msg = ProcessMsg(r.rentGainedMsg, replace);
								if (r.broadcast)				
									getServer().broadcastMessage(msg);
								else
									player.sendMessage(msg);
								break;
							case 1://Not in old group
								player.sendMessage("You need to be in " + r.GetOldGroup().getName() + " to be buy the rank "+r.name);
								break;
							case 2:
								player.sendMessage("You are already in " + r.GetGroup().getName() +" which " + r.name + " grants.");
								break;	
							}//end switch
						}
						else
						{
							player.sendMessage("You don't have enough items. You need at least " + Method.format(r.rentAmount));
						}
					}
					else
					{//use block id				
						DebugPrint(rankname + " Using block "+ r.rentCost +" for cost");
						ItemStack item = new ItemStack(r.rentCost, (int) r.rentAmount);
						if (CheckItems(player,item))
						{
							DebugPrint("You have the required items");															
							switch(PromotePlayer(player,r))
							{
							case 0://Everything went fine
								ConsumeItems(player,item); //Consume items.
								Map<String, String> replace = new HashMap<String, String>();				
								replace.putAll(ProcessMsgVars(player));
								replace.putAll(ProcessMsgVars(r));
								String msg = ProcessMsg(r.rentGainedMsg, replace);
								if (r.broadcast)				
									getServer().broadcastMessage(msg);
								else
									player.sendMessage(msg);
								break;
							case 1://Not in old group
								player.sendMessage("You need to be in " + r.GetOldGroup().getName() + " to be buy the rank "+r.name);
								break;
							case 2:
								player.sendMessage("You are already in " + r.GetGroup() +" which " + r.name + " grants.");
								break;	
							}//end switch
						}//end player has items check
						else
						{							
							player.sendMessage("You don't have enough items. You need at least " + r.rentAmount + " of " + Material.getMaterial(r.rentCost));
						}
					}//end check to see if we are using money
				}//end check to see if we can buy this
			}//end check of rank name
		}
	}

	public void BuyAbility(Player player, String abilityname)
	{	//Given a player and a rank. Try to have that player buy that rank.	
		DebugPrint("Looking for " + abilityname);
		for(Ability ab : Abilities.keySet())
		{
			DebugPrint("Checking "+abilityname + "=" + ab.name);
			if (ab.name.equalsIgnoreCase(abilityname))
			{//found the ability we are looking for. See if it is for sale		
				DebugPrint(abilityname + " found. Checking cost: " +ab.cost);
				//Check if we have passed the minimum time				
				if (ab.cost>=0 && ab.minTime < GetPlaytime(player.getName()))
				{
					if (ab.cost==0)
					{//use money
						DebugPrint(abilityname + " Using money for cost");
						if (Method.getAccount(player.getName()).hasEnough(ab.amount))
						{
							DebugPrint("You have the required money");															
							switch(AddPlayerNode(player,ab))
							{
							case 0://Everything went fine
								Method.getAccount(player.getName()).subtract(ab.amount); //Consume money.
								Map<String, String> replace = new HashMap<String, String>();				
								replace.putAll(ProcessMsgVars(player));
								replace.putAll(ProcessMsgVars(ab));
								String msg = ProcessMsg(ab.msg, replace);
								if (ab.broadcast)				
									getServer().broadcastMessage(msg);
								else
									player.sendMessage(msg);
								break;
							case 1://Don't have the permissions
								player.sendMessage("You don't have the permissions to buy " + ab.name);
								break;
							case 2:
								player.sendMessage("You are already all the permissions " + ab.name + " grants.");
								break;		
							}//end switch
						}
						else
						{
							player.sendMessage("You don't have enough items. You need at least " + Method.format(ab.amount));
						}
					}

					else
					{//use block id				
						DebugPrint(abilityname + " Using block "+ ab.cost +" for cost");
						ItemStack item = new ItemStack(ab.cost, (int) ab.amount);	
						//right now the player must have exactly that item.
						//Example: a stack of 5 gold bars instead of a stack of 10 gold bars.
						if (CheckItems(player,item))
						{
							DebugPrint("You have the required items");															
							switch(AddPlayerNode(player,ab))
							{
							case 0://Everything went fine	
								ConsumeItems(player,item);
								Map<String, String> replace = new HashMap<String, String>();				
								replace.putAll(ProcessMsgVars(player));
								replace.putAll(ProcessMsgVars(ab));
								String msg = ProcessMsg(ab.msg, replace);
								if (ab.broadcast)				
									getServer().broadcastMessage(msg);
								else
									player.sendMessage(msg);
								break;
							case 1://Don't have the permissions
								player.sendMessage("You don't have the permissions to buy " + ab.name);
								break;
							case 2:
								player.sendMessage("You are already all the permissions " + ab.name + " grants.");
								break;	
							}//end switch
						}//end player has items check
						else
						{							
							player.sendMessage("You don't have enough items. You need at least " + ab.amount + " of " + Material.getMaterial(ab.cost));
						}
					}//end check to see if we are using money/block
				}
			}//end check to see if we can buy this
		}//end check of rank name
	}
	
	public void RentAbility(Player player,String abilityname)
	{//RentedGroups
		DebugPrint("Looking for " + abilityname);
		for(Ability ab : Abilities.keySet())
		{
			DebugPrint("Checking "+ abilityname + "=" + ab.name);
			if (ab.name.equalsIgnoreCase(abilityname))
			{//found the rank we are looking for. See if it is for sale		
				DebugPrint(abilityname + " found. Checking cost: " +ab.rentCost);
				if (ab.rentCost>=0)
				{
					if (ab.rentCost==0)
					{//use money
						DebugPrint(abilityname + " Using money for cost");
						if (Method.getAccount(player.getName()).hasEnough(ab.rentAmount))
						{
							DebugPrint("You have the required items");															
							switch(AddPlayerNode(player,ab))
							{
							case 0://Everything went fine
								Method.getAccount(player.getName()).subtract(ab.rentAmount); //Consume money.								
								RentedAbilities.add(new PurchasedAbility(player.getName(), ab));
								Map<String, String> replace = new HashMap<String, String>();				
								replace.putAll(ProcessMsgVars(player));
								replace.putAll(ProcessMsgVars(ab));
								String msg = ProcessMsg(ab.rentGainedMsg, replace);
								if (ab.broadcast)				
									getServer().broadcastMessage(msg);
								else
									player.sendMessage(msg);
								break;
							case 1://Don't have the permissions
								player.sendMessage("You don't have the permissions to buy " + ab.name);
								break;
							case 2:
								player.sendMessage("You are already all the permissions " + ab.name + " grants.");
								break;	
							}//end switch
						}
						else
						{
							player.sendMessage("You don't have enough items. You need at least " + Method.format(ab.rentAmount));
						}
					}
					else
					{//use block id				
						DebugPrint(abilityname + " Using block "+ ab.rentCost +" for cost");
						ItemStack item = new ItemStack(ab.rentCost, (int) ab.rentAmount);
						if (CheckItems(player,item))
						{
							DebugPrint("You have the required items");															
							switch(AddPlayerNode(player,ab))
							{
							case 0://Everything went fine
								ConsumeItems(player,item); //Consume items.
								Map<String, String> replace = new HashMap<String, String>();				
								replace.putAll(ProcessMsgVars(player));
								replace.putAll(ProcessMsgVars(ab));
								String msg = ProcessMsg(ab.rentGainedMsg, replace);
								if (ab.broadcast)				
									getServer().broadcastMessage(msg);
								else
									player.sendMessage(msg);
								break;
							case 1://Don't have the permissions
								player.sendMessage("You don't have the permissions to buy " + ab.name);
								break;
							case 2:
								player.sendMessage("You are already all the permissions " + ab.name + " grants.");
								break;	
							}//end switch
						}//end player has items check
						else
						{							
							player.sendMessage("You don't have enough items. You need at least " + ab.rentAmount + " of " + Material.getMaterial(ab.rentCost));
						}
					}//end check to see if we are using money
				}//end check to see if we can buy this
			}//end check of rank name
		}
	}
	
	public int PromotePlayer(Player p, Rank r)
	{
		//Entry entry = perms.getHandler().getUserObject(p.getWorld().getName(), p.getName());
		DebugPrint("PromotePlayer: Checking " + p.getName() + " for " + r.name);
		//check to see if we are not already in this group.
		if (!perms.inGroup(p, r.GetGroup().getWorld(),r.GetGroup().getName()))
		{//if we are not in the group check to see if we are in the old/leser.
			DebugPrint("PromotePlayer: " + p.getName() + " is not in group " + r.GetGroup().getName() + " yet.");
			if (r.GetOldGroup() != null)
			{	
				if (!perms.inGroup(p,r.GetOldGroup().getWorld(), r.GetOldGroup().getName()))				
					return 1;
				//we are in the old/lesser group. See if we have enough time to promote.	
				DebugPrint("PromotePlayer: " + p.getName() + " is in old group " + r.GetOldGroup().getName() + ".");					
			}	

			DebugPrint("PromotePlayer: " + p.getName() + " is ready to be promoted.");
			//everything looks good. Lets promote!				
			perms.AddGroup(p, r.GetGroup().getWorld(),r.GetGroup().getName());	

			if (r.remove && r.GetOldGroup() != null)
				perms.RemoveGroup(p.getWorld().getName(),p.getName(), r.GetOldGroup().getWorld(),r.GetOldGroup().getName());
			//getServer().broadcastMessage(ChatColor.AQUA + p.getName() + ChatColor.YELLOW + " has been promoted to " + ChatColor.AQUA + r.GetGroup().getName());
			return 0;													
		}
		else
			return 2;//already in that group
	}
	
	public int AddPlayerNode(Player p, Ability ab)
	{
		//Entry entry = perms.getHandler().getUserObject(p.getWorld().getName(), p.getName());
		DebugPrint("AddPlayerNode: Checking " + p.getName() + " for " + ab.name);
		//check to see if we already have all the permissions.
		boolean hasAll=true;
		for(String node : ab.Nodes)
		{
			if (!perms.HasPermission(p, node))
			{			
				hasAll=false;
				break;				
			}
		}
		
		
		if (!hasAll)
		{//We will gain at least one permission from this.
			DebugPrint("AddPlayerNode: " + p.getName() + " will gain at least 1 permission from " + ab.name);
			//check to see if we require a permission
			if (ab.permission != "")
			{	
				if (!perms.HasPermission(p, ab.permission, ab.world))				
					return 1;
				//we have the required permission node	
				DebugPrint("AddPlayerNode: " + p.getName() + " has the permission node " + ab.permission + ".");					
			}	

			DebugPrint("AddPlayerNode: " + p.getName() + " is ready to have the permissions added.");
			//everything looks good. Lets promote!	
			for(String node : ab.Nodes)
			{							
				perms.AddNode(p, node, ab.world);
				break;				
			}
			
			return 0;													
		}
		else
			return 2;//already has all the nodes
	}	

	public void RemovePlayerNode(Player p, Ability ab)
	{
		//Entry entry = perms.getHandler().getUserObject(p.getWorld().getName(), p.getName());
		DebugPrint("RemovePlayerNode: Removing " + ab.name + " from " + p.getName());				
		
		for(String node : ab.Nodes)
		{							
			perms.RemoveNode(p, node, ab.world);
			break;				
		}
	}	
	
	public int CheckRanks(Player[] player)
	{
		int promoted=0;
		for(Player p : player)
		{
			if (CheckRanks(p))
				promoted += 1;
		}
		return promoted;
	}

	public boolean CheckRanks(Player p)
	{										
		long time = GetPlaytime(p.getName());
		DebugPrint("CheckRanks: Checking " + p.getName() + " against " + Integer.toString(Ranks.size()) + " ranks");
		//for each player on the server, loop though ranks and check if we need to promote.
		for(Rank r : Ranks.keySet())
		{			
			DebugPrint("CheckRanks: Checking " + p.getName() + " for " + r.GetGroup().getName());				
			if (time >= r.time && r.time>=0)
			{//Time looks good, lets try to promote.
				DebugPrint("CheckRanks: " + p.getName() + " time is great enough. Trying to promote to " + r.GetGroup().getName());
				int doPromote =PromotePlayer(p,r);
				switch(doPromote)
				{
				case 0:
					DebugPrint("CheckRanks: " + p.getName() + " is now in " + r.GetGroup().getName());
					Map<String, String> replace = new HashMap<String, String>();				
					replace.putAll(ProcessMsgVars(p));
					replace.putAll(ProcessMsgVars(r));
					String msg = ProcessMsg(r.msg, replace);
					if (r.broadcast)				
						getServer().broadcastMessage(msg);
					else
						p.sendMessage(msg);
					return true;			
				case 1:
					DebugPrint("CheckRanks: " + p.getName() + " is not in " + r.GetOldGroup().getName());
					break;					
				case 2:
					DebugPrint("CheckRanks: " + p.getName() + " is already in " + r.GetGroup().getName());
					break;					
				}				
			}
		}		
		return false;
	}	

	public void CheckRented(int interval)
	{		
		for (Iterator<PurchasedRank> iter = RentedRanks.iterator() ; iter.hasNext();)
		{
			PurchasedRank pa = iter.next();
			Player p = getServer().getPlayer(pa.playername);
			if (p != null && p.isOnline())
			{//player is online, remove some duration and check if we need to remove or not.
				DebugPrint("Check to see if " + p.getName() + ":"+ pa.rank.name + "  expired");
				DebugPrint("Info: Used " + pa.rank.rentTime/1000*20 + "  / " + pa.durationTicks);
				pa.durationTicks -= interval;
				if (pa.durationTicks <= 0)
				{//Rent ran out. Demote back to orginal group.
					DebugPrint("Demoting " + p.getName());
					perms.RemoveGroup(p.getWorld().getName(), p.getName(), pa.rank.GetOldGroup().getWorld(), pa.rank.GetOldGroup().getName());
					if (pa.rank.rentReturn && pa.rank.GetOldGroup() != null)
						perms.AddGroup(p, pa.rank.GetGroup().getWorld(), pa.rank.GetGroup().getName());
					Map<String, String> replace = new HashMap<String, String>();				
					replace.putAll(ProcessMsgVars(p));
					replace.putAll(ProcessMsgVars(pa.rank));
					String msg = ProcessMsg(pa.rank.rentLostMsg, replace);
					if (pa.rank.rentBroadcast)				
						getServer().broadcastMessage(msg);
					else
						p.sendMessage(msg);
					iter.remove();
				}
				else
				{
					long left = pa.durationTicks*50; //convert from ticks to milliseconds.					
					DebugPrint( p.getName() + " still has " + Mills2Time(left) + " in " + pa.rank.GetGroup().getName() );
				}

			}
		}
		
		for (Iterator<PurchasedAbility> iter = RentedAbilities.iterator() ; iter.hasNext();)
		{
			PurchasedAbility pa = iter.next();
			Player p = getServer().getPlayer(pa.playername);
			if (p != null && p.isOnline())
			{//player is online, remove some duration and check if we need to remove or not.
				DebugPrint("Check to see if " + p.getName() + ":"+ pa.ability.name + "  expired");
				DebugPrint("Info: Used " + pa.ability.rentTime/1000*20 + "  / " + pa.durationTicks);
				pa.durationTicks -= interval;
				if (pa.durationTicks <= 0)
				{//Rent ran out. Remove ability.
					DebugPrint("Removing " + pa.ability.name + " from " + p.getName());
					RemovePlayerNode(p,pa.ability);
					Map<String, String> replace = new HashMap<String, String>();				
					replace.putAll(ProcessMsgVars(p));
					replace.putAll(ProcessMsgVars(pa.ability));
					String msg = ProcessMsg(pa.ability.rentLostMsg, replace);
					if (pa.ability.rentBroadcast)				
						getServer().broadcastMessage(msg);
					else
						p.sendMessage(msg);
					iter.remove();
				}
				else
				{
					long left = pa.durationTicks*50; //convert from ticks to milliseconds.					
					DebugPrint( p.getName() + " still has " + Mills2Time(left) + " in " + pa.ability.name );
				}

			}
		}
	}

	public boolean CheckAbilities(Player p)
	{										
		long time = GetPlaytime(p.getName());
		DebugPrint("CheckAbilities: Checking " + p.getName() + " against " + Integer.toString(Abilities.size()) + " ranks");
		//for each player on the server, loop though ranks and check if we need to promote.
		for(Ability ab : Abilities.keySet())
		{			
			DebugPrint("CheckAbilities: Checking " + p.getName() + " for " + ab.name);				
			if (time >= ab.time && ab.time>=0)
			{//Time looks good, lets try to promote.
				DebugPrint("CheckAbilities: " + p.getName() + " time is great enough. Trying to promote to " + ab.name);
				switch(AddPlayerNode(p,ab))
				{
				case 0:
					DebugPrint("CheckAbilities: " + p.getName() + " is now has " + ab.name);
					Map<String, String> replace = new HashMap<String, String>();				
					replace.putAll(ProcessMsgVars(p));
					replace.putAll(ProcessMsgVars(ab));
					String msg = ProcessMsg(ab.msg, replace);
					if (ab.broadcast)				
						getServer().broadcastMessage(msg);
					else
						p.sendMessage(msg);
					return true;			
				case 1:
					DebugPrint("CheckAbilities: " + p.getName() + " is not in " + ab.name);
					break;					
				case 2:
					DebugPrint("CheckAbilities: " + p.getName() + " is already in " + ab.name);
					break;					
				}				
			}
		}		
		return false;
	}		
	
	public void update(int interval) {
		CheckRanks(getServer().getOnlinePlayers());
		CheckRented(interval);		
		//savePlaytime();		
	}

	public String Mills2Time(long mills)
	{
		long time = mills / 1000;  
		String seconds = Integer.toString((int)(time % 60));  
		String minutes = Integer.toString((int)((time % 3600) / 60));  
		String hours = Integer.toString((int)(time / 3600));    
		if (seconds.length() < 2) {  
			seconds = "0" + seconds;  
		}  
		if (minutes.length() < 2) {  
			minutes = "0" + minutes;  
		}  
		if (hours.length() < 2) {  
			hours = "0" + hours;  
		}
		return  hours + ":" + minutes + ":" + seconds;
	}

	public long Time2Mills(String time)
	{
		SimpleDateFormat format = new SimpleDateFormat("kk:mm:ss");		
		try {
			Date d = format.parse(time);
			return d.getTime();
		} catch (ParseException e) {
			ThrowSimpleError(e);
			return 0;
		}		
	}

	public Player matchPlayer(String filter)
	{
		Player[] players = getServer().getOnlinePlayers();
		for (Player player : players) {
			if (player.getName().equalsIgnoreCase(filter)) {
				return player;
			}
		}
		return null;
	}

	private String ProcessMsg(String msg,Map<String, String>replace)
	{
		for(String from : replace.keySet())
		{
			String to = replace.get(from);
			//DebugPrint("From: "+from);
			//DebugPrint("To: "+to);

			msg = msg.replaceAll("<" + from + ">",to);

		}
		return ProcessMsg(msg);

	}

	private String ProcessMsg(String msg)
	{	
		msg = msg.replaceAll("&", "§");
		msg = msg.replaceAll("§§", "&");
		return msg;
		//return msg.replaceAll("&[\\d]", "§$1");			
	}

	public boolean isParsableToInt(String i)
	{
		try
		{
			Integer.parseInt(i);
			return true;
		}
		catch(NumberFormatException nfe)
		{
			return false;
		}
	}

	private Map<String,String>ProcessMsgVars(Player p)
	{
		Map<String, String> replace = new HashMap<String, String>();
		if (p != null)
		{
			replace.put("player.name",p.getName());
			replace.put("player.world",p.getWorld().getName());
		}
		else
		{
			replace.put("player.name","Console");
			replace.put("player.world","None");
		}
		return replace;
	}

	@SuppressWarnings("unused")
	private Map<String,String>ProcessMsgVars(GenericGroup g)
	{
		Map<String, String> replace = new HashMap<String, String>();
		replace.put("group.name", g.getName());
		replace.put("group.world",g.getWorld());		
		return replace;
	}
	private Map<String,String>ProcessMsgVars(Rank r)

	{
		Map<String, String> replace = new HashMap<String, String>();		
		replace.put("rank.group", r.GetGroup().getName());
		if (r.GetOldGroup() != null)
			replace.put("rank.oldgroup", r.GetOldGroup().getName());
		else
			replace.put("rank.oldgroup", "");
		replace.put("rank.world",r.GetGroup().getWorld());	
		Class<Rank> rClass = Rank.class;
		Field[] methods = rClass.getFields();
		for(Field f : methods)
		{
			try {
				replace.put("rank."+f.getName(),f.get(r).toString());
			} catch (IllegalArgumentException e) {
				DebugPrint("Can not get property " + f.getName());
			} catch (IllegalAccessException e) {
				DebugPrint("Can not get property " + f.getName());
			} catch (Exception e)
			{
				ThrowSimpleError(e);
			}

		}

		return replace;
	}
	private Map<String,String>ProcessMsgVars(Ability ab)
	{
		Map<String, String> replace = new HashMap<String, String>();					
		Class<Ability> abClass = Ability.class;
		Field[] methods = abClass.getFields();
		for(Field f : methods)
		{
			try {
				replace.put("ability."+f.getName(),f.get(ab).toString());
			} catch (IllegalArgumentException e) {
				DebugPrint("Can not get property " + f.getName());
			} catch (IllegalAccessException e) {
				DebugPrint("Can not get property " + f.getName());
			} catch (Exception e)
			{
				ThrowSimpleError(e);
			}

		}

		return replace;
	}
	
	private boolean CheckItems(Player player, ItemStack costStack)
	{
		//make sure we have enough
		int cost = costStack.getAmount();
		boolean hasEnough=false;
		for (ItemStack invStack : player.getInventory().getContents())
		{
			if(invStack == null)
				continue;
			if (invStack.getTypeId() == costStack.getTypeId()) {

				int inv = invStack.getAmount();				
				if (cost - inv >= 0) {
					cost = cost - inv;
				} else {
					hasEnough=true;
					break;
				}			
			}
		}
		return hasEnough;		
	}

	private boolean ConsumeItems(Player player, ItemStack costStack)
	{		
		//Loop though each item and consume as needed. We should of already
		//checked to make sure we had enough with CheckItems.
		for (ItemStack invStack : player.getInventory().getContents())
		{
			if(invStack == null)
				continue;

			if (invStack.getTypeId() == costStack.getTypeId()) {
				int inv = invStack.getAmount();
				int cost = costStack.getAmount();
				if (cost - inv >= 0) {
					costStack.setAmount(cost - inv);
					player.getInventory().remove(invStack);
				} else {
					costStack.setAmount(0);
					invStack.setAmount(inv - cost);
					break;
				}				
			}
		}
		return true;		
	}
	class TimeRankChecker implements Runnable {

		private timerank plugin;
		private final int interval;

		public TimeRankChecker(timerank origin, final int interval) {
			this.plugin = origin;
			this.interval = interval;
		}

		@Override
		public void run() {	    	
			plugin.update(interval);
		}

	}	
}


