/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.csuite.tests;

import com.android.csuite.core.ApkInstaller.ApkInstallerException;
import com.android.csuite.core.DeviceUtils.DeviceTimestamp;
import com.android.csuite.core.DeviceUtils.DeviceUtilsException;
import com.android.csuite.core.DeviceUtils.RunnableThrowingDeviceNotAvailable;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.RunUtil;

import org.junit.Assert;
import org.junit.Before;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/** A test that collects warm start launch time of a single app using perfetto. */
public class WarmAppLaunchTest extends BaseAppCompatTest {

    @Option(
            name = "warm-app-launch-count",
            description = "Number of times to launch the app.")
    private int mAppLaunchCount = 9;

    @Override
    @Before
    public void setUp() throws DeviceNotAvailableException, ApkInstallerException, IOException {
        super.setUp();

        try {
            mDeviceUtils.warmLaunchPackage(mPackageName);
            RunUtil.getDefault().sleep(mAppLaunchTimeoutMs);
        } catch (DeviceUtilsException e) {
            Assert.fail("Failed to launch package: " + e.getMessage());
        }
        mDeviceUtils.pressHome();
    }

    /**
     * Implements the specific logic for warm app launching the app repeatedly.
     */
    @Override
    protected void performAppLaunch(
        AtomicReference<DeviceTimestamp> startTime,
        AtomicReference<DeviceTimestamp> videoStartTime) throws DeviceNotAvailableException {

        RunnableThrowingDeviceNotAvailable launchJob =
                () -> {
                    startTime.set(mDeviceUtils.currentTimeMillis());
                    try {
                        for (int i = 0; i < mAppLaunchCount; i++) {
                            mDeviceUtils.warmLaunchPackage(mPackageName);
                            CLog.d(
                                    "Waiting %s milliseconds for the app to launch fully.",
                                    mAppLaunchTimeoutMs);
                            RunUtil.getDefault().sleep(mAppLaunchTimeoutMs);
                            mDeviceUtils.pressHome();
                        }
                    } catch (DeviceUtilsException e) {
                        Assert.fail(
                                "Failed to launch package " + mPackageName + ": " + e.getMessage());
                    }
                };

        if (mRecordScreen) {
            mTestUtils.collectScreenRecord(
                launchJob,
                mPackageName,
                videoStartTimeOnDevice -> videoStartTime.set(videoStartTimeOnDevice));
        } else {
            launchJob.run();
        }
    }
}