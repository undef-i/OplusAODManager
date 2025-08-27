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
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextClock;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
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
        if (!lpparam.packageName.equals(SYSTEM_UI_PACKAGE))
            return;

        try {
            final Class<?> aodRecordClass = XposedHelpers.findClass(AOD_RECORD_CLASS, lpparam.classLoader);
            XposedBridge.hookAllMethods(aodRecordClass, "startShow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    final Object aodRecordInstance = param.thisObject;
                    final Context context = (Context) XposedHelpers.getObjectField(aodRecordInstance, "mContext");

                    new Handler(Looper.getMainLooper()).post(() -> {
                        try {
                            ViewGroup rootLayout = (ViewGroup) XposedHelpers.getObjectField(aodRecordInstance,
                                    "mRootLayout");
                            if (rootLayout == null)
                                return;

                            View oldView = rootLayout.findViewWithTag("custom_aod_root_view");
                            if (oldView != null)
                                rootLayout.removeView(oldView);

                            View originalAodClockLayout = (View) XposedHelpers.getObjectField(aodRecordInstance,
                                    "mAodClockLayout");
                            if (originalAodClockLayout != null)
                                rootLayout.removeView(originalAodClockLayout);

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
                ViewGroup.LayoutParams.MATCH_PARENT));

        if (jsonLayoutStr == null || jsonLayoutStr.isEmpty()) {
            TextView defaultView = new TextView(context);
            defaultView.setText("Please configure in OplusAOD Manager");
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
                if (view == null)
                    continue;

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
                if (view.getParent() != null)
                    continue;
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
            return null;
        }
    }

    private void applyViewProperties(Context context, View view, JSONObject json) {
        List<String> specialKeys = Arrays.asList(
                "type", "id", "width", "height", "layout_width", "layout_height",
                "layout_rules", "marginLeft", "marginRight", "marginTop", "marginBottom",
                "tag", "progress_tag", "style");

        if (json.has("tag")) {
            String tag = json.optString("tag");
            if (tag.startsWith("data:user_image")) {
                if (view instanceof ImageView) {
                    try {
                        String urisJsonStr = prefs.getString(KEY_IMAGE_URIS, "[]");
                        JSONArray uris = new JSONArray(urisJsonStr);
                        if (uris.length() == 0)
                            return;
                        String targetUriStr = null;
                        if (tag.equals("data:user_image_random")) {
                            targetUriStr = uris.getString(random.nextInt(uris.length()));
                        } else if (tag.matches("data:user_image\\[\\d+\\]")) {
                            String indexStr = tag.substring(tag.indexOf('[') + 1, tag.indexOf(']'));
                            int index = Integer.parseInt(indexStr);
                            if (index >= 0 && index < uris.length()) {
                                targetUriStr = uris.getString(index);
                            }
                        } else {
                            targetUriStr = uris.getString(0);
                        }
                        if (targetUriStr != null) {
                            Uri imageUri = Uri.parse(targetUriStr);
                            Bitmap bitmap = BitmapFactory
                                    .decodeStream(context.getContentResolver().openInputStream(imageUri));
                            ((ImageView) view).setImageBitmap(bitmap);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to load user image", e);
                    }
                }
            } else {
                switch (tag) {
                    case "data:date":
                        if (view instanceof TextView) {
                            ((TextView) view).setText(
                                    new SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(new Date()));
                        }
                        break;
                    case "data:battery_level":
                        if (view instanceof TextView) {
                            ((TextView) view).setText(getBatteryLevel(context));
                        }
                        break;
                    case "data:battery_charging":
                        if (!isDeviceCharging(context)) {
                            view.setVisibility(View.GONE);
                        }
                        break;
                }
            }
        }

        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!specialKeys.contains(key)) {
                try {
                    applyGenericProperty(view, key, json.get(key));
                } catch (JSONException e) {
                    Log.e(TAG, "Error getting value for key: " + key, e);
                }
            }
        }

        if (view instanceof ProgressBar) {
            String progressTag = json.optString("progress_tag");
            if ("data:battery_level".equals(progressTag)) {
                ((ProgressBar) view).setProgress(getBatteryLevelInt(context));
            }
        }
    }

    private void applyGenericProperty(View view, String key, Object value) {
        String methodName = "set" + key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1);
        for (Method method : view.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                continue;
            }
            try {
                Class<?> paramType = method.getParameterTypes()[0];
                Object convertedValue = convertValueToType(value, paramType, key, view);
                if (convertedValue != null) {
                    method.invoke(view, convertedValue);
                    return;
                }
            } catch (Exception e) {}
        }
    }

    private Object convertValueToType(Object value, Class<?> targetType, String key, View view) {
        if (targetType == float.class || targetType == Float.class) {
            if (value instanceof Number)
                return ((Number) value).floatValue();
            if (value instanceof String)
                return Float.parseFloat((String) value);
        } else if (targetType == int.class || targetType == Integer.class) {
            if (value instanceof Number)
                return ((Number) value).intValue();
            if (value instanceof String) {
                String strValue = (String) value;
                try {
                    return Color.parseColor(strValue);
                } catch (Exception e) {
                }
                if (key.equals("gravity") && view instanceof TextView) {
                    try {
                        return parseGravity(strValue);
                    } catch (Exception e) {
                    }
                }
                try {
                    return Integer.parseInt(strValue);
                } catch (Exception e) {
                }
            }
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            if (value instanceof Boolean)
                return value;
        } else if (CharSequence.class.isAssignableFrom(targetType)) {
            return value.toString();
        }
        return null;
    }

    private int parseGravity(String gravityString) {
        int gravity = Gravity.NO_GRAVITY;
        if (gravityString == null)
            return gravity;
        String[] gravities = gravityString.toLowerCase(Locale.ROOT).split("\\|");
        for (String g : gravities) {
            switch (g.trim()) {
                case "left":
                    gravity |= Gravity.LEFT;
                    break;
                case "right":
                    gravity |= Gravity.RIGHT;
                    break;
                case "top":
                    gravity |= Gravity.TOP;
                    break;
                case "bottom":
                    gravity |= Gravity.BOTTOM;
                    break;
                case "center":
                    gravity |= Gravity.CENTER;
                    break;
                case "center_horizontal":
                    gravity |= Gravity.CENTER_HORIZONTAL;
                    break;
                case "center_vertical":
                    gravity |= Gravity.CENTER_VERTICAL;
                    break;
            }
        }
        return gravity;
    }

    private RelativeLayout.LayoutParams createLayoutParams(JSONObject json, Map<String, Integer> idMap) {
        int width = json.optString("layout_width", "wrap_content").equalsIgnoreCase("match_parent")
                ? ViewGroup.LayoutParams.MATCH_PARENT
                : dpToPx(json.optInt("width", -2));
        int height = json.optString("layout_height", "wrap_content").equalsIgnoreCase("match_parent")
                ? ViewGroup.LayoutParams.MATCH_PARENT
                : dpToPx(json.optInt("height", -2));
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(width, height);
        JSONObject rules = json.optJSONObject("layout_rules");
        if (rules != null) {
            if (rules.optBoolean("centerInParent"))
                params.addRule(RelativeLayout.CENTER_IN_PARENT);
            if (rules.optBoolean("centerHorizontal"))
                params.addRule(RelativeLayout.CENTER_HORIZONTAL);
            if (rules.optBoolean("centerVertical"))
                params.addRule(RelativeLayout.CENTER_VERTICAL);
            if (rules.optBoolean("alignParentTop"))
                params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            if (rules.optBoolean("alignParentBottom"))
                params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            if (rules.optBoolean("alignParentLeft"))
                params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            if (rules.optBoolean("alignParentRight"))
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            if (rules.has("below")) {
                Integer id = idMap.get(rules.optString("below"));
                if (id != null)
                    params.addRule(RelativeLayout.BELOW, id);
            }
            if (rules.has("above")) {
                Integer id = idMap.get(rules.optString("above"));
                if (id != null)
                    params.addRule(RelativeLayout.ABOVE, id);
            }
            if (rules.has("toRightOf")) {
                Integer id = idMap.get(rules.optString("toRightOf"));
                if (id != null)
                    params.addRule(RelativeLayout.RIGHT_OF, id);
            }
            if (rules.has("toLeftOf")) {
                Integer id = idMap.get(rules.optString("toLeftOf"));
                if (id != null)
                    params.addRule(RelativeLayout.LEFT_OF, id);
            }
        }
        params.leftMargin = dpToPx(json.optInt("marginLeft", 0));
        params.topMargin = dpToPx(json.optInt("marginTop", 0));
        params.rightMargin = dpToPx(json.optInt("marginRight", 0));
        params.bottomMargin = dpToPx(json.optInt("marginBottom", 0));
        return params;
    }

    private int dpToPx(int dp) {
        if (dp < 0)
            return dp;
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    private String getBatteryLevel(Context context) {
        return getBatteryLevelInt(context) + "%";
    }

    private int getBatteryLevelInt(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);
        if (batteryStatus == null)
            return 0;
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return (int) (level * 100 / (float) scale);
    }

    private boolean isDeviceCharging(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);
        if (batteryStatus == null)
            return false;
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
    }
}