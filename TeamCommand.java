package com.teamplugin.commands;

import com.teamplugin.TeamPlugin;
import com.teamplugin.managers.TeamManager;
import com.teamplugin.models.Team;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class TeamCommand implements CommandExecutor, TabCompleter {

    private final TeamPlugin plugin;
    private final TeamManager tm;

    public TeamCommand(TeamPlugin plugin) {
        this.plugin = plugin;
        this.tm     = plugin.getTeamManager();
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create"   -> handleCreate(player, args);
            case "invite"   -> handleInvite(player, args);
            case "join"     -> handleJoin(player, args);
            case "leave"    -> handleLeave(player);
            case "kick"     -> handleKick(player, args);
            case "info"     -> handleInfo(player, args);
            case "settings" -> handleSettings(player, args);
            case "chat"     -> handleChat(player, args);
            case "admin"    -> handleAdmin(player, args);
            default         -> sendHelp(player);
        }
        return true;
    }

    // ── /team create [name] [password] ───────────────────────────────────────

    private void handleCreate(Player player, String[] args) {
        if (tm.getPlayerTeam(player.getUniqueId()) != null) {
            player.sendMessage(plugin.msg("already-in-team"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.msg("no-permission") + " Usage: /team create <name> [password]");
            return;
        }
        String name = args[1];
        int minLen = plugin.getConfig().getInt("settings.min-team-name-length", 3);
        int maxLen = plugin.getConfig().getInt("settings.max-team-name-length", 16);
        if (name.length() < minLen || name.length() > maxLen) {
            player.sendMessage(TeamPlugin.color("&cTeam name must be " + minLen + "-" + maxLen + " characters."));
            return;
        }
        if (name.equalsIgnoreCase("admin")) {
            player.sendMessage(TeamPlugin.color("&cThat name is reserved."));
            return;
        }
        if (tm.teamExists(name)) {
            player.sendMessage(plugin.msg("team-already-exists"));
            return;
        }
        String password = args.length >= 3 ? args[2] : null;
        Team team = tm.createTeam(name, player.getUniqueId(), password);
        player.sendMessage(plugin.msg("team-created").replace("{team}", team.getDisplayName()));
    }

    // ── /team invite <player> ─────────────────────────────────────────────────

    private void handleInvite(Player player, String[] args) {
        Team team = tm.getPlayerTeam(player.getUniqueId());
        if (team == null) { player.sendMessage(plugin.msg("not-in-team")); return; }
        if (!team.isAdmin(player.getUniqueId())) {
            player.sendMessage(plugin.msg("no-permission")); return;
        }
        if (args.length < 2) {
            player.sendMessage(TeamPlugin.color("&cUsage: /team invite <player>")); return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { player.sendMessage(plugin.msg("player-not-found")); return; }
        if (tm.getPlayerTeam(target.getUniqueId()) != null) {
            player.sendMessage(TeamPlugin.color("&cThat player is already in a team.")); return;
        }
        tm.sendInvite(target, team);
        player.sendMessage(plugin.msg("invite-sent").replace("{player}", target.getName()));
        target.sendMessage(plugin.msg("invite-received")
                .replace("{player}", player.getName())
                .replace("{team}", team.getDisplayName()));
    }

    // ── /team join <name> [password] ─────────────────────────────────────────

    private void handleJoin(Player player, String[] args) {
        if (tm.getPlayerTeam(player.getUniqueId()) != null) {
            player.sendMessage(plugin.msg("already-in-team")); return;
        }
        if (args.length < 2) {
            player.sendMessage(TeamPlugin.color("&cUsage: /team join <name> [password]")); return;
        }
        Team team = tm.getTeam(args[1]);
        if (team == null) { player.sendMessage(plugin.msg("team-not-found")); return; }

        // Check for pending invite (bypasses invite-only / password)
        String pendingTeamId = tm.getPendingInvite(player.getUniqueId());
        boolean hasInvite = team.getId().equals(pendingTeamId);

        if (!hasInvite) {
            if (team.isInviteOnly() && team.getPassword() == null) {
                player.sendMessage(TeamPlugin.color("&cThis team is invite-only.")); return;
            }
            if (team.getPassword() != null) {
                if (args.length < 3 || !team.getPassword().equals(args[2])) {
                    player.sendMessage(plugin.msg("wrong-password")); return;
                }
            }
        }

        tm.clearInvite(player.getUniqueId());
        tm.addToTeam(team, player.getUniqueId());
        player.sendMessage(plugin.msg("joined-team").replace("{team}", team.getDisplayName()));

        // Notify team
        broadcastToTeam(team, TeamPlugin.color("&e" + player.getName() + " &ajoined the team!"), player.getUniqueId());
    }

    // ── /team leave ───────────────────────────────────────────────────────────

    private void handleLeave(Player player) {
        Team team = tm.getPlayerTeam(player.getUniqueId());
        if (team == null) { player.sendMessage(plugin.msg("not-in-team")); return; }

        if (team.isOwner(player.getUniqueId())) {
            // Disband the team
            String name = team.getDisplayName();
            broadcastToTeam(team, plugin.msg("team-disbanded").replace("{team}", name), null);
            tm.disbandTeam(team);
        } else {
            String name = team.getDisplayName();
            tm.removeFromTeam(team, player.getUniqueId());
            player.sendMessage(plugin.msg("left-team").replace("{team}", name));
            broadcastToTeam(team, TeamPlugin.color("&e" + player.getName() + " &cleft the team."), null);
        }
    }

    // ── /team kick <player> ───────────────────────────────────────────────────

    private void handleKick(Player player, String[] args) {
        Team team = tm.getPlayerTeam(player.getUniqueId());
        if (team == null) { player.sendMessage(plugin.msg("not-in-team")); return; }
        if (!team.isAdmin(player.getUniqueId())) {
            player.sendMessage(plugin.msg("no-permission")); return;
        }
        if (args.length < 2) {
            player.sendMessage(TeamPlugin.color("&cUsage: /team kick <player>")); return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { player.sendMessage(plugin.msg("player-not-found")); return; }
        if (!team.hasMember(target.getUniqueId())) {
            player.sendMessage(plugin.msg("player-not-in-team")); return;
        }
        if (team.isOwner(target.getUniqueId())) {
            player.sendMessage(TeamPlugin.color("&cYou cannot kick the team owner.")); return;
        }
        tm.removeFromTeam(team, target.getUniqueId());
        player.sendMessage(plugin.msg("kicked-player").replace("{player}", target.getName()));
        target.sendMessage(plugin.msg("kicked-from-team").replace("{team}", team.getDisplayName()));
    }

    // ── /team info [name] ─────────────────────────────────────────────────────

    private void handleInfo(Player player, String[] args) {
        Team team;
        if (args.length >= 2) {
            team = tm.getTeam(args[1]);
            if (team == null) { player.sendMessage(plugin.msg("team-not-found")); return; }
        } else {
            team = tm.getPlayerTeam(player.getUniqueId());
            if (team == null) { player.sendMessage(plugin.msg("not-in-team")); return; }
        }

        player.sendMessage(TeamPlugin.color("&8&m                    "));
        player.sendMessage(TeamPlugin.color("&b&lTeam: &f" + team.getDisplayName()));
        player.sendMessage(TeamPlugin.color("&7Members: &f" + team.size()));
        player.sendMessage(TeamPlugin.color("&7Password: &f" + (team.getPassword() != null ? "&aYes" : "&cNo")));
        player.sendMessage(TeamPlugin.color("&7Invite-only: &f" + (team.isInviteOnly() ? "&aYes" : "&cNo")));

        StringBuilder members = new StringBuilder("&7Members: ");
        for (UUID m : team.getMembers()) {
            Player p = Bukkit.getPlayer(m);
            String name = p != null ? p.getName() : Bukkit.getOfflinePlayer(m).getName();
            if (name == null) name = m.toString().substring(0, 8);
            if (team.isOwner(m))      members.append("&6").append(name).append("★ ");
            else if (team.isAdmin(m)) members.append("&e").append(name).append("⬆ ");
            else                       members.append("&f").append(name).append(" ");
        }
        player.sendMessage(TeamPlugin.color(members.toString()));
        player.sendMessage(TeamPlugin.color("&8&m                    "));
    }

    // ── /team settings <option> [value] ──────────────────────────────────────

    private void handleSettings(Player player, String[] args) {
        Team team = tm.getPlayerTeam(player.getUniqueId());
        if (team == null) { player.sendMessage(plugin.msg("not-in-team")); return; }
        if (!team.isAdmin(player.getUniqueId())) {
            player.sendMessage(plugin.msg("no-permission")); return;
        }

        if (args.length < 2) {
            player.sendMessage(TeamPlugin.color(
                    "&b/team settings name <new-name>\n" +
                    "&b/team settings password <new-password|none>\n" +
                    "&b/team settings inviteonly <true|false>"));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "name" -> {
                if (args.length < 3) { player.sendMessage(TeamPlugin.color("&cUsage: /team settings name <new-name>")); return; }
                String newName = args[2];
                if (newName.equalsIgnoreCase("admin")) { player.sendMessage(TeamPlugin.color("&cReserved name.")); return; }
                if (tm.teamExists(newName) && !newName.equalsIgnoreCase(team.getDisplayName())) {
                    player.sendMessage(plugin.msg("team-already-exists")); return;
                }
                team.setDisplayName(newName);
                tm.save();
                player.sendMessage(TeamPlugin.color("&aTeam name updated to &e" + newName + "&a."));
            }
            case "password" -> {
                if (args.length < 3) { player.sendMessage(TeamPlugin.color("&cUsage: /team settings password <value|none>")); return; }
                String pw = args[2].equalsIgnoreCase("none") ? null : args[2];
                team.setPassword(pw);
                tm.save();
                player.sendMessage(TeamPlugin.color(pw == null ? "&aPassword removed." : "&aPassword updated."));
            }
            case "inviteonly" -> {
                if (args.length < 3) { player.sendMessage(TeamPlugin.color("&cUsage: /team settings inviteonly <true|false>")); return; }
                boolean v = Boolean.parseBoolean(args[2]);
                team.setInviteOnly(v);
                tm.save();
                player.sendMessage(TeamPlugin.color("&aInvite-only set to &e" + v + "&a."));
            }
            default -> player.sendMessage(TeamPlugin.color("&cUnknown setting. Use name, password, or inviteonly."));
        }
    }

    // ── /team chat <enable|disable> ───────────────────────────────────────────

    private void handleChat(Player player, String[] args) {
        Team team = tm.getPlayerTeam(player.getUniqueId());
        if (team == null) { player.sendMessage(plugin.msg("not-in-team")); return; }

        if (args.length < 2) {
            boolean current = tm.isTeamChatEnabled(player.getUniqueId());
            player.sendMessage(TeamPlugin.color("&7Team chat is currently: " + (current ? "&aENABLED" : "&cDISABLED")));
            return;
        }

        boolean enable = args[1].equalsIgnoreCase("enable");
        boolean disable = args[1].equalsIgnoreCase("disable");

        if (!enable && !disable) {
            player.sendMessage(TeamPlugin.color("&cUsage: /team chat <enable|disable>")); return;
        }

        tm.setTeamChat(player.getUniqueId(), enable);
        player.sendMessage(plugin.msg(enable ? "team-chat-enabled" : "team-chat-disabled"));
    }

    // ── /team admin ... ───────────────────────────────────────────────────────

    private void handleAdmin(Player player, String[] args) {
        if (args.length < 2) { sendAdminHelp(player); return; }

        // Must not be banned
        if (tm.isAdminBanned(player.getUniqueId())) {
            player.sendMessage(plugin.msg("admin-banned")); return;
        }

        // Check if player is in the admin team
        Team adminTeam = tm.getTeam("admin");
        boolean isAdminTeamMember = adminTeam != null && adminTeam.hasMember(player.getUniqueId());
        boolean isOpAdmin = player.hasPermission("teamplugin.admin");

        switch (args[1].toLowerCase()) {

            // /team admin settings – for the admin team itself
            case "settings" -> {
                if (!isAdminTeamMember && !isOpAdmin) { player.sendMessage(plugin.msg("no-permission")); return; }
                handleSettings(player, new String[]{"settings", args.length > 2 ? args[2] : "", args.length > 3 ? args[3] : ""});
            }

            // /team admin add <player> – promote someone to admin team (by joining with password)
            case "add" -> {
                if (!isOpAdmin) { player.sendMessage(plugin.msg("no-permission")); return; }
                if (args.length < 3) { player.sendMessage(TeamPlugin.color("&cUsage: /team admin add <player>")); return; }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) { player.sendMessage(plugin.msg("player-not-found")); return; }

                // Make sure admin team exists
                Team at = ensureAdminTeam(player);
                if (at == null) return;

                if (at.hasMember(target.getUniqueId())) {
                    player.sendMessage(TeamPlugin.color("&cThat player is already in the admin team.")); return;
                }
                // If they're in another team, don't move them automatically – just invite
                tm.sendInvite(target, at);
                player.sendMessage(plugin.msg("invite-sent").replace("{player}", target.getName()));
                target.sendMessage(plugin.msg("invite-received")
                        .replace("{player}", player.getName())
                        .replace("{team}", at.getDisplayName()));
            }

            // /team admin ban <player>
            case "ban" -> {
                if (!isOpAdmin) { player.sendMessage(plugin.msg("no-permission")); return; }
                if (args.length < 3) { player.sendMessage(TeamPlugin.color("&cUsage: /team admin ban <player>")); return; }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) { player.sendMessage(plugin.msg("player-not-found")); return; }
                tm.banFromAdmin(target.getUniqueId());
                player.sendMessage(plugin.msg("admin-banned").replace("{player}", target.getName()));
                target.sendMessage(plugin.msg("ban-target").replace("{player}", player.getName()));
            }

            // /team admin unban <player>
            case "unban" -> {
                if (!isOpAdmin) { player.sendMessage(plugin.msg("no-permission")); return; }
                if (args.length < 3) { player.sendMessage(TeamPlugin.color("&cUsage: /team admin unban <player>")); return; }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) { player.sendMessage(plugin.msg("player-not-found")); return; }
                tm.unbanFromAdmin(target.getUniqueId());
                player.sendMessage(plugin.msg("admin-unbanned").replace("{player}", target.getName()));
                target.sendMessage(plugin.msg("unban-target").replace("{player}", player.getName()));
            }

            // /team admin manage <teamId> – view/edit another team's settings
            case "manage" -> {
                if (!isAdminTeamMember && !isOpAdmin) { player.sendMessage(plugin.msg("no-permission")); return; }
                if (args.length < 3) { player.sendMessage(TeamPlugin.color("&cUsage: /team admin manage <teamId>")); return; }
                Team target = tm.getTeam(args[2]);
                if (target == null) { player.sendMessage(plugin.msg("team-not-found")); return; }
                // Show full info about that team
                handleInfo(player, new String[]{"info", args[2]});
            }

            // /team admin remove <teamId> – disband a team
            case "remove" -> {
                if (!isAdminTeamMember && !isOpAdmin) { player.sendMessage(plugin.msg("no-permission")); return; }
                if (args.length < 3) { player.sendMessage(TeamPlugin.color("&cUsage: /team admin remove <teamId>")); return; }
                Team target = tm.getTeam(args[2]);
                if (target == null) { player.sendMessage(plugin.msg("team-not-found")); return; }
                String name = target.getDisplayName();
                broadcastToTeam(target, plugin.msg("team-disbanded").replace("{team}", name), null);
                tm.disbandTeam(target);
                player.sendMessage(TeamPlugin.color("&aTeam &e" + name + " &adisbanded."));
            }

            // /team admin create [password] – create the admin team (or re-create)
            case "create" -> {
                if (!isOpAdmin) { player.sendMessage(plugin.msg("no-permission")); return; }
                if (tm.teamExists("admin")) {
                    player.sendMessage(TeamPlugin.color("&cAdmin team already exists.")); return;
                }
                String pw = args.length >= 3 ? args[2]
                        : plugin.getConfig().getString("admin.password", "admin123");
                Team at = tm.createTeam("admin", plugin.getConfig().getString("admin.team-name", "Admin"),
                        player.getUniqueId(), pw);
                player.sendMessage(TeamPlugin.color("&aAdmin team created with password: &e" + pw));
            }

            default -> sendAdminHelp(player);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Team ensureAdminTeam(Player creator) {
        if (!tm.teamExists("admin")) {
            String pw = plugin.getConfig().getString("admin.password", "admin123");
            return tm.createTeam("admin",
                    plugin.getConfig().getString("admin.team-name", "Admin"),
                    creator.getUniqueId(), pw);
        }
        return tm.getTeam("admin");
    }

    private void broadcastToTeam(Team team, String message, UUID exclude) {
        for (UUID m : team.getMembers()) {
            if (exclude != null && m.equals(exclude)) continue;
            Player p = Bukkit.getPlayer(m);
            if (p != null) p.sendMessage(message);
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(TeamPlugin.color(
                "&8&m                    \n" +
                "&b&lTeam Commands\n" +
                "&b/team create &7<name> [password]\n" +
                "&b/team invite &7<player>\n" +
                "&b/team join &7<name> [password]\n" +
                "&b/team leave\n" +
                "&b/team kick &7<player>\n" +
                "&b/team info &7[name]\n" +
                "&b/team settings &7<name|password|inviteonly> <value>\n" +
                "&b/team chat &7<enable|disable>\n" +
                "&b/team admin &7... (admin commands)\n" +
                "&8&m                    "));
    }

    private void sendAdminHelp(Player player) {
        player.sendMessage(TeamPlugin.color(
                "&8&m                    \n" +
                "&c&lAdmin Commands\n" +
                "&c/team admin create &7[password]\n" +
                "&c/team admin add &7<player>\n" +
                "&c/team admin ban &7<player>\n" +
                "&c/team admin unban &7<player>\n" +
                "&c/team admin manage &7<teamId>\n" +
                "&c/team admin remove &7<teamId>\n" +
                "&c/team admin settings\n" +
                "&8&m                    "));
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player player)) return completions;

        if (args.length == 1) {
            List<String> subs = Arrays.asList("create", "invite", "join", "leave", "kick", "info", "settings", "chat", "admin");
            for (String s : subs) if (s.startsWith(args[0].toLowerCase())) completions.add(s);

        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "invite", "kick" -> {
                    for (Player p : Bukkit.getOnlinePlayers())
                        if (p.getName().toLowerCase().startsWith(args[1].toLowerCase()))
                            completions.add(p.getName());
                }
                case "join", "info" -> {
                    for (Team t : tm.getAllTeams())
                        if (t.getId().startsWith(args[1].toLowerCase()))
                            completions.add(t.getId());
                }
                case "chat" -> completions.addAll(Arrays.asList("enable", "disable"));
                case "settings" -> completions.addAll(Arrays.asList("name", "password", "inviteonly"));
                case "admin" -> completions.addAll(Arrays.asList("create", "add", "ban", "unban", "manage", "remove", "settings"));
            }

        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            switch (args[1].toLowerCase()) {
                case "add", "ban", "unban" -> {
                    for (Player p : Bukkit.getOnlinePlayers())
                        if (p.getName().toLowerCase().startsWith(args[2].toLowerCase()))
                            completions.add(p.getName());
                }
                case "manage", "remove" -> {
                    for (Team t : tm.getAllTeams())
                        if (t.getId().startsWith(args[2].toLowerCase()))
                            completions.add(t.getId());
                }
            }
        }

        return completions;
    }
}
