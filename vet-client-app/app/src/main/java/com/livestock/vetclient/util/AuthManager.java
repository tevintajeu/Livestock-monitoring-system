package com.livestock.vetclient.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Offline Authentication, PIN Hashing (SHA-256), and Role-Based Access Manager.
 *
 * Roles:
 * - ROLE_FARMER (Owner / Herder): Plain-language HCD view, milk logging, simple action checklist.
 * - ROLE_VET (Visiting Field Veterinarian): Clinical diagnostic engine, raw 3-axis vectors, IP config.
 */
public class AuthManager {

    public static final String ROLE_FARMER = "ROLE_FARMER";
    public static final String ROLE_VET = "ROLE_VET";

    public static final String DEFAULT_FARMER_PIN = "1234";
    public static final String DEFAULT_VET_PIN = "9999";

    private static final String PREF_AUTH = "mifugosafe_auth_prefs";
    private static final String KEY_ACTIVE_ROLE = "key_active_role";
    private static final String KEY_IS_LOGGED_IN = "key_is_logged_in";
    private static final String KEY_FARMER_PIN_HASH = "key_farmer_pin_hash";
    private static final String KEY_VET_PIN_HASH = "key_vet_pin_hash";
    private static final String KEY_DEMO_QUICK_LOGIN = "key_demo_quick_login";
    private static final String KEY_HANDLED_PREFIX = "key_handled_node_";

    private final SharedPreferences prefs;

    public AuthManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_AUTH, Context.MODE_PRIVATE);
        ensureDefaultPinsInitialized();
    }

    private void ensureDefaultPinsInitialized() {
        if (!prefs.contains(KEY_FARMER_PIN_HASH)) {
            prefs.edit()
                    .putString(KEY_FARMER_PIN_HASH, sha256(DEFAULT_FARMER_PIN))
                    .putString(KEY_VET_PIN_HASH, sha256(DEFAULT_VET_PIN))
                    .apply();
        }
    }

    /**
     * Computes SHA-256 hex digest for offline PIN verification.
     */
    public static String sha256(String input) {
        if (input == null) input = "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(input.trim().getBytes(StandardCharsets.UTF_8));
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

    /**
     * Verifies a 4-digit PIN for the selected role and persists the active session if valid.
     */
    public boolean verifyPinAndLogin(String role, String rawPin) {
        if (rawPin == null || rawPin.trim().length() != 4) {
            return false;
        }
        String inputHash = sha256(rawPin.trim());
        String targetKey = ROLE_VET.equals(role) ? KEY_VET_PIN_HASH : KEY_FARMER_PIN_HASH;
        String expectedHash = prefs.getString(targetKey, sha256(
                ROLE_VET.equals(role) ? DEFAULT_VET_PIN : DEFAULT_FARMER_PIN
        ));

        if (inputHash.equalsIgnoreCase(expectedHash)) {
            setSessionRole(role);
            return true;
        }
        return false;
    }

    /**
     * Sets the active session role directly (used by Demo Quick Login or 1-tap role switch).
     */
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

    public String toggleRole() {
        String nextRole = isVetMode() ? ROLE_FARMER : ROLE_VET;
        setSessionRole(nextRole);
        return nextRole;
    }

    public String getRoleBadgeLabel() {
        if (isVetMode()) {
            return "🩺 Mode: Veterinarian";
        }
        return "👨🏾‍🌾 Mode: Herder";
    }

    public String getRoleDisplayName() {
        return isVetMode() ? "Field Veterinarian" : "Cow Owner / Herder";
    }

    public boolean isDemoQuickLoginEnabled() {
        return prefs.getBoolean(KEY_DEMO_QUICK_LOGIN, false);
    }

    public void setDemoQuickLoginEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DEMO_QUICK_LOGIN, enabled).apply();
    }

    public void logout() {
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, false).apply();
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

