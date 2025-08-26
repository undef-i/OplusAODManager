package org.noxylva.oplusaod;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AnalogClock;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextClock;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Constructor;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    private static final String TAG = "OplusAOD";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String MODULE_PACKAGE_NAME = "org.noxylva.oplusaod";

    private static final String PREFS_NAME = "OplusAODPrefs";
    private static final String KEY_JSON_LAYOUT = "aod_json_layout";
    private static final String KEY_IMAGE_URIS = "aod_image_uris";

    private static final String AOD_RECORD_CLASS = "com.oplus.systemui.aod.AodRecord";
    private static XSharedPreferences prefs;
    private final Random random = new Random();

    @Override
    public void initZygote(StartupParam startupParam) {
        prefs = new XSharedPreferences(MODULE_PACKAGE_NAME, PREFS_NAME);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals(SYSTEM_UI_PACKAGE)) return;

        try {
            final Class<?> aodRecordClass = XposedHelpers.findClass(AOD_RECORD_CLASS, lpparam.classLoader);
            XposedBridge.hookAllMethods(aodRecordClass, "startShow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    final Object aodRecordInstance = param.thisObject;
                    final Context context = (Context) XposedHelpers.getObjectField(aodRecordInstance, "mContext");

                    new Handler(Looper.getMainLooper()).post(() -> {
                        try {
                            ViewGroup rootLayout = (ViewGroup) XposedHelpers.getObjectField(aodRecordInstance, "mRootLayout");
                            if (rootLayout == null) return;

                            View oldView = rootLayout.findViewWithTag("custom_aod_root_view");
                            if (oldView != null) rootLayout.removeView(oldView);

                            View originalAodClockLayout = (View) XposedHelpers.getObjectField(aodRecordInstance, "mAodClockLayout");
                            if (originalAodClockLayout != null) rootLayout.removeView(originalAodClockLayout);

                            View customView = createCustomView(context);
                            if (customView != null) {
                                customView.setTag("custom_aod_root_view");
                                rootLayout.addView(customView);
                            }
                        } catch (Throwable t) {
                            Log.e(TAG, "Error in startShow hook.", t);
                        }
                    });
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook AodRecord.", t);
        }
    }

    private View createCustomView(Context context) {
        prefs.reload();
        String jsonLayoutStr = prefs.getString(KEY_JSON_LAYOUT, null);
        RelativeLayout container = new RelativeLayout(context);
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        if (jsonLayoutStr == null || jsonLayoutStr.isEmpty()) {
            TextView defaultView = new TextView(context);
            defaultView.setText("Please configure in OplusAOD Designer");
            defaultView.setTextColor(Color.WHITE);
            defaultView.setTextSize(18);
            defaultView.setGravity(Gravity.CENTER);
            container.addView(defaultView);
            return container;
        }

        try {
            JSONArray viewsArray = new JSONArray(jsonLayoutStr);
            Map<String, Integer> idMap = new HashMap<>();
            List<View> createdViews = new ArrayList<>();
            List<JSONObject> jsonObjects = new ArrayList<>();

            for (int i = 0; i < viewsArray.length(); i++) {
                JSONObject viewJson = viewsArray.getJSONObject(i);
                View view = createViewByType(context, viewJson);
                if (view == null) continue;

                int viewId = View.generateViewId();
                view.setId(viewId);
                if (viewJson.has("id")) {
                    idMap.put(viewJson.getString("id"), viewId);
                }
                applyViewProperties(context, view, viewJson);
                createdViews.add(view);
                jsonObjects.add(viewJson);
            }

            for (int i = 0; i < createdViews.size(); i++) {
                View view = createdViews.get(i);
                JSONObject viewJson = jsonObjects.get(i);
                if (view.getParent() != null) continue;
                RelativeLayout.LayoutParams params = createLayoutParams(viewJson, idMap);
                view.setLayoutParams(params);
                container.addView(view);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse JSON and build layout.", e);
            TextView errorView = new TextView(context);
            errorView.setText("Failed to parse layout JSON.\nCheck logs for details.");
            errorView.setTextColor(Color.RED);
            errorView.setGravity(Gravity.CENTER);
            container.addView(errorView);
        }
        return container;
    }

    private View createViewByType(Context context, JSONObject json) {
        String type = json.optString("type", "android.widget.TextView");
        try {
            if (type.equals("android.widget.ProgressBar")) {
                if ("horizontal".equalsIgnoreCase(json.optString("style"))) {
                    return new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
                }
            }
            Class<?> viewClass = Class.forName(type);
            Constructor<?> constructor = viewClass.getConstructor(Context.class);
            return (View) constructor.newInstance(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create view via reflection: " + type, e);
            TextView errorView = new TextView(context);
            errorView.setText("Cannot create type:\n" + type);
            errorView.setTextColor(Color.YELLOW);
            return errorView;
        }
    }

    private void applyViewProperties(Context context, View view, JSONObject json) {
        if (json.has("alpha")) {
            view.setAlpha((float) json.optDouble("alpha", 1.0));
        }

        String tag = json.optString("tag", "");
        if (!tag.isEmpty()) {
            if (tag.startsWith("data:user_image")) {
                if (view instanceof ImageView) {
                    try {
                        String urisJsonStr = prefs.getString(KEY_IMAGE_URIS, "[]");
                        JSONArray uris = new JSONArray(urisJsonStr);
                        if (uris.length() == 0) return;

                        String targetUriStr = null;

                        if (tag.equals("data:user_image_random")) {
                            int randomIndex = random.nextInt(uris.length());
                            targetUriStr = uris.getString(randomIndex);
                        } else if (tag.matches("data:user_image\\[\\d+\\]")) {
                            try {
                                String indexStr = tag.substring(tag.indexOf('[') + 1, tag.indexOf(']'));
                                int index = Integer.parseInt(indexStr);
                                if (index >= 0 && index < uris.length()) {
                                    targetUriStr = uris.getString(index);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to parse image index: " + tag, e);
                            }
                        } else {
                            targetUriStr = uris.getString(0);
                        }

                        if (targetUriStr != null) {
                            Uri imageUri = Uri.parse(targetUriStr);
                            Bitmap bitmap = BitmapFactory.decodeStream(context.getContentResolver().openInputStream(imageUri));
                            ((ImageView) view).setImageBitmap(bitmap);
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Failed to load user image from Provider URI", e);
                    }
                }
            } else {
                switch (tag) {
                    case "data:date":
                        if (view instanceof TextView) {
                            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
                            ((TextView) view).setText(dateFormat.format(new Date()));
                        }
                        break;
                    case "data:battery_level":
                        if (view instanceof TextView) ((TextView) view).setText(getBatteryLevel(context));
                        break;
                    case "data:battery_charging":
                        if (!isDeviceCharging(context)) view.setVisibility(View.GONE);
                        break;
                }
            }
        } else if (json.has("random_texts")) {
            if (view instanceof TextView) {
                try {
                    JSONArray textsArray = json.getJSONArray("random_texts");
                    if (textsArray.length() > 0) {
                        List<String> texts = new ArrayList<>();
                        for (int i = 0; i < textsArray.length(); i++) texts.add(textsArray.getString(i));
                        texts.removeIf(s -> s.trim().isEmpty());
                        if (!texts.isEmpty()) {
                            ((TextView) view).setText(texts.get(random.nextInt(texts.size())));
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse random_texts", e);
                }
            }
        } else if (json.has("text")) {
            if (view instanceof TextView) {
                ((TextView) view).setText(json.optString("text"));
            }
        }

        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            if (json.has("textColor")) try { textView.setTextColor(Color.parseColor(json.getString("textColor"))); } catch (Exception ignored) {}
            if (json.has("textSize")) textView.setTextSize((float) json.optDouble("textSize"));
            if (json.has("textStyle")) {
                switch (json.optString("textStyle")) {
                    case "bold": textView.setTypeface(null, Typeface.BOLD); break;
                    case "italic": textView.setTypeface(null, Typeface.ITALIC); break;
                    case "bold_italic": textView.setTypeface(null, Typeface.BOLD_ITALIC); break;
                }
            }
        }

        if (view instanceof TextClock) {
            TextClock textClock = (TextClock) view;
            if (json.has("format12Hour")) textClock.setFormat12Hour(json.optString("format12Hour"));
            if (json.has("format24Hour")) textClock.setFormat24Hour(json.optString("format24Hour"));
        }

        if (view instanceof ImageView) {
            ImageView imageView = (ImageView) view;
            if (json.has("scaleType")) try { imageView.setScaleType(ImageView.ScaleType.valueOf(json.getString("scaleType").toUpperCase(Locale.ROOT))); } catch (Exception ignored) {}
        }

        if (view instanceof ProgressBar) {
            ProgressBar progressBar = (ProgressBar) view;
            if (json.has("max")) {
                progressBar.setMax(json.optInt("max", 100));
            }
            String progressTag = json.optString("progress_tag");
            if ("data:battery_level".equals(progressTag)) {
                progressBar.setProgress(getBatteryLevelInt(context));
            }
        }
    }

    private RelativeLayout.LayoutParams createLayoutParams(JSONObject json, Map<String, Integer> idMap) {
        int width = json.optString("layout_width", "wrap_content").equalsIgnoreCase("match_parent") ?
                ViewGroup.LayoutParams.MATCH_PARENT : dpToPx(json.optInt("width", -2));

        int height = json.optString("layout_height", "wrap_content").equalsIgnoreCase("match_parent") ?
                ViewGroup.LayoutParams.MATCH_PARENT : dpToPx(json.optInt("height", -2));

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(width, height);

        JSONObject rules = json.optJSONObject("layout_rules");
        if (rules != null) {
            if (rules.optBoolean("centerInParent")) params.addRule(RelativeLayout.CENTER_IN_PARENT);
            if (rules.optBoolean("centerHorizontal")) params.addRule(RelativeLayout.CENTER_HORIZONTAL);
            if (rules.optBoolean("centerVertical")) params.addRule(RelativeLayout.CENTER_VERTICAL);
            if (rules.optBoolean("alignParentTop")) params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            if (rules.optBoolean("alignParentBottom")) params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            if (rules.optBoolean("alignParentLeft")) params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            if (rules.optBoolean("alignParentRight")) params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);

            if (rules.has("below")) {
                Integer anchorId = idMap.get(rules.optString("below"));
                if (anchorId != null) params.addRule(RelativeLayout.BELOW, anchorId);
            }
            if (rules.has("above")) {
                Integer anchorId = idMap.get(rules.optString("above"));
                if (anchorId != null) params.addRule(RelativeLayout.ABOVE, anchorId);
            }
            if (rules.has("toRightOf")) {
                Integer anchorId = idMap.get(rules.optString("toRightOf"));
                if (anchorId != null) params.addRule(RelativeLayout.RIGHT_OF, anchorId);
            }
            if (rules.has("toLeftOf")) {
                Integer anchorId = idMap.get(rules.optString("toLeftOf"));
                if (anchorId != null) params.addRule(RelativeLayout.LEFT_OF, anchorId);
            }
            if (rules.has("alignBaseline")) {
                Integer anchorId = idMap.get(rules.optString("alignBaseline"));
                if (anchorId != null) params.addRule(RelativeLayout.ALIGN_BASELINE, anchorId);
            }
        }

        params.leftMargin = dpToPx(json.optInt("marginLeft", 0));
        params.topMargin = dpToPx(json.optInt("marginTop", 0));
        params.rightMargin = dpToPx(json.optInt("marginRight", 0));
        params.bottomMargin = dpToPx(json.optInt("marginBottom", 0));

        return params;
    }

    private int dpToPx(int dp) {
        if (dp < 0) return dp;
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    private String getBatteryLevel(Context context) {
        return getBatteryLevelInt(context) + "%";
    }

    private int getBatteryLevelInt(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);
        if (batteryStatus == null) return 0;
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryPct = level * 100 / (float)scale;
        return (int) batteryPct;
    }

    private boolean isDeviceCharging(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);
        if (batteryStatus == null) return false;
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;
    }
}