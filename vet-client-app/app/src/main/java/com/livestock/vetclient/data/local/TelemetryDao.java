package com.livestock.vetclient.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.livestock.vetclient.data.model.AnimalProfile;
import com.livestock.vetclient.data.model.ProductionRecord;
import com.livestock.vetclient.data.model.TelemetryRecord;
import com.livestock.vetclient.data.model.UserAccount;

import java.util.List;

/**
 * Data Access Object (DAO) for querying and persisting livestock telemetry,
 * personalized animal profiles, milk production records, and offline user accounts.
 */
@Dao
public interface TelemetryDao {

    // --- Telemetry Records ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<TelemetryRecord> records);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TelemetryRecord record);

    /**
     * Queries the most recent telemetry reading for every distinct livestock node.
     * Nodes with active anomalies are sorted first for immediate triage.
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

    @Query("SELECT COUNT(DISTINCT node_id) FROM telemetry_records")
    int getDistinctNodeCount();

    @Query("SELECT COUNT(*) FROM telemetry_records WHERE id IN " +
           "(SELECT MAX(id) FROM telemetry_records GROUP BY node_id) AND anomaly_flag > 0")
    int getActiveAnomalyCount();

    @Query("SELECT * FROM telemetry_records ORDER BY timestamp DESC")
    List<TelemetryRecord> getAllRecords();

    @Query("DELETE FROM telemetry_records")
    void deleteAll();

    @Query("DELETE FROM telemetry_records WHERE node_id = :nodeId")
    void deleteTelemetryForNode(String nodeId);

    // --- Animal Profiles (Human-Centered Identity) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertProfile(AnimalProfile profile);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertProfilesIgnoreConflict(List<AnimalProfile> profiles);

    @Query("SELECT * FROM animal_profiles WHERE node_id = :nodeId LIMIT 1")
    AnimalProfile getProfileForNode(String nodeId);

    @Query("SELECT * FROM animal_profiles ORDER BY node_id ASC")
    List<AnimalProfile> getAllProfiles();

    @Query("SELECT COUNT(*) FROM animal_profiles")
    int getProfileCount();

    @Query("DELETE FROM animal_profiles WHERE node_id = :nodeId")
    void deleteProfileForNode(String nodeId);

    @Query("DELETE FROM animal_profiles")
    void deleteAllProfiles();

    // --- Production & Milk Yield Records ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertProductionRecord(ProductionRecord record);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertProductionRecords(List<ProductionRecord> records);

    @Query("SELECT * FROM production_records WHERE node_id = :nodeId ORDER BY timestamp DESC")
    List<ProductionRecord> getProductionForNode(String nodeId);

    @Query("SELECT * FROM production_records ORDER BY timestamp DESC")
    List<ProductionRecord> getAllProductionRecords();

    @Query("SELECT COUNT(*) FROM production_records")
    int getProductionRecordCount();

    @Query("DELETE FROM production_records WHERE node_id = :nodeId")
    void deleteProductionForNode(String nodeId);

    @Query("DELETE FROM production_records")
    void deleteAllProduction();

    // --- Offline User Accounts ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUserAccount(UserAccount account);

    @Query("SELECT * FROM user_accounts WHERE role = :role LIMIT 1")
    UserAccount getUserAccountByRole(String role);
}
