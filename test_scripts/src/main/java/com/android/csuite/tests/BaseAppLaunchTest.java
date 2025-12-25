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

import com.android.csuite.core.ApkInstaller;
import com.android.csuite.core.ApkInstaller.ApkInstallerException;
import com.android.csuite.core.AutoFDOProfileCollector;
import com.android.csuite.core.BlankScreenDetectorWithSameColorRectangle;
import com.android.csuite.core.BlankScreenDetectorWithSameColorRectangle.BlankScreen;
import com.android.csuite.core.DeviceUtils;
import com.android.csuite.core.DeviceUtils.DeviceTimestamp;
import com.android.csuite.core.DeviceUtils.DeviceUtilsException;
import com.android.csuite.core.DropboxEntryCrashDetector.DropboxEntry;
import com.android.csuite.core.TestUtils;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestLogData;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

/**
 * Base abstract class for app launch related tests, providing common options, setup, teardown, and
 * crash/blank screen detection logic.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public abstract class BaseAppLaunchTest extends BaseHostJUnit4Test {

    @VisibleForTesting static final String SCREENSHOT_AFTER_LAUNCH = "screenshot-after-launch";
    @VisibleForTesting static final String COLLECT_APP_VERSION = "collect-app-version";
    @VisibleForTesting static final String COLLECT_GMS_VERSION = "collect-gms-version";
    @VisibleForTesting static final String RECORD_SCREEN = "record-screen";

    @Rule public TestLogData mLogData = new TestLogData();
    protected ApkInstaller mApkInstaller;
    protected boolean mIsLastTestPass;
    protected boolean mIsApkSaved = false;
    protected AutoFDOProfileCollector mAutoFDOProfileCollector;

    @Option(name = RECORD_SCREEN, description = "Whether to record screen during test.")
    protected boolean mRecordScreen;

    @Option(
            name = SCREENSHOT_AFTER_LAUNCH,
            description = "Whether to take a screenshot after a package is launched.")
    protected boolean mScreenshotAfterLaunch;

    @Option(
            name = COLLECT_APP_VERSION,
            description =
                    "Whether to collect package version information and store the information in"
                            + " test log files.")
    protected boolean mCollectAppVersion;

    @Option(
            name = COLLECT_GMS_VERSION,
            description =
                    "Whether to collect GMS core version information and store the information in"
                            + " test log files.")
    protected boolean mCollectGmsVersion;

    @Option(
            name = "collect-autofdo-profile",
            description =
                    "Whether to collect kernel AutoFDO profile and store the information in"
                            + " test log files.")
    private boolean mCollectAutoFDOProfile;

    @Option(
            name = "install-apk",
            description =
                    "The path to an apk file or a directory of apk files of a single package to be"
                            + " installed on device. Can be repeated.")
    protected final List<File> mApkPaths = new ArrayList<>();

    @Option(
            name = "install-arg",
            description = "Arguments for the 'adb install-multiple' package installation command.")
    protected final List<String> mInstallArgs = new ArrayList<>();

    @Option(
            name = "save-apk-when",
            description = "When to save apk files to the test result artifacts.")
    protected TestUtils.TakeEffectWhen mSaveApkWhen = TestUtils.TakeEffectWhen.NEVER;

    @Option(name = "package-name", description = "Package name of testing app.")
    protected String mPackageName;

    @Option(
            name = "app-launch-timeout-ms",
            description = "Time to wait for app to launch in msecs.")
    protected int mAppLaunchTimeoutMs = 15000;

    @Option(
            name = "blank-screen-same-color-area-threshold",
            description =
                    "Percentage of the screen which, if occupied by a same-color rectangle "
                            + "area, indicates that the app has reached a blank screen.")
    protected double mBlankScreenSameColorThreshold = -1;

    // TODO(jelenacvetic): Remove this option once this method is tested.
    @Option(
            name = "cold-app-launch",
            description = "Whether to use coldAppLaunch method to launch the app.")
    protected boolean mColdAppLaunch = false;

    @Option(name = "check-if-app-launched", description = "Whether to check if the app launched.")
    protected boolean mCheckIfAppLaunched = false;

    protected DeviceUtils mDeviceUtils;
    protected TestUtils mTestUtils;

    @Before
    public void setUp() throws DeviceNotAvailableException, ApkInstallerException, IOException {
        Assert.assertNotNull("Package name cannot be null", mPackageName);
        mIsLastTestPass = false;

        mDeviceUtils = DeviceUtils.getInstance(getDevice());
        mTestUtils = TestUtils.getInstance(getTestInformation(), mLogData);

        mApkInstaller = ApkInstaller.getInstance(getDevice());
        mApkInstaller.install(
                mApkPaths.stream().map(File::toPath).collect(Collectors.toList()), mInstallArgs);

        if (mCollectGmsVersion) {
            mTestUtils.collectGmsVersion(mPackageName);
        }

        if (mCollectAppVersion) {
            mTestUtils.collectAppVersion(mPackageName);
        }

        if (mCollectAutoFDOProfile) {
            mAutoFDOProfileCollector = AutoFDOProfileCollector.newInstance(getDevice());
        }

        mDeviceUtils.freezeRotation();
    }

    /**
     * Abstract method to be implemented by subclasses to define their specific app launch logic.
     *
     * @param startTime A reference to capture the device timestamp when the launch job starts.
     * @param videoStartTime A reference to capture the device timestamp when screen recording
     *     starts (if enabled).
     * @throws DeviceNotAvailableException
     */
    protected abstract void performAppLaunch(
            AtomicReference<DeviceTimestamp> startTime,
            AtomicReference<DeviceTimestamp> videoStartTime)
            throws DeviceNotAvailableException;

    @Test
    public void testAppLaunchCommonLogic() throws DeviceNotAvailableException, IOException {
        CLog.d("Launching package: %s.", mPackageName);

        try {
            if (!mDeviceUtils.isPackageInstalled(mPackageName)) {
                Assert.fail(
                        "Package "
                                + mPackageName
                                + " is not installed on the device. Aborting the test.");
            }
        } catch (DeviceUtilsException e) {
            Assert.fail("Failed to check the installed package list: " + e.getMessage());
        }

        AtomicReference<DeviceTimestamp> startTime = new AtomicReference<>();
        AtomicReference<DeviceTimestamp> videoStartTime = new AtomicReference<>();

        if (mCollectAutoFDOProfile
                && !mAutoFDOProfileCollector.recordAutoFDOProfile(mAppLaunchTimeoutMs / 1000.0)) {
            CLog.e("Failed to record AutoFDO profile.");
        }

        Set<String> activitiesBeforeLaunch = new HashSet<>();
        if (mCheckIfAppLaunched) {
            try {
                activitiesBeforeLaunch = mDeviceUtils.getActiveActivities();
                CLog.d("Activities before launch: %s", activitiesBeforeLaunch);
            } catch (DeviceUtilsException e) {
                Assert.fail("Failed to get activities before launch: " + e.getMessage());
            }
        }
        performAppLaunch(startTime, videoStartTime);

        CLog.d("Completed launching package: %s", mPackageName);
        DeviceTimestamp endTime = mDeviceUtils.currentTimeMillis();

        try {
            List<DropboxEntry> crashEntries =
                    mDeviceUtils.getCrashEntriesFromDropbox(mPackageName, startTime.get(), endTime);
            String crashLog =
                    mTestUtils.compileTestFailureMessage(
                            mPackageName, crashEntries, true, videoStartTime.get());
            if (!crashLog.isBlank()) {
                Assert.fail(crashLog);
            }
        } catch (IOException e) {
            Assert.fail("Error while getting dropbox crash log: " + e);
        }

        if (mBlankScreenSameColorThreshold > 0) {
            BufferedImage screen;
            try (InputStreamSource screenShot =
                    mTestUtils.getTestInformation().getDevice().getScreenshot()) {
                Preconditions.checkNotNull(screenShot);
                screen = ImageIO.read(screenShot.createInputStream());
            }
            BlankScreen blankScreen =
                    BlankScreenDetectorWithSameColorRectangle.getBlankScreen(screen);
            double blankScreenPercent = blankScreen.getBlankScreenPercent();
            if (blankScreenPercent > mBlankScreenSameColorThreshold) {
                BlankScreenDetectorWithSameColorRectangle.saveBlankScreenArtifact(
                        mPackageName,
                        blankScreen,
                        mTestUtils.getTestArtifactReceiver(),
                        mTestUtils.getTestInformation().getDevice().getSerialNumber());
                Assert.fail(
                        String.format(
                                "Blank screen detected with same-color rectangle area percentage of"
                                        + " %.2f%%",
                                blankScreenPercent * 100));
            }
        }

        if (mCheckIfAppLaunched) {
            try {
                Set<String> activitiesAfterLaunch = mDeviceUtils.getActiveActivities();
                CLog.d("Activities after launch: %s", activitiesAfterLaunch);
                Assert.assertFalse(
                        "Activities before and after launch are the same.",
                        activitiesBeforeLaunch.equals(activitiesAfterLaunch));
            } catch (DeviceUtilsException e) {
                Assert.fail("Failed to get activities after launch: " + e.getMessage());
            }
        }

        mIsLastTestPass = true;
    }

    @After
    public void tearDown() throws DeviceNotAvailableException, ApkInstallerException {
        if (!mIsApkSaved) {
            mIsApkSaved =
                    mTestUtils.saveApks(mSaveApkWhen, mIsLastTestPass, mPackageName, mApkPaths);
        }

        if (mScreenshotAfterLaunch) {
            mTestUtils.collectScreenshot(mPackageName);
        }

        if (mCollectAutoFDOProfile) {
            try {
                mAutoFDOProfileCollector.collectAutoFDOProfile(
                        mTestUtils.getTestArtifactReceiver());
            } catch (DeviceNotAvailableException e) {
                CLog.e("AutoFDO profile collection failed during teardown: %s", e.getMessage());
            }
        }

        mDeviceUtils.stopPackage(mPackageName);
        mDeviceUtils.unfreezeRotation();

        mApkInstaller.uninstallAllInstalledPackages();
    }
}
