package com.livestock.vetclient.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.livestock.vetclient.R;
import com.livestock.vetclient.data.model.TelemetryRecord;
import com.livestock.vetclient.data.repository.TelemetryRepository;
import com.livestock.vetclient.ui.adapter.TelemetryHistoryAdapter;
import com.livestock.vetclient.util.BiometricDiagnosticEngine;

import java.util.List;
import java.util.Locale;

/**
 * Diagnostic Detail Controller for a specific monitored livestock node.
 * Displays real-time clinical assessment, biometric indicators, and historical readings.
 */
public class NodeDetailActivity extends AppCompatActivity {

    private String nodeId;
    private TelemetryRepository repository;

    // Header Views
    private TextView tvNodeId;
    private TextView tvStatusBadge;
    private TextView tvLastSynced;

    // Metric Views
    private TextView tvTemp;
    private TextView tvTempAlert;
    private TextView tvAccMagnitude;
    private TextView tvAccVector;

    // Diagnostic Advice & History
    private TextView tvClinicalAdvice;
    private TextView tvHistoryCount;
    private RecyclerView recyclerHistory;
    private TelemetryHistoryAdapter historyAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_node_detail);

        nodeId = getIntent().getStringExtra(MainActivity.EXTRA_NODE_ID);
        if (nodeId == null || nodeId.trim().isEmpty()) {
            nodeId = "UNKNOWN_NODE";
        }

        repository = new TelemetryRepository(getApplication());

        initViews();
        setupHistoryRecyclerView();
        loadNodeTelemetry();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.detail_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Node: " + nodeId);
        }

        tvNodeId = findViewById(R.id.detail_tv_node_id);
        tvStatusBadge = findViewById(R.id.detail_tv_status_badge);
        tvLastSynced = findViewById(R.id.detail_tv_last_synced);

        tvTemp = findViewById(R.id.detail_tv_temp);
        tvTempAlert = findViewById(R.id.detail_tv_temp_alert);
        tvAccMagnitude = findViewById(R.id.detail_tv_acc_mag);
        tvAccVector = findViewById(R.id.detail_tv_acc_vector);

        tvClinicalAdvice = findViewById(R.id.detail_tv_clinical_advice);
        tvHistoryCount = findViewById(R.id.detail_tv_history_count);
        recyclerHistory = findViewById(R.id.recycler_detail_history);

        tvNodeId.setText(nodeId);
    }

    private void setupHistoryRecyclerView() {
        historyAdapter = new TelemetryHistoryAdapter();
        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        recyclerHistory.setAdapter(historyAdapter);
    }

    /**
     * Loads all historical telemetry data stored for this node from Room DB.
     */
    private void loadNodeTelemetry() {
        repository.loadHistoryForNode(nodeId, history -> runOnUiThread(() -> {
            if (history == null || history.isEmpty()) {
                tvClinicalAdvice.setText("No historical records available for " + nodeId);
                tvHistoryCount.setText("Total Records: 0");
                return;
            }

            TelemetryRecord latest = history.get(0);
            BiometricDiagnosticEngine.NodeSummaryStats stats = BiometricDiagnosticEngine.computeStats(history);

            // Bind Header
            tvLastSynced.setText("Last Synced: " + latest.getFormattedDateTime());
            bindStatusBadge(latest);

            // Bind Temperature
            tvTemp.setText(String.format(Locale.US, "%.1f °C", latest.getTemperature()));
            if (latest.isFever()) {
                tvTemp.setTextColor(ContextCompat.getColor(this, R.color.status_anomaly));
                tvTempAlert.setText("⚠️ FEVER DETECTED (> 39.5°C)");
                tvTempAlert.setTextColor(ContextCompat.getColor(this, R.color.status_anomaly));
            } else {
                tvTemp.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                tvTempAlert.setText("Normal physiological range (38.0 - 39.5°C)");
                tvTempAlert.setTextColor(ContextCompat.getColor(this, R.color.status_healthy));
            }

            // Bind 3D Acceleration
            tvAccMagnitude.setText(String.format(Locale.US, "%.2f m/s²", latest.getAccelerationMagnitude()));
            tvAccVector.setText(String.format(
                    Locale.US,
                    "X: %.2f | Y: %.2f | Z: %.2f (Peak: %.2f)",
                    latest.getAccX(),
                    latest.getAccY(),
                    latest.getAccZ(),
                    stats.maxAccMagnitude
            ));

            // Bind Clinical Diagnostic Advice
            String advice = BiometricDiagnosticEngine.generateClinicalAdvice(latest, stats);
            tvClinicalAdvice.setText(advice);

            // Update History RecyclerView
            tvHistoryCount.setText("Total Records: " + history.size() + " (" + stats.anomalyCount + " anomalies)");
            historyAdapter.updateData(history);
        }));
    }

    private void bindStatusBadge(TelemetryRecord record) {
        if (record.getAnomalyFlag() == 1) {
            int redColor = ContextCompat.getColor(this, R.color.status_anomaly);
            tvStatusBadge.setBackgroundResource(R.drawable.badge_anomaly);
            tvStatusBadge.setTextColor(redColor);

            if (record.isFever() && record.isHighAcceleration()) {
                tvStatusBadge.setText("CRITICAL: FEVER & DISTRESS");
            } else if (record.isFever()) {
                tvStatusBadge.setText("ALERT: FEVER");
            } else {
                tvStatusBadge.setText("ALERT: HIGH MOTION");
            }
        } else {
            int greenColor = ContextCompat.getColor(this, R.color.status_healthy);
            tvStatusBadge.setBackgroundResource(R.drawable.badge_healthy);
            tvStatusBadge.setTextColor(greenColor);
            tvStatusBadge.setText(R.string.status_healthy_badge);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
