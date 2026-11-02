package com.livestock.vetclient.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.livestock.vetclient.R;
import com.livestock.vetclient.data.model.AnimalProfile;
import com.livestock.vetclient.data.model.ProductionRecord;
import com.livestock.vetclient.data.model.TelemetryRecord;
import com.livestock.vetclient.data.repository.TelemetryRepository;
import com.livestock.vetclient.ui.adapter.TelemetryHistoryAdapter;
import com.livestock.vetclient.util.AuthManager;
import com.livestock.vetclient.util.BiometricDiagnosticEngine;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Screen 3: Animal Health, Action Checklist & Yield Detail Controller (NodeDetailActivity).
 *
 * Implements:
 * 1. Personalized Animal Identity Header + "✏️ Edit Name/Profile" dialog.
 * 2. Card 1: "What You Should Do Now" (3-step Farmer Checklist vs Full Vet Differential Diagnosis).
 * 3. Card 2: Milk & Calving Output Tracker + Health-to-Yield Correlation Banner.
 * 4. Card 3: Visual Vitals Gauges (LinearProgressIndicator bars; raw X/Y/Z only shown to ROLE_VET).
 * 5. Card 4: Recent History Timeline Log.
 */
public class NodeDetailActivity extends AppCompatActivity {

    private String nodeId;
    private TelemetryRepository repository;
    private AuthManager authManager;
    private AnimalProfile currentProfile;

    // Header Views
    private TextView tvNodeId;
    private TextView tvBreedStatus;
    private TextView tvStatusBadge;
    private TextView tvLastSynced;
    private TextView tvProfileNotes;
    private MaterialButton btnEditProfile;
    private MaterialButton btnDeleteCow;

    // Card 1: Actionable Triage Views
    private TextView tvTriageRoleSubtitle;
    private LinearLayout layoutFarmerChecklist;
    private CheckBox cbStep1;
    private CheckBox cbStep2;
    private CheckBox cbStep3;
    private TextView tvClinicalAdvice;
    private TextView tvLastHandledStatus;
    private MaterialButton btnMarkHandled;

    // Card 2: Milk & Output Tracker Views
    private TextView tvYieldBanner;
    private TextView tvTodayMilkTotal;
    private TextView tvSevenDayMilkAvg;
    private TextView tvCalfStatusValue;
    private TextView tvRecentMilkLogs;
    private MaterialButton btnRecordMilkDetail;

    // Card 3: Visual Vitals Gauges
    private TextView tvTemp;
    private LinearProgressIndicator gaugeTempBar;
    private TextView tvTempAlert;

    private TextView tvAccMagnitude;
    private LinearProgressIndicator gaugeMotionBar;
    private TextView tvAccVector;

    private TextView tvDistance;
    private LinearProgressIndicator gaugeDistanceBar;
    private TextView tvDistanceAlert;

    // Card 4: History Log
    private TextView tvHistoryCount;
    private RecyclerView recyclerHistory;
    private TelemetryHistoryAdapter historyAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_node_detail);

        nodeId = getIntent().getStringExtra(MainActivity.EXTRA_NODE_ID);
        if (nodeId == null || nodeId.trim().isEmpty()) {
            nodeId = "NODE_001";
        }

        repository = new TelemetryRepository(getApplication());
        authManager = new AuthManager(this);

        initViews();
        setupHistoryRecyclerView();
        setupListeners();
        loadFullNodeDetail();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.detail_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Animal Health & Yield");
        }

        tvNodeId = findViewById(R.id.detail_tv_node_id);
        tvBreedStatus = findViewById(R.id.detail_tv_breed_status);
        tvStatusBadge = findViewById(R.id.detail_tv_status_badge);
        tvLastSynced = findViewById(R.id.detail_tv_last_synced);
        tvProfileNotes = findViewById(R.id.detail_tv_profile_notes);
        btnEditProfile = findViewById(R.id.btn_edit_animal_profile);
        btnDeleteCow = findViewById(R.id.btn_delete_cow);

        tvTriageRoleSubtitle = findViewById(R.id.tv_triage_role_subtitle);
        layoutFarmerChecklist = findViewById(R.id.layout_farmer_checklist);
        cbStep1 = findViewById(R.id.cb_farmer_step_1);
        cbStep2 = findViewById(R.id.cb_farmer_step_2);
        cbStep3 = findViewById(R.id.cb_farmer_step_3);
        tvClinicalAdvice = findViewById(R.id.detail_tv_clinical_advice);
        tvLastHandledStatus = findViewById(R.id.tv_last_handled_status);
        btnMarkHandled = findViewById(R.id.btn_mark_handled);

        tvYieldBanner = findViewById(R.id.detail_tv_yield_banner);
        tvTodayMilkTotal = findViewById(R.id.tv_today_milk_total);
        tvSevenDayMilkAvg = findViewById(R.id.tv_seven_day_milk_avg);
        tvCalfStatusValue = findViewById(R.id.tv_calf_status_value);
        tvRecentMilkLogs = findViewById(R.id.tv_recent_milk_logs);
        btnRecordMilkDetail = findViewById(R.id.btn_record_milk_detail);

        tvTemp = findViewById(R.id.detail_tv_temp);
        gaugeTempBar = findViewById(R.id.gauge_temp_bar);
        tvTempAlert = findViewById(R.id.detail_tv_temp_alert);

        tvAccMagnitude = findViewById(R.id.detail_tv_acc_mag);
        gaugeMotionBar = findViewById(R.id.gauge_motion_bar);
        tvAccVector = findViewById(R.id.detail_tv_acc_vector);

        tvDistance = findViewById(R.id.detail_tv_distance);
        gaugeDistanceBar = findViewById(R.id.gauge_distance_bar);
        tvDistanceAlert = findViewById(R.id.detail_tv_distance_alert);

        tvHistoryCount = findViewById(R.id.detail_tv_history_count);
        recyclerHistory = findViewById(R.id.recycler_detail_history);
    }

    private void setupHistoryRecyclerView() {
        historyAdapter = new TelemetryHistoryAdapter();
        historyAdapter.setVetMode(authManager.isVetMode());
        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        recyclerHistory.setAdapter(historyAdapter);
    }

    private void setupListeners() {
        btnEditProfile.setOnClickListener(v -> showEditProfileDialog());
        btnDeleteCow.setOnClickListener(v -> confirmDeleteThisCow());
        btnRecordMilkDetail.setOnClickListener(v -> showRecordMilkDialog());

        btnMarkHandled.setOnClickListener(v -> {
            cbStep1.setChecked(true);
            cbStep2.setChecked(true);
            cbStep3.setChecked(true);
            authManager.markNodeHandled(nodeId);
            updateHandledStatusUi();
            String cowName = (currentProfile != null) ? currentProfile.getCustomName() : nodeId;
            Toast.makeText(this, "✅ Marked " + cowName + " as checked & handled!", Toast.LENGTH_SHORT).show();
        });
    }

    private void confirmDeleteThisCow() {
        String name = (currentProfile != null) ? currentProfile.getCustomName() : nodeId;
        new AlertDialog.Builder(this)
                .setTitle("Delete " + name + "?")
                .setMessage("Are you sure you want to delete " + name + " (" + nodeId + ") and all associated health, telemetry readings, and milk logs?\n\nThis cannot be undone.")
                .setIcon(R.drawable.ic_warning_triangle)
                .setPositiveButton("Delete", (dialog, which) -> {
                    repository.deleteAnimalCompletely(nodeId, () -> runOnUiThread(() -> {
                        Toast.makeText(this, "Deleted " + name + " and all records", Toast.LENGTH_SHORT).show();
                        finish();
                    }));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateHandledStatusUi() {
        long ts = authManager.getNodeLastHandledTime(nodeId);
        if (ts > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM HH:mm", Locale.getDefault());
            tvLastHandledStatus.setVisibility(View.VISIBLE);
            tvLastHandledStatus.setText("✅ Last Checked & Handled (" + authManager.getRoleDisplayName() + "): " + sdf.format(new Date(ts)));
        } else {
            tvLastHandledStatus.setVisibility(View.GONE);
        }
    }

    /**
     * Loads AnimalProfile, TelemetryHistory, and ProductionHistory from Room SQLite.
     */
    private void loadFullNodeDetail() {
        repository.loadNodeDetailBundle(nodeId, bundle -> runOnUiThread(() -> {
            currentProfile = bundle.profile;
            boolean isVet = authManager.isVetMode();

            // 1. Bind Animal Identity Header
            tvNodeId.setText(currentProfile.getDisplayHeader());
            if (isVet) {
                tvBreedStatus.setText(currentProfile.getBreedAndStatusSubtitle() + " • [" + nodeId + "]");
            } else {
                tvBreedStatus.setText(currentProfile.getBreedAndStatusSubtitle());
            }
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(currentProfile.getCustomName() + " – Health & Yield");
            }

            if (!currentProfile.getNotes().isEmpty()) {
                tvProfileNotes.setVisibility(View.VISIBLE);
                tvProfileNotes.setText("📝 Notes: " + currentProfile.getNotes());
            } else {
                tvProfileNotes.setVisibility(View.GONE);
            }

            updateHandledStatusUi();

            List<TelemetryRecord> history = bundle.telemetryHistory;
            TelemetryRecord latest = (history != null && !history.isEmpty()) ? history.get(0) : null;

            // 2. Bind Card 2: Milk & Output Tracker
            tvTodayMilkTotal.setText(String.format(Locale.US, "%.1f L", bundle.todayMilkLiters));
            tvSevenDayMilkAvg.setText(String.format(Locale.US, "%.1f L", bundle.sevenDayAvgLiters));
            tvCalfStatusValue.setText(bundle.latestCalfStatus);

            String yieldBannerText = BiometricDiagnosticEngine.generateHealthToYieldBanner(latest, currentProfile);
            tvYieldBanner.setText(yieldBannerText);
            if (latest != null && latest.isFever()) {
                tvYieldBanner.setBackgroundResource(R.drawable.bg_summary_danger);
                tvYieldBanner.setTextColor(ContextCompat.getColor(this, R.color.status_anomaly));
            } else if (latest != null && (latest.isCalvingDrop() || latest.isStray() || latest.isPredatorOrPanic())) {
                tvYieldBanner.setBackgroundResource(R.drawable.bg_summary_warning);
                tvYieldBanner.setTextColor(ContextCompat.getColor(this, R.color.status_warning));
            } else {
                tvYieldBanner.setBackgroundResource(R.drawable.bg_summary_healthy);
                tvYieldBanner.setTextColor(ContextCompat.getColor(this, R.color.status_healthy));
            }

            bindRecentProductionSummary(bundle.productionHistory);

            if (latest == null) {
                tvClinicalAdvice.setText("No telemetry readings available yet for " + currentProfile.getCustomName());
                tvHistoryCount.setText("Total Readings: 0");
                return;
            }

            BiometricDiagnosticEngine.NodeSummaryStats stats = BiometricDiagnosticEngine.computeStats(history);

            // 3. Bind Status Badge & Timestamp
            tvLastSynced.setText("Last Updated: " + latest.getFormattedDateTime());
            bindStatusBadge(latest);

            // 4. Bind Card 1: "What You Should Do Now" (Farmer Checklist vs Vet Clinical Protocol)
            String[] steps = BiometricDiagnosticEngine.generateFarmerChecklistSteps(latest, currentProfile.getCustomName());
            cbStep1.setText(steps[0]);
            cbStep2.setText(steps[1]);
            cbStep3.setText(steps[2]);

            String vetProtocol = BiometricDiagnosticEngine.generateClinicalAdvice(latest, stats);
            tvClinicalAdvice.setText(vetProtocol);

            if (isVet) {
                tvTriageRoleSubtitle.setText("🩺 Veterinary Clinical Differential Diagnosis & Protocol");
                tvTriageRoleSubtitle.setTextColor(ContextCompat.getColor(this, R.color.primary_navy));
                tvClinicalAdvice.setVisibility(View.VISIBLE);
                layoutFarmerChecklist.setVisibility(View.VISIBLE);
            } else {
                tvTriageRoleSubtitle.setText("👨🏾‍🌾 Simple 3-Step Herder Care Checklist");
                tvTriageRoleSubtitle.setTextColor(ContextCompat.getColor(this, R.color.status_healthy));
                tvClinicalAdvice.setVisibility(View.GONE);
                layoutFarmerChecklist.setVisibility(View.VISIBLE);
            }

            // 5. Bind Card 3: Visual Vitals Gauges
            bindVisualVitalsGauges(latest, stats, isVet);

            // 6. Bind Card 4: History RecyclerView
            historyAdapter.setVetMode(isVet);
            tvHistoryCount.setText(String.format(Locale.US, "Readings: %d (%d alerts)", history.size(), stats.anomalyCount));
            historyAdapter.updateData(history);
        }));
    }

    private void bindVisualVitalsGauges(TelemetryRecord latest,
                                        BiometricDiagnosticEngine.NodeSummaryStats stats,
                                        boolean isVet) {
        int greenColor = ContextCompat.getColor(this, R.color.status_healthy);
        int amberColor = ContextCompat.getColor(this, R.color.status_warning);
        int redColor = ContextCompat.getColor(this, R.color.status_anomaly);

        // A. Body Heat Gauge (36.0°C to 42.0°C -> 0 to 100%)
        double temp = latest.getTemperature();
        int tempProgress = (int) Math.round(((temp - 36.0) / 6.0) * 100.0);
        tempProgress = Math.max(5, Math.min(100, tempProgress));
        gaugeTempBar.setProgress(tempProgress);
        tvTemp.setText(latest.getDetailedHumanTempLabel());

        if (latest.isFever()) {
            tvTemp.setTextColor(redColor);
            gaugeTempBar.setIndicatorColor(redColor);
            tvTempAlert.setText("⚠️ High Fever (>39.5°C)! Move to shade and call vet.");
            tvTempAlert.setTextColor(redColor);
        } else if (latest.isCalvingDrop()) {
            tvTemp.setTextColor(amberColor);
            gaugeTempBar.setIndicatorColor(amberColor);
            tvTempAlert.setText("🐄 Calving temperature drop (<37.8°C) — prepare maternity pen.");
            tvTempAlert.setTextColor(amberColor);
        } else {
            tvTemp.setTextColor(greenColor);
            gaugeTempBar.setIndicatorColor(greenColor);
            tvTempAlert.setText("✅ Healthy body heat (38.0°C – 39.5°C)");
            tvTempAlert.setTextColor(greenColor);
        }

        // B. Movement Gauge (0 to 20 m/s² dynamic acceleration -> 0 to 100%)
        double dynAccel = latest.getDynamicAcceleration();
        int motionProgress = (int) Math.round((dynAccel / 20.0) * 100.0);
        motionProgress = Math.max(8, Math.min(100, motionProgress));
        gaugeMotionBar.setProgress(motionProgress);
        tvAccMagnitude.setText(latest.getDetailedHumanMotionLabel());

        if (latest.isPredatorOrPanic()) {
            tvAccMagnitude.setTextColor(redColor);
            gaugeMotionBar.setIndicatorColor(redColor);
        } else if (dynAccel > 4.0) {
            tvAccMagnitude.setTextColor(amberColor);
            gaugeMotionBar.setIndicatorColor(amberColor);
        } else {
            tvAccMagnitude.setTextColor(greenColor);
            gaugeMotionBar.setIndicatorColor(greenColor);
        }

        // Only show raw X/Y/Z vector text to ROLE_VET!
        if (isVet) {
            tvAccVector.setVisibility(View.VISIBLE);
            tvAccVector.setText(String.format(
                    Locale.US,
                    "🩺 Raw Telemetry — X: %.2f | Y: %.2f | Z: %.2f | Dyn: %.2f m/s² | Peak |a|: %.2f m/s²",
                    latest.getAccX(),
                    latest.getAccY(),
                    latest.getAccZ(),
                    dynAccel,
                    stats.maxAccMagnitude
            ));
        } else {
            tvAccVector.setVisibility(View.GONE);
        }

        // C. Herd Distance Gauge (0m to 1000m -> 0 to 100%)
        double dist = latest.getDistance();
        int distProgress = (int) Math.round((dist / 1000.0) * 100.0);
        distProgress = Math.max(5, Math.min(100, distProgress));
        gaugeDistanceBar.setProgress(distProgress);
        tvDistance.setText(latest.getDetailedHumanDistanceLabel());

        if (latest.isStray()) {
            tvDistance.setTextColor(redColor);
            gaugeDistanceBar.setIndicatorColor(redColor);
            tvDistanceAlert.setText("🚨 Outside 500m safe Boma boundary! Send herder to retrieve.");
            tvDistanceAlert.setTextColor(redColor);
        } else if (dist > 350.0) {
            tvDistance.setTextColor(amberColor);
            gaugeDistanceBar.setIndicatorColor(amberColor);
            tvDistanceAlert.setText("Grazing near outer pasture boundary (Safe limit: 500m)");
            tvDistanceAlert.setTextColor(amberColor);
        } else {
            tvDistance.setTextColor(greenColor);
            gaugeDistanceBar.setIndicatorColor(greenColor);
            tvDistanceAlert.setText("✅ Safely inside Boma grazing zone (Peak: " + String.format(Locale.US, "%.0fm", stats.maxDistance) + ")");
            tvDistanceAlert.setTextColor(greenColor);
        }
    }

    private void bindRecentProductionSummary(List<ProductionRecord> prodList) {
        if (prodList == null || prodList.isEmpty()) {
            tvRecentMilkLogs.setText("No daily milk entries logged yet. Tap the button below to record today's milk.");
            return;
        }
        StringBuilder sb = new StringBuilder("Recent Milk & Calving Logs:\n");
        int limit = Math.min(3, prodList.size());
        for (int i = 0; i < limit; i++) {
            ProductionRecord pr = prodList.get(i);
            sb.append(String.format(Locale.US, "• %s: Morning %.1fL + Evening %.1fL = %.1fL (%s)",
                    pr.getFormattedDateTime(),
                    pr.getMorningMilkLiters(),
                    pr.getEveningMilkLiters(),
                    pr.getTotalMilkLiters(),
                    pr.getCalfStatus()));
            if (i < limit - 1) sb.append("\n");
        }
        tvRecentMilkLogs.setText(sb.toString());
    }

    private void bindStatusBadge(TelemetryRecord record) {
        int greenColor = ContextCompat.getColor(this, R.color.status_healthy);
        int amberColor = ContextCompat.getColor(this, R.color.status_warning);
        int redColor = ContextCompat.getColor(this, R.color.status_anomaly);

        if (record.isPredatorOrPanic()) {
            tvStatusBadge.setBackgroundResource(R.drawable.badge_anomaly);
            tvStatusBadge.setTextColor(redColor);
            tvStatusBadge.setText("🚨 DANGER • HATARI");
        } else if (record.isStray()) {
            tvStatusBadge.setBackgroundResource(R.drawable.badge_anomaly);
            tvStatusBadge.setTextColor(redColor);
            tvStatusBadge.setText("📍 STRAYING");
        } else if (record.isFever()) {
            tvStatusBadge.setBackgroundResource(R.drawable.badge_warning);
            tvStatusBadge.setTextColor(amberColor);
            tvStatusBadge.setText("🌡️ FEVER ALERT");
        } else if (record.isCalvingDrop()) {
            tvStatusBadge.setBackgroundResource(R.drawable.badge_warning);
            tvStatusBadge.setTextColor(amberColor);
            tvStatusBadge.setText("🐄 CALVING SOON");
        } else {
            tvStatusBadge.setBackgroundResource(R.drawable.badge_healthy);
            tvStatusBadge.setTextColor(greenColor);
            tvStatusBadge.setText("✓ HEALTHY");
        }
    }

    /**
     * Dialog allowing the Farmer or Vet to personalize the cow's name, ear tag, breed, and lactation status.
     */
    private void showEditProfileDialog() {
        if (currentProfile == null) {
            currentProfile = AnimalProfile.createDefaultForNode(nodeId);
        }

        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad / 2, pad, pad / 2);

        final EditText etName = new EditText(this);
        etName.setHint("Cow Name (e.g. Malkia)");
        etName.setText(currentProfile.getCustomName());
        container.addView(etName);

        final EditText etTag = new EditText(this);
        etTag.setHint("Ear Tag Number (e.g. KE-042)");
        etTag.setText(currentProfile.getEarTagNumber());
        container.addView(etTag);

        final EditText etBreed = new EditText(this);
        etBreed.setHint("Breed (e.g. Boran, Sahiwal, Zebu)");
        etBreed.setText(currentProfile.getBreed());
        container.addView(etBreed);

        TextView tvStatusLabel = new TextView(this);
        tvStatusLabel.setText("Lactation / Pregnancy Status:");
        tvStatusLabel.setPadding(0, pad / 2, 0, 4);
        container.addView(tvStatusLabel);

        final Spinner spinnerLactation = new Spinner(this);
        String[] statuses = new String[]{"Milking", "Pregnant", "Dry"};
        spinnerLactation.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, statuses));
        for (int i = 0; i < statuses.length; i++) {
            if (statuses[i].equalsIgnoreCase(currentProfile.getLactationStatus())) {
                spinnerLactation.setSelection(i);
                break;
            }
        }
        container.addView(spinnerLactation);

        final EditText etNotes = new EditText(this);
        etNotes.setHint("Herder / Vet Notes");
        etNotes.setText(currentProfile.getNotes());
        container.addView(etNotes);

        new AlertDialog.Builder(this)
                .setTitle("✏️ Edit Cow Profile (" + nodeId + ")")
                .setView(container)
                .setPositiveButton("Save Profile", (dialog, which) -> {
                    currentProfile.setCustomName(etName.getText().toString().trim());
                    currentProfile.setEarTagNumber(etTag.getText().toString().trim());
                    currentProfile.setBreed(etBreed.getText().toString().trim());
                    currentProfile.setLactationStatus((String) spinnerLactation.getSelectedItem());
                    currentProfile.setNotes(etNotes.getText().toString().trim());

                    repository.saveAnimalProfile(currentProfile, () -> runOnUiThread(() -> {
                        Toast.makeText(this, "Updated profile for " + currentProfile.getCustomName(), Toast.LENGTH_SHORT).show();
                        loadFullNodeDetail();
                    }));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Dialog allowing the Farmer or Vet to log Morning/Evening Milk Liters and Calf Status.
     */
    private void showRecordMilkDialog() {
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad / 2, pad, pad / 2);

        final EditText etMorning = new EditText(this);
        etMorning.setHint("Morning Milk (Liters, e.g. 5.5)");
        etMorning.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        container.addView(etMorning);

        final EditText etEvening = new EditText(this);
        etEvening.setHint("Evening Milk (Liters, e.g. 4.0)");
        etEvening.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        container.addView(etEvening);

        TextView tvCalfLabel = new TextView(this);
        tvCalfLabel.setText("Calf / Calving Status:");
        tvCalfLabel.setPadding(0, pad / 2, 0, 4);
        container.addView(tvCalfLabel);

        final Spinner spinnerCalf = new Spinner(this);
        String[] calfOptions = new String[]{"Normal", "Expecting Soon", "Calved"};
        spinnerCalf.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, calfOptions));
        container.addView(spinnerCalf);

        final EditText etNotes = new EditText(this);
        etNotes.setHint("Notes (e.g. Healthy udder / colostrum)");
        container.addView(etNotes);

        String cowTitle = (currentProfile != null) ? currentProfile.getDisplayHeader() : nodeId;
        new AlertDialog.Builder(this)
                .setTitle("🥛 Record Daily Milk – " + cowTitle)
                .setView(container)
                .setPositiveButton("Save Record", (dialog, which) -> {
                    double morning = parseDoubleSafe(etMorning.getText().toString());
                    double evening = parseDoubleSafe(etEvening.getText().toString());
                    String calfStatus = (String) spinnerCalf.getSelectedItem();
                    String notes = etNotes.getText().toString().trim();

                    ProductionRecord record = new ProductionRecord(
                            nodeId,
                            System.currentTimeMillis(),
                            morning,
                            evening,
                            calfStatus,
                            authManager.getRoleDisplayName(),
                            notes
                    );
                    repository.saveProductionRecord(record, () -> runOnUiThread(() -> {
                        Toast.makeText(this, String.format(Locale.US,
                                "Logged %.1f Liters for %s",
                                record.getTotalMilkLiters(),
                                currentProfile != null ? currentProfile.getCustomName() : nodeId), Toast.LENGTH_SHORT).show();
                        loadFullNodeDetail();
                    }));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private double parseDoubleSafe(String val) {
        if (val == null || val.trim().isEmpty()) return 0.0;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return 0.0;
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
