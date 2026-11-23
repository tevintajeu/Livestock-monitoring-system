package com.livestock.vetclient.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.livestock.vetclient.data.local.AppDatabase;
import com.livestock.vetclient.data.model.UserAccount;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Offline Authentication, Salted SHA-256 PIN Hashing, Rate-Limiting Brute Force Protection,
 * and Role-Based Access Control Manager for MifugoSafe.
 *
 * Roles:
 * - ROLE_FARMER (Cow Owner / Herder): Human-centered triage, yield/milk tracker, simple action checklist.
 * - ROLE_VET (Visiting Field Veterinarian): Clinical diagnostic engine, raw 3-axis vectors, gateway settings.
 */
public class AuthManager {

    public static final String ROLE_FARMER = "ROLE_FARMER";
    public static final String ROLE_VET = "ROLE_VET";

    public static final String DEFAULT_FARMER_PIN = "1234";
    public static final String DEFAULT_VET_PIN = "9999";

    public static final String DEFAULT_FARMER_NAME = "Cow Owner / Herder";
    public static final String DEFAULT_VET_NAME = "Field Veterinarian";

    private static final String SALT_PREFIX = "MifugoSafeSecureSalt_2026_";
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 30000L; // 30 seconds

    private static final String PREF_AUTH = "mifugosafe_auth_prefs";
    private static final String KEY_ACTIVE_ROLE = "key_active_role";
    private static final String KEY_IS_LOGGED_IN = "key_is_logged_in";
    private static final String KEY_FARMER_PIN_HASH = "key_farmer_pin_hash";
    private static final String KEY_VET_PIN_HASH = "key_vet_pin_hash";
    private static final String KEY_FARMER_NAME = "key_farmer_name";
    private static final String KEY_VET_NAME = "key_vet_name";
    private static final String KEY_FAILED_PREFIX = "key_failed_attempts_";
    private static final String KEY_LOCKOUT_PREFIX = "key_lockout_until_";
    private static final String KEY_HANDLED_PREFIX = "key_handled_node_";

    private final SharedPreferences prefs;
    private final Context appContext;

    public AuthManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = this.appContext.getSharedPreferences(PREF_AUTH, Context.MODE_PRIVATE);
        ensureDefaultPinsInitialized();
    }

    private void ensureDefaultPinsInitialized() {
        if (!prefs.contains(KEY_FARMER_PIN_HASH)) {
            prefs.edit()
                    .putString(KEY_FARMER_PIN_HASH, hashPin(DEFAULT_FARMER_PIN))
                    .putString(KEY_VET_PIN_HASH, hashPin(DEFAULT_VET_PIN))
                    .putString(KEY_FARMER_NAME, DEFAULT_FARMER_NAME)
                    .putString(KEY_VET_NAME, DEFAULT_VET_NAME)
                    .apply();
        }
    }

    /**
     * Salted SHA-256 PIN hashing to defend against offline rainbow tables and pre-computed dictionaries.
     */
    public static String hashPin(String rawPin) {
        if (rawPin == null) rawPin = "";
        return sha256(SALT_PREFIX + rawPin.trim());
    }

    /**
     * Computes raw SHA-256 hex digest.
     */
    public static String sha256(String input) {
        if (input == null) input = "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    // --- Session Status ---

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public void logout() {
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, false).apply();
    }

    public void setSessionRole(String role) {
        String validRole = ROLE_VET.equals(role) ? ROLE_VET : ROLE_FARMER;
        prefs.edit()
                .putString(KEY_ACTIVE_ROLE, validRole)
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .apply();
    }

    public String getActiveRole() {
        return prefs.getString(KEY_ACTIVE_ROLE, ROLE_FARMER);
    }

    public boolean isVetMode() {
        return ROLE_VET.equals(getActiveRole());
    }

    public boolean isFarmerMode() {
        return !isVetMode();
    }

    public String getDisplayNameForRole(String role) {
        String key = ROLE_VET.equals(role) ? KEY_VET_NAME : KEY_FARMER_NAME;
        String fallback = ROLE_VET.equals(role) ? DEFAULT_VET_NAME : DEFAULT_FARMER_NAME;
        return prefs.getString(key, fallback);
    }

    public String getActiveDisplayName() {
        return getDisplayNameForRole(getActiveRole());
    }

    public void setDisplayName(String role, String name) {
        if (name == null || name.trim().isEmpty()) return;
        String key = ROLE_VET.equals(role) ? KEY_VET_NAME : KEY_FARMER_NAME;
        prefs.edit().putString(key, name.trim()).apply();
    }

    public String getRoleBadgeLabel() {
        if (isVetMode()) {
            return "🩺 Field Veterinarian";
        }
        return "👨🏾‍🌾 Cow Owner / Herder";
    }

    public String getRoleDisplayName() {
        return isVetMode() ? "Field Veterinarian" : "Cow Owner / Herder";
    }

    // --- Brute-Force Rate Limiting & Lockout Protection ---

    public boolean isLockedOut(String role) {
        long lockoutUntil = prefs.getLong(KEY_LOCKOUT_PREFIX + role, 0L);
        return System.currentTimeMillis() < lockoutUntil;
    }

    public long getLockoutRemainingSeconds(String role) {
        long lockoutUntil = prefs.getLong(KEY_LOCKOUT_PREFIX + role, 0L);
        long diff = lockoutUntil - System.currentTimeMillis();
        return diff > 0 ? (diff / 1000) + 1 : 0;
    }

    public int getFailedAttempts(String role) {
        return prefs.getInt(KEY_FAILED_PREFIX + role, 0);
    }

    public void recordFailedAttempt(String role) {
        int attempts = getFailedAttempts(role) + 1;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_FAILED_PREFIX + role, attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            editor.putLong(KEY_LOCKOUT_PREFIX + role, System.currentTimeMillis() + LOCKOUT_DURATION_MS);
        }
        editor.apply();
    }

    public void resetFailedAttempts(String role) {
        prefs.edit()
                .remove(KEY_FAILED_PREFIX + role)
                .remove(KEY_LOCKOUT_PREFIX + role)
                .apply();
    }

    // --- PIN Verification & Registration ---

    /**
     * Verifies PIN without creating a session (e.g. for confirming destructive operations).
     */
    public boolean verifyPinOnly(String role, String rawPin) {
        if (rawPin == null || rawPin.trim().length() != 4) {
            return false;
        }
        String targetKey = ROLE_VET.equals(role) ? KEY_VET_PIN_HASH : KEY_FARMER_PIN_HASH;
        String expectedHash = prefs.getString(targetKey, "");

        String saltedInputHash = hashPin(rawPin.trim());
        String legacyInputHash = sha256(rawPin.trim());

        // Supports salted hash, plus legacy SHA-256 migration
        return saltedInputHash.equalsIgnoreCase(expectedHash) || legacyInputHash.equalsIgnoreCase(expectedHash);
    }

    /**
     * Verifies a 4-digit PIN for the selected role with rate-limiting protection.
     * Persists the active session if valid.
     */
    public boolean verifyPinAndLogin(String role, String rawPin) {
        if (isLockedOut(role)) {
            return false;
        }

        if (verifyPinOnly(role, rawPin)) {
            resetFailedAttempts(role);

            // Automatically upgrade stored hash to salted format if not already
            String targetKey = ROLE_VET.equals(role) ? KEY_VET_PIN_HASH : KEY_FARMER_PIN_HASH;
            prefs.edit().putString(targetKey, hashPin(rawPin.trim())).apply();

            setSessionRole(role);

            // Update last login in Room DB asynchronously
            AppDatabase.databaseWriteExecutor.execute(() -> {
                try {
                    UserAccount account = new UserAccount(
                            role,
                            getDisplayNameForRole(role),
                            hashPin(rawPin.trim()),
                            System.currentTimeMillis()
                    );
                    AppDatabase.getInstance(appContext).telemetryDao().insertUserAccount(account);
                } catch (Exception ignored) {}
            });

            return true;
        }

        recordFailedAttempt(role);
        return false;
    }

    /**
     * Signs up or updates user account credentials (Role, Display Name, and 4-digit PIN).
     */
    public void registerOrUpdateAccount(String role, String displayName, String rawPin) {
        if (rawPin == null || rawPin.trim().length() != 4) {
            throw new IllegalArgumentException("PIN must be exactly 4 digits");
        }

        String validRole = ROLE_VET.equals(role) ? ROLE_VET : ROLE_FARMER;
        String validName = (displayName != null && !displayName.trim().isEmpty())
                ? displayName.trim()
                : (ROLE_VET.equals(validRole) ? DEFAULT_VET_NAME : DEFAULT_FARMER_NAME);

        String targetPinKey = ROLE_VET.equals(validRole) ? KEY_VET_PIN_HASH : KEY_FARMER_PIN_HASH;
        String targetNameKey = ROLE_VET.equals(validRole) ? KEY_VET_NAME : KEY_FARMER_NAME;
        String hashedPin = hashPin(rawPin.trim());

        prefs.edit()
                .putString(targetPinKey, hashedPin)
                .putString(targetNameKey, validName)
                .apply();

        resetFailedAttempts(validRole);
        setSessionRole(validRole);

        // Sync with local Room SQLite
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                UserAccount account = new UserAccount(
                        validRole,
                        validName,
                        hashedPin,
                        System.currentTimeMillis()
                );
                AppDatabase.getInstance(appContext).telemetryDao().insertUserAccount(account);
            } catch (Exception ignored) {}
        });
    }

    /**
     * Records when a farmer or vet marks an animal alert checklist as "Checked / Handled".
     */
    public void markNodeHandled(String nodeId) {
        if (nodeId == null) return;
        prefs.edit().putLong(KEY_HANDLED_PREFIX + nodeId, System.currentTimeMillis()).apply();
    }

    public long getNodeLastHandledTime(String nodeId) {
        if (nodeId == null) return 0L;
        return prefs.getLong(KEY_HANDLED_PREFIX + nodeId, 0L);
    }
}
