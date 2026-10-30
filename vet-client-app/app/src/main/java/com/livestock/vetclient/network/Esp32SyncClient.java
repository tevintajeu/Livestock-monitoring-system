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
 * ESP32 Edge Gateway over local Wi-Fi Access Point (SoftAP).
 *
 * Downloads raw telemetry CSV payload, validates schema integrity, parses data rows
 * into TelemetryRecord models, and dispatches callbacks onto the Android Main (UI) Thread.
 */
public class Esp32SyncClient {

    private static final String TAG = "Esp32SyncClient";
    public static final String DEFAULT_GATEWAY_URL = "http://192.168.4.1/data";

    private final OkHttpClient httpClient;
    private final Handler mainHandler;

    /**
     * Callback interface for synchronization events.
     */
    public interface SyncCallback {
        void onSyncStarted();
        void onSyncSuccess(List<TelemetryRecord> records);
        void onSyncFailure(String errorMessage);
    }

    public Esp32SyncClient() {
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // Configure generous timeouts suitable for ESP32 flash-memory reads & Wi-Fi AP latency
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    /**
     * Synchronously/Asynchronously executes HTTP GET to the ESP32 gateway endpoint.
     *
     * @param gatewayUrl e.g. "http://192.168.4.1/data"
     * @param callback   UI thread callback listener
     */
    public void syncFromGateway(String gatewayUrl, @NonNull final SyncCallback callback) {
        final String targetUrl = (gatewayUrl != null && !gatewayUrl.trim().isEmpty())
                ? gatewayUrl.trim()
                : DEFAULT_GATEWAY_URL;

        callback.onSyncStarted();
        Log.d(TAG, "Initiating HTTP GET sync to ESP32 Gateway: " + targetUrl);

        Request request = new Request.Builder()
                .url(targetUrl)
                .header("Accept", "text/csv, text/plain")
                .header("User-Agent", "LivestockVetClient/1.0")
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
                        final String errMsg = "ESP32 Gateway responded with HTTP code: " + response.code();
                        Log.e(TAG, errMsg);
                        mainHandler.post(() -> callback.onSyncFailure(errMsg));
                        return;
                    }

                    // Read full CSV payload
                    String rawCsvData = responseBody.string();
                    Log.d(TAG, "Received raw payload bytes: " + rawCsvData.length());

                    // Parse CSV data on worker thread
                    List<TelemetryRecord> parsedRecords = parseCsvPayload(rawCsvData);

                    if (parsedRecords.isEmpty()) {
                        mainHandler.post(() -> callback.onSyncFailure("Connected to gateway, but 0 valid telemetry records could be parsed."));
                    } else {
                        mainHandler.post(() -> callback.onSyncSuccess(parsedRecords));
                    }

                } catch (Exception ex) {
                    Log.e(TAG, "Error reading/parsing CSV response", ex);
                    mainHandler.post(() -> callback.onSyncFailure("Payload parsing error: " + ex.getMessage()));
                }
            }
        });
    }

    /**
     * Parses the multi-line CSV string received from the ESP32 gateway.
     *
     * Supported CSV Schemas:
     * Unified (8-field): NODE_ID, TEMP, ACC_X, ACC_Y, ACC_Z, DISTANCE, ANOMALY_FLAG, TIMESTAMP
     * Firmware (5-field): NODE_ID, TEMP, ACC_X, DISTANCE, ANOMALY_FLAG
     * Legacy (6/7-field): NODE_ID, TEMP, ACC_X, ACC_Y, ACC_Z, ANOMALY_FLAG[, TIMESTAMP]
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

                // Skip blank lines or comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Check for header row
                String lower = line.toLowerCase();
                if (lower.startsWith("node_id") || lower.startsWith("node") || lower.contains("anomaly")) {
                    Log.d(TAG, "Skipping CSV header row at line " + lineNumber);
                    continue;
                }

                String[] tokens = line.split(",");
                if (tokens.length < 5) {
                    Log.w(TAG, "Skipping malformed row " + lineNumber + " (expected >= 5 tokens, got " + tokens.length + "): " + line);
                    continue;
                }

                try {
                    String nodeId = tokens[0].trim();
                    double temp = Double.parseDouble(tokens[1].trim());
                    double accX = Double.parseDouble(tokens[2].trim());
                    double accY = 0.0;
                    double accZ = 9.8;
                    double distance = 0.0;
                    int anomalyFlag = 0;
                    long recordTimestamp = baseTime - (records.size() * 1000L);

                    if (tokens.length == 5) {
                        // 5-field firmware schema: NODE_ID, TEMP, ACC_X, DISTANCE, ANOMALY_FLAG
                        distance = Double.parseDouble(tokens[3].trim());
                        anomalyFlag = Integer.parseInt(tokens[4].trim());
                    } else if (tokens.length == 6) {
                        // 6-field legacy schema: NODE_ID, TEMP, ACC_X, ACC_Y, ACC_Z, ANOMALY_FLAG
                        accY = Double.parseDouble(tokens[3].trim());
                        accZ = Double.parseDouble(tokens[4].trim());
                        anomalyFlag = Integer.parseInt(tokens[5].trim());
                    } else if (tokens.length == 7) {
                        // Could be:
                        // Case A: node_id, temp, acc_x, acc_y, acc_z, distance, anomaly_flag
                        // Case B: node_id, temp, acc_x, acc_y, acc_z, anomaly_flag, timestamp
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
                        // Unified 8-field schema: NODE_ID, TEMP, ACC_X, ACC_Y, ACC_Z, DISTANCE, ANOMALY_FLAG, TIMESTAMP
                        accY = Double.parseDouble(tokens[3].trim());
                        accZ = Double.parseDouble(tokens[4].trim());
                        distance = Double.parseDouble(tokens[5].trim());
                        anomalyFlag = Integer.parseInt(tokens[6].trim());
                        recordTimestamp = parseEpoch(tokens[7].trim(), recordTimestamp);
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
                    Log.w(TAG, "Number format error at line " + lineNumber + ": " + line, nfe);
                }
            }
        }

        Log.i(TAG, "Successfully parsed " + records.size() + " telemetry records from CSV.");
        return records;
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

    /**
     * Formats actionable troubleshooting messages for common ESP32 Wi-Fi connection issues.
     */
    private String formatNetworkError(IOException e, String url) {
        String msg = e.getMessage() != null ? e.getMessage() : e.toString();
        if (msg.contains("Failed to connect") || msg.contains("ConnectException") || msg.contains("ENETUNREACH")) {
            return "Unable to reach ESP32 Gateway (" + url + ").\n\nPlease ensure your phone is connected to the ESP32 Wi-Fi Access Point (e.g. \"ESP32_LIVESTOCK_AP\").";
        } else if (msg.contains("timeout") || msg.contains("SocketTimeoutException")) {
            return "Connection timed out. The ESP32 Gateway may be busy reading flash storage or LoRa buffers.";
        } else if (msg.contains("CLEARTEXT")) {
            return "Cleartext HTTP traffic was rejected by Android security. Ensure networkSecurityConfig is active.";
        }
        return "Network sync error: " + msg;
    }
}
