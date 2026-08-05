package com.boltz.tapjacker;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class MainActivity extends AppCompatActivity {

    private static final int[] SWATCH_IDS = {
            R.id.swatchRed, R.id.swatchOrange, R.id.swatchGreen, R.id.swatchBlue, R.id.swatchPurple};
    private static final int[] RING_IDS = {
            R.id.ringRed, R.id.ringOrange, R.id.ringGreen, R.id.ringBlue, R.id.ringPurple};
    private static final int[] SWATCH_COLORS = {
            R.color.decoy_red, R.color.decoy_orange, R.color.decoy_green,
            R.color.decoy_blue, R.color.decoy_purple};
    private static final int DEFAULT_SWATCH_INDEX = 1; // orange

    private MaterialCardView cardPermission;
    private ImageView ivPermissionIcon;
    private TextView tvPermissionStatus;
    private MaterialButton btnGrantPermission;
    private TextInputLayout tilButtonText;
    private TextInputEditText etButtonText;
    private Slider sliderWidth;
    private Slider sliderHeight;
    private Slider sliderOpacity;
    private TextView tvWidthValue;
    private TextView tvHeightValue;
    private TextView tvOpacityValue;
    private MaterialSwitch switchLock;
    private ImageView ivHintIcon;
    private TextView tvDragHint;
    private MaterialButton btnShowOverlay;

    private int selectedColor;
    private boolean overlayShown = false;

    private OverlayService overlayService;
    private boolean isBound = false;

    private final ActivityResultLauncher<Intent> overlayPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> refreshPermissionStatus());

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            OverlayService.LocalBinder binder = (OverlayService.LocalBinder) service;
            overlayService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            overlayService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bindViews();

        tvWidthValue.setText(getString(R.string.value_dp, (int) sliderWidth.getValue()));
        tvHeightValue.setText(getString(R.string.value_dp, (int) sliderHeight.getValue()));
        tvOpacityValue.setText(getString(R.string.value_percent, (int) sliderOpacity.getValue()));

        cardPermission.setOnClickListener(v -> requestOverlayPermission());
        btnGrantPermission.setOnClickListener(v -> requestOverlayPermission());

        sliderWidth.addOnChangeListener((slider, value, fromUser) -> {
            tvWidthValue.setText(getString(R.string.value_dp, (int) value));
            pushConfigIfShown();
        });
        sliderHeight.addOnChangeListener((slider, value, fromUser) -> {
            tvHeightValue.setText(getString(R.string.value_dp, (int) value));
            pushConfigIfShown();
        });
        sliderOpacity.addOnChangeListener((slider, value, fromUser) -> {
            tvOpacityValue.setText(getString(R.string.value_percent, (int) value));
            pushConfigIfShown();
        });
        etButtonText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (s.toString().trim().length() > 0) {
                    tilButtonText.setError(null);
                }
                pushConfigIfShown();
            }
        });

        setupColorSwatches();

        switchLock.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            tvDragHint.setText(isChecked ? R.string.hint_locked : R.string.hint_drag);
            ivHintIcon.setImageResource(isChecked ? R.drawable.ic_lock : R.drawable.ic_info);
            ivHintIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(
                    this, isChecked ? R.color.brand_primary : R.color.text_secondary)));
            pushConfigIfShown();
        });

        btnShowOverlay.setOnClickListener(v -> toggleOverlay());
        updateActionButton();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void bindViews() {
        cardPermission = findViewById(R.id.cardPermission);
        ivPermissionIcon = findViewById(R.id.ivPermissionIcon);
        tvPermissionStatus = findViewById(R.id.tvPermissionStatus);
        btnGrantPermission = findViewById(R.id.btnGrantPermission);
        tilButtonText = findViewById(R.id.tilButtonText);
        etButtonText = findViewById(R.id.etButtonText);
        sliderWidth = findViewById(R.id.sliderWidth);
        sliderHeight = findViewById(R.id.sliderHeight);
        sliderOpacity = findViewById(R.id.sliderOpacity);
        tvWidthValue = findViewById(R.id.tvWidthValue);
        tvHeightValue = findViewById(R.id.tvHeightValue);
        tvOpacityValue = findViewById(R.id.tvOpacityValue);
        switchLock = findViewById(R.id.switchLock);
        ivHintIcon = findViewById(R.id.ivHintIcon);
        tvDragHint = findViewById(R.id.tvDragHint);
        btnShowOverlay = findViewById(R.id.btnShowOverlay);
    }

    private void setupColorSwatches() {
        for (int i = 0; i < SWATCH_IDS.length; i++) {
            final int index = i;
            findViewById(SWATCH_IDS[i]).setOnClickListener(v -> selectSwatch(index));
        }
        selectSwatch(DEFAULT_SWATCH_INDEX);
    }

    private void selectSwatch(int index) {
        selectedColor = ContextCompat.getColor(this, SWATCH_COLORS[index]);
        for (int i = 0; i < RING_IDS.length; i++) {
            findViewById(RING_IDS[i]).setVisibility(i == index ? View.VISIBLE : View.INVISIBLE);
        }
        pushConfigIfShown();
    }

    private OverlayConfig buildConfig() {
        return new OverlayConfig(
                etButtonText.getText() != null ? etButtonText.getText().toString() : "",
                (int) sliderWidth.getValue(),
                (int) sliderHeight.getValue(),
                (int) sliderOpacity.getValue(),
                selectedColor,
                switchLock.isChecked());
    }

    private void pushConfigIfShown() {
        if (overlayShown && isBound && overlayService != null) {
            overlayService.updateConfig(buildConfig());
        }
    }

    private void toggleOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
            return;
        }

        if (!overlayShown) {
            CharSequence text = etButtonText.getText();
            if (text == null || text.toString().trim().isEmpty()) {
                tilButtonText.setError(getString(R.string.error_button_text_required));
                return;
            }

            Intent intent = new Intent(this, OverlayService.class);
            ContextCompat.startForegroundService(this, intent);
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
            isBound = true;
            overlayShown = true;
            updateActionButton();
            new android.os.Handler(getMainLooper()).postDelayed(this::pushConfigIfShown, 150);
        } else {
            if (isBound && overlayService != null) {
                overlayService.hideOverlay();
            }
            stopService(new Intent(this, OverlayService.class));
            if (isBound) {
                unbindService(connection);
                isBound = false;
            }
            overlayShown = false;
            updateActionButton();
        }
    }

    private void updateActionButton() {
        btnShowOverlay.setText(overlayShown ? R.string.btn_hide_overlay : R.string.btn_show_overlay);
        btnShowOverlay.setIconResource(overlayShown ? R.drawable.ic_stop : R.drawable.ic_play);

        int background = ContextCompat.getColor(this,
                overlayShown ? R.color.danger : R.color.brand_primary);
        int foreground = ContextCompat.getColor(this,
                overlayShown ? R.color.white : R.color.on_brand_primary);

        btnShowOverlay.setBackgroundTintList(ColorStateList.valueOf(background));
        btnShowOverlay.setTextColor(foreground);
        btnShowOverlay.setIconTint(ColorStateList.valueOf(foreground));
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        overlayPermissionLauncher.launch(intent);
    }

    private void refreshPermissionStatus() {
        boolean granted = Settings.canDrawOverlays(this);

        tvPermissionStatus.setText(granted
                ? R.string.permission_status_granted
                : R.string.permission_status_denied);
        tvPermissionStatus.setTextColor(ContextCompat.getColor(
                this, granted ? R.color.success : R.color.danger));

        ivPermissionIcon.setImageResource(granted
                ? R.drawable.ic_shield_check
                : R.drawable.ic_shield_alert);
        ivPermissionIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(
                this, granted ? R.color.success : R.color.danger)));
        ivPermissionIcon.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(
                this, granted ? R.color.success_container : R.color.danger_container)));

        btnGrantPermission.setVisibility(granted ? View.GONE : View.VISIBLE);
        cardPermission.setClickable(!granted);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionStatus();
    }

    @Override
    protected void onDestroy() {
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
        super.onDestroy();
    }
}
