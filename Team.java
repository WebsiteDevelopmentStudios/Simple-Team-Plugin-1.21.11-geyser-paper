package com.teamplugin.models;

import java.util.*;

public class Team {

    private final String id;          // unique lowercase key
    private String displayName;
    private String password;          // null = open
    private final UUID owner;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Set<UUID> admins   = new HashSet<>();  // team-level admins (not server admins)
    private boolean inviteOnly;

    public Team(String id, String displayName, UUID owner, String password) {
        this.id          = id.toLowerCase();
        this.displayName = displayName;
        this.owner       = owner;
        this.password    = (password != null && !password.isEmpty()) ? password : null;
        this.inviteOnly  = (this.password != null);
        members.add(owner);
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String getId()          { return id; }
    public String getDisplayName() { return displayName; }
    public void   setDisplayName(String n) { this.displayName = n; }

    public String getPassword()    { return password; }
    public void   setPassword(String p) {
        this.password   = (p != null && !p.isEmpty()) ? p : null;
        this.inviteOnly = (this.password != null);
    }

    public UUID      getOwner()    { return owner; }
    public Set<UUID> getMembers()  { return Collections.unmodifiableSet(members); }
    public Set<UUID> getAdmins()   { return Collections.unmodifiableSet(admins); }

    public boolean isInviteOnly()  { return inviteOnly; }
    public void    setInviteOnly(boolean v) { this.inviteOnly = v; }

    // ── Membership ───────────────────────────────────────────────────────────

    public void addMember(UUID uuid)    { members.add(uuid); }
    public void removeMember(UUID uuid) { members.remove(uuid); admins.remove(uuid); }
    public boolean hasMember(UUID uuid) { return members.contains(uuid); }

    public void   addAdmin(UUID uuid)    { admins.add(uuid); }
    public void   removeAdmin(UUID uuid) { admins.remove(uuid); }
    public boolean isAdmin(UUID uuid)   { return admins.contains(uuid) || uuid.equals(owner); }

    public boolean isOwner(UUID uuid)   { return uuid.equals(owner); }

    public int size() { return members.size(); }
}
