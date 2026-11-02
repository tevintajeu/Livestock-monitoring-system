package com.livestock.vetclient.util;

import com.livestock.vetclient.data.model.AnimalProfile;
import com.livestock.vetclient.data.model.TelemetryRecord;

import java.util.List;
import java.util.Locale;

/**
 * Dual-Persona Diagnostic & Action Checklist Engine:
 * 1. Farmer / Herder View (ROLE_FARMER): Plain-language 3-step actionable checklists (zero technical jargon).
 * 2. Field Veterinarian View (ROLE_VET): Full clinical differential diagnosis & pharmacological protocols.
 * 3. Health-to-Yield Correlation Engine: Connects thermal anomalies directly to milk yield expectations.
 */
public class BiometricDiagnosticEngine {

    public static final double NORMAL_TEMP_MIN = 38.0;
    public static final double NORMAL_TEMP_MAX = 39.5;

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

            if (r.needsAttention()) {
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
     * Returns 3 plain-language checklist steps for the Cow Owner / Herder (ROLE_FARMER).
     */
    public static String[] generateFarmerChecklistSteps(TelemetryRecord latest, String cowName) {
        String name = (cowName != null && !cowName.trim().isEmpty()) ? cowName.trim() : "this cow";
        if (latest == null) {
            return new String[]{
                    "1. Sync with the Boma Receiver Box to get today's health update.",
                    "2. Check that the neck collar is fastened comfortably on " + name + ".",
                    "3. Record morning and evening milk liters below."
            };
        }

        if (latest.isPredatorOrPanic()) {
            return new String[]{
                    "1. Go to the pasture immediately with another herder — " + name + " is running in panic!",
                    "2. Check for wild predators, thieves, or fence wire entanglement.",
                    "3. Bring " + name + " back inside the safe Boma enclosure and inspect for injuries."
            };
        }

        if (latest.isStray()) {
            return new String[]{
                    String.format(Locale.US, "1. Send a herder out now — %s has wandered %.0fm away from the Boma.", name, latest.getDistance()),
                    "2. Guide " + name + " back inside the 500m safe grazing boundary before dusk.",
                    "3. Check water troughs near the Boma so she does not wander looking for water."
            };
        }

        if (latest.isFever()) {
            return new String[]{
                    "1. Separate " + name + " from the herd into a cool, shaded Boma pen with clean water.",
                    "2. Check under the ears and neck for ticks, and feel the udder for heat or swelling (mastitis).",
                    "3. Call the Field Veterinarian for treatment and log today's milk separately."
            };
        }

        if (latest.isCalvingDrop()) {
            return new String[]{
                    "1. Do NOT take " + name + " to far pastures today — calving sign (<37.8°C) detected!",
                    "2. Prepare a clean, dry maternity pen at the Boma with fresh bedding and water.",
                    "3. Watch closely over the next 12–24 hours for labor signs and first colostrum milk."
            };
        }

        return new String[]{
                "1. Let " + name + " continue normal grazing and watering — all vitals are healthy (Salama).",
                "2. Record today's morning and evening milk yield in the Milk Tracker card below.",
                "3. Do a quick visual check during evening Boma lock-up."
        };
    }

    /**
     * Generates full veterinary clinical differential diagnosis & pharmacological protocol for ROLE_VET.
     */
    public static String generateClinicalAdvice(TelemetryRecord latestRecord, NodeSummaryStats stats) {
        if (latestRecord == null) {
            return "No telemetry data recorded for this livestock unit.";
        }

        StringBuilder advice = new StringBuilder();

        if (latestRecord.isPredatorOrPanic()) {
            advice.append("🚨 ACUTE DISTRESS / PREDATOR OR TRAUMA EVENT (Flag 3):\n")
                  .append("• Dynamic Acceleration: ").append(String.format(Locale.US, "%.2f m/s²", latestRecord.getDynamicAcceleration()))
                  .append(" (Threshold: >14.0 m/s²).\n")
                  .append("• Differential: Predator pack harassment, rustling/theft flight response, or acute musculoskeletal entrapment.\n")
                  .append("• Clinical Protocol: Immediate physical inspection for lacerations, bite wounds, or lameness. Administer wound antiseptics and NSAID analgesia (e.g., Flunixin meglumine 1.1–2.2 mg/kg IV) if trauma is confirmed.\n\n");
        }

        if (latestRecord.isStray()) {
            advice.append("🚨 GEOFENCE BREACH / STRAY ALERT (Flag 2):\n")
                  .append("• Anchor Distance: ").append(String.format(Locale.US, "%.0f m", latestRecord.getDistance()))
                  .append(" (Safe perimeter: 500 m | Peak recorded: ").append(String.format(Locale.US, "%.0f m", stats.maxDistance)).append(").\n")
                  .append("• Epidemiology Risk: Contact with neighboring herds increases transmission risk of Foot-and-Mouth Disease (FMD) and vector ticks.\n")
                  .append("• Clinical Protocol: Retrieve animal, inspect for foreign ticks (Rhipicephalus appendiculatus), and apply acaricide spray upon return.\n\n");
        }

        if (latestRecord.isFever()) {
            advice.append("⚠️ CLINICAL PYREXIA / FEVER (> 39.5°C | Flag 1):\n")
                  .append("• Core Temperature: ").append(String.format(Locale.US, "%.1f°C", latestRecord.getTemperature()))
                  .append(" (Normal Bovine Range: 38.0°C – 39.5°C | Historical Max: ").append(String.format(Locale.US, "%.1f°C", stats.maxTemp)).append(").\n")
                  .append("• Differential Diagnosis:\n")
                  .append("  1. East Coast Fever (Theileria parva) – palpate parotid/prescapular lymph nodes.\n")
                  .append("  2. Bovine Anaplasmosis / Babesiosis – check mucous membranes for pallor/icterus.\n")
                  .append("  3. Acute Clinical Mastitis – perform California Mastitis Test (CMT) on all quarters.\n")
                  .append("  4. Bovine Respiratory Disease (BRD) – auscultate cranioventral lung fields.\n")
                  .append("• Pharmacological Protocol: Buparvaquone (2.5 mg/kg IM) if ECF confirmed, Oxytetracycline LA (20 mg/kg IM), and Flunixin meglumine or Meloxicam (0.5 mg/kg SC) for antipyresis. Observe milk withdrawal periods.\n\n");
        }

        if (latestRecord.isCalvingDrop()) {
            advice.append("🐄 PRE-PARTURITION HYPOTHERMIC DROP (< 37.8°C | Flag 1):\n")
                  .append("• Core Temperature: ").append(String.format(Locale.US, "%.1f°C", latestRecord.getTemperature()))
                  .append(" (Progesterone decline prior to Stage I labor).\n")
                  .append("• Differential: Impending parturition within 12–24 hours vs. subclinical hypocalcemia (milk fever).\n")
                  .append("• Clinical Protocol: Isolate in clean maternity pen, monitor cervical dilation/water bag, and keep Calcium Borogluconate 23% IV/SC on standby for post-partum paresis.\n\n");
        }

        if (!latestRecord.needsAttention()) {
            advice.append("✅ NORMAL PHYSIOLOGICAL BASELINE (Flag 0):\n")
                  .append("• Core Temperature: ").append(String.format(Locale.US, "%.1f°C", latestRecord.getTemperature()))
                  .append(" (Avg: ").append(String.format(Locale.US, "%.1f°C", stats.avgTemp)).append(")\n")
                  .append("• Dynamic Motion: ").append(String.format(Locale.US, "%.2f m/s²", latestRecord.getDynamicAcceleration()))
                  .append(" | 3D |a|: ").append(String.format(Locale.US, "%.2f m/s²", latestRecord.getAccelerationMagnitude())).append("\n")
                  .append("• Boma Proximity: ").append(String.format(Locale.US, "%.0f m", latestRecord.getDistance())).append(" (Within 500m safe zone)\n")
                  .append("• Clinical Assessment: Resting/grazing homeostasis maintained. Continue routine ectoparasite dipping and deworming schedule.");
        }

        return advice.toString().trim();
    }

    /**
     * Generates the Health-to-Yield Insight Banner correlating temperature/activity with milk output.
     */
    public static String generateHealthToYieldBanner(TelemetryRecord latest, AnimalProfile profile) {
        if (latest == null) {
            return "✅ Log morning and evening milk below to track herd productivity.";
        }
        if (latest.isFever()) {
            return "⚠️ Fever detected! Expect temporary milk drop. Check udder for mastitis.";
        }
        if (latest.isCalvingDrop()) {
            return "🐄 Calving drop (<37.8°C) detected! Prepare for colostrum milking after birth.";
        }
        if (latest.isPredatorOrPanic() || latest.isStray()) {
            return "⚠️ High stress / walking far can reduce evening milk yield. Provide water and rest.";
        }
        if (profile != null && "Dry".equalsIgnoreCase(profile.getLactationStatus())) {
            return "ℹ️ Currently in Dry period (not milking). Body heat and grazing are healthy.";
        }
        if (profile != null && "Pregnant".equalsIgnoreCase(profile.getLactationStatus())) {
            return "🐄 Pregnant & healthy (" + String.format(Locale.US, "%.1f°C", latest.getTemperature()) + "). Monitor for calving temperature drop.";
        }
        return "✅ Milk yield & body heat are normal. Optimal grazing conditions.";
    }
}
