package com.livestock.vetclient.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
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
 * 2. HTTP GET synchronization against the localized ESP32 Edge Gateway AP.
 * 3. Local persistence and caching via Room Database.
 * 4. Navigation to diagnostic detail telemetry for individual animals.
 */
public class MainActivity extends AppCompatActivity implements LivestockNodeAdapter.OnNodeClickListener {

    public static final String EXTRA_NODE_ID = "extra_node_id";
    public static final String GATEWAY_ENDPOINT = "http://192.168.4.1/data";

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

        tvCountTotal = findViewById(R.id.tv_count_total_nodes);
        tvCountAnomaly = findViewById(R.id.tv_count_anomaly_nodes);
        tvCountHealthy = findViewById(R.id.tv_count_healthy_nodes);
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
    }

    /**
     * Executes the HTTP GET synchronization routine against the ESP32 Edge Gateway.
     */
    private void triggerGatewaySync() {
        syncClient.syncFromGateway(GATEWAY_ENDPOINT, new Esp32SyncClient.SyncCallback() {
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

                // Compute triage counts
                int total = latestRecords.size();
                int anomalies = 0;
                for (TelemetryRecord record : latestRecords) {
                    if (record.getAnomalyFlag() == 1) {
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
                .setNeutralButton("Settings", (dialog, which) -> {
                    // Open Wi-Fi settings so vet can connect to ESP32 AP
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
                    } catch (Exception ignored) {}
                })
                .show();
    }
}
