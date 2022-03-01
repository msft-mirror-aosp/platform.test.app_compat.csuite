/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.pixel.tests;

import static androidx.test.platform.app.InstrumentationRegistry.getArguments;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.pixel.utils.DeviceUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppLaunchRotateTest {
    private static final String KEY_PACKAGE_NAME = "package";
    private static final String ROTATE_LANDSCAPE =
            "content insert --uri content://settings/system"
                    + " --bind name:s:user_rotation --bind value:i:1";
    private static final String ROTATE_PORTRAIT =
            "content insert --uri content://settings/system"
                    + " --bind name:s:user_rotation --bind value:i:0";
    private static final int LAUNCH_TIME_MS = 30000; // 30 seconds
    private static final int WAIT_ONE_SECOND_IN_MS = 1000;

    private DeviceUtils mDeviceUtils;
    private UiDevice mDevice;
    private Bundle mArgs;
    private String mPackage;

    @Before
    public void setUp() throws Exception {
        mDevice = UiDevice.getInstance(getInstrumentation());
        mArgs = getArguments();
        mPackage = mArgs.getString(KEY_PACKAGE_NAME);
        if (mPackage == null) {
            throw new IllegalArgumentException("Package name cannot be null");
        }
        mDeviceUtils = new DeviceUtils(mDevice);

        mDeviceUtils.createLogDataDir();
        wakeAndUnlockScreen();
        // Start from the home screen
        mDeviceUtils.backToHome(mDevice.getLauncherPackageName());
        mDeviceUtils.startRecording(mPackage);
    }

    @After
    public void tearDown() throws Exception {
        mDeviceUtils.stopRecording();
        mDevice.unfreezeRotation();
    }

    private void wakeAndUnlockScreen() throws Exception {
        mDevice.wakeUp();
        SystemClock.sleep(WAIT_ONE_SECOND_IN_MS);
        mDevice.executeShellCommand("wm dismiss-keyguard");
        SystemClock.sleep(WAIT_ONE_SECOND_IN_MS);
    }

    @Test
    public void testRotateDevice() throws Exception {
        // Launch the 3P app
        Context context = getInstrumentation().getContext();
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(mPackage);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        // Wait for the 3P app to appear
        mDevice.wait(Until.hasObject(By.pkg(mPackage).depth(0)), LAUNCH_TIME_MS);
        mDevice.waitForIdle();
        Assert.assertTrue(
                "3P app main page should show up", mDevice.hasObject(By.pkg(mPackage).depth(0)));

        // Turn off the automatic rotation
        mDevice.freezeRotation();
        mDevice.executeShellCommand(ROTATE_PORTRAIT);
        SystemClock.sleep(WAIT_ONE_SECOND_IN_MS);
        mDeviceUtils.takeScreenshot(mPackage, "set_portrait_mode");
        Assert.assertTrue("Screen should be in portrait mode", mDevice.isNaturalOrientation());

        mDevice.executeShellCommand(ROTATE_LANDSCAPE);
        SystemClock.sleep(WAIT_ONE_SECOND_IN_MS);
        mDeviceUtils.takeScreenshot(mPackage, "rotate_landscape");
        Assert.assertFalse("Screen should be in landscape mode", mDevice.isNaturalOrientation());

        mDevice.executeShellCommand(ROTATE_PORTRAIT);
        SystemClock.sleep(WAIT_ONE_SECOND_IN_MS);
        mDeviceUtils.takeScreenshot(mPackage, "rotate_portrait");
        Assert.assertTrue("Screen should be in portrait mode", mDevice.isNaturalOrientation());
    }
}
