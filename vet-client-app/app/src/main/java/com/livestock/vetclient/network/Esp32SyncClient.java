package com.livestock.vetclient.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.livestock.vetclient.data.model.TelemetryRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * High-performance network synchronization client designed to communicate with the
 * ESP32 Edge Gateway (Boma Receiver) over local Wi-Fi Access Point (SoftAP).
 *
 * Supports tolerant parsing of:
 * - 5-field Wokwi/Gateway CSV: NODE_ID,TEMP,DYNAMIC_ACCEL,DISTANCE,ANOMALY_FLAG
 * - 6/7/8-field Legacy/Unified CSV: NODE_ID,TEMP,ACC_X,ACC_Y,ACC_Z,[DISTANCE,]ANOMALY_FLAG[,TIMESTAMP]
 *
 * Anomaly Flags:
 * 0 = Healthy (Salama)
 * 1 = Fever / Calving Drop (<37.8°C or >39.5°C)
 * 2 = Straying (>500m)
 * 3 = Predator / Panic Motion (>14.0 m/s²)
 */
public class Esp32SyncClient {

    private static final String TAG = "Esp32SyncClient";
    public static final String DEFAULT_GATEWAY_URL = "http://127.0.0.1:8080/data";

    private final OkHttpClient httpClient;
    private final Handler mainHandler;

    public interface SyncCallback {
        void onSyncStarted();
        void onSyncSuccess(List<TelemetryRecord> records);
        void onSyncFailure(String errorMessage);
    }

    public Esp32SyncClient() {
        Handler h = null;
        try {
            Looper mainLooper = Looper.getMainLooper();
            if (mainLooper != null) {
                h = new Handler(mainLooper);
            }
        } catch (Throwable ignored) {}
        this.mainHandler = h;

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public void syncFromGateway(String gatewayUrl, @NonNull final SyncCallback callback) {
        final String targetUrl = (gatewayUrl != null && !gatewayUrl.trim().isEmpty())
                ? gatewayUrl.trim()
                : DEFAULT_GATEWAY_URL;

        callback.onSyncStarted();
        Log.d(TAG, "Initiating HTTP GET sync to Boma Gateway: " + targetUrl);

        Request request = new Request.Builder()
                .url(targetUrl)
                .header("Accept", "text/csv, text/plain")
                .header("User-Agent", "MifugoSafeClient/2.0")
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "HTTP GET failed while reaching gateway", e);
                String message = formatNetworkError(e, targetUrl);
                mainHandler.post(() -> callback.onSyncFailure(message));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful() || responseBody == null) {
                        final String errMsg = "Boma Receiver responded with status code: " + response.code();
                        Log.e(TAG, errMsg);
                        mainHandler.post(() -> callback.onSyncFailure(errMsg));
                        return;
                    }

                    String rawCsvData = responseBody.string();
                    Log.d(TAG, "Received raw payload bytes: " + rawCsvData.length());

                    List<TelemetryRecord> parsedRecords = parseCsvPayload(rawCsvData);

                    if (parsedRecords.isEmpty()) {
                        mainHandler.post(() -> callback.onSyncFailure("Connected to Boma Receiver, but no new cow readings were found."));
                    } else {
                        mainHandler.post(() -> callback.onSyncSuccess(parsedRecords));
                    }

                } catch (Exception ex) {
                    Log.e(TAG, "Error reading/parsing CSV response", ex);
                    mainHandler.post(() -> callback.onSyncFailure("Error reading Boma Receiver data: " + ex.getMessage()));
                }
            }
        });
    }

    /**
     * Tolerant multi-schema CSV parser supporting both 5-field Wokwi/Gateway CSV and 6/7/8-field CSV.
     *
     * Supported CSV Schemas:
     * - 5-field (Wokwi/Gateway): NODE_ID, TEMP, DYNAMIC_ACCEL, DISTANCE, ANOMALY_FLAG
     * - 6-field (Legacy):        NODE_ID, TEMP, ACC_X, ACC_Y, ACC_Z, ANOMALY_FLAG
     * - 7-field (Legacy + TS):   NODE_ID, TEMP, ACC_X, ACC_Y, ACC_Z, ANOMALY_FLAG, TIMESTAMP
     * - 7-field (With Dist):     NODE_ID, TEMP, ACC_X, ACC_Y, ACC_Z, DISTANCE, ANOMALY_FLAG
     * - 8-field (Unified):       NODE_ID, TEMP, ACC_X, ACC_Y, ACC_Z, DISTANCE, ANOMALY_FLAG, TIMESTAMP
     */
    public List<TelemetryRecord> parseCsvPayload(String csvContent) throws IOException {
        List<TelemetryRecord> records = new ArrayList<>();
        if (csvContent == null || csvContent.trim().isEmpty()) {
            return records;
        }

        try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
            String line;
            int lineNumber = 0;
            long baseTime = System.currentTimeMillis();

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#") || line.startsWith("---")) {
                    continue;
                }

                String lower = line.toLowerCase();
                if (lower.startsWith("node_id") || lower.startsWith("node,") || lower.contains("anomaly_flag")) {
                    safeLogD("Skipping CSV header row at line " + lineNumber);
                    continue;
                }

                String[] tokens = line.split(",");
                if (tokens.length < 5) {
                    safeLogW("Skipping malformed row " + lineNumber + " (expected >= 5 tokens, got " + tokens.length + "): " + line);
                    continue;
                }

                try {
                    String nodeId = tokens[0].trim();
                    if (nodeId.isEmpty()) continue;

                    double temp = Double.parseDouble(tokens[1].trim());
                    double accX = Double.parseDouble(tokens[2].trim());
                    double accY = 0.0;
                    double accZ = 0.0;
                    double distance = 0.0;
                    int anomalyFlag = 0;
                    long recordTimestamp = baseTime - (records.size() * 1000L);

                    if (tokens.length == 5) {
                        // 5-field Wokwi/Gateway CSV: NODE_ID, TEMP, DYNAMIC_ACCEL, DISTANCE, ANOMALY_FLAG
                        // Store dynamic_accel in accX with accY = 0.0 and accZ = 0.0
                        distance = Double.parseDouble(tokens[3].trim());
                        anomalyFlag = parseSafeAnomalyFlag(tokens[4].trim());
                    } else if (tokens.length == 6) {
                        // 6-field legacy CSV: NODE_ID, TEMP, ACC_X, ACC_Y, ACC_Z, ANOMALY_FLAG
                        accY = Double.parseDouble(tokens[3].trim());
                        accZ = Double.parseDouble(tokens[4].trim());
                        anomalyFlag = parseSafeAnomalyFlag(tokens[5].trim());
                    } else if (tokens.length == 7) {
                        // 7-field CSV:
                        // Case A: NODE_ID, TEMP, ACC_X, ACC_Y, ACC_Z, DISTANCE, ANOMALY_FLAG
                        // Case B: NODE_ID, TEMP, ACC_X, ACC_Y, ACC_Z, ANOMALY_FLAG, TIMESTAMP
                        accY = Double.parseDouble(tokens[3].trim());
                        accZ = Double.parseDouble(tokens[4].trim());
                        double val5 = Double.parseDouble(tokens[5].trim());
                        double val6 = Double.parseDouble(tokens[6].trim());

                        if (val6 > 100000) {
                            anomalyFlag = (int) val5;
                            recordTimestamp = parseEpoch(tokens[6].trim(), recordTimestamp);
                        } else {
                            distance = val5;
                            anomalyFlag = (int) val6;
                        }
                    } else {
                        // 8-field unified CSV: NODE_ID, TEMP, ACC_X, ACC_Y, ACC_Z, DISTANCE, ANOMALY_FLAG, TIMESTAMP
                        accY = Double.parseDouble(tokens[3].trim());
                        accZ = Double.parseDouble(tokens[4].trim());
                        distance = Double.parseDouble(tokens[5].trim());
                        anomalyFlag = parseSafeAnomalyFlag(tokens[6].trim());
                        recordTimestamp = parseEpoch(tokens[7].trim(), recordTimestamp);
                    }

                    // Normalize anomalyFlag according to firmware rules:
                    // 0 = Healthy, 1 = Fever/Calving (<37.8°C or >39.5°C), 2 = Straying (>500m), 3 = Predator/Panic (>14.0 m/s²)
                    double dynAccel = (Math.abs(accY) < 0.001 && Math.abs(accZ) < 0.001)
                            ? Math.abs(accX)
                            : Math.abs(Math.sqrt(accX * accX + accY * accY + accZ * accZ) - 9.81);

                    if (anomalyFlag < 0 || anomalyFlag > 3) {
                        anomalyFlag = 1;
                    }
                    if (anomalyFlag == 0) {
                        if (dynAccel > 14.0) {
                            anomalyFlag = 3;
                        } else if (distance > 500.0) {
                            anomalyFlag = 2;
                        } else if (temp > 39.5 || (temp < 37.8 && temp >= 35.0)) {
                            anomalyFlag = 1;
                        }
                    }

                    TelemetryRecord record = new TelemetryRecord(
                            nodeId,
                            temp,
                            accX,
                            accY,
                            accZ,
                            distance,
                            anomalyFlag,
                            recordTimestamp
                    );

                    records.add(record);

                } catch (NumberFormatException nfe) {
                    safeLogW("Skipping unparseable row at line " + lineNumber + ": " + line);
                }
            }
        }

        safeLogI("Successfully parsed " + records.size() + " telemetry records from CSV.");
        return records;
    }

    private void safeLogD(String msg) {
        try { Log.d(TAG, msg); } catch (Throwable ignored) {}
    }

    private void safeLogI(String msg) {
        try { Log.i(TAG, msg); } catch (Throwable ignored) {}
    }

    private void safeLogW(String msg) {
        try { Log.w(TAG, msg); } catch (Throwable ignored) {}
    }

    private int parseSafeAnomalyFlag(String raw) {
        try {
            return (int) Double.parseDouble(raw);
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseEpoch(String rawTimestamp, long defaultTimestamp) {
        try {
            long parsed = Long.parseLong(rawTimestamp.trim());
            if (parsed < 10000000000L) {
                parsed *= 1000L;
            }
            return parsed;
        } catch (Exception e) {
            return defaultTimestamp;
        }
    }

    private String formatNetworkError(IOException e, String url) {
        String msg = e.getMessage() != null ? e.getMessage() : e.toString();
        if (msg.contains("Failed to connect") || msg.contains("ConnectException") || msg.contains("ENETUNREACH")) {
            return "Could not connect to the Boma Receiver Box.\n\n" +
                   "1. Make sure the Boma Box is powered on.\n" +
                   "2. Connect your phone Wi-Fi to \"ESP32_LIVESTOCK_AP\".\n" +
                   "3. Tap \"Update Herd Status\" again.";
        } else if (msg.contains("timeout") || msg.contains("SocketTimeoutException")) {
            return "The Boma Receiver took too long to respond. Move closer to the Boma Box and try again.";
        }
        return "Could not reach Boma Receiver (" + msg + ")";
    }
}
