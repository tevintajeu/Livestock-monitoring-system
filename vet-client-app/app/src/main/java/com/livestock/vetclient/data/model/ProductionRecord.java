package com.livestock.vetclient.data.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Entity tracking daily livestock economic output (morning/evening milk yield and calving status)
 * stored 100% offline in Room SQLite and correlated with biometric health alerts.
 */
@Entity(
    tableName = "production_records",
    indices = {
        @Index(value = {"node_id"}),
        @Index(value = {"timestamp"})
    }
)
public class ProductionRecord implements Serializable {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    @ColumnInfo(name = "node_id")
    private String nodeId;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    @ColumnInfo(name = "morning_milk_liters")
    private double morningMilkLiters;

    @ColumnInfo(name = "evening_milk_liters")
    private double eveningMilkLiters;

    /**
     * Values: "Normal", "Expecting Soon", "Calved"
     */
    @ColumnInfo(name = "calf_status")
    private String calfStatus;

    @ColumnInfo(name = "recorded_by")
    private String recordedBy;

    @ColumnInfo(name = "notes")
    private String notes;

    public ProductionRecord() {
        this.nodeId = "NODE_001";
        this.timestamp = System.currentTimeMillis();
        this.calfStatus = "Normal";
        this.recordedBy = "Herder";
        this.notes = "";
    }

    @Ignore
    public ProductionRecord(@NonNull String nodeId, long timestamp,
                            double morningMilkLiters, double eveningMilkLiters,
                            String calfStatus, String recordedBy, String notes) {
        this.nodeId = nodeId;
        this.timestamp = (timestamp > 0) ? timestamp : System.currentTimeMillis();
        this.morningMilkLiters = Math.max(0.0, morningMilkLiters);
        this.eveningMilkLiters = Math.max(0.0, eveningMilkLiters);
        this.calfStatus = (calfStatus != null && !calfStatus.isEmpty()) ? calfStatus : "Normal";
        this.recordedBy = (recordedBy != null && !recordedBy.isEmpty()) ? recordedBy : "Herder";
        this.notes = notes != null ? notes : "";
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(@NonNull String nodeId) {
        this.nodeId = nodeId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getMorningMilkLiters() {
        return morningMilkLiters;
    }

    public void setMorningMilkLiters(double morningMilkLiters) {
        this.morningMilkLiters = morningMilkLiters;
    }

    public double getEveningMilkLiters() {
        return eveningMilkLiters;
    }

    public void setEveningMilkLiters(double eveningMilkLiters) {
        this.eveningMilkLiters = eveningMilkLiters;
    }

    public String getCalfStatus() {
        return calfStatus != null ? calfStatus : "Normal";
    }

    public void setCalfStatus(String calfStatus) {
        this.calfStatus = calfStatus;
    }

    public String getRecordedBy() {
        return recordedBy != null ? recordedBy : "Herder";
    }

    public void setRecordedBy(String recordedBy) {
        this.recordedBy = recordedBy;
    }

    public String getNotes() {
        return notes != null ? notes : "";
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public double getTotalMilkLiters() {
        return morningMilkLiters + eveningMilkLiters;
    }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        return sdf.format(new Date(this.timestamp));
    }

    public String getFormattedDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM HH:mm", Locale.getDefault());
        return sdf.format(new Date(this.timestamp));
    }
}

