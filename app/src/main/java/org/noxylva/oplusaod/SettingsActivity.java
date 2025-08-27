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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.eclipse.tm4e.core.registry.IThemeSource;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import io.github.rosemoe.sora.widget.CodeEditor;

public class SettingsActivity extends AppCompatActivity implements ImageListAdapter.OnImageDeleteListener {

    private static final String TAG = "OplusAOD_Settings";

    private static final String PREFS_NAME = "OplusAODPrefs";
    private static final String KEY_JSON_LAYOUT = "aod_json_layout";
    private static final String KEY_IMAGE_URIS = "aod_image_uris";

    private static final String THEME_NAME = "quietlight";
    private static final String THEME_PATH = "textmate/" + THEME_NAME + ".json";
    private static final String LANGUAGES_CONFIG_PATH = "textmate/language.json";
    private static final String JSON_SCOPE_NAME = "source.json";

    private SharedPreferences sharedPreferences;
    private CodeEditor jsonLayoutInput;
    private ImageListAdapter imageListAdapter;
    private final List<String> imageUriList = new ArrayList<>();
    private ActivityResultLauncher<String[]> imagePickerLauncher;
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static boolean isTextMateInitialized = false;
    private TextMateLanguage textMateLanguage;

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
        initializeEditor();

        jsonLayoutInput.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                this::handleImageSelection);

        restoreDefaultButton.setOnClickListener(v -> restoreDefaultJson());
        addImageButton.setOnClickListener(v -> imagePickerLauncher.launch(new String[]{"image/*"}));
        previewButton.setOnClickListener(v -> showPreview());
        saveButton.setOnClickListener(v -> saveSettings());
        restartSystemUIButton.setOnClickListener(v -> restartSystemUI());
    }

    private void initializeEditor() {
        executor.execute(() -> {
            try {
                if (!isTextMateInitialized) {
                    FileProviderRegistry.getInstance().addFileProvider(new AssetsFileResolver(getAssets()));
                    ThemeRegistry themeRegistry = ThemeRegistry.getInstance();
                    
                    InputStream themeStream = getAssets().open(THEME_PATH);
                    IThemeSource themeSource = IThemeSource.fromInputStream(themeStream, THEME_PATH, null);
                    ThemeModel themeModel = new ThemeModel(themeSource, THEME_NAME);
                    
                    themeRegistry.loadTheme(themeModel);
                    themeRegistry.setTheme(THEME_NAME);
                    GrammarRegistry.getInstance().loadGrammars(LANGUAGES_CONFIG_PATH);
                    isTextMateInitialized = true;
                }

                this.textMateLanguage = TextMateLanguage.create(JSON_SCOPE_NAME, true);

                runOnUiThread(() -> {
                    try {
                        jsonLayoutInput.setColorScheme(TextMateColorScheme.create(ThemeRegistry.getInstance()));
                        jsonLayoutInput.setEditorLanguage(this.textMateLanguage);
                        loadSettings();
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to apply color scheme or language to editor.", e);
                        Toast.makeText(SettingsActivity.this, "Failed to setup editor.", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize TextMate in background.", e);
                runOnUiThread(() -> Toast.makeText(this, "Error initializing code editor.", Toast.LENGTH_LONG).show());
            }
        });
    }

    private void setupRecyclerView() {
        RecyclerView imageListRecyclerView = findViewById(R.id.image_list_recyclerview);
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
            if (jsonText.trim().startsWith("[")) {
                new JSONArray(jsonText);
            } else {
                new org.json.JSONObject(jsonText);
            }
        } catch (JSONException e) {
            Toast.makeText(this, "Save failed: Invalid JSON format", Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_JSON_LAYOUT, jsonText);
        editor.putString(KEY_IMAGE_URIS, new JSONArray(imageUriList).toString());
        editor.apply();

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
    }

    private void handleImageSelection(List<Uri> sourceUris) {
        if (sourceUris == null || sourceUris.isEmpty()) {
            Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Processing " + sourceUris.size() + " images...", Toast.LENGTH_SHORT).show();
        
        executor.execute(() -> {
            int successCount = 0;
            for (Uri sourceUri : sourceUris) {
                try {
                    getContentResolver().takePersistableUriPermission(sourceUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
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
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to copy image: " + sourceUri, e);
                }
            }
            
            int finalSuccessCount = successCount;
            runOnUiThread(() -> {
                imageListAdapter.notifyDataSetChanged();
                Toast.makeText(this, "Successfully added " + finalSuccessCount + " images", Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    public void onDeleteClick(int position) {
        if (position >= 0 && position < imageUriList.size()) {
            String uriString = imageUriList.get(position);
            Uri uri = Uri.parse(uriString);
            String fileName = uri.getLastPathSegment();

            if (fileName != null) {
                File fileToDelete = new File(getFilesDir(), fileName);
                if (fileToDelete.exists() && fileToDelete.delete()) {
                    Log.i(TAG, "Deleted file: " + fileName);
                }
            }
            imageUriList.remove(position);
            imageListAdapter.notifyItemRemoved(position);
            saveImageUrisToPrefs();
            Toast.makeText(this, "Image deleted", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveImageUrisToPrefs() {
        sharedPreferences.edit()
                .putString(KEY_IMAGE_URIS, new JSONArray(imageUriList).toString())
                .apply();
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
        Toast.makeText(this, "Attempting to restart SystemUI...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "pkill -f com.android.systemui"});
                
                int exitCode = process.waitFor();
                runOnUiThread(() -> {
                    if (exitCode == 0) {
                        Toast.makeText(this, "SystemUI restarted.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Failed to restart SystemUI. Root access denied or command failed?", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.root_required), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showPreview() {
        String jsonText = jsonLayoutInput.getText().toString();
        Intent intent = new Intent(this, AodPreviewActivity.class);
        intent.putExtra(KEY_JSON_LAYOUT, jsonText);
        intent.putExtra(KEY_IMAGE_URIS, new JSONArray(imageUriList).toString());
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}