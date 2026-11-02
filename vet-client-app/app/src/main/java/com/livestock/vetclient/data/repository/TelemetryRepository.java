package com.livestock.vetclient.data.repository;

import android.app.Application;

import com.livestock.vetclient.data.local.AppDatabase;
import com.livestock.vetclient.data.local.TelemetryDao;
import com.livestock.vetclient.data.model.AnimalProfile;
import com.livestock.vetclient.data.model.ProductionRecord;
import com.livestock.vetclient.data.model.TelemetryRecord;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository layer isolating UI controllers from raw SQLite and Room database logic.
 * Coordinates TelemetryRecord, AnimalProfile, and ProductionRecord access.
 */
public class TelemetryRepository {

    private final TelemetryDao telemetryDao;

    public interface DataCallback<T> {
        void onLoaded(T data);
    }

    public interface SyncSaveCallback {
        void onSaved(int recordCount);
        void onError(Exception e);
    }

    /**
     * Composite data bundle for dashboard and detail screens.
     */
    public static class DashboardBundle {
        public final List<TelemetryRecord> latestRecords;
        public final Map<String, AnimalProfile> profileMap;
        public final Map<String, Double> todayMilkMap;

        public DashboardBundle(List<TelemetryRecord> latestRecords,
                               Map<String, AnimalProfile> profileMap,
                               Map<String, Double> todayMilkMap) {
            this.latestRecords = latestRecords;
            this.profileMap = profileMap;
            this.todayMilkMap = todayMilkMap;
        }
    }

    public static class NodeDetailBundle {
        public final AnimalProfile profile;
        public final List<TelemetryRecord> telemetryHistory;
        public final List<ProductionRecord> productionHistory;
        public final double todayMilkLiters;
        public final double sevenDayAvgLiters;
        public final String latestCalfStatus;

        public NodeDetailBundle(AnimalProfile profile,
                                List<TelemetryRecord> telemetryHistory,
                                List<ProductionRecord> productionHistory,
                                double todayMilkLiters,
                                double sevenDayAvgLiters,
                                String latestCalfStatus) {
            this.profile = profile;
            this.telemetryHistory = telemetryHistory;
            this.productionHistory = productionHistory;
            this.todayMilkLiters = todayMilkLiters;
            this.sevenDayAvgLiters = sevenDayAvgLiters;
            this.latestCalfStatus = latestCalfStatus;
        }
    }

    public TelemetryRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        this.telemetryDao = db.telemetryDao();
    }

    /**
     * Persists a batch of synced telemetry records in a background thread,
     * and ensures every synced node has a friendly AnimalProfile.
     */
    public void saveSyncedRecords(List<TelemetryRecord> records, SyncSaveCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                AppDatabase.ensureSeeded(telemetryDao);
                telemetryDao.insertAll(records);
                for (TelemetryRecord r : records) {
                    if (telemetryDao.getProfileForNode(r.getNodeId()) == null) {
                        telemetryDao.insertProfile(AnimalProfile.createDefaultForNode(r.getNodeId()));
                    }
                }
                if (callback != null) {
                    callback.onSaved(records.size());
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError(e);
                }
            }
        });
    }

    /**
     * Loads the latest telemetry state for each monitored node alongside AnimalProfiles and today's milk totals.
     */
    public void loadDashboardBundle(DataCallback<DashboardBundle> callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase.ensureSeeded(telemetryDao);
            List<TelemetryRecord> latest = telemetryDao.getLatestRecordsPerNode();
            List<AnimalProfile> profiles = telemetryDao.getAllProfiles();
            List<ProductionRecord> allProduction = telemetryDao.getAllProductionRecords();

            Map<String, AnimalProfile> profileMap = new HashMap<>();
            if (profiles != null) {
                for (AnimalProfile p : profiles) {
                    profileMap.put(p.getNodeId(), p);
                }
            }

            // Ensure every node in latest has a profile
            if (latest != null) {
                for (TelemetryRecord r : latest) {
                    if (!profileMap.containsKey(r.getNodeId())) {
                        AnimalProfile fallback = AnimalProfile.createDefaultForNode(r.getNodeId());
                        telemetryDao.insertProfile(fallback);
                        profileMap.put(r.getNodeId(), fallback);
                    }
                }
            }

            // Calculate latest day's milk per node (within last 24 hours)
            Map<String, Double> todayMilkMap = new HashMap<>();
            long cutoff24h = System.currentTimeMillis() - (24 * 3600_000L);
            if (allProduction != null) {
                for (ProductionRecord pr : allProduction) {
                    if (pr.getTimestamp() >= cutoff24h && !todayMilkMap.containsKey(pr.getNodeId())) {
                        todayMilkMap.put(pr.getNodeId(), pr.getTotalMilkLiters());
                    }
                }
            }

            if (callback != null) {
                callback.onLoaded(new DashboardBundle(latest, profileMap, todayMilkMap));
            }
        });
    }

    /**
     * Backward-compatible loader for latest records per node.
     */
    public void loadLatestRecordsPerNode(DataCallback<List<TelemetryRecord>> callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase.ensureSeeded(telemetryDao);
            List<TelemetryRecord> latest = telemetryDao.getLatestRecordsPerNode();
            if (callback != null) {
                callback.onLoaded(latest);
            }
        });
    }

    /**
     * Loads full detail bundle (Profile, Telemetry History, Production History, Yield Stats) for a node.
     */
    public void loadNodeDetailBundle(String nodeId, DataCallback<NodeDetailBundle> callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase.ensureSeeded(telemetryDao);
            AnimalProfile profile = telemetryDao.getProfileForNode(nodeId);
            if (profile == null) {
                profile = AnimalProfile.createDefaultForNode(nodeId);
                telemetryDao.insertProfile(profile);
            }

            List<TelemetryRecord> history = telemetryDao.getHistoryForNode(nodeId);
            List<ProductionRecord> prodHistory = telemetryDao.getProductionForNode(nodeId);

            double todayMilk = 0.0;
            double sevenDayAvg = 0.0;
            String latestCalfStatus = "Normal";

            if (prodHistory != null && !prodHistory.isEmpty()) {
                ProductionRecord newest = prodHistory.get(0);
                latestCalfStatus = newest.getCalfStatus();

                long now = System.currentTimeMillis();
                long cutoff24h = now - (24 * 3600_000L);
                long cutoff7d = now - (7 * 24 * 3600_000L);

                if (newest.getTimestamp() >= cutoff24h) {
                    todayMilk = newest.getTotalMilkLiters();
                }

                double sum7d = 0.0;
                int count7d = 0;
                for (ProductionRecord pr : prodHistory) {
                    if (pr.getTimestamp() >= cutoff7d && pr.getTotalMilkLiters() > 0) {
                        sum7d += pr.getTotalMilkLiters();
                        count7d++;
                    }
                }
                if (count7d > 0) {
                    sevenDayAvg = sum7d / count7d;
                }
            }

            if (callback != null) {
                callback.onLoaded(new NodeDetailBundle(
                        profile, history, prodHistory, todayMilk, sevenDayAvg, latestCalfStatus
                ));
            }
        });
    }

    /**
     * Backward-compatible loader for chronological telemetry history.
     */
    public void loadHistoryForNode(String nodeId, DataCallback<List<TelemetryRecord>> callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<TelemetryRecord> history = telemetryDao.getHistoryForNode(nodeId);
            if (callback != null) {
                callback.onLoaded(history);
            }
        });
    }

    /**
     * Saves or updates an AnimalProfile in Room SQLite.
     */
    public void saveAnimalProfile(AnimalProfile profile, Runnable onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            telemetryDao.insertProfile(profile);
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    /**
     * Saves a daily milk / calving ProductionRecord in Room SQLite.
     */
    public void saveProductionRecord(ProductionRecord record, Runnable onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            telemetryDao.insertProductionRecord(record);
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    /**
     * Deletes a specific animal and all its associated telemetry, profile, and milk logs.
     */
    public void deleteAnimalCompletely(String nodeId, Runnable onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                telemetryDao.deleteTelemetryForNode(nodeId);
                telemetryDao.deleteProfileForNode(nodeId);
                telemetryDao.deleteProductionForNode(nodeId);
            } catch (Exception ignored) {}
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    /**
     * Completely wipes all telemetry records, animal profiles, and milk records.
     * Provides a 100% clean slate so the user only tracks real active nodes.
     */
    public void clearAllData(Runnable onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                telemetryDao.deleteAll();
                telemetryDao.deleteAllProfiles();
                telemetryDao.deleteAllProduction();
            } catch (Exception ignored) {}
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    /**
     * Clears local database cache.
     */
    public void clearAllRecords(Runnable onComplete) {
        clearAllData(onComplete);
    }
}
