package org.noxylva.oplusaod;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "OplusAOD_Settings";

    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "OplusAODPrefs";
    private static final String KEY_JSON_LAYOUT = "aod_json_layout";
    private static final String KEY_IMAGE_PATH = "aod_image_path";

    private EditText jsonLayoutInput;
    private ImageView imagePreview;

    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private String selectedImagePath;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);

        ScrollView rootScrollView = findViewById(R.id.root_scroll_view);
        jsonLayoutInput = findViewById(R.id.json_layout_input);
        imagePreview = findViewById(R.id.image_preview);
        Button restoreDefaultButton = findViewById(R.id.restore_default_button);
        Button chooseImageButton = findViewById(R.id.choose_image_button);
        Button saveButton = findViewById(R.id.save_button);
        Button restartSystemUIButton = findViewById(R.id.restart_systemui_button);

        jsonLayoutInput.setOnTouchListener((v, event) -> {
            if (v.getId() == R.id.json_layout_input) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                if ((event.getAction() & MotionEvent.ACTION_UP) != 0) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                }
            }
            return false;
        });

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            copyImageToFilesDir(uri);
                        }
                    }
                });

        loadSettings();

        restoreDefaultButton.setOnClickListener(v -> restoreDefaultJson());
        chooseImageButton.setOnClickListener(v -> chooseImage());
        saveButton.setOnClickListener(v -> saveSettings());
        restartSystemUIButton.setOnClickListener(v -> restartSystemUI());
    }

    private void loadSettings() {
        String jsonLayout = sharedPreferences.getString(KEY_JSON_LAYOUT, "");
        if (jsonLayout.isEmpty()) {
            restoreDefaultJson();
        } else {
            jsonLayoutInput.setText(jsonLayout);
        }

        selectedImagePath = sharedPreferences.getString(KEY_IMAGE_PATH, null);
        if (selectedImagePath != null) {
            loadImageInBackground(selectedImagePath);
        }
    }
    
    private void loadImageInBackground(String uriString) {
        executorService.execute(() -> {
            try {
                Uri imageUri = Uri.parse(uriString);
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                handler.post(() -> imagePreview.setImageBitmap(bitmap));
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load image preview in background", e);
            }
        });
    }


    private void saveSettings() {
        String jsonText = jsonLayoutInput.getText().toString();
        try {
            new JSONArray(jsonText);
        } catch (JSONException e) {
            Toast.makeText(this, "Save failed: Invalid JSON format!", Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_JSON_LAYOUT, jsonText);

        if (selectedImagePath != null) {
            editor.putString(KEY_IMAGE_PATH, selectedImagePath);
        } else {
            editor.remove(KEY_IMAGE_PATH);
        }

        editor.apply();
        Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show();
    }

    private void restoreDefaultJson() {
        String defaultJson = "[\n" +
                "  {\n" +
                "    \"type\": \"TextClock\",\n" +
                "    \"id\": \"clock_view\",\n" +
                "    \"format24Hour\": \"HH:mm\",\n" +
                "    \"textSize\": 64,\n" +
                "    \"textColor\": \"#FFFFFFFF\",\n" +
                "    \"textStyle\": \"bold\",\n" +
                "    \"layout_rules\": {\n" +
                "      \"centerHorizontal\": true,\n" +
                "      \"alignParentTop\": true\n" +
                "    },\n" +
                "    \"marginTop\": 100\n" +
                "  },\n" +
                "  {\n" +
                "    \"type\": \"TextView\",\n" +
                "    \"id\": \"date_view\",\n" +
                "    \"tag\": \"data:date\",\n" +
                "    \"textSize\": 16,\n" +
                "    \"textColor\": \"#FFCCCCCC\",\n" +
                "    \"layout_rules\": {\n" +
                "      \"below\": \"clock_view\",\n" +
                "      \"centerHorizontal\": true\n" +
                "    }\n" +
                "  },\n" +
                "  {\n" +
                "    \"type\": \"ImageView\",\n" +
                "    \"id\": \"image_view\",\n" +
                "    \"tag\": \"data:user_image\",\n" +
                "    \"width\": 120,\n" +
                "    \"height\": 120,\n" +
                "    \"alpha\": 0.3,\n" +
                "    \"scaleType\": \"centerCrop\",\n" +
                "    \"layout_rules\": {\n" +
                "      \"centerInParent\": true\n" +
                "    }\n" +
                "  },\n" +
                "  {\n" +
                "    \"type\": \"TextView\",\n" +
                "    \"id\": \"random_view\",\n" +
                "    \"random_texts\": [\"foo\", \"bar\", \"baz\"],\n" +
                "    \"textSize\": 15,\n" +
                "    \"textColor\": \"#FFAAAAAA\",\n" +
                "    \"textStyle\": \"italic\",\n" +
                "    \"layout_rules\": {\n" +
                "      \"centerInParent\": true\n" +
                "    }\n" +
                "  },\n" +
                "  {\n" +
                "    \"type\": \"TextView\",\n" +
                "    \"id\": \"battery_view\",\n" +
                "    \"tag\": \"data:battery_level\",\n" +
                "    \"textSize\": 14,\n" +
                "    \"textColor\": \"#FF888888\",\n" +
                "    \"layout_rules\": {\n" +
                "      \"alignParentBottom\": true,\n" +
                "      \"centerHorizontal\": true\n" +
                "    },\n" +
                "    \"marginBottom\": 100\n" +
                "  },\n" +
                "  {\n" +
                "    \"type\": \"TextView\",\n" +
                "    \"id\": \"charging_view\",\n" +
                "    \"tag\": \"data:battery_charging\",\n" +
                "    \"text\": \"⚡\",\n" +
                "    \"textSize\": 14,\n" +
                "    \"textColor\": \"#FF00FF00\",\n" +
                "    \"layout_rules\": {\n" +
                "      \"alignBaseline\": \"battery_view\",\n" +
                "      \"toRightOf\": \"battery_view\"\n" +
                "    },\n" +
                "    \"marginLeft\": 8\n" +
                "  }\n" +
                "]";
        jsonLayoutInput.setText(defaultJson);
    }
    
    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }
    
    private void copyImageToFilesDir(Uri sourceUri) {
        File destinationFile = new File(getFilesDir(), "aod_image.png");

        try (InputStream in = getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(destinationFile)) {
            
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            
            Uri providerUri = Uri.parse("content://" + ImageProvider.AUTHORITY + "/" + destinationFile.getName());
            selectedImagePath = providerUri.toString(); 
            
            imagePreview.setImageURI(sourceUri);
            
            Log.i(TAG, "Image copied. Provider URI to be saved: " + selectedImagePath);

        } catch (Exception e) {
            Log.e(TAG, "Failed to copy image", e);
            Toast.makeText(this, "Failed to process image.", Toast.LENGTH_SHORT).show();
        }
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