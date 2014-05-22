/*
 * Copyright 2014 Ben Gibbs.
 *
 *     This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

 ****************************************************************************
 * This software is a plugin for the Bukkit Minecraft Server				*
 * 														*
 ****************************************************************************

 */

package com.wasteofplastic.uuidwatcher;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class UUIDWatcher extends JavaPlugin implements Listener {
    boolean logDebug = false; // For debugging purposes
    private FileConfiguration config;
    private Economy econ = null;

    @Override
    public void onDisable() {
	saveConfig();
    }

    @Override
    public void onEnable() {	
	saveDefaultConfig();
	config = getConfig();
	loadPlayers();
	// Set up economy and permissions via Vault
	RegisteredServiceProvider<Economy> economyProvider = Bukkit.getServer().getServicesManager()
		.getRegistration(net.milkbowl.vault.economy.Economy.class);
	if (economyProvider != null) {
	    econ = economyProvider.getProvider();
	} else {
	    getLogger().severe("Could not set up Economy!");
	}

	// Register events
	PluginManager pm = getServer().getPluginManager();		
	pm.registerEvents(this, this); 
	// Send stats
	try {
	    MetricsLite metrics = new MetricsLite(this);
	    metrics.start();
	} catch (IOException e) {
	    // Failed to submit the stats :-(
	    getLogger().warning("Failed to submit the stats");
	}
    }



    private void loadPlayers() {
	// Go through every player that's logged in and check if they are in the file
	for (Player player : this.getServer().getOnlinePlayers()) {
	    checkPlayer(player);
	}

    }

    private void wipeInfo(final String playerName, final UUID check, final UUID playerUUID) {
	getServer().getScheduler().runTask(this, new Runnable() {
	    @Override
	    public void run() {

		if (config.getBoolean("wipe.vault",false)) {
		    //Zero the balance
		    getLogger().info("Trying to wipe all Vault accounts in all worlds for " + playerName);
		    // Retrieve the current balance of this player in oldWorld
		    if (econ != null) { 
			Double oldBalance = roundDown(econ.getBalance(playerName),2);
			econ.withdrawPlayer(playerName, oldBalance);
			getLogger().info("Deleting " + econ.format(oldBalance) + " from " + playerName + "'s general economy account");
			// In every world
			for (World world : Bukkit.getServer().getWorlds()) {
			    // Retrieve the current balance of this player in oldWorld
			    oldBalance = roundDown(econ.getBalance(playerName, world.getName()),2);
			    econ.withdrawPlayer(playerName, world.getName(), oldBalance);
			    getLogger().info("Deleting " + econ.format(oldBalance) + " from " + playerName + "'s "+world.getName() +" world economy account");
			} 
		    } else {
			getLogger().severe("Vault is not loaded!");
		    }
		}
		// Wipe balance using a console command
		// Example:
		// eco set [playername] 0
		// 
		List<String> wipeBalanceList = config.getStringList("wipe.balance");
		for (String wipeBalance : wipeBalanceList) {
		    if (!wipeBalance.isEmpty()) {
			// Substitute
			wipeBalance = wipeBalance.toLowerCase().replace("[playername]", playerName);
			getLogger().info("Wiping balance by running console command " + wipeBalance);
			getServer().dispatchCommand(getServer().getConsoleSender(), wipeBalance);
		    }
		}
		if (config.getBoolean("wipe.worldguard",false)) {
		    //Zero the balance
		}
		String wipe = config.getString("wipe.perms","");
		if (!wipe.isEmpty()) {
		    // Substitute
		    wipe = wipe.toLowerCase().replace("[playername]", playerName);
		    wipe = wipe.toLowerCase().replace("[oldUUID]", check.toString());
		    getServer().dispatchCommand(getServer().getConsoleSender(), wipe);

		}
	    }
	});

    }

    /**
     * @param event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onLogin(PlayerJoinEvent event) {
	// Get player object who logged in
	checkPlayer(event.getPlayer());
    }

    private void checkPlayer(Player player) {
	// Get their name
	final String playerName = player.getName();
	getLogger().info("Checking " + playerName);
	// Get their UUID
	final UUID playerUUID = player.getUniqueId();
	if (config.get("players." + playerName.toLowerCase()) != null) {
	    //Check UUID
	    try {
		UUID check = UUID.fromString(config.getString("players." + playerName.toLowerCase()));
		if (!check.equals(playerUUID)) {
		    // It's the right name, but not the right player
		    getLogger().info(playerName + " is no longer the player we thought they were. Triggering player wipe...");
		    // Wipe info
		    wipeInfo(playerName, check, playerUUID);			
		} // else do nothing
	    } catch (Exception e) {
		// UUID conversion failed - corrupted file?
		getLogger().severe("UUID lookup for " + playerName + " failed - corrupted config.yml?");
		e.printStackTrace();
	    }
	} else {
	    // Add this player to the config file
	    getLogger().info("Adding new player " + playerName);
	    config.set("players."+playerName.toLowerCase(), playerUUID.toString());
	    saveConfig();
	}
    }

    /**
     * Rounds a double down to a set number of places
     * @param value
     * @param places
     * @return
     */
    private double roundDown(double value, int places) {
	BigDecimal bd = new BigDecimal(value);
	bd = bd.setScale(places, RoundingMode.HALF_DOWN);
	return bd.doubleValue();
    }
}