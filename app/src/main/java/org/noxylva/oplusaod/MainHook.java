package org.noxylva.oplusaod;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class MainHook implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    private static final String TAG = "OplusAOD";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String MODULE_PACKAGE_NAME = "org.noxylva.oplusaod";

    private static final String PREFS_NAME = "OplusAODPrefs";
    private static final String KEY_SINGLE_TEXT = "custom_aod_text";
    private static final String KEY_RANDOM_LIST = "random_text_list";
    private static final String KEY_RANDOM_ENABLED = "random_mode_enabled";

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
                            if (rootLayout == null || rootLayout.findViewWithTag("custom_aod_view") != null) return;

                            View originalAodClockLayout = (View) XposedHelpers.getObjectField(aodRecordInstance, "mAodClockLayout");
                            if (originalAodClockLayout != null) rootLayout.removeView(originalAodClockLayout);
                            
                            View customView = createCustomView(context, rootLayout);
                            if (customView != null) {
                                customView.setTag("custom_aod_view");
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

    private View createCustomView(Context targetContext, ViewGroup root) {
        String textToShow = "OplusAOD Manager"; 
        prefs.reload(); 

        boolean isRandomMode = prefs.getBoolean(KEY_RANDOM_ENABLED, false);

        if (isRandomMode) {
            String randomListStr = prefs.getString(KEY_RANDOM_LIST, "");
            if (randomListStr != null && !randomListStr.trim().isEmpty()) {
                List<String> texts = new ArrayList<>(Arrays.asList(randomListStr.split("\\r?\\n")));

                texts.removeIf(s -> s.trim().isEmpty());

                if (!texts.isEmpty()) {
                    textToShow = texts.get(random.nextInt(texts.size()));
                    Log.i(TAG, "Random mode: selected '" + textToShow + "' from list.");
                } else {
                     Log.w(TAG, "Random mode is on, but the list is empty after filtering. Using default text.");
                }
            } else {
                Log.w(TAG, "Random mode is on, but the list is empty. Using default text.");
            }
        } else {
            String singleText = prefs.getString(KEY_SINGLE_TEXT, null);
            if (singleText != null && !singleText.trim().isEmpty()) {
                textToShow = singleText.trim();
                Log.i(TAG, "Single mode: showing text '" + textToShow + "'");
            } else {
                 Log.w(TAG, "Single mode: text is empty. Using default text.");
            }
        }

        try {
            Context moduleContext = targetContext.createPackageContext(MODULE_PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY);
            LayoutInflater inflater = LayoutInflater.from(moduleContext);
            int layoutId = moduleContext.getResources().getIdentifier("custom_aod_layout", "layout", MODULE_PACKAGE_NAME);
            if (layoutId == 0) return null;

            View customView = inflater.inflate(layoutId, root, false);
            int textViewId = moduleContext.getResources().getIdentifier("custom_text", "id", MODULE_PACKAGE_NAME);
            TextView textView = customView.findViewById(textViewId);
            
            if (textView != null) {
                textView.setText(textToShow);
            }
            return customView;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create custom view.", e);
            return null;
        }
    }
}