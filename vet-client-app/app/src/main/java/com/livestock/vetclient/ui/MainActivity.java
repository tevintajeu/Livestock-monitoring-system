package com.livestock.vetclient.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.livestock.vetclient.R;
import com.livestock.vetclient.data.model.TelemetryRecord;
import com.livestock.vetclient.data.repository.TelemetryRepository;
import com.livestock.vetclient.network.Esp32SyncClient;
import com.livestock.vetclient.ui.adapter.LivestockNodeAdapter;

import java.util.List;

/**
 * Main Controller for the Veterinary Client Application.
 *
 * Coordinates:
 * 1. Offline triage dashboard displaying monitored livestock nodes.
 * 2. HTTP GET synchronization against the localized ESP32 Edge Gateway AP or custom IP.
 * 3. Local persistence and caching via Room Database.
 * 4. Navigation to diagnostic detail telemetry for individual animals.
 */
public class MainActivity extends AppCompatActivity implements LivestockNodeAdapter.OnNodeClickListener {

    public static final String EXTRA_NODE_ID = "extra_node_id";
    public static final String DEFAULT_GATEWAY_ENDPOINT = "http://192.168.4.1/data";
    private static final String PREF_NAME = "vet_client_prefs";
    private static final String PREF_KEY_GATEWAY_URL = "pref_gateway_url";

    // Data layer & Networking
    private TelemetryRepository repository;
    private Esp32SyncClient syncClient;

    // UI Components
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerViewNodes;
    private LivestockNodeAdapter nodeAdapter;
    private LinearLayout layoutEmptyState;
    private LinearLayout layoutSyncProgress;
    private TextView tvSyncStatusMsg;
    private TextView tvGatewayEndpoint;

    // Triage Counter Views
    private TextView tvCountTotal;
    private TextView tvCountAnomaly;
    private TextView tvCountHealthy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Data & Networking singletons
        repository = new TelemetryRepository(getApplication());
        syncClient = new Esp32SyncClient();

        initViews();
        setupRecyclerView();
        setupListeners();

        // Initial load from local Room DB cache (supports 100% offline startup)
        refreshDashboardFromLocalDb();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        recyclerViewNodes = findViewById(R.id.recycler_livestock_nodes);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        layoutSyncProgress = findViewById(R.id.layout_sync_progress);
        tvSyncStatusMsg = findViewById(R.id.tv_sync_status_msg);
        tvGatewayEndpoint = findViewById(R.id.tv_gateway_endpoint);

        tvCountTotal = findViewById(R.id.tv_count_total_nodes);
        tvCountAnomaly = findViewById(R.id.tv_count_anomaly_nodes);
        tvCountHealthy = findViewById(R.id.tv_count_healthy_nodes);

        updateGatewayEndpointDisplay();
    }

    private void setupRecyclerView() {
        nodeAdapter = new LivestockNodeAdapter(this);
        recyclerViewNodes.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewNodes.setAdapter(nodeAdapter);
    }

    private void setupListeners() {
        // Top Bar Sync Button
        findViewById(R.id.btn_sync_gateway).setOnClickListener(v -> triggerGatewaySync());

        // Empty state sync button
        findViewById(R.id.btn_empty_sync).setOnClickListener(v -> triggerGatewaySync());

        // Pull to refresh gesture
        swipeRefreshLayout.setOnRefreshListener(this::triggerGatewaySync);

        // Gateway endpoint configuration click listener
        View layoutGateway = findViewById(R.id.layout_gateway_config);
        if (layoutGateway != null) {
            layoutGateway.setOnClickListener(v -> showGatewayConfigDialog());
        }
        if (tvGatewayEndpoint != null) {
            tvGatewayEndpoint.setOnClickListener(v -> showGatewayConfigDialog());
        }
    }

    private String getGatewayUrl() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_KEY_GATEWAY_URL, DEFAULT_GATEWAY_ENDPOINT);
    }

    private void setGatewayUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            url = DEFAULT_GATEWAY_ENDPOINT;
        }
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_KEY_GATEWAY_URL, url.trim()).apply();
        updateGatewayEndpointDisplay();
    }

    private void updateGatewayEndpointDisplay() {
        if (tvGatewayEndpoint != null) {
            tvGatewayEndpoint.setText("Target: " + getGatewayUrl());
        }
    }

    /**
     * Dialog allowing the veterinarian to select or enter the Gateway endpoint:
     * - Physical ESP32 SoftAP (http://192.168.4.1/data)
     * - Android Emulator Mock Gateway (http://10.0.2.2:8080/data)
     * - Custom LAN IP for physical device Wi-Fi testing (e.g. http://192.168.1.X:8080/data)
     */
    private void showGatewayConfigDialog() {
        final EditText input = new EditText(this);
        input.setText(getGatewayUrl());
        input.setSelectAllOnFocus(true);
        input.setHint("http://192.168.4.1/data");

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, pad / 2);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Configure Gateway Endpoint")
                .setMessage("Enter the HTTP URL for the ESP32 Gateway AP or PC mock server:")
                .setView(container)
                .setPositiveButton("Save", (dialog, which) -> {
                    String entered = input.getText().toString().trim();
                    if (!entered.isEmpty()) {
                        setGatewayUrl(entered);
                        Toast.makeText(this, "Gateway updated: " + entered, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Emulator Preset", (dialog, which) -> {
                    setGatewayUrl("http://10.0.2.2:8080/data");
                    Toast.makeText(this, "Set to Android Emulator localhost", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Default AP", (dialog, which) -> {
                    setGatewayUrl(DEFAULT_GATEWAY_ENDPOINT);
                    Toast.makeText(this, "Reset to ESP32 SoftAP (192.168.4.1)", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    /**
     * Executes the HTTP GET synchronization routine against the configured ESP32 Edge Gateway.
     */
    private void triggerGatewaySync() {
        final String targetUrl = getGatewayUrl();

        syncClient.syncFromGateway(targetUrl, new Esp32SyncClient.SyncCallback() {
            @Override
            public void onSyncStarted() {
                runOnUiThread(() -> {
                    layoutSyncProgress.setVisibility(View.VISIBLE);
                    tvSyncStatusMsg.setText(R.string.syncing_data);
                });
            }

            @Override
            public void onSyncSuccess(List<TelemetryRecord> records) {
                // Batch insert parsed records into Room Database in background
                repository.saveSyncedRecords(records, new TelemetryRepository.SyncSaveCallback() {
                    @Override
                    public void onSaved(int recordCount) {
                        runOnUiThread(() -> {
                            layoutSyncProgress.setVisibility(View.GONE);
                            swipeRefreshLayout.setRefreshing(false);

                            String successMsg = getString(R.string.sync_success, recordCount);
                            Snackbar.make(recyclerViewNodes, successMsg, Snackbar.LENGTH_LONG)
                                    .setBackgroundTint(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.primary_navy))
                                    .setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.white))
                                    .show();

                            // Reload triage dashboard from updated SQLite database
                            refreshDashboardFromLocalDb();
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        runOnUiThread(() -> {
                            layoutSyncProgress.setVisibility(View.GONE);
                            swipeRefreshLayout.setRefreshing(false);
                            showErrorDialog("Database Error", "Failed to cache records locally: " + e.getMessage());
                        });
                    }
                });
            }

            @Override
            public void onSyncFailure(String errorMessage) {
                runOnUiThread(() -> {
                    layoutSyncProgress.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);

                    // Show prominent error dialog with troubleshooting advice
                    showErrorDialog("Gateway Sync Failed", errorMessage);
                });
            }
        });
    }

    /**
     * Reads the latest telemetry state for each distinct node from the local Room database.
     */
    private void refreshDashboardFromLocalDb() {
        repository.loadLatestRecordsPerNode(latestRecords -> runOnUiThread(() -> {
            if (latestRecords == null || latestRecords.isEmpty()) {
                // Display empty state prompt
                layoutEmptyState.setVisibility(View.VISIBLE);
                recyclerViewNodes.setVisibility(View.GONE);
                updateTriageCounters(0, 0, 0);
            } else {
                layoutEmptyState.setVisibility(View.GONE);
                recyclerViewNodes.setVisibility(View.VISIBLE);
                nodeAdapter.updateData(latestRecords);

                // Compute triage counts (anomalies include fever, distress motion, and geofence stray alerts)
                int total = latestRecords.size();
                int anomalies = 0;
                for (TelemetryRecord record : latestRecords) {
                    if (record.getAnomalyFlag() > 0 || record.isStray()) {
                        anomalies++;
                    }
                }
                int healthy = total - anomalies;
                updateTriageCounters(total, anomalies, healthy);
            }
        }));
    }

    private void updateTriageCounters(int total, int anomalies, int healthy) {
        tvCountTotal.setText(String.valueOf(total));
        tvCountAnomaly.setText(String.valueOf(anomalies));
        tvCountHealthy.setText(String.valueOf(healthy));
    }

    /**
     * User clicked a livestock card; navigate to full diagnostic history screen.
     */
    @Override
    public void onNodeClick(TelemetryRecord record) {
        Intent intent = new Intent(this, NodeDetailActivity.class);
        intent.putExtra(EXTRA_NODE_ID, record.getNodeId());
        startActivity(intent);
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setIcon(R.drawable.ic_warning_triangle)
                .setPositiveButton("OK", null)
                .setNeutralButton("Wi-Fi Settings", (dialog, which) -> {
                    // Open Wi-Fi settings so vet can connect to ESP32 AP
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Change IP", (dialog, which) -> showGatewayConfigDialog())
                .show();
    }
}
