package com.livestock.vetclient.util;

import com.livestock.vetclient.data.model.TelemetryRecord;

import java.util.List;
import java.util.Locale;

/**
 * Diagnostic analysis utility designed for veterinary triage.
 * Evaluates physiological temperature thresholds, 3-axis accelerometer patterns,
 * and geofence boundary distance.
 */
public class BiometricDiagnosticEngine {

    public static final double NORMAL_TEMP_MIN = 38.0;
    public static final double NORMAL_TEMP_MAX = 39.5; // Fever threshold

    public static class NodeSummaryStats {
        public double currentTemp;
        public double minTemp;
        public double maxTemp;
        public double avgTemp;
        public double maxAccMagnitude;
        public double currentDistance;
        public double maxDistance;
        public int totalReadings;
        public int anomalyCount;
        public boolean hasActiveFever;
        public boolean hasActiveMotionAlert;
        public boolean hasActiveStrayAlert;
    }

    /**
     * Computes biometric and geofencing summary statistics for a given node's historical records.
     */
    public static NodeSummaryStats computeStats(List<TelemetryRecord> records) {
        NodeSummaryStats stats = new NodeSummaryStats();
        if (records == null || records.isEmpty()) {
            return stats;
        }

        TelemetryRecord latest = records.get(0);
        stats.currentTemp = latest.getTemperature();
        stats.currentDistance = latest.getDistance();
        stats.hasActiveFever = latest.isFever();
        stats.hasActiveMotionAlert = latest.isMotionAnomaly();
        stats.hasActiveStrayAlert = latest.isStray();
        stats.totalReadings = records.size();

        double sumTemp = 0;
        double minT = Double.MAX_VALUE;
        double maxT = Double.MIN_VALUE;
        double maxAcc = 0;
        double maxDist = 0;
        int anomalies = 0;

        for (TelemetryRecord r : records) {
            double t = r.getTemperature();
            sumTemp += t;
            if (t < minT) minT = t;
            if (t > maxT) maxT = t;

            double accMag = r.getAccelerationMagnitude();
            if (accMag > maxAcc) maxAcc = accMag;

            double dist = r.getDistance();
            if (dist > maxDist) maxDist = dist;

            if (r.getAnomalyFlag() > 0 || r.isStray()) {
                anomalies++;
            }
        }

        stats.minTemp = minT;
        stats.maxTemp = maxT;
        stats.avgTemp = sumTemp / records.size();
        stats.maxAccMagnitude = maxAcc;
        stats.maxDistance = maxDist;
        stats.anomalyCount = anomalies;

        return stats;
    }

    /**
     * Generates veterinary clinical recommendations based on telemetry findings.
     */
    public static String generateClinicalAdvice(TelemetryRecord latestRecord, NodeSummaryStats stats) {
        if (latestRecord == null) {
            return "No telemetry data recorded for this livestock unit.";
        }

        StringBuilder advice = new StringBuilder();

        if (latestRecord.isStray() || latestRecord.getAnomalyFlag() == 2) {
            advice.append("🚨 GEOFENCE BREACH / STRAY ALERT:\n")
                  .append("• Recorded distance is ").append(String.format(Locale.US, "%.0f meters", latestRecord.getDistance()))
                  .append(" from anchor gateway (Perimeter threshold: 500m).\n")
                  .append("• High risk of livestock theft, predator exposure, or separation from the herd.\n")
                  .append("• Recommendation: Dispatch herder/ranger team immediately along outer pasture boundary.\n\n");
        }

        if (latestRecord.isFever()) {
            advice.append("⚠️ CLINICAL FEVER (> 39.5°C):\n")
                  .append("• Recorded temperature is ").append(String.format(Locale.US, "%.1f°C", latestRecord.getTemperature()))
                  .append(" (Normal: 38.0°C – 39.5°C).\n")
                  .append("• High suspicion of systemic infection (e.g., Bovine Respiratory Disease, Tick-borne infection, Anaplasmosis, Mastitis).\n")
                  .append("• Recommendation: Isolate animal immediately, conduct physical examination, auscultate lungs, and assess need for antipyretics and antimicrobial therapy.\n\n");
        }

        if (latestRecord.isMotionAnomaly()) {
            advice.append("🚨 ACUTE DISTRESS / MOTION ANOMALY:\n")
                  .append("• Dynamic acceleration anomaly flag active without febrile temperature.\n")
                  .append("• Corresponds with violent movement, rapid flight (predator harassment/pack hunting), fence entrapment, or severe trauma.\n")
                  .append("• Recommendation: Dispatch field ranger/handler to node GPS vicinity to check for physical injury, wire entanglement, or predator presence.\n\n");
        }

        if (latestRecord.getAnomalyFlag() == 0 && !latestRecord.isStray()) {
            advice.append("✅ NORMAL VITAL PARAMETERS:\n")
                  .append("• Core temperature (").append(String.format(Locale.US, "%.1f°C", latestRecord.getTemperature()))
                  .append("), acceleration vectors (")
                  .append(String.format(Locale.US, "%.2f m/s²", latestRecord.getAccelerationMagnitude()))
                  .append("), and pasture boundary distance (")
                  .append(String.format(Locale.US, "%.0fm", latestRecord.getDistance()))
                  .append(") reflect resting/grazing baseline.\n")
                  .append("• No immediate clinical intervention required.");
        }

        return advice.toString();
    }
}
