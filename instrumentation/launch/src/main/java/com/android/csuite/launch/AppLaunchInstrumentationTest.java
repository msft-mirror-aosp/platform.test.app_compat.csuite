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

package com.android.csuite.launch;

import android.app.ActivityManager;
import android.app.ActivityManager.ProcessErrorStateInfo;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.Preconditions;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Application Compatibility Test that launches an application and detects crashes. */
@RunWith(AndroidJUnit4.class)
public final class AppLaunchInstrumentationTest {

    private static final String TAG = AppLaunchInstrumentationTest.class.getSimpleName();
    private static final String PACKAGE_TO_LAUNCH = "package_to_launch";
    private static final String APP_LAUNCH_TIMEOUT_MSECS = "app_launch_timeout_ms";
    private static final String ENABLE_SPLASH_SCREEN = "enable-splash-screen";

    // time waiting for app to launch
    private int mAppLaunchTimeout = 7000;

    private Context mContext;
    private ActivityManager mActivityManager;
    private PackageManager mPackageManager;
    private Bundle mArgs;
    private Instrumentation mInstrumentation;
    private String mLauncherPackageName;
    private Map<String, List<String>> mAppErrors = new HashMap<>();

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();

        // Get permissions for privileged device operations.
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity();

        mContext = InstrumentationRegistry.getTargetContext();
        mActivityManager = mContext.getSystemService(ActivityManager.class);
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
    public void launchApp() throws Exception {
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
        launchActivity(packageName, intent);
    }

    private Intent getLaunchIntentForPackage(String packageName) {
        UiModeManager umm = mContext.getSystemService(UiModeManager.class);
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
}
