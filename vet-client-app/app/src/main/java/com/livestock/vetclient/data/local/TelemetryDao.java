package com.livestock.vetclient.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.livestock.vetclient.data.model.TelemetryRecord;

import java.util.List;

/**
 * Data Access Object (DAO) for querying and persisting livestock telemetry data.
 * Designed for offline-first clinical field operations.
 */
@Dao
public interface TelemetryDao {

    /**
     * Inserts a batch of synchronized telemetry records from the ESP32 gateway.
     * Uses REPLACE strategy on primary key conflict.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<TelemetryRecord> records);

    /**
     * Inserts a single telemetry record.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TelemetryRecord record);

    /**
     * Queries the most recent telemetry reading for every distinct livestock node.
     * Nodes with anomaly_flag = 1 are sorted first to provide emergency clinical triage.
     */
    @Query("SELECT * FROM telemetry_records WHERE id IN " +
           "(SELECT MAX(id) FROM telemetry_records GROUP BY node_id) " +
           "ORDER BY anomaly_flag DESC, node_id ASC")
    List<TelemetryRecord> getLatestRecordsPerNode();

    /**
     * Retrieves all historical telemetry records for a specific node ID,
     * sorted in reverse chronological order (newest readings first).
     */
    @Query("SELECT * FROM telemetry_records WHERE node_id = :nodeId ORDER BY timestamp DESC")
    List<TelemetryRecord> getHistoryForNode(String nodeId);

    /**
     * Count total distinct livestock nodes registered in local storage.
     */
    @Query("SELECT COUNT(DISTINCT node_id) FROM telemetry_records")
    int getDistinctNodeCount();

    /**
     * Count currently active nodes that have an active anomaly in their latest reading.
     */
    @Query("SELECT COUNT(*) FROM telemetry_records WHERE id IN " +
           "(SELECT MAX(id) FROM telemetry_records GROUP BY node_id) AND anomaly_flag = 1")
    int getActiveAnomalyCount();

    /**
     * Retrieves all records stored across the 30-day retention window.
     */
    @Query("SELECT * FROM telemetry_records ORDER BY timestamp DESC")
    List<TelemetryRecord> getAllRecords();

    /**
     * Clears all local telemetry cache.
     */
    @Query("DELETE FROM telemetry_records")
    void deleteAll();
}
