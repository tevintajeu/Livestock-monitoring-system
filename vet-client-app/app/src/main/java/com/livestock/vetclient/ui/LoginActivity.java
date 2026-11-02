package com.livestock.vetclient.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.livestock.vetclient.R;
import com.livestock.vetclient.data.local.AppDatabase;
import com.livestock.vetclient.util.AuthManager;

/**
 * Screen 1: Offline M-Pesa Style 4-Digit PIN & Role Login Activity.
 *
 * Allows switching between two distinct personas:
 * 1. ROLE_FARMER (Cow Owner / Herder): Plain-language HCD view, milk logging, simple action checklist.
 * 2. ROLE_VET (Field Veterinarian): Clinical diagnostic engine, raw 3-axis vectors, Gateway IP settings.
 */
public class LoginActivity extends AppCompatActivity {

    private AuthManager authManager;
    private String selectedRole = AuthManager.ROLE_FARMER;
    private final StringBuilder enteredPin = new StringBuilder();

    private MaterialButton btnRoleFarmer;
    private MaterialButton btnRoleVet;
    private TextView tvRoleDescription;
    private TextView tvPinPrompt;
    private View[] pinDots;
    private SwitchMaterial switchDemoQuickLogin;
    private MaterialButton btnDemoInstantEnter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authManager = new AuthManager(this);
        selectedRole = authManager.getActiveRole();

        // Trigger background seeding of friendly animal profiles (Malkia, Simba, Chebet) on startup
        AppDatabase.getInstance(this);

        initViews();
        setupRoleButtons();
        setupKeypadListeners();
        updateRoleSelectionUi();
    }

    private void initViews() {
        btnRoleFarmer = findViewById(R.id.btn_role_farmer);
        btnRoleVet = findViewById(R.id.btn_role_vet);
        tvRoleDescription = findViewById(R.id.tv_role_description);
        tvPinPrompt = findViewById(R.id.tv_pin_prompt);

        pinDots = new View[]{
                findViewById(R.id.pin_dot_1),
                findViewById(R.id.pin_dot_2),
                findViewById(R.id.pin_dot_3),
                findViewById(R.id.pin_dot_4)
        };

        switchDemoQuickLogin = findViewById(R.id.switch_demo_quick_login);
        btnDemoInstantEnter = findViewById(R.id.btn_demo_instant_enter);

        switchDemoQuickLogin.setChecked(authManager.isDemoQuickLoginEnabled());
        switchDemoQuickLogin.setOnCheckedChangeListener((buttonView, isChecked) -> {
            authManager.setDemoQuickLoginEnabled(isChecked);
            if (isChecked) {
                Toast.makeText(this, "Demo Quick Login Enabled: Tap role or Instant Login", Toast.LENGTH_SHORT).show();
            }
        });

        btnDemoInstantEnter.setOnClickListener(v -> {
            authManager.setSessionRole(selectedRole);
            navigateToDashboard();
        });
    }

    private void setupRoleButtons() {
        btnRoleFarmer.setOnClickListener(v -> {
            selectedRole = AuthManager.ROLE_FARMER;
            enteredPin.setLength(0);
            updateRoleSelectionUi();
            updatePinDotsUi();
            if (switchDemoQuickLogin.isChecked()) {
                authManager.setSessionRole(AuthManager.ROLE_FARMER);
                navigateToDashboard();
            }
        });

        btnRoleVet.setOnClickListener(v -> {
            selectedRole = AuthManager.ROLE_VET;
            enteredPin.setLength(0);
            updateRoleSelectionUi();
            updatePinDotsUi();
            if (switchDemoQuickLogin.isChecked()) {
                authManager.setSessionRole(AuthManager.ROLE_VET);
                navigateToDashboard();
            }
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

    private void onDigitPressed(String digit) {
        if (enteredPin.length() < 4) {
            enteredPin.append(digit);
            updatePinDotsUi();
            if (enteredPin.length() == 4) {
                attemptPinLogin();
            }
        }
    }

    private void attemptPinLogin() {
        if (switchDemoQuickLogin.isChecked() && enteredPin.length() == 0) {
            authManager.setSessionRole(selectedRole);
            navigateToDashboard();
            return;
        }

        String pin = enteredPin.toString();
        if (authManager.verifyPinAndLogin(selectedRole, pin)) {
            Toast.makeText(this, "Welcome (" + authManager.getRoleDisplayName() + ")", Toast.LENGTH_SHORT).show();
            navigateToDashboard();
        } else {
            enteredPin.setLength(0);
            updatePinDotsUi();
            String hint = AuthManager.ROLE_VET.equals(selectedRole) ? "9999" : "1234";
            Toast.makeText(this, "Incorrect PIN. Default for this role is " + hint, Toast.LENGTH_LONG).show();
        }
    }

    private void updateRoleSelectionUi() {
        boolean isVet = AuthManager.ROLE_VET.equals(selectedRole);
        int green = ContextCompat.getColor(this, R.color.status_healthy);
        int navy = ContextCompat.getColor(this, R.color.primary_navy);
        int white = ContextCompat.getColor(this, R.color.white);
        int cardBg = ContextCompat.getColor(this, R.color.surface_card);
        int darkText = ContextCompat.getColor(this, R.color.text_primary);

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
            tvPinPrompt.setText("Enter 4-Digit Herder PIN (Default: 1234)");
            btnDemoInstantEnter.setText("⚡ Instant Login as Cow Owner / Herder");
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
            tvPinPrompt.setText("Enter 4-Digit Veterinarian PIN (Default: 9999)");
            btnDemoInstantEnter.setText("⚡ Instant Login as Field Veterinarian");
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
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}

