/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.compatibility.testtype;

import com.android.csuite.core.AbstractCSuiteTest;
import com.android.csuite.core.DeviceUtils;
import com.android.csuite.core.DeviceUtils.DeviceUtilsException;
import com.android.csuite.core.TestUtils;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.TestDescription;

import com.google.common.annotations.VisibleForTesting;

import org.junit.Assert;

import java.io.IOException;

/** A test that verifies that a single app can be successfully launched. */
public class AppLaunchTest extends AbstractCSuiteTest {
    @VisibleForTesting static final String SCREENSHOT_AFTER_LAUNCH = "screenshot-after-launch";
    @VisibleForTesting static final String COLLECT_APP_VERSION = "collect-app-version";
    @VisibleForTesting static final String COLLECT_GMS_VERSION = "collect-gms-version";
    @VisibleForTesting static final String RECORD_SCREEN = "record-screen";

    @Option(name = RECORD_SCREEN, description = "Whether to record screen during test.")
    private boolean mRecordScreen;

    @Option(
            name = SCREENSHOT_AFTER_LAUNCH,
            description = "Whether to take a screenshost after a package is launched.")
    private boolean mScreenshotAfterLaunch;

    @Option(
            name = COLLECT_APP_VERSION,
            description =
                    "Whether to collect package version information and store the information in"
                            + " test log files.")
    private boolean mCollectAppVersion;

    @Option(
            name = COLLECT_GMS_VERSION,
            description =
                    "Whether to collect GMS core version information and store the information in"
                            + " test log files.")
    private boolean mCollectGmsVersion;

    @Option(name = "package-name", description = "Package name of testing app.")
    private String mPackageName;

    @Option(
            name = "app-launch-timeout-ms",
            description = "Time to wait for app to launch in msecs.")
    private int mAppLaunchTimeoutMs = 5000;

    public AppLaunchTest() {
        this(null);
    }

    @VisibleForTesting
    AppLaunchTest(String packageName) {
        mPackageName = packageName;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void run() throws DeviceNotAvailableException {
        Assert.assertNotNull("Package name cannot be null", mPackageName);

        DeviceUtils deviceUtils = DeviceUtils.getInstance(getDevice());
        TestUtils testUtils = TestUtils.getInstance(this);

        if (mCollectGmsVersion) {
            testUtils.collectGmsVersion(mPackageName);
        }

        if (mCollectAppVersion) {
            testUtils.collectAppVersion(mPackageName);
        }

        deviceUtils.freezeRotation();
        deviceUtils.resetPackage(mPackageName);

        if (mRecordScreen) {
            testUtils.collectScreenRecord(
                    () -> {
                        launchPackageAndCheckForCrash();
                    },
                    mPackageName);
        } else {
            launchPackageAndCheckForCrash();
        }

        if (mScreenshotAfterLaunch) {
            testUtils.collectScreenshot(mPackageName);
        }

        deviceUtils.stopPackage(mPackageName);
        deviceUtils.unfreezeRotation();
    }

    private void launchPackageAndCheckForCrash() throws DeviceNotAvailableException {
        CLog.d("Launching package: %s.", mPackageName);

        DeviceUtils deviceUtils = DeviceUtils.getInstance(getDevice());
        TestUtils testUtils = TestUtils.getInstance(this);

        long startTime = deviceUtils.currentTimeMillis();
        try {
            deviceUtils.launchPackage(mPackageName);
        } catch (DeviceUtilsException e) {
            testFailed(e.getMessage());
            return;
        }

        try {
            Thread.sleep(mAppLaunchTimeoutMs);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        CLog.d("Completed launching package: %s", mPackageName);

        try {
            String crashLog = testUtils.getDropboxPackageCrashLog(mPackageName, startTime, true);
            if (crashLog != null) {
                testFailed(crashLog);
                return;
            }
        } catch (IOException e) {
            testFailed("Error while getting dropbox crash log: " + e);
            return;
        }

        if (!testUtils.isPackageProcessRunning(mPackageName)) {
            testFailed(
                    String.format(
                            "The process for package %s is no longer found running on the device,"
                                    + " but no explicit crashes were detected; Check logcat for"
                                    + " details.",
                            mPackageName));
            return;
        }
    }

    /** {@inheritDoc} */
    @Override
    protected TestDescription createTestDescription() {
        return new TestDescription(getClass().getSimpleName(), mPackageName);
    }
}
