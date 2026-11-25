package com.livestock.vetclient.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.livestock.vetclient.R;
import com.livestock.vetclient.data.model.AnimalProfile;
import com.livestock.vetclient.data.model.ProductionRecord;
import com.livestock.vetclient.data.model.TelemetryRecord;
import com.livestock.vetclient.data.repository.TelemetryRepository;
import com.livestock.vetclient.network.Esp32SyncClient;
import com.livestock.vetclient.ui.adapter.LivestockNodeAdapter;
import com.livestock.vetclient.util.AuthManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Screen 2: Human-Centered Herd Dashboard (MainActivity).
 *
 * Serves two personas seamlessly:
 * 1. ROLE_FARMER (Cow Owner / Herder): Zero technical jargon, real cow names, plain-language triage,
 *    guided "Boma Box" sync card, interactive filter chips, and 1-tap milk logging.
 * 2. ROLE_VET (Visiting Field Veterinarian): Unlocks raw acceleration vectors, Gateway IP configuration,
 *    and deep clinical diagnostic workflows.
 */
public class MainActivity extends AppCompatActivity implements LivestockNodeAdapter.OnNodeClickListener {

    public static final String EXTRA_NODE_ID = "extra_node_id";
    public static final String DEFAULT_GATEWAY_ENDPOINT = "http://192.168.4.1:8080/data";
    private static final String PREF_NAME = "vet_client_prefs";
    private static final String PREF_KEY_GATEWAY_URL = "pref_gateway_url";

    private static final int FILTER_ALL = 0;
    private static final int FILTER_ATTENTION = 1;
    private static final int FILTER_FEVER_CALVING = 2;
    private static final int FILTER_STRAYED = 3;

    private TelemetryRepository repository;
    private Esp32SyncClient syncClient;
    private AuthManager authManager;

    private List<TelemetryRecord> allLatestRecords = new ArrayList<>();
    private Map<String, AnimalProfile> profileMap = new HashMap<>();
    private Map<String, Double> todayMilkMap = new HashMap<>();
    private int currentFilter = FILTER_ALL;

    // UI Components
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerViewNodes;
    private LivestockNodeAdapter nodeAdapter;
    private LinearLayout layoutEmptyState;
    private TextView tvEmptyTitle;
    private TextView tvEmptyDesc;
    private LinearLayout layoutSyncProgress;
    private TextView tvSyncStatusMsg;
    private TextView tvGatewayEndpoint;
    private TextView tvBomaBoxStatus;

    // Role & Settings Controls
    private TextView tvActiveRoleBadge;
    private View btnMenuDropdown;

    // Filter Chips
    private MaterialButton chipFilterAll;
    private MaterialButton chipFilterAttention;
    private MaterialButton chipFilterFever;
    private MaterialButton chipFilterStrayed;
    private MaterialButton chipQuickLogMilk;

    // Triage Counter Views
    private TextView tvCountTotal;
    private TextView tvCountAnomaly;
    private TextView tvCountHealthy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        authManager = new AuthManager(this);
        if (!authManager.isLoggedIn()) {
            redirectToLogin();
            return;
        }

        setContentView(R.layout.activity_main);

        repository = new TelemetryRepository(getApplication());
        syncClient = new Esp32SyncClient();

        initViews();
        setupRecyclerView();
        setupListeners();
        applyRoleUiState();
        refreshDashboardFromLocalDb();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (authManager == null) {
            authManager = new AuthManager(this);
        }
        if (!authManager.isLoggedIn()) {
            redirectToLogin();
            return;
        }
        applyRoleUiState();
        refreshDashboardFromLocalDb();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void initViews() {
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        recyclerViewNodes = findViewById(R.id.recycler_livestock_nodes);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        tvEmptyTitle = findViewById(R.id.tv_empty_title);
        tvEmptyDesc = findViewById(R.id.tv_empty_desc);
        layoutSyncProgress = findViewById(R.id.layout_sync_progress);
        tvSyncStatusMsg = findViewById(R.id.tv_sync_status_msg);
        tvGatewayEndpoint = findViewById(R.id.tv_gateway_endpoint);
        tvBomaBoxStatus = findViewById(R.id.tv_boma_box_status);

        tvActiveRoleBadge = findViewById(R.id.tv_active_role_badge);
        btnMenuDropdown = findViewById(R.id.btn_menu_dropdown);

        chipFilterAll = findViewById(R.id.chip_filter_all);
        chipFilterAttention = findViewById(R.id.chip_filter_attention);
        chipFilterFever = findViewById(R.id.chip_filter_fever);
        chipFilterStrayed = findViewById(R.id.chip_filter_strayed);
        chipQuickLogMilk = findViewById(R.id.chip_quick_log_milk);

        tvCountTotal = findViewById(R.id.tv_count_total_nodes);
        tvCountAnomaly = findViewById(R.id.tv_count_anomaly_nodes);
        tvCountHealthy = findViewById(R.id.tv_count_healthy_nodes);

        updateGatewayEndpointDisplay();
    }

    private void setupRecyclerView() {
        nodeAdapter = new LivestockNodeAdapter(this);
        int spanCount = getResources().getInteger(R.integer.grid_columns);
        recyclerViewNodes.setLayoutManager(new GridLayoutManager(this, spanCount));
        recyclerViewNodes.setAdapter(nodeAdapter);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (recyclerViewNodes != null) {
            int spanCount = getResources().getInteger(R.integer.grid_columns);
            if (recyclerViewNodes.getLayoutManager() instanceof GridLayoutManager) {
                ((GridLayoutManager) recyclerViewNodes.getLayoutManager()).setSpanCount(spanCount);
            } else {
                recyclerViewNodes.setLayoutManager(new GridLayoutManager(this, spanCount));
            }
        }
    }

    private void setupListeners() {
        // Primary 56dp Sync CTA Button
        findViewById(R.id.btn_sync_gateway).setOnClickListener(v -> triggerGatewaySync());
        findViewById(R.id.btn_empty_sync).setOnClickListener(v -> triggerGatewaySync());

        // Pull to refresh gesture
        swipeRefreshLayout.setOnRefreshListener(this::triggerGatewaySync);

        // "Need Help Connecting?" 3-Step Friendly Guide
        View helpBtn = findViewById(R.id.tv_need_help_connecting);
        if (helpBtn != null) {
            helpBtn.setOnClickListener(v -> showBomaConnectionHelpDialog());
        }

        // Options Dropdown Menu (Log Out, IP Settings, Wipe Data, Help)
        if (btnMenuDropdown != null) {
            btnMenuDropdown.setOnClickListener(this::showOptionsMenu);
        }

        // Informative Tap on Role Badge
        if (tvActiveRoleBadge != null) {
            tvActiveRoleBadge.setOnClickListener(v -> {
                String roleInfo = authManager.isVetMode() ? "Field Veterinarian" : "Cow Owner / Herder";
                Toast.makeText(this, "Authenticated as " + authManager.getActiveDisplayName() + " (" + roleInfo + ")", Toast.LENGTH_SHORT).show();
            });
        }

        if (tvGatewayEndpoint != null) {
            tvGatewayEndpoint.setOnClickListener(v -> {
                if (authManager.isVetMode()) {
                    showGatewayConfigDialog();
                }
            });
        }

        // Interactive Triage Filter Chips
        chipFilterAll.setOnClickListener(v -> {
            currentFilter = FILTER_ALL;
            applyFilterAndRender();
        });
        chipFilterAttention.setOnClickListener(v -> {
            currentFilter = FILTER_ATTENTION;
            applyFilterAndRender();
        });
        chipFilterFever.setOnClickListener(v -> {
            currentFilter = FILTER_FEVER_CALVING;
            applyFilterAndRender();
        });
        chipFilterStrayed.setOnClickListener(v -> {
            currentFilter = FILTER_STRAYED;
            applyFilterAndRender();
        });
        chipQuickLogMilk.setOnClickListener(v -> showQuickMilkPickerDialog());
    }

    /**
     * Adapts Top AppBar, Gateway IP visibility, and Card Jargon level to the active persona.
     */
    private void applyRoleUiState() {
        boolean isVet = authManager.isVetMode();
        if (tvActiveRoleBadge != null) {
            tvActiveRoleBadge.setText(authManager.getRoleBadgeLabel());
            tvActiveRoleBadge.setBackgroundResource(isVet ? R.drawable.bg_role_vet_badge : R.drawable.bg_role_farmer_badge);
        }

        if (isVet) {
            tvGatewayEndpoint.setVisibility(View.VISIBLE);
            tvBomaBoxStatus.setText("Boma Gateway Receiver (Vet Clinical Mode)");
        } else {
            tvGatewayEndpoint.setVisibility(View.GONE);
            tvBomaBoxStatus.setText("Boma Receiver: Ready to Sync");
        }
        updateGatewayEndpointDisplay();
    }

    /**
     * Friendly 3-Step Illustrated Help Dialog for connecting to the Boma Box Wi-Fi without technical jargon.
     */
    private void showBomaConnectionHelpDialog() {
        String message = "Follow these 3 simple steps to get the latest cow health updates from the Boma Box:\n\n" +
                "1️⃣ Stand near the Boma Receiver Box and make sure its power switch is ON.\n\n" +
                "2️⃣ Open your phone's Wi-Fi Settings and tap \"ESP32_LIVESTOCK_AP\" (no internet is needed).\n\n" +
                "3️⃣ Return here and tap the green \"🔄 Update Herd Status\" button.";

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("📡 How to Connect to the Boma Box")
                .setMessage(message)
                .setPositiveButton("Open Phone Wi-Fi", (dialog, which) -> {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Got It", null);

        if (authManager.isVetMode()) {
            builder.setNeutralButton("Advanced IP Setup", (dialog, which) -> showGatewayConfigDialog());
        }
        builder.show();
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
            tvGatewayEndpoint.setText("Endpoint: " + getGatewayUrl());
        }
    }

    private void showGatewayConfigDialog() {
        final EditText input = new EditText(this);
        input.setText(getGatewayUrl());
        input.setSelectAllOnFocus(true);
        input.setHint("http://127.0.0.1:8080/data");

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, pad / 2);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Configure Gateway Endpoint (Vet/Admin)")
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
                .setNegativeButton("Default Boma AP", (dialog, which) -> {
                    setGatewayUrl(DEFAULT_GATEWAY_ENDPOINT);
                    Toast.makeText(this, "Reset to Boma Box AP (192.168.4.1)", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showOptionsMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu menu = popup.getMenu();

        String roleStr = authManager.isVetMode() ? "Field Veterinarian" : "Cow Owner / Herder";
        MenuItem headerItem = menu.add(Menu.NONE, 1, 1, "👤 " + authManager.getActiveDisplayName() + " (" + roleStr + ")");
        headerItem.setEnabled(false);

        if (authManager.isVetMode()) {
            menu.add(Menu.NONE, 2, 2, "⚙️ Gateway IP Settings");
        }
        menu.add(Menu.NONE, 3, 3, "❓ Boma Connection Help");
        menu.add(Menu.NONE, 4, 4, "🗑️ Clear All Animals");
        menu.add(Menu.NONE, 5, 5, "🚪 Log Out / Switch User");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 2) {
                showGatewayConfigDialog();
                return true;
            } else if (id == 3) {
                showBomaConnectionHelpDialog();
                return true;
            } else if (id == 4) {
                confirmClearAllCows();
                return true;
            } else if (id == 5) {
                performLogout();
                return true;
            }
            return false;
        });

        popup.show();
    }

    private void performLogout() {
        new AlertDialog.Builder(this)
                .setTitle("🚪 Log Out")
                .setMessage("Are you sure you want to log out of your session as " + authManager.getActiveDisplayName() + "?")
                .setPositiveButton("Log Out", (dialog, which) -> {
                    authManager.logout();
                    Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
                    redirectToLogin();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

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
                repository.saveSyncedRecords(records, new TelemetryRepository.SyncSaveCallback() {
                    @Override
                    public void onSaved(int recordCount) {
                        runOnUiThread(() -> {
                            layoutSyncProgress.setVisibility(View.GONE);
                            swipeRefreshLayout.setRefreshing(false);

                            String successMsg = getString(R.string.sync_success, recordCount);
                            Snackbar.make(recyclerViewNodes, successMsg, Snackbar.LENGTH_LONG)
                                    .setBackgroundTint(ContextCompat.getColor(MainActivity.this, R.color.status_healthy))
                                    .setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white))
                                    .show();

                            refreshDashboardFromLocalDb();
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        runOnUiThread(() -> {
                            layoutSyncProgress.setVisibility(View.GONE);
                            swipeRefreshLayout.setRefreshing(false);
                            showErrorDialog("Storage Error", "Could not save herd updates: " + e.getMessage());
                        });
                    }
                });
            }

            @Override
            public void onSyncFailure(String errorMessage) {
                runOnUiThread(() -> {
                    layoutSyncProgress.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                    showErrorDialog("Boma Receiver Not Reached", errorMessage);
                });
            }
        });
    }

    /**
     * Loads the latest telemetry state, animal profiles, and today's milk records from Room SQLite.
     */
    private void refreshDashboardFromLocalDb() {
        repository.loadDashboardBundle(bundle -> runOnUiThread(() -> {
            allLatestRecords = (bundle.latestRecords != null) ? bundle.latestRecords : new ArrayList<>();
            profileMap = (bundle.profileMap != null) ? bundle.profileMap : new HashMap<>();
            todayMilkMap = (bundle.todayMilkMap != null) ? bundle.todayMilkMap : new HashMap<>();

            int total = allLatestRecords.size();
            int attentionCount = 0;
            int feverCalvingCount = 0;
            int strayedCount = 0;

            for (TelemetryRecord r : allLatestRecords) {
                if (r.needsAttention()) {
                    attentionCount++;
                }
                if (r.isFever() || r.isCalvingDrop() || r.getAnomalyFlag() == 1) {
                    feverCalvingCount++;
                }
                if (r.isStray() || r.isPredatorOrPanic()) {
                    strayedCount++;
                }
            }

            int healthyCount = Math.max(0, total - attentionCount);
            updateTriageCounters(total, attentionCount, healthyCount);
            updateFilterChipLabels(total, attentionCount, feverCalvingCount, strayedCount);
            applyFilterAndRender();
        }));
    }

    private void updateTriageCounters(int total, int anomalies, int healthy) {
        tvCountTotal.setText(String.valueOf(total));
        tvCountAnomaly.setText(String.valueOf(anomalies));
        tvCountHealthy.setText(String.valueOf(healthy));
    }

    private void updateFilterChipLabels(int total, int attention, int feverCalving, int strayed) {
        chipFilterAll.setText(String.format(Locale.US, "All Cows (%d)", total));
        chipFilterAttention.setText(String.format(Locale.US, "🚨 Needs Attention (%d)", attention));
        chipFilterFever.setText(String.format(Locale.US, "🌡️ Fever/Calving (%d)", feverCalving));
        chipFilterStrayed.setText(String.format(Locale.US, "🏃 Strayed (%d)", strayed));
    }

    private void applyFilterAndRender() {
        updateActiveChipStyling();

        List<TelemetryRecord> filtered = new ArrayList<>();
        for (TelemetryRecord r : allLatestRecords) {
            if (currentFilter == FILTER_ATTENTION) {
                if (r.needsAttention()) filtered.add(r);
            } else if (currentFilter == FILTER_FEVER_CALVING) {
                if (r.isFever() || r.isCalvingDrop() || r.getAnomalyFlag() == 1) filtered.add(r);
            } else if (currentFilter == FILTER_STRAYED) {
                if (r.isStray() || r.isPredatorOrPanic()) filtered.add(r);
            } else {
                filtered.add(r);
            }
        }

        if (filtered.isEmpty()) {
            layoutEmptyState.setVisibility(View.VISIBLE);
            recyclerViewNodes.setVisibility(View.GONE);
            if (!allLatestRecords.isEmpty()) {
                tvEmptyTitle.setText("No Cows in This Filter");
                tvEmptyDesc.setText("All cows are clear for this category! Tap \"All Cows\" above to view the full herd.");
            } else {
                tvEmptyTitle.setText(R.string.empty_records_title);
                tvEmptyDesc.setText(R.string.empty_records_desc);
            }
        } else {
            layoutEmptyState.setVisibility(View.GONE);
            recyclerViewNodes.setVisibility(View.VISIBLE);
            nodeAdapter.updateBundle(filtered, profileMap, todayMilkMap, authManager.isVetMode());
        }
    }

    private void updateActiveChipStyling() {
        int navy = ContextCompat.getColor(this, R.color.primary_navy);
        int red = ContextCompat.getColor(this, R.color.status_anomaly);
        int amber = ContextCompat.getColor(this, R.color.status_warning);
        int white = ContextCompat.getColor(this, R.color.white);
        int cardBg = ContextCompat.getColor(this, R.color.surface_card);

        styleChip(chipFilterAll, currentFilter == FILTER_ALL, navy, white, cardBg);
        styleChip(chipFilterAttention, currentFilter == FILTER_ATTENTION, red, white, cardBg);
        styleChip(chipFilterFever, currentFilter == FILTER_FEVER_CALVING, amber, white, cardBg);
        styleChip(chipFilterStrayed, currentFilter == FILTER_STRAYED, red, white, cardBg);
    }

    private void styleChip(MaterialButton chip, boolean selected, int accentColor, int white, int cardBg) {
        if (selected) {
            chip.setBackgroundTintList(ColorStateList.valueOf(accentColor));
            chip.setTextColor(white);
            chip.setStrokeWidth(0);
        } else {
            chip.setBackgroundTintList(ColorStateList.valueOf(cardBg));
            chip.setTextColor(accentColor);
            chip.setStrokeColor(ColorStateList.valueOf(accentColor));
            chip.setStrokeWidth((int) (2 * getResources().getDisplayMetrics().density));
        }
    }

    @Override
    public void onNodeClick(TelemetryRecord record) {
        Intent intent = new Intent(this, NodeDetailActivity.class);
        intent.putExtra(EXTRA_NODE_ID, record.getNodeId());
        startActivity(intent);
    }

    @Override
    public void onNodeLongClick(TelemetryRecord record, AnimalProfile profile) {
        String name = (profile != null) ? profile.getCustomName() : record.getNodeId();
        new AlertDialog.Builder(this)
                .setTitle("Delete " + name + "?")
                .setMessage("Delete " + name + " (" + record.getNodeId() + ") and all associated health and milk records?\n\nThis will remove it completely.")
                .setIcon(R.drawable.ic_warning_triangle)
                .setPositiveButton("Delete", (dialog, which) -> {
                    repository.deleteAnimalCompletely(record.getNodeId(), () -> runOnUiThread(() -> {
                        Toast.makeText(this, "Deleted " + name, Toast.LENGTH_SHORT).show();
                        refreshDashboardFromLocalDb();
                    }));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearAllCows() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚠️ Clear All Animals (Clean Slate)?");
        builder.setMessage("This will permanently remove all livestock records and telemetry stored offline on this device.\n\nTo confirm, please enter your 4-digit PIN:");
        builder.setIcon(R.drawable.ic_warning_triangle);

        final EditText pinInput = new EditText(this);
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setHint("Enter 4-digit PIN");
        builder.setView(pinInput);

        builder.setPositiveButton("Verify & Clear All", (dialog, which) -> {
            String entered = pinInput.getText() != null ? pinInput.getText().toString().trim() : "";
            if (authManager.verifyPinOnly(authManager.getActiveRole(), entered)) {
                repository.clearAllData(() -> runOnUiThread(() -> {
                    Toast.makeText(this, "Clean slate ready! All animals cleared.", Toast.LENGTH_SHORT).show();
                    refreshDashboardFromLocalDb();
                }));
            } else {
                Toast.makeText(this, "Incorrect PIN. Action aborted for security.", Toast.LENGTH_LONG).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    @Override
    public void onLogMilkClick(TelemetryRecord record, AnimalProfile profile) {
        showRecordMilkDialog(record.getNodeId(), profile);
    }

    private void showQuickMilkPickerDialog() {
        if (allLatestRecords.isEmpty()) {
            Toast.makeText(this, "No cows available yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[allLatestRecords.size()];
        for (int i = 0; i < allLatestRecords.size(); i++) {
            String nodeId = allLatestRecords.get(i).getNodeId();
            AnimalProfile p = profileMap.get(nodeId);
            labels[i] = (p != null) ? p.getDisplayHeader() + " (" + p.getLactationStatus() + ")" : nodeId;
        }

        new AlertDialog.Builder(this)
                .setTitle("🥛 Select Cow to Log Milk")
                .setItems(labels, (dialog, which) -> {
                    TelemetryRecord chosen = allLatestRecords.get(which);
                    AnimalProfile p = profileMap.get(chosen.getNodeId());
                    if (p == null) p = AnimalProfile.createDefaultForNode(chosen.getNodeId());
                    showRecordMilkDialog(chosen.getNodeId(), p);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRecordMilkDialog(String nodeId, AnimalProfile profile) {
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
        tvCalfLabel.setText("Calf / Pregnancy Status:");
        tvCalfLabel.setPadding(0, pad / 2, 0, 4);
        container.addView(tvCalfLabel);

        final Spinner spinnerCalf = new Spinner(this);
        String[] calfOptions = new String[]{"Normal", "Expecting Soon", "Calved"};
        spinnerCalf.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, calfOptions));
        container.addView(spinnerCalf);

        final EditText etNotes = new EditText(this);
        etNotes.setHint("Optional notes (e.g., Good appetite)");
        container.addView(etNotes);

        String cowTitle = (profile != null) ? profile.getDisplayHeader() : nodeId;
        new AlertDialog.Builder(this)
                .setTitle("🥛 Log Daily Milk – " + cowTitle)
                .setView(container)
                .setPositiveButton("Save Record", (dialog, which) -> {
                    double morning = parseDoubleSafe(etMorning.getText().toString());
                    double evening = parseDoubleSafe(etEvening.getText().toString());
                    String calfStatus = (String) spinnerCalf.getSelectedItem();
                    String notes = etNotes.getText().toString().trim();

                    ProductionRecord newRecord = new ProductionRecord(
                            nodeId,
                            System.currentTimeMillis(),
                            morning,
                            evening,
                            calfStatus,
                            authManager.getRoleDisplayName(),
                            notes
                    );
                    repository.saveProductionRecord(newRecord, () -> runOnUiThread(() -> {
                        Toast.makeText(this, String.format(Locale.US,
                                "Saved %.1f L milk record for %s",
                                newRecord.getTotalMilkLiters(),
                                profile != null ? profile.getCustomName() : nodeId), Toast.LENGTH_SHORT).show();
                        refreshDashboardFromLocalDb();
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

    private void showErrorDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setIcon(R.drawable.ic_warning_triangle)
                .setPositiveButton("OK", null)
                .setNeutralButton("Open Phone Wi-Fi", (dialog, which) -> {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
                    } catch (Exception ignored) {}
                });

        if (authManager.isVetMode()) {
            builder.setNegativeButton("Change IP", (dialog, which) -> showGatewayConfigDialog());
        }
        builder.show();
    }
}
