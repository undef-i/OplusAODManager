package org.noxylva.oplusaod;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.Toast;
import io.github.rosemoe.sora.widget.CodeEditor;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity implements ImageListAdapter.OnImageDeleteListener {

    private static final String TAG = "OplusAOD_Settings";

    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "OplusAODPrefs";
    private static final String KEY_JSON_LAYOUT = "aod_json_layout";
    private static final String KEY_IMAGE_URIS = "aod_image_uris";

    private CodeEditor jsonLayoutInput;
    private RecyclerView imageListRecyclerView;
    private ImageListAdapter imageListAdapter;
    private final List<String> imageUriList = new ArrayList<>();

    private ActivityResultLauncher<String> imagePickerLauncher;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);

        jsonLayoutInput = findViewById(R.id.json_layout_input);
        Button restoreDefaultButton = findViewById(R.id.restore_default_button);
        Button addImageButton = findViewById(R.id.add_image_button);
        Button previewButton = findViewById(R.id.preview_button);
        Button saveButton = findViewById(R.id.save_button);
        Button restartSystemUIButton = findViewById(R.id.restart_systemui_button);

        setupRecyclerView();

        jsonLayoutInput.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if ((event.getAction() & MotionEvent.ACTION_UP) != 0) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetMultipleContents(),
                this::handleImageSelection
        );

        loadSettings();

        restoreDefaultButton.setOnClickListener(v -> restoreDefaultJson());
        addImageButton.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        previewButton.setOnClickListener(v -> showPreview());
        saveButton.setOnClickListener(v -> saveSettings());
        restartSystemUIButton.setOnClickListener(v -> restartSystemUI());
    }

    private void setupRecyclerView() {
        imageListRecyclerView = findViewById(R.id.image_list_recyclerview);
        imageListAdapter = new ImageListAdapter(this, imageUriList, this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        imageListRecyclerView.setLayoutManager(layoutManager);
        imageListRecyclerView.setAdapter(imageListAdapter);
    }

    private void loadSettings() {
        String jsonLayout = sharedPreferences.getString(KEY_JSON_LAYOUT, "");
        if (jsonLayout.isEmpty()) {
            restoreDefaultJson();
        } else {
            jsonLayoutInput.setText(jsonLayout);
        }

        String urisJson = sharedPreferences.getString(KEY_IMAGE_URIS, "[]");
        imageUriList.clear();
        try {
            JSONArray jsonArray = new JSONArray(urisJson);
            for (int i = 0; i < jsonArray.length(); i++) {
                imageUriList.add(jsonArray.getString(i));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse image URIs JSON", e);
        }
        imageListAdapter.notifyDataSetChanged();
    }

    private void saveSettings() {
        String jsonText = jsonLayoutInput.getText().toString();
        try {
            new JSONArray(jsonText);
        } catch (JSONException e) {
            Toast.makeText(this, "Save failed: Invalid JSON format", Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_JSON_LAYOUT, jsonText);


        saveImageUrisToPrefs();

        editor.apply();
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
    }

    private void handleImageSelection(List<Uri> sourceUris) {
        if (sourceUris == null || sourceUris.isEmpty()) {
            Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Processing " + sourceUris.size() + " images in background...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            int successCount = 0;
            for (Uri sourceUri : sourceUris) {
                String fileName = "aod_image_" + System.currentTimeMillis() + ".png";
                File destinationFile = new File(getFilesDir(), fileName);

                try (InputStream in = getContentResolver().openInputStream(sourceUri);
                     OutputStream out = new FileOutputStream(destinationFile)) {
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                    Uri providerUri = Uri.parse("content://" + ImageProvider.AUTHORITY + "/" + destinationFile.getName());
                    imageUriList.add(providerUri.toString());
                    successCount++;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to copy image: " + sourceUri, e);
                }
            }

            saveImageUrisToPrefs();
            int finalSuccessCount = successCount;
            runOnUiThread(() -> {
                imageListAdapter.notifyDataSetChanged();
                Toast.makeText(this, "Successfully added " + finalSuccessCount + " images", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    @Override
    public void onDeleteClick(int position) {
        if (position >= 0 && position < imageUriList.size()) {
            String uriString = imageUriList.get(position);
            Uri uri = Uri.parse(uriString);
            String fileName = uri.getLastPathSegment();
            if (fileName != null) {
                File file = new File(getFilesDir(), fileName);
                if (file.exists()) {
                    file.delete();
                }
            }
            imageUriList.remove(position);
            imageListAdapter.notifyItemRemoved(position);
            imageListAdapter.notifyItemRangeChanged(position, imageUriList.size());
            saveImageUrisToPrefs();
            Toast.makeText(this, "Image deleted", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveImageUrisToPrefs() {
        JSONArray jsonArray = new JSONArray(imageUriList);
        sharedPreferences.edit().putString(KEY_IMAGE_URIS, jsonArray.toString()).apply();
    }

    private void restoreDefaultJson() {
        try (InputStream inputStream = getResources().openRawResource(R.raw.default_layout);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            StringWriter writer = new StringWriter();
            char[] buffer = new char[1024];
            int n;
            while ((n = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, n);
            }
            jsonLayoutInput.setText(writer.toString());
            Toast.makeText(this, "Default layout restored", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to read default JSON template", e);
            Toast.makeText(this, "Error: Unable to load default template!", Toast.LENGTH_LONG).show();
        }
    }

    private void restartSystemUI() {
        try {
            Process process = Runtime.getRuntime().exec("su -c pkill -f com.android.systemui");
            process.waitFor();
            Toast.makeText(this, "Restarting SystemUI...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.root_required), Toast.LENGTH_LONG).show();
        }
    }

    private void showPreview() {
        String jsonText = jsonLayoutInput.getText().toString();
        if (jsonText.trim().isEmpty()) {
            Toast.makeText(this, "JSON is empty, cannot preview", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            new JSONArray(jsonText);
        } catch (JSONException e) {
            Toast.makeText(this, "Preview failed: Invalid JSON format!", Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(this, AodPreviewActivity.class);
        intent.putExtra("aod_json_layout", jsonText);
        
        JSONArray imageUrisArray = new JSONArray(imageUriList);
        intent.putExtra("aod_image_uris", imageUrisArray.toString());

        startActivity(intent);
    }
}