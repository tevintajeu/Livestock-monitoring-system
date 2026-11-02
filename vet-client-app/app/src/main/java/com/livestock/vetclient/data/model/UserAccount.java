package com.livestock.vetclient.data.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.io.Serializable;

/**
 * Offline Role-Based User Account Entity.
 * Stores SHA-256 PIN hashes for ROLE_FARMER and ROLE_VET locally in Room SQLite.
 */
@Entity(tableName = "user_accounts")
public class UserAccount implements Serializable {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "role")
    private String role;

    @ColumnInfo(name = "display_name")
    private String displayName;

    @ColumnInfo(name = "pin_hash")
    private String pinHash;

    @ColumnInfo(name = "last_login_timestamp")
    private long lastLoginTimestamp;

    public UserAccount() {
        this.role = "ROLE_FARMER";
        this.displayName = "Cow Owner / Herder";
        this.pinHash = "";
        this.lastLoginTimestamp = System.currentTimeMillis();
    }

    @Ignore
    public UserAccount(@NonNull String role, String displayName, String pinHash, long lastLoginTimestamp) {
        this.role = role;
        this.displayName = displayName;
        this.pinHash = pinHash;
        this.lastLoginTimestamp = lastLoginTimestamp;
    }

    @NonNull
    public String getRole() {
        return role;
    }

    public void setRole(@NonNull String role) {
        this.role = role;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPinHash() {
        return pinHash;
    }

    public void setPinHash(String pinHash) {
        this.pinHash = pinHash;
    }

    public long getLastLoginTimestamp() {
        return lastLoginTimestamp;
    }

    public void setLastLoginTimestamp(long lastLoginTimestamp) {
        this.lastLoginTimestamp = lastLoginTimestamp;
    }
}

