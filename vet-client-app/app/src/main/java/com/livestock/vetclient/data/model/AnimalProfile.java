package com.livestock.vetclient.data.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.io.Serializable;
import java.util.Locale;

/**
 * Human-Centered Animal Identity Profile Entity.
 * Maps cold hardware node IDs (e.g., "NODE_001") to culturally familiar livestock names,
 * ear tag numbers, breed, and lactation status for Kenyan farmers and herders.
 */
@Entity(tableName = "animal_profiles")
public class AnimalProfile implements Serializable {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "node_id")
    private String nodeId;

    @ColumnInfo(name = "custom_name")
    private String customName;

    @ColumnInfo(name = "ear_tag_number")
    private String earTagNumber;

    @ColumnInfo(name = "breed")
    private String breed;

    /**
     * Values: "Milking", "Pregnant", "Dry"
     */
    @ColumnInfo(name = "lactation_status")
    private String lactationStatus;

    @ColumnInfo(name = "notes")
    private String notes;

    public AnimalProfile() {
        this.nodeId = "NODE_001";
        this.customName = "Malkia";
        this.earTagNumber = "KE-042";
        this.breed = "Boran";
        this.lactationStatus = "Milking";
        this.notes = "";
    }

    @Ignore
    public AnimalProfile(@NonNull String nodeId, String customName, String earTagNumber,
                         String breed, String lactationStatus, String notes) {
        this.nodeId = nodeId;
        this.customName = customName;
        this.earTagNumber = earTagNumber;
        this.breed = breed;
        this.lactationStatus = lactationStatus;
        this.notes = notes != null ? notes : "";
    }

    @NonNull
    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(@NonNull String nodeId) {
        this.nodeId = nodeId;
    }

    public String getCustomName() {
        return (customName != null && !customName.trim().isEmpty()) ? customName.trim() : nodeId;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public String getEarTagNumber() {
        return (earTagNumber != null && !earTagNumber.trim().isEmpty()) ? earTagNumber.trim() : "KE-001";
    }

    public void setEarTagNumber(String earTagNumber) {
        this.earTagNumber = earTagNumber;
    }

    public String getBreed() {
        return (breed != null && !breed.trim().isEmpty()) ? breed.trim() : "Zebu";
    }

    public void setBreed(String breed) {
        this.breed = breed;
    }

    public String getLactationStatus() {
        return (lactationStatus != null && !lactationStatus.trim().isEmpty()) ? lactationStatus.trim() : "Milking";
    }

    public void setLactationStatus(String lactationStatus) {
        this.lactationStatus = lactationStatus;
    }

    public String getNotes() {
        return notes != null ? notes : "";
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    /**
     * Formatted friendly header: e.g. "Malkia (Tag #KE-042)"
     */
    public String getDisplayHeader() {
        return getCustomName() + " (Tag #" + getEarTagNumber() + ")";
    }

    /**
     * Formatted breed and lactation subtitle: e.g. "Boran • Milking"
     */
    public String getBreedAndStatusSubtitle() {
        return getBreed() + " • " + getLactationStatus();
    }

    /**
     * Creates a friendly profile for any newly discovered sensor node.
     * Keeps the identity consistent with the hardware node ID.
     */
    public static AnimalProfile createDefaultForNode(@NonNull String nodeId) {
        String cleanId = nodeId.trim().toUpperCase(Locale.US);
        return new AnimalProfile(cleanId, cleanId, "TAG-" + cleanId, "Boran", "Milking", "Active Monitored Wearable Node");
    }
}

