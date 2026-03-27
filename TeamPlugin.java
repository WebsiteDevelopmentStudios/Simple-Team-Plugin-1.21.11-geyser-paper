package com.teamplugin;

import com.teamplugin.commands.TeamCommand;
import com.teamplugin.listeners.ChatListener;
import com.teamplugin.managers.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;

public class TeamPlugin extends JavaPlugin {

    private TeamManager teamManager;
    private static TeamPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        teamManager = new TeamManager(this);

        // Register command
        TeamCommand teamCmd = new TeamCommand(this);
        getCommand("team").setExecutor(teamCmd);
        getCommand("team").setTabCompleter(teamCmd);

        // Register listener
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        getLogger().info("TeamPlugin enabled!");
    }

    @Override
    public void onDisable() {
        if (teamManager != null) teamManager.save();
        getLogger().info("TeamPlugin disabled.");
    }

    public static TeamPlugin getInstance() { return instance; }
    public TeamManager getTeamManager()    { return teamManager; }

    /**
     * Convenience: get a coloured message from config, with the prefix applied.
     */
    public String msg(String key) {
        String prefix = getConfig().getString("messages.prefix", "&8[&bTeams&8] ");
        String raw    = getConfig().getString("messages." + key, "&cMissing message: " + key);
        return color(prefix + raw);
    }

    /**
     * Colour-code a string using & codes.
     */
    public static String color(String s) {
        return s.replace("&", "§");
    }

    /**
     * Convert a legacy-colour string to an Adventure Component.
     */
    public static Component component(String s) {
        return LegacyComponentSerializer.legacySection().deserialize(color(s));
    }
}
