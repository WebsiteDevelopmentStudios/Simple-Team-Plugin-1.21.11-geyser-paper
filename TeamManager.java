package com.teamplugin.managers;

import com.teamplugin.TeamPlugin;
import com.teamplugin.models.Team;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TeamManager {

    private final TeamPlugin plugin;

    // id (lowercase) → Team
    private final Map<String, Team> teams = new ConcurrentHashMap<>();

    // player UUID → team id
    private final Map<UUID, String> playerTeam = new ConcurrentHashMap<>();

    // invitee UUID → team id  (pending invites)
    private final Map<UUID, String> pendingInvites = new ConcurrentHashMap<>();

    // players with team chat ON
    private final Set<UUID> teamChatEnabled = ConcurrentHashMap.newKeySet();

    // players banned from admin commands
    private final Set<UUID> adminBanned = ConcurrentHashMap.newKeySet();

    private File dataFile;
    private FileConfiguration data;

    public TeamManager(TeamPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    public boolean teamExists(String id) {
        return teams.containsKey(id.toLowerCase());
    }

    public Team getTeam(String id) {
        return teams.get(id.toLowerCase());
    }

    public Team getPlayerTeam(UUID uuid) {
        String id = playerTeam.get(uuid);
        return id == null ? null : teams.get(id);
    }

    public Collection<Team> getAllTeams() {
        return Collections.unmodifiableCollection(teams.values());
    }

    public Team createTeam(String name, UUID owner, String password) {
        Team team = new Team(name, name, owner, password);
        teams.put(team.getId(), team);
        playerTeam.put(owner, team.getId());
        save();
        return team;
    }

    public void disbandTeam(Team team) {
        for (UUID m : team.getMembers()) {
            playerTeam.remove(m);
            teamChatEnabled.remove(m);
        }
        teams.remove(team.getId());
        save();
    }

    public void addToTeam(Team team, UUID uuid) {
        team.addMember(uuid);
        playerTeam.put(uuid, team.getId());
        pendingInvites.remove(uuid);
        save();
    }

    public void removeFromTeam(Team team, UUID uuid) {
        team.removeMember(uuid);
        playerTeam.remove(uuid);
        teamChatEnabled.remove(uuid);
        save();
    }

    // ── Invites ───────────────────────────────────────────────────────────────

    public void sendInvite(Player invitee, Team team) {
        pendingInvites.put(invitee.getUniqueId(), team.getId());
        int timeout = plugin.getConfig().getInt("settings.invite-timeout-seconds", 60);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (pendingInvites.remove(invitee.getUniqueId(), team.getId())) {
                if (invitee.isOnline()) {
                    invitee.sendMessage(plugin.msg("invite-expired").replace("{team}", team.getDisplayName()));
                }
            }
        }, timeout * 20L);
    }

    public String getPendingInvite(UUID uuid) {
        return pendingInvites.get(uuid);
    }

    public void clearInvite(UUID uuid) {
        pendingInvites.remove(uuid);
    }

    // ── Team chat ─────────────────────────────────────────────────────────────

    public boolean isTeamChatEnabled(UUID uuid) { return teamChatEnabled.contains(uuid); }

    public boolean toggleTeamChat(UUID uuid) {
        if (teamChatEnabled.contains(uuid)) { teamChatEnabled.remove(uuid); return false; }
        teamChatEnabled.add(uuid); return true;
    }

    public void setTeamChat(UUID uuid, boolean enabled) {
        if (enabled) teamChatEnabled.add(uuid); else teamChatEnabled.remove(uuid);
    }

    // ── Admin ban ─────────────────────────────────────────────────────────────

    public boolean isAdminBanned(UUID uuid) { return adminBanned.contains(uuid); }
    public void banFromAdmin(UUID uuid)     { adminBanned.add(uuid);    save(); }
    public void unbanFromAdmin(UUID uuid)   { adminBanned.remove(uuid); save(); }

    // ── Persistence (YAML) ────────────────────────────────────────────────────

    private void load() {
        dataFile = new File(plugin.getDataFolder(), "teams.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try { dataFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        // Load teams
        if (data.isConfigurationSection("teams")) {
            for (String id : data.getConfigurationSection("teams").getKeys(false)) {
                String base = "teams." + id + ".";
                String displayName = data.getString(base + "displayName", id);
                String password    = data.getString(base + "password", null);
                String ownerStr    = data.getString(base + "owner");
                if (ownerStr == null) continue;
                UUID owner = UUID.fromString(ownerStr);

                Team team = new Team(id, displayName, owner, password);
                team.setInviteOnly(data.getBoolean(base + "inviteOnly", password != null));

                List<String> memberList = data.getStringList(base + "members");
                for (String s : memberList) {
                    try { team.addMember(UUID.fromString(s)); } catch (Exception ignored) {}
                }
                List<String> adminList = data.getStringList(base + "admins");
                for (String s : adminList) {
                    try { team.addAdmin(UUID.fromString(s)); } catch (Exception ignored) {}
                }

                teams.put(id, team);
            }
        }

        // Rebuild playerTeam map
        for (Team t : teams.values()) {
            for (UUID m : t.getMembers()) {
                playerTeam.put(m, t.getId());
            }
        }

        // Load admin bans
        List<String> bans = data.getStringList("adminBanned");
        for (String s : bans) {
            try { adminBanned.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
    }

    public void save() {
        if (data == null || dataFile == null) return;

        data.set("teams", null); // clear first
        for (Team t : teams.values()) {
            String base = "teams." + t.getId() + ".";
            data.set(base + "displayName", t.getDisplayName());
            data.set(base + "password", t.getPassword());
            data.set(base + "owner", t.getOwner().toString());
            data.set(base + "inviteOnly", t.isInviteOnly());

            List<String> memberStrs = new ArrayList<>();
            for (UUID m : t.getMembers()) memberStrs.add(m.toString());
            data.set(base + "members", memberStrs);

            List<String> adminStrs = new ArrayList<>();
            for (UUID a : t.getAdmins()) adminStrs.add(a.toString());
            data.set(base + "admins", adminStrs);
        }

        List<String> banStrs = new ArrayList<>();
        for (UUID b : adminBanned) banStrs.add(b.toString());
        data.set("adminBanned", banStrs);

        try { data.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }
}
