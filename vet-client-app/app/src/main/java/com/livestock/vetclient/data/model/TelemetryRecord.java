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
 * Entity representing a single telemetry payload transmitted from a wearable LoRa node
 * to the ESP32 Edge Gateway and synchronized locally to the Android client.
 *
 * Unified CSV Payload Schema:
 * node_id, temp, acc_x, acc_y, acc_z, distance, anomaly_flag, timestamp
 * Example: NODE_001, 39.5, 1.2, 0.5, 9.8, 340.5, 1, 1727169615
 */
@Entity(
    tableName = "telemetry_records",
    indices = {
        @Index(value = {"node_id"}),
        @Index(value = {"timestamp"}),
        @Index(value = {"anomaly_flag"})
    }
)
public class TelemetryRecord implements Serializable {

    public static final double FEVER_THRESHOLD_CELSIUS = 39.5;
    public static final double GEOFENCE_RADIUS_METERS = 500.0;

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    @ColumnInfo(name = "node_id")
    private String nodeId;

    @ColumnInfo(name = "temperature")
    private double temperature;

    @ColumnInfo(name = "acc_x")
    private double accX;

    @ColumnInfo(name = "acc_y")
    private double accY;

    @ColumnInfo(name = "acc_z")
    private double accZ;

    /**
     * Relative distance from central anchor gateway in meters (0 - 1000m).
     * Values > 500m denote geofence perimeter breach / stray alert.
     */
    @ColumnInfo(name = "distance")
    private double distance;

    /**
     * Anomaly Flag:
     * 0 = Normal / Healthy biometric & motion state
     * 1 = Biometric / Motion Anomaly (Fever > 39.5°C or High-Acceleration Distress / Predator event)
     * 2 = Stray Alert / Geofence breach (> 500m)
     */
    @ColumnInfo(name = "anomaly_flag")
    private int anomalyFlag;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    // Default constructor for Room
    public TelemetryRecord() {
        this.nodeId = "";
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Full parameterized constructor for unified CSV telemetry.
     */
    @Ignore
    public TelemetryRecord(@NonNull String nodeId, double temperature,
                           double accX, double accY, double accZ,
                           double distance, int anomalyFlag, long timestamp) {
        this.nodeId = nodeId;
        this.temperature = temperature;
        this.accX = accX;
        this.accY = accY;
        this.accZ = accZ;
        this.distance = distance;
        this.anomalyFlag = anomalyFlag;
        this.timestamp = (timestamp > 0) ? timestamp : System.currentTimeMillis();
    }

    /**
     * Backward-compatible constructor for 6-field legacy telemetry payloads.
     */
    @Ignore
    public TelemetryRecord(@NonNull String nodeId, double temperature,
                           double accX, double accY, double accZ,
                           int anomalyFlag, long timestamp) {
        this(nodeId, temperature, accX, accY, accZ, 0.0, anomalyFlag, timestamp);
    }

    // --- Getters and Setters ---

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

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getAccX() {
        return accX;
    }

    public void setAccX(double accX) {
        this.accX = accX;
    }

    public double getAccY() {
        return accY;
    }

    public void setAccY(double accY) {
        this.accY = accY;
    }

    public double getAccZ() {
        return accZ;
    }

    public void setAccZ(double accZ) {
        this.accZ = accZ;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public int getAnomalyFlag() {
        return anomalyFlag;
    }

    public void setAnomalyFlag(int anomalyFlag) {
        this.anomalyFlag = anomalyFlag;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    // --- Biometric & Diagnostic Helper Methods ---

    /**
     * Checks if livestock temperature exceeds the bovine/livestock fever threshold (> 39.5°C).
     */
    public boolean isFever() {
        return this.temperature > FEVER_THRESHOLD_CELSIUS;
    }

    /**
     * Calculates the 3D acceleration vector magnitude in m/s^2:
     * |a| = sqrt(acc_x^2 + acc_y^2 + acc_z^2)
     */
    public double getAccelerationMagnitude() {
        return Math.sqrt((accX * accX) + (accY * accY) + (accZ * accZ));
    }

    /**
     * Identifies if the animal has breached the 500m geofence or triggered a stray alert.
     */
    public boolean isStray() {
        return this.distance > GEOFENCE_RADIUS_METERS || this.anomalyFlag == 2;
    }

    /**
     * Identifies if the anomaly is primarily motion/predator/distress induced.
     */
    public boolean isMotionAnomaly() {
        return this.anomalyFlag == 1 && !isFever();
    }

    /**
     * Returns human-readable diagnostic status for veterinary clinical triage.
     */
    public String getDiagnosticStatusSummary() {
        if (anomalyFlag == 2 || isStray()) {
            return "ALERT: Straying / Geofence Breach (" + String.format(Locale.US, "%.0fm", distance) + ")";
        } else if (anomalyFlag == 1) {
            if (isFever() && isHighAcceleration()) {
                return "CRITICAL: High Fever (" + String.format(Locale.US, "%.1f°C", temperature) + ") & Erratic Motion";
            } else if (isFever()) {
                return "ALERT: Febrile State / Fever (>39.5°C)";
            } else {
                return "ALERT: High Acceleration / Distress / Predator";
            }
        }
        return "Normal / Optimal Biometrics";
    }

    /**
     * Heuristic for high dynamic acceleration event (excluding resting 1.0g gravity vector).
     */
    public boolean isHighAcceleration() {
        double magnitude = getAccelerationMagnitude();
        // Resting gravity is ~9.8 m/s^2. We detect dynamic spikes.
        return magnitude > 14.0 || (magnitude > 1.6 && magnitude < 4.0);
    }

    /**
     * Returns formatted date and time for reporting.
     */
    public String getFormattedDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(this.timestamp));
    }

    public String getFormattedTimeOnly() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(this.timestamp));
    }

    @NonNull
    @Override
    public String toString() {
        return "TelemetryRecord{" +
                "nodeId='" + nodeId + '\'' +
                ", temp=" + temperature +
                ", acc=[" + accX + "," + accY + "," + accZ + "]" +
                ", distance=" + distance +
                ", anomaly=" + anomalyFlag +
                ", timestamp=" + timestamp +
                '}';
    }
}
