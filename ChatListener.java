package com.teamplugin.listeners;

import com.teamplugin.TeamPlugin;
import com.teamplugin.models.Team;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final TeamPlugin plugin;

    public ChatListener(TeamPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Team team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());

        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (team != null && plugin.getTeamManager().isTeamChatEnabled(player.getUniqueId())) {
            // Cancel global chat and send to team only
            event.setCancelled(true);

            String format = plugin.getConfig().getString("settings.chat-format", "[{team}] {player}: {message}");
            String formatted = TeamPlugin.color(
                    format.replace("{team}", team.getDisplayName())
                          .replace("{player}", player.getName())
                          .replace("{message}", message)
            );
            Component msg = TeamPlugin.component(formatted);

            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (team.hasMember(online.getUniqueId())) {
                    online.sendMessage(msg);
                }
            }
            // Also log to console
            plugin.getServer().getConsoleSender().sendMessage(msg);

        } else {
            // Global chat – inject team prefix into display name
            if (team != null) {
                String globalFormat = plugin.getConfig().getString("settings.global-format", "[{team}] {player}: {message}");
                String formatted = TeamPlugin.color(
                        globalFormat.replace("{team}", team.getDisplayName())
                                    .replace("{player}", player.getName())
                                    .replace("{message}", message)
                );
                event.setCancelled(true);
                Component msg = TeamPlugin.component(formatted);
                plugin.getServer().broadcast(msg);
                plugin.getServer().getConsoleSender().sendMessage(msg);
            }
            // If no team, let Minecraft handle it normally
        }
    }
}
