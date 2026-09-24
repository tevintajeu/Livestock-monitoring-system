package com.livestock.vetclient.data.repository;

import android.app.Application;

import com.livestock.vetclient.data.local.AppDatabase;
import com.livestock.vetclient.data.local.TelemetryDao;
import com.livestock.vetclient.data.model.TelemetryRecord;

import java.util.List;

/**
 * Repository layer isolating UI controllers from raw SQLite and Room database logic.
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

    public TelemetryRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        this.telemetryDao = db.telemetryDao();
    }

    /**
     * Persists a batch of synced telemetry records in a background thread.
     */
    public void saveSyncedRecords(List<TelemetryRecord> records, SyncSaveCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                telemetryDao.insertAll(records);
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
     * Loads the latest telemetry state for each monitored node.
     */
    public void loadLatestRecordsPerNode(DataCallback<List<TelemetryRecord>> callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<TelemetryRecord> latest = telemetryDao.getLatestRecordsPerNode();
            if (callback != null) {
                callback.onLoaded(latest);
            }
        });
    }

    /**
     * Loads chronological telemetry history for a specific node ID.
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
     * Clears local database cache.
     */
    public void clearAllRecords(Runnable onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            telemetryDao.deleteAll();
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }
}
