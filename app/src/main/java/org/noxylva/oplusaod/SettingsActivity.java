package org.noxylva.oplusaod;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends Activity {

    private SwitchMaterial randomModeSwitch;
    private LinearLayout singleTextLayout, randomTextLayout;
    private EditText singleTextInput, randomTextInput;

    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "OplusAODPrefs";
    private static final String KEY_SINGLE_TEXT = "custom_aod_text";
    private static final String KEY_RANDOM_LIST = "random_text_list";
    private static final String KEY_RANDOM_ENABLED = "random_mode_enabled";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);

        randomModeSwitch = findViewById(R.id.random_mode_switch);
        singleTextLayout = findViewById(R.id.single_text_layout);
        randomTextLayout = findViewById(R.id.random_text_layout);
        singleTextInput = findViewById(R.id.single_text_input);
        randomTextInput = findViewById(R.id.random_text_input);
        Button saveButton = findViewById(R.id.save_button);
        Button restartSystemUIButton = findViewById(R.id.restart_systemui_button);

        loadSettings();

        randomModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateUiVisibility(isChecked);
        });

        saveButton.setOnClickListener(v -> saveSettings());
        restartSystemUIButton.setOnClickListener(v -> restartSystemUI());
    }

    private void updateUiVisibility(boolean isRandomMode) {
        if (isRandomMode) {
            singleTextLayout.setVisibility(View.GONE);
            randomTextLayout.setVisibility(View.VISIBLE);
        } else {
            singleTextLayout.setVisibility(View.VISIBLE);
            randomTextLayout.setVisibility(View.GONE);
        }
    }

    private void loadSettings() {
        boolean isRandomEnabled = sharedPreferences.getBoolean(KEY_RANDOM_ENABLED, false);
        String singleText = sharedPreferences.getString(KEY_SINGLE_TEXT, "AOD MOD SUCCESS!");
        String randomList = sharedPreferences.getString(KEY_RANDOM_LIST, "");

        randomModeSwitch.setChecked(isRandomEnabled);
        singleTextInput.setText(singleText);
        randomTextInput.setText(randomList);

        updateUiVisibility(isRandomEnabled);
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        
        editor.putBoolean(KEY_RANDOM_ENABLED, randomModeSwitch.isChecked());
        editor.putString(KEY_SINGLE_TEXT, singleTextInput.getText().toString());
        editor.putString(KEY_RANDOM_LIST, randomTextInput.getText().toString());
        
        editor.apply();
        Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show();
    }

    private void restartSystemUI() {
        try {
            Process process = Runtime.getRuntime().exec("su -c pkill -f com.android.systemui");
            process.waitFor();
            Toast.makeText(this, "Restarting SystemUI...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Root permission required to restart SystemUI", Toast.LENGTH_LONG).show();
        }
    }
}