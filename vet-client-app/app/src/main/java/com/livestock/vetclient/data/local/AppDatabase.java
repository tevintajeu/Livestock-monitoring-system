package com.livestock.vetclient.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.livestock.vetclient.data.model.AnimalProfile;
import com.livestock.vetclient.data.model.ProductionRecord;
import com.livestock.vetclient.data.model.TelemetryRecord;
import com.livestock.vetclient.data.model.UserAccount;
import com.livestock.vetclient.util.AuthManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main Room Database for the MifugoSafe Livestock & Veterinary Client Application.
 * Manages local persistent storage of livestock biometrics, personalized animal identities,
 * daily milk/calving production output, and offline role-based security accounts.
 */
@Database(
    entities = {
        TelemetryRecord.class,
        AnimalProfile.class,
        ProductionRecord.class,
        UserAccount.class
    },
    version = 3,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "livestock_telemetry_db";
    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;

    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public abstract TelemetryDao telemetryDao();

    /**
     * Singleton instance provider with destructive migration fallback and automatic HCD seeding.
     */
    public static AppDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    )
                    .fallbackToDestructiveMigration()
                    .build();

                    // Pre-seed offline user accounts and optional first-launch defaults
                    Context appCtx = context.getApplicationContext();
                    databaseWriteExecutor.execute(() -> ensureSeeded(appCtx, INSTANCE.telemetryDao()));
                }
            }
        }
        return INSTANCE;
    }

    public static synchronized void ensureSeeded(TelemetryDao dao) {
        ensureAccountsSeeded(dao);
    }

    public static synchronized void ensureAccountsSeeded(TelemetryDao dao) {
        try {
            if (dao.getUserAccountByRole(AuthManager.ROLE_FARMER) == null) {
                dao.insertUserAccount(new UserAccount(
                        AuthManager.ROLE_FARMER,
                        "Cow Owner / Herder",
                        AuthManager.sha256(AuthManager.DEFAULT_FARMER_PIN),
                        System.currentTimeMillis()
                ));
            }
            if (dao.getUserAccountByRole(AuthManager.ROLE_VET) == null) {
                dao.insertUserAccount(new UserAccount(
                        AuthManager.ROLE_VET,
                        "Field Veterinarian",
                        AuthManager.sha256(AuthManager.DEFAULT_VET_PIN),
                        System.currentTimeMillis()
                ));
            }
        } catch (Exception ignored) {}
    }

    /**
     * Seeds initial sample animals ONLY ONCE on very first app run.
     * If the user deletes animals or clears the database, they will NEVER be re-seeded.
     */
    public static synchronized void ensureSeeded(Context context, TelemetryDao dao) {
        ensureAccountsSeeded(dao);
        if (context == null) return;

        android.content.SharedPreferences prefs = context.getSharedPreferences("livestock_app_prefs", Context.MODE_PRIVATE);
        boolean alreadySeeded = prefs.getBoolean("has_initialized_defaults", false);
        if (alreadySeeded) {
            return;
        }

        try {
            // Seed once on fresh install so app isn't blank initially
            List<AnimalProfile> defaultProfiles = Arrays.asList(
                    new AnimalProfile("NODE_001", "Malkia", "KE-042", "Boran", "Milking",
                            "Lead dairy cow. High morning yield; monitor udder health closely."),
                    new AnimalProfile("NODE_002", "Simba", "KE-019", "Sahiwal", "Dry",
                            "Strong rangeland grazer; frequently explores outer pasture perimeter."),
                    new AnimalProfile("NODE_003", "Chebet", "KE-088", "Zebu", "Pregnant",
                            "Due to calve soon. Keep in near-boma maternity paddock.")
            );
            dao.insertProfilesIgnoreConflict(defaultProfiles);

            long now = System.currentTimeMillis();
            long hourMs = 3600_000L;
            List<TelemetryRecord> sampleTelemetry = new ArrayList<>();
            sampleTelemetry.add(new TelemetryRecord("NODE_001", 40.2, 1.4, 0.4, 9.8, 220.0, 1, now - 60_000L));
            sampleTelemetry.add(new TelemetryRecord("NODE_002", 38.6, 2.5, 0.8, 9.8, 680.0, 2, now - 120_000L));
            sampleTelemetry.add(new TelemetryRecord("NODE_003", 38.6, 0.9, 0.3, 9.8, 140.0, 0, now - 180_000L));
            dao.insertAll(sampleTelemetry);

            prefs.edit().putBoolean("has_initialized_defaults", true).apply();
        } catch (Exception ignored) {}
    }
}
