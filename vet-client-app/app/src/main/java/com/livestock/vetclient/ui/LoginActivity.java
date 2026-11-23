package com.livestock.vetclient.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.livestock.vetclient.R;
import com.livestock.vetclient.data.local.AppDatabase;
import com.livestock.vetclient.util.AuthManager;

/**
 * Screen 1: Offline Security Authentication, Salted SHA-256 PIN Verification,
 * Brute-Force Rate Limiting, and User Account Registration Activity.
 *
 * Supports two distinct roles:
 * 1. ROLE_FARMER (Cow Owner / Herder): Human-centered triage, yield/milk tracker, simple action checklist.
 * 2. ROLE_VET (Field Veterinarian): Clinical diagnostic engine, raw 3-axis vectors, gateway settings.
 */
public class LoginActivity extends AppCompatActivity {

    private AuthManager authManager;
    private String selectedRole = AuthManager.ROLE_FARMER;
    private boolean isLoginMode = true;
    private final StringBuilder enteredPin = new StringBuilder();

    // Mode Tabs
    private MaterialButton tabModeLogin;
    private MaterialButton tabModeSignup;

    // Role Buttons & Description
    private MaterialButton btnRoleFarmer;
    private MaterialButton btnRoleVet;
    private TextView tvRoleDescription;

    // Login Mode Views
    private LinearLayout layoutLoginContainer;
    private TextView tvLockoutWarning;
    private TextView tvPinPrompt;
    private View[] pinDots;
    private View[] keypadButtons;
    private CountDownTimer lockoutTimer;

    // Sign Up Mode Views
    private LinearLayout layoutSignupContainer;
    private TextView tvSignupHeading;
    private TextInputEditText etSignupName;
    private TextInputEditText etSignupPin;
    private TextInputEditText etSignupConfirmPin;
    private MaterialButton btnSignupSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authManager = new AuthManager(this);
        selectedRole = authManager.getActiveRole();

        // Trigger background seeding of friendly animal profiles on startup
        AppDatabase.getInstance(this);

        initViews();
        setupModeTabs();
        setupRoleButtons();
        setupKeypadListeners();
        setupSignUpListeners();

        updateRoleSelectionUi();
        checkAndApplyLockout();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lockoutTimer != null) {
            lockoutTimer.cancel();
        }
    }

    private void initViews() {
        tabModeLogin = findViewById(R.id.tab_mode_login);
        tabModeSignup = findViewById(R.id.tab_mode_signup);

        btnRoleFarmer = findViewById(R.id.btn_role_farmer);
        btnRoleVet = findViewById(R.id.btn_role_vet);
        tvRoleDescription = findViewById(R.id.tv_role_description);

        layoutLoginContainer = findViewById(R.id.layout_login_container);
        tvLockoutWarning = findViewById(R.id.tv_lockout_warning);
        tvPinPrompt = findViewById(R.id.tv_pin_prompt);

        pinDots = new View[]{
                findViewById(R.id.pin_dot_1),
                findViewById(R.id.pin_dot_2),
                findViewById(R.id.pin_dot_3),
                findViewById(R.id.pin_dot_4)
        };

        keypadButtons = new View[]{
                findViewById(R.id.btn_pin_0), findViewById(R.id.btn_pin_1), findViewById(R.id.btn_pin_2),
                findViewById(R.id.btn_pin_3), findViewById(R.id.btn_pin_4), findViewById(R.id.btn_pin_5),
                findViewById(R.id.btn_pin_6), findViewById(R.id.btn_pin_7), findViewById(R.id.btn_pin_8),
                findViewById(R.id.btn_pin_9), findViewById(R.id.btn_pin_clear), findViewById(R.id.btn_pin_unlock)
        };

        layoutSignupContainer = findViewById(R.id.layout_signup_container);
        tvSignupHeading = findViewById(R.id.tv_signup_heading);
        etSignupName = findViewById(R.id.et_signup_name);
        etSignupPin = findViewById(R.id.et_signup_pin);
        etSignupConfirmPin = findViewById(R.id.et_signup_confirm_pin);
        btnSignupSubmit = findViewById(R.id.btn_signup_submit);
    }

    private void setupModeTabs() {
        tabModeLogin.setOnClickListener(v -> {
            isLoginMode = true;
            updateTabModeUi();
            checkAndApplyLockout();
        });

        tabModeSignup.setOnClickListener(v -> {
            isLoginMode = false;
            updateTabModeUi();
        });
    }

    private void updateTabModeUi() {
        int activeBg = ContextCompat.getColor(this, R.color.primary_navy);
        int activeText = ContextCompat.getColor(this, R.color.white);
        int inactiveText = ContextCompat.getColor(this, R.color.text_secondary);

        if (isLoginMode) {
            tabModeLogin.setBackgroundTintList(ColorStateList.valueOf(activeBg));
            tabModeLogin.setTextColor(activeText);
            tabModeSignup.setBackgroundTintList(ColorStateList.valueOf(android.R.color.transparent));
            tabModeSignup.setTextColor(inactiveText);

            layoutLoginContainer.setVisibility(View.VISIBLE);
            layoutSignupContainer.setVisibility(View.GONE);
        } else {
            tabModeSignup.setBackgroundTintList(ColorStateList.valueOf(activeBg));
            tabModeSignup.setTextColor(activeText);
            tabModeLogin.setBackgroundTintList(ColorStateList.valueOf(android.R.color.transparent));
            tabModeLogin.setTextColor(inactiveText);

            layoutLoginContainer.setVisibility(View.GONE);
            layoutSignupContainer.setVisibility(View.VISIBLE);

            // Pre-fill existing name if available
            String currentName = authManager.getDisplayNameForRole(selectedRole);
            if (currentName != null && !currentName.isEmpty()) {
                etSignupName.setText(currentName);
            }
        }
    }

    private void setupRoleButtons() {
        btnRoleFarmer.setOnClickListener(v -> {
            selectedRole = AuthManager.ROLE_FARMER;
            enteredPin.setLength(0);
            updateRoleSelectionUi();
            updatePinDotsUi();
            checkAndApplyLockout();
        });

        btnRoleVet.setOnClickListener(v -> {
            selectedRole = AuthManager.ROLE_VET;
            enteredPin.setLength(0);
            updateRoleSelectionUi();
            updatePinDotsUi();
            checkAndApplyLockout();
        });
    }

    private void setupKeypadListeners() {
        int[] digitButtonIds = {
                R.id.btn_pin_0, R.id.btn_pin_1, R.id.btn_pin_2, R.id.btn_pin_3, R.id.btn_pin_4,
                R.id.btn_pin_5, R.id.btn_pin_6, R.id.btn_pin_7, R.id.btn_pin_8, R.id.btn_pin_9
        };

        for (int i = 0; i < digitButtonIds.length; i++) {
            final String digit = String.valueOf(i);
            View btn = findViewById(digitButtonIds[i]);
            if (btn != null) {
                btn.setOnClickListener(v -> onDigitPressed(digit));
            }
        }

        findViewById(R.id.btn_pin_clear).setOnClickListener(v -> {
            enteredPin.setLength(0);
            updatePinDotsUi();
        });

        findViewById(R.id.btn_pin_unlock).setOnClickListener(v -> attemptPinLogin());
    }

    private void setupSignUpListeners() {
        btnSignupSubmit.setOnClickListener(v -> attemptSignUp());
    }

    private void onDigitPressed(String digit) {
        if (authManager.isLockedOut(selectedRole)) {
            Toast.makeText(this, "Keypad is temporarily locked", Toast.LENGTH_SHORT).show();
            return;
        }

        if (enteredPin.length() < 4) {
            enteredPin.append(digit);
            updatePinDotsUi();
            if (enteredPin.length() == 4) {
                attemptPinLogin();
            }
        }
    }

    private void attemptPinLogin() {
        if (authManager.isLockedOut(selectedRole)) {
            checkAndApplyLockout();
            return;
        }

        String pin = enteredPin.toString();
        if (pin.length() != 4) {
            Toast.makeText(this, "Please enter your 4-digit PIN", Toast.LENGTH_SHORT).show();
            return;
        }

        if (authManager.verifyPinAndLogin(selectedRole, pin)) {
            Toast.makeText(this, "Welcome, " + authManager.getActiveDisplayName(), Toast.LENGTH_SHORT).show();
            navigateToDashboard();
        } else {
            enteredPin.setLength(0);
            updatePinDotsUi();

            if (authManager.isLockedOut(selectedRole)) {
                Toast.makeText(this, "Too many failed attempts! Keypad locked for security.", Toast.LENGTH_LONG).show();
                checkAndApplyLockout();
            } else {
                int attemptsRemaining = 5 - authManager.getFailedAttempts(selectedRole);
                Toast.makeText(this, "Incorrect PIN. Attempts remaining: " + attemptsRemaining, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void attemptSignUp() {
        String name = etSignupName.getText() != null ? etSignupName.getText().toString().trim() : "";
        String pin = etSignupPin.getText() != null ? etSignupPin.getText().toString().trim() : "";
        String confirmPin = etSignupConfirmPin.getText() != null ? etSignupConfirmPin.getText().toString().trim() : "";

        if (name.isEmpty()) {
            etSignupName.setError("Please enter your name or title");
            etSignupName.requestFocus();
            return;
        }

        if (pin.length() != 4 || !pin.matches("\\d{4}")) {
            etSignupPin.setError("PIN must be exactly 4 digits");
            etSignupPin.requestFocus();
            return;
        }

        if (!pin.equals(confirmPin)) {
            etSignupConfirmPin.setError("PINs do not match");
            etSignupConfirmPin.requestFocus();
            return;
        }

        try {
            authManager.registerOrUpdateAccount(selectedRole, name, pin);
            Toast.makeText(this, "Account registered successfully for " + name + "!", Toast.LENGTH_SHORT).show();
            navigateToDashboard();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to register account: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void checkAndApplyLockout() {
        if (authManager.isLockedOut(selectedRole)) {
            long remaining = authManager.getLockoutRemainingSeconds(selectedRole);
            setKeypadEnabled(false);
            tvLockoutWarning.setVisibility(View.VISIBLE);
            startLockoutCountdown(remaining);
        } else {
            setKeypadEnabled(true);
            tvLockoutWarning.setVisibility(View.GONE);
            if (lockoutTimer != null) {
                lockoutTimer.cancel();
            }
        }
    }

    private void setKeypadEnabled(boolean enabled) {
        if (keypadButtons != null) {
            for (View btn : keypadButtons) {
                if (btn != null) {
                    btn.setEnabled(enabled);
                    btn.setAlpha(enabled ? 1.0f : 0.4f);
                }
            }
        }
    }

    private void startLockoutCountdown(long seconds) {
        if (lockoutTimer != null) {
            lockoutTimer.cancel();
        }

        lockoutTimer = new CountDownTimer(seconds * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long remainingSec = (millisUntilFinished / 1000) + 1;
                tvLockoutWarning.setText("⛔ Keypad locked due to failed attempts. Try again in " + remainingSec + "s");
            }

            @Override
            public void onFinish() {
                authManager.resetFailedAttempts(selectedRole);
                checkAndApplyLockout();
                Toast.makeText(LoginActivity.this, "Keypad unlocked. Please enter your PIN.", Toast.LENGTH_SHORT).show();
            }
        }.start();
    }

    private void updateRoleSelectionUi() {
        boolean isVet = AuthManager.ROLE_VET.equals(selectedRole);
        int green = ContextCompat.getColor(this, R.color.status_healthy);
        int navy = ContextCompat.getColor(this, R.color.primary_navy);
        int white = ContextCompat.getColor(this, R.color.white);
        int cardBg = ContextCompat.getColor(this, R.color.surface_card);
        int darkText = ContextCompat.getColor(this, R.color.text_primary);

        String activeName = authManager.getDisplayNameForRole(selectedRole);

        if (!isVet) {
            btnRoleFarmer.setBackgroundTintList(ColorStateList.valueOf(green));
            btnRoleFarmer.setTextColor(white);
            btnRoleFarmer.setStrokeWidth(0);

            btnRoleVet.setBackgroundTintList(ColorStateList.valueOf(cardBg));
            btnRoleVet.setTextColor(darkText);
            btnRoleVet.setStrokeColor(ColorStateList.valueOf(navy));
            btnRoleVet.setStrokeWidth((int) (2 * getResources().getDisplayMetrics().density));

            tvRoleDescription.setBackgroundResource(R.drawable.bg_summary_healthy);
            tvRoleDescription.setTextColor(green);
            tvRoleDescription.setText("👨🏾‍🌾 Herder Mode: Real cow names, plain-language care steps & daily milk logging.");
            tvPinPrompt.setText("Enter 4-Digit Herder PIN (" + activeName + ")");
            tvSignupHeading.setText("Register Cow Owner / Herder Profile");
            btnSignupSubmit.setBackgroundTintList(ColorStateList.valueOf(green));
        } else {
            btnRoleVet.setBackgroundTintList(ColorStateList.valueOf(navy));
            btnRoleVet.setTextColor(white);
            btnRoleVet.setStrokeWidth(0);

            btnRoleFarmer.setBackgroundTintList(ColorStateList.valueOf(cardBg));
            btnRoleFarmer.setTextColor(darkText);
            btnRoleFarmer.setStrokeColor(ColorStateList.valueOf(green));
            btnRoleFarmer.setStrokeWidth((int) (2 * getResources().getDisplayMetrics().density));

            tvRoleDescription.setBackgroundResource(R.drawable.bg_metric_pill);
            tvRoleDescription.setTextColor(navy);
            tvRoleDescription.setText("🩺 Clinical Vet Mode: Differential diagnosis, 3-axis raw vectors & Gateway IP setup.");
            tvPinPrompt.setText("Enter 4-Digit Veterinarian PIN (" + activeName + ")");
            tvSignupHeading.setText("Register Field Veterinarian Profile");
            btnSignupSubmit.setBackgroundTintList(ColorStateList.valueOf(navy));
        }

        if (!isLoginMode) {
            etSignupName.setText(activeName);
        }
    }

    private void updatePinDotsUi() {
        int len = enteredPin.length();
        for (int i = 0; i < pinDots.length; i++) {
            pinDots[i].setBackgroundResource(i < len ? R.drawable.pin_dot_filled : R.drawable.pin_dot_empty);
        }
    }

    private void navigateToDashboard() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
