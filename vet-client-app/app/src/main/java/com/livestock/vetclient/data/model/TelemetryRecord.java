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
 * Entity representing a single telemetry payload transmitted from a wearable node
 * to the ESP32 Edge Gateway and synchronized locally to the Android client.
 *
 * Supported CSV Schemas:
 * - 5-field Firmware/Wokwi CSV: NODE_ID,TEMP,DYNAMIC_ACCEL,DISTANCE,ANOMALY_FLAG
 * - 6/7/8-field CSV: NODE_ID,TEMP,ACC_X,ACC_Y,ACC_Z,[DISTANCE,]ANOMALY_FLAG[,TIMESTAMP]
 *
 * Anomaly Flag Mapping:
 * 0 = Healthy
 * 1 = Fever (>39.5°C) or Pre-Calving Thermal Drop (<37.8°C)
 * 2 = Straying (>500m from Boma Receiver)
 * 3 = Predator / Theft Panic Motion (>14.0 m/s²)
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
    public static final double CALVING_DROP_THRESHOLD_CELSIUS = 37.8;
    public static final double MIN_VALID_TEMP_CELSIUS = 35.0;
    public static final double GEOFENCE_RADIUS_METERS = 500.0;
    public static final double PREDATOR_ACCEL_THRESHOLD = 14.0;

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
     * Relative distance from central Boma anchor gateway in meters (0 - 1000m).
     * Values > 500m denote geofence perimeter breach / stray alert.
     */
    @ColumnInfo(name = "distance")
    private double distance;

    /**
     * Anomaly Flag:
     * 0 = Healthy (Salama)
     * 1 = Fever (>39.5°C) or Calving Drop (<37.8°C)
     * 2 = Stray Alert / Geofence breach (>500m)
     * 3 = Predator / Panic Motion (>14.0 m/s²)
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
     * Checks if livestock temperature exceeds the bovine fever threshold (> 39.5°C).
     */
    public boolean isFever() {
        return this.temperature > FEVER_THRESHOLD_CELSIUS;
    }

    /**
     * Checks for pre-parturition (calving) body temperature drop (< 37.8°C and > 35.0°C).
     */
    public boolean isCalvingDrop() {
        return this.temperature < CALVING_DROP_THRESHOLD_CELSIUS && this.temperature >= MIN_VALID_TEMP_CELSIUS;
    }

    /**
     * Checks if there is any thermal health condition (Fever or Calving drop or Flag 1).
     */
    public boolean isThermalAlert() {
        return isFever() || isCalvingDrop() || anomalyFlag == 1;
    }

    /**
     * Calculates the 3D acceleration vector magnitude in m/s^2:
     * |a| = sqrt(acc_x^2 + acc_y^2 + acc_z^2)
     */
    public double getAccelerationMagnitude() {
        return Math.sqrt((accX * accX) + (accY * accY) + (accZ * accZ));
    }

    /**
     * Returns gravity-compensated dynamic acceleration (m/s^2).
     * Works seamlessly for both 5-field CSV (where accX is already dynamic acceleration and accY=0, accZ=0)
     * and 3-axis CSV (where resting gravity ~9.81 m/s^2 is present on Z-axis).
     */
    public double getDynamicAcceleration() {
        if (Math.abs(accY) < 0.001 && Math.abs(accZ) < 0.001) {
            return Math.abs(accX);
        }
        double totalMag = getAccelerationMagnitude();
        return Math.abs(totalMag - 9.81);
    }

    /**
     * Identifies if the animal has breached the 500m geofence or triggered a stray alert.
     */
    public boolean isStray() {
        return this.distance > GEOFENCE_RADIUS_METERS || this.anomalyFlag == 2;
    }

    /**
     * Identifies predator attack, theft panic, or high dynamic acceleration (>14.0 m/s² or Flag 3).
     */
    public boolean isPredatorOrPanic() {
        return this.anomalyFlag == 3
                || getDynamicAcceleration() > PREDATOR_ACCEL_THRESHOLD
                || getAccelerationMagnitude() > PREDATOR_ACCEL_THRESHOLD;
    }

    /**
     * Heuristic for high dynamic acceleration event.
     */
    public boolean isHighAcceleration() {
        return isPredatorOrPanic();
    }

    /**
     * Identifies if the anomaly is primarily motion/predator/distress induced.
     */
    public boolean isMotionAnomaly() {
        return isPredatorOrPanic() || (this.anomalyFlag == 1 && !isFever() && !isCalvingDrop());
    }

    /**
     * Checks whether this cow needs any attention (Fever, Calving, Stray, or Predator/Panic).
     */
    public boolean needsAttention() {
        return anomalyFlag > 0 || isFever() || isCalvingDrop() || isStray() || isPredatorOrPanic();
    }

    // --- Human-Centered Plain-Language Translators (No Technical Jargon) ---

    /**
     * Translates temperature into plain farmer-friendly language.
     * Example: "38.6°C (Normal)" vs "40.2°C (High Fever!)" vs "37.4°C (Calving Soon!)"
     */
    public String getHumanTemperatureLabel() {
        if (isFever()) {
            return String.format(Locale.US, "%.1f°C (Hot!)", temperature);
        } else if (isCalvingDrop()) {
            return String.format(Locale.US, "%.1f°C (Calving)", temperature);
        }
        return String.format(Locale.US, "%.1f°C (Normal)", temperature);
    }

    /**
     * Detailed temperature label for detail screen gauges.
     */
    public String getDetailedHumanTempLabel() {
        if (isFever()) {
            return String.format(Locale.US, "%.1f°C (High Fever!)", temperature);
        } else if (isCalvingDrop()) {
            return String.format(Locale.US, "%.1f°C (Calving Soon!)", temperature);
        }
        return String.format(Locale.US, "%.1f°C (Normal Body Heat)", temperature);
    }

    /**
     * Translates acceleration into plain farmer-friendly motion language.
     * Example: "Calm" / "Grazing" vs "Running!"
     */
    public String getHumanMotionShortLabel() {
        if (isPredatorOrPanic()) {
            return "Running!";
        }
        double dyn = getDynamicAcceleration();
        if (dyn > 4.0) {
            return "Active";
        }
        return "Calm";
    }

    /**
     * Detailed motion label for detail screen gauges.
     */
    public String getDetailedHumanMotionLabel() {
        if (isPredatorOrPanic()) {
            return "Running / Distressed!";
        }
        double dyn = getDynamicAcceleration();
        if (dyn > 3.5) {
            return "Active Walking / Grazing";
        }
        return "Calm / Grazing Peacefully";
    }

    /**
     * Translates distance into plain farmer-friendly proximity language.
     * Example: "220m (Near)" vs "680m (Far!)"
     */
    public String getHumanDistanceShortLabel() {
        if (isStray()) {
            return String.format(Locale.US, "%.0fm (Far!)", distance);
        }
        return String.format(Locale.US, "%.0fm (Near)", distance);
    }

    /**
     * Detailed distance label for detail screen gauges.
     */
    public String getDetailedHumanDistanceLabel() {
        if (isStray()) {
            return String.format(Locale.US, "Far from Herd! (%.0fm from Boma)", distance);
        } else if (distance > 350) {
            return String.format(Locale.US, "Outer Pasture (%.0fm from Boma)", distance);
        }
        return String.format(Locale.US, "Near Boma (%.0fm)", distance);
    }

    /**
     * Generates a plain-language summary sentence for the Animal Card using the cow's real name.
     */
    public String getPlainLanguageSummary(String cowName) {
        String name = (cowName != null && !cowName.trim().isEmpty()) ? cowName.trim() : nodeId;

        if (isPredatorOrPanic()) {
            return String.format(Locale.US,
                    "🚨 Panic running detected! Check pasture immediately to protect %s from predators or theft.",
                    name);
        }
        if (isStray() && isFever()) {
            return String.format(Locale.US,
                    "🚨 %s has strayed far (%.0fm) AND has a high fever (%.1f°C). Find and bring to shade immediately!",
                    name, distance, temperature);
        }
        if (isStray()) {
            return String.format(Locale.US,
                    "📍 %s has wandered far from the Boma (%.0fm). Send a herder to bring her back safely.",
                    name, distance);
        }
        if (isFever()) {
            return String.format(Locale.US,
                    "🔥 Body is very hot (%.1f°C). Keep %s in the shade, give water, and call the vet.",
                    temperature, name);
        }
        if (isCalvingDrop()) {
            return String.format(Locale.US,
                    "🐄 Calving sign detected (%.1f°C). Do not take %s to far pasture today.",
                    temperature, name);
        }
        return String.format(Locale.US,
                "✅ %s is grazing calmly near the Boma (%.1f°C). All health signs are normal (Salama).",
                name, temperature);
    }

    /**
     * Returns human-readable diagnostic status for veterinary clinical triage.
     */
    public String getDiagnosticStatusSummary() {
        if (isPredatorOrPanic()) {
            return "CRITICAL: Predator / Panic Motion (" + String.format(Locale.US, "%.1f m/s²", getDynamicAcceleration()) + ")";
        } else if (isStray()) {
            return "ALERT: Straying / Geofence Breach (" + String.format(Locale.US, "%.0fm", distance) + ")";
        } else if (isFever()) {
            return "ALERT: Febrile State / Fever (" + String.format(Locale.US, "%.1f°C", temperature) + ")";
        } else if (isCalvingDrop()) {
            return "ALERT: Pre-Calving Temperature Drop (" + String.format(Locale.US, "%.1f°C", temperature) + ")";
        }
        return "Normal / Optimal Biometrics";
    }

    /**
     * Returns formatted date and time for reporting.
     */
    public String getFormattedDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM HH:mm", Locale.getDefault());
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
