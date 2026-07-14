package com.littlebluenote.common.dto;

import java.io.Serializable;
import java.util.List;

/** Lightweight user projection shared across services via OpenFeign. */
public class UserDTO implements Serializable {
    private Long id;
    private String username;
    private String displayName;
    private String party;
    private String leaning;
    private String position;
    private String almaMater;
    private String state;
    private String bio;
    private List<String> interests;
    private Long committeeId;
    private String avatar;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getParty() { return party; }
    public void setParty(String party) { this.party = party; }
    public String getLeaning() { return leaning; }
    public void setLeaning(String leaning) { this.leaning = leaning; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getAlmaMater() { return almaMater; }
    public void setAlmaMater(String almaMater) { this.almaMater = almaMater; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public List<String> getInterests() { return interests; }
    public void setInterests(List<String> interests) { this.interests = interests; }
    public Long getCommitteeId() { return committeeId; }
    public void setCommitteeId(Long committeeId) { this.committeeId = committeeId; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
}
