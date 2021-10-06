/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.csuite;

import android.app.ActivityManager;
import android.app.ActivityManager.ProcessErrorStateInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.DropBoxManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.Preconditions;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Application Compatibility Test that launches an application and detects crashes. */
@RunWith(AndroidJUnit4.class)
public final class AppLaunchTest {

    private static final String TAG = AppLaunchTest.class.getSimpleName();
    private static final String PACKAGE_TO_LAUNCH = "package_to_launch";
    private static final String APP_LAUNCH_TIMEOUT_MSECS = "app_launch_timeout_ms";
    private static final String ENABLE_SPLASH_SCREEN = "enable-splash-screen";
    private static final Set<String> DROPBOX_TAGS = new HashSet<>();
    private static final int MAX_CRASH_SNIPPET_LINES = 20;
    private static final int MAX_NUM_CRASH_SNIPPET = 3;

    // time waiting for app to launch
    private int mAppLaunchTimeout = 7000;

    private Context mContext;
    private ActivityManager mActivityManager;
    private PackageManager mPackageManager;
    private Bundle mArgs;
    private Instrumentation mInstrumentation;
    private String mLauncherPackageName;
    private Map<String, List<String>> mAppErrors = new HashMap<>();

    static {
        DROPBOX_TAGS.add("SYSTEM_TOMBSTONE");
        DROPBOX_TAGS.add("system_app_anr");
        DROPBOX_TAGS.add("system_app_native_crash");
        DROPBOX_TAGS.add("system_app_crash");
        DROPBOX_TAGS.add("data_app_anr");
        DROPBOX_TAGS.add("data_app_native_crash");
        DROPBOX_TAGS.add("data_app_crash");
    }

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();

        // Get permissions for privileged device operations.
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity();

        mContext = InstrumentationRegistry.getTargetContext();
        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        mPackageManager = mContext.getPackageManager();
        mArgs = InstrumentationRegistry.getArguments();

        // resolve launcher package name
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolveInfo =
                mPackageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        mLauncherPackageName = resolveInfo.activityInfo.packageName;
        Assert.assertNotNull("failed to resolve package name for launcher", mLauncherPackageName);
        Log.v(TAG, "Using launcher package name: " + mLauncherPackageName);

        // Parse optional inputs.
        String appLaunchTimeoutMsecs = mArgs.getString(APP_LAUNCH_TIMEOUT_MSECS);
        if (appLaunchTimeoutMsecs != null) {
            mAppLaunchTimeout = Integer.parseInt(appLaunchTimeoutMsecs);
        }
        mInstrumentation.getUiAutomation().setRotation(UiAutomation.ROTATION_FREEZE_0);
    }

    @After
    public void tearDown() throws Exception {
        mInstrumentation.getUiAutomation().setRotation(UiAutomation.ROTATION_UNFREEZE);
    }

    /**
     * Actual test case that launches the package and throws an exception on the first error.
     *
     * @throws Exception
     */
    @Test
    public void testAppStability() throws Exception {
        String packageName = mArgs.getString(PACKAGE_TO_LAUNCH);
        Preconditions.checkStringNotEmpty(
                packageName,
                String.format(
                        "Missing argument, use %s to specify the package to launch",
                        PACKAGE_TO_LAUNCH));

        Log.d(TAG, "Launching app " + packageName);
        Intent intent = getLaunchIntentForPackage(packageName);
        if (intent == null) {
            Log.w(TAG, String.format("Skipping %s; no launch intent", packageName));
            return;
        }
        long startTime = System.currentTimeMillis();
        launchActivity(packageName, intent);

        checkDropbox(startTime, packageName);
        if (mAppErrors.containsKey(packageName)) {
            StringBuilder message =
                    new StringBuilder("Error(s) detected for package: ").append(packageName);
            List<String> errors = mAppErrors.get(packageName);
            for (int i = 0; i < MAX_NUM_CRASH_SNIPPET && i < errors.size(); i++) {
                String err = errors.get(i);
                message.append("\n\n");
                // limit the size of each crash snippet
                message.append(truncate(err, MAX_CRASH_SNIPPET_LINES));
            }
            if (errors.size() > MAX_NUM_CRASH_SNIPPET) {
                message.append(
                        String.format(
                                "\n... %d more errors omitted ...",
                                errors.size() - MAX_NUM_CRASH_SNIPPET));
            }
            Assert.fail(message.toString());
        }
        // last check: see if app process is still running
        Assert.assertTrue(
                "app package \""
                        + packageName
                        + "\" no longer found in running "
                        + "tasks, but no explicit crashes were detected; check logcat for "
                        + "details",
                processStillUp(packageName));
    }

    /**
     * Truncate the text to at most the specified number of lines, and append a marker at the end
     * when truncated
     *
     * @param text
     * @param maxLines
     * @return
     */
    private static String truncate(String text, int maxLines) {
        String[] lines = text.split("\\r?\\n");
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < maxLines && i < lines.length; i++) {
            ret.append(lines[i]);
            ret.append('\n');
        }
        if (lines.length > maxLines) {
            ret.append("... ");
            ret.append(lines.length - maxLines);
            ret.append(" more lines truncated ...\n");
        }
        return ret.toString();
    }

    /**
     * Check dropbox for entries of interest regarding the specified process
     *
     * @param startTime if not 0, only check entries with timestamp later than the start time
     * @param processName the process name to check for
     */
    private void checkDropbox(long startTime, String processName) {
        DropBoxManager dropbox =
                (DropBoxManager) mContext.getSystemService(Context.DROPBOX_SERVICE);
        DropBoxManager.Entry entry = null;
        while (null != (entry = dropbox.getNextEntry(null, startTime))) {
            try {
                // only check entries with tag that's of interest
                String tag = entry.getTag();
                if (DROPBOX_TAGS.contains(tag)) {
                    String content = entry.getText(4096);
                    if (content != null) {
                        if (content.contains(processName)) {
                            addProcessError(processName, "dropbox:" + tag, content);
                        }
                    }
                }
                startTime = entry.getTimeMillis();
            } finally {
                entry.close();
            }
        }
    }

    private Intent getLaunchIntentForPackage(String packageName) {
        UiModeManager umm = (UiModeManager) mContext.getSystemService(Context.UI_MODE_SERVICE);
        boolean isLeanback = umm.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        Intent intent = null;
        if (isLeanback) {
            intent = mPackageManager.getLeanbackLaunchIntentForPackage(packageName);
        } else {
            intent = mPackageManager.getLaunchIntentForPackage(packageName);
        }
        return intent;
    }

    /**
     * Launches and activity and queries for errors.
     *
     * @param packageName {@link String} the package name of the application to launch.
     * @return {@link Collection} of {@link ProcessErrorStateInfo} detected during the app launch.
     */
    private void launchActivity(String packageName, Intent intent) {
        Log.d(
                TAG,
                String.format(
                        "launching package \"%s\" with intent: %s",
                        packageName, intent.toString()));

        // Launch Activity
        if (mArgs.getString(ENABLE_SPLASH_SCREEN, "false").equals("true")) {
            Bundle bundle = new Bundle();
            bundle.putInt("android.activity.splashScreenStyle", 1);
            mContext.startActivity(intent, bundle);
        } else {
            mContext.startActivity(intent);
        }

        try {
            // artificial delay: in case app crashes after doing some work during launch
            Thread.sleep(mAppLaunchTimeout);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    private void addProcessError(String processName, String errorType, String errorInfo) {
        // parse out the package name if necessary, for apps with multiple processes
        String pkgName = processName.split(":", 2)[0];
        List<String> errors;
        if (mAppErrors.containsKey(pkgName)) {
            errors = mAppErrors.get(pkgName);
        } else {
            errors = new ArrayList<>();
        }
        errors.add(String.format("### Type: %s, Details:\n%s", errorType, errorInfo));
        mAppErrors.put(pkgName, errors);
    }

    /**
     * Determine if a given package is still running.
     *
     * @param packageName {@link String} package to look for
     * @return True if package is running, false otherwise.
     */
    private boolean processStillUp(String packageName) {
        @SuppressWarnings("deprecation")
        List<RunningTaskInfo> infos = mActivityManager.getRunningTasks(100);
        for (RunningTaskInfo info : infos) {
            if (info.baseActivity.getPackageName().equals(packageName)) {
                return true;
            }
        }
        return false;
    }
}
