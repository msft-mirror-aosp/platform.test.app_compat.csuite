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

package com.android.csuite.core;

import com.android.compatibility.FailureCollectingListener;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.LogcatReceiver;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.CompatibilityTestResult;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.StreamUtil;

import org.json.JSONException;
import org.junit.Assert;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class AppLaunchTester {

    private boolean mRecordScreen;
    private boolean mScreenshotAfterLaunch;
    private boolean mCollectAppVersion;
    private boolean mCollectGmsVersion;
    private boolean mEnableSplashScreen;
    private int mAppLaunchTimeoutMs = 15000;

    private static final String ENABLE_SPLASH_SCREEN = "enable-splash-screen";
    private static final String GMS_PACKAGE_NAME = "com.google.android.gms";
    private static final String LAUNCH_TEST_RUNNER =
            "com.android.compatibilitytest.AppCompatibilityRunner";
    private static final String LAUNCH_TEST_PACKAGE = "com.android.compatibilitytest";
    private static final String PACKAGE_TO_LAUNCH = "package_to_launch";
    private static final String APP_LAUNCH_TIMEOUT_LABEL = "app_launch_timeout_ms";
    private static final int LOGCAT_SIZE_BYTES = 20 * 1024 * 1024;
    private static final int BASE_INSTRUMENTATION_TEST_TIMEOUT_MS = 10 * 1000;

    private final AbstractCSuiteTest mBaseTest;
    private LogcatReceiver mLogcat;

    public AppLaunchTester(AbstractCSuiteTest baseTest) {
        mBaseTest = baseTest;
    }

    /**
     * Launches an package and checks for crash.
     *
     * @param packageName
     * @throws DeviceNotAvailableException
     */
    public void launchPackageAndCheckCrash(String packageName) throws DeviceNotAvailableException {
        Assert.assertNotNull("Package name cannot be null", packageName);

        mLogcat = new LogcatReceiver(mBaseTest.getDevice(), LOGCAT_SIZE_BYTES, 0);
        mLogcat.start();

        try {
            testPackage(packageName);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            mLogcat.stop();
        }
    }

    /**
     * Attempts to test a package and reports the results.
     *
     * @param listener The {@link ITestInvocationListener}.
     * @throws DeviceNotAvailableException
     */
    private void testPackage(String packageName)
            throws DeviceNotAvailableException, InterruptedException {
        CLog.d("Started testing package: %s.", packageName);

        CompatibilityTestResult result = createCompatibilityTestResult();
        result.packageName = packageName;

        try {
            if (mCollectAppVersion) {
                String versionCode =
                        DeviceUtils.getPackageVersionCode(mBaseTest.getDevice(), packageName);
                String versionName =
                        DeviceUtils.getPackageVersionName(mBaseTest.getDevice(), packageName);
                CLog.i(
                        "Testing package %s versionCode=%s, versionName=%s",
                        packageName, versionCode, versionName);
                mBaseTest.addTestArtifact(
                        String.format("%s_[versionCode=%s]", packageName, versionCode),
                        LogDataType.TEXT,
                        versionCode.getBytes());
                mBaseTest.addTestArtifact(
                        String.format("%s_[versionName=%s]", packageName, versionName),
                        LogDataType.TEXT,
                        versionName.getBytes());
            }

            if (mRecordScreen) {
                File video =
                        DeviceUtils.runWithScreenRecording(
                                mBaseTest.getDevice(),
                                () -> {
                                    launchPackage(result);
                                });
                if (video != null) {
                    mBaseTest.addTestArtifact(
                            packageName
                                    + "_screenrecord_"
                                    + mBaseTest.getDevice().getSerialNumber(),
                            LogDataType.MP4,
                            video);
                } else {
                    CLog.e("Failed to get screen recording.");
                }
            } else {
                launchPackage(result);
            }

            if (mScreenshotAfterLaunch) {
                try (InputStreamSource screenSource = mBaseTest.getDevice().getScreenshot()) {
                    mBaseTest.addTestArtifact(
                            packageName + "_screenshot_" + mBaseTest.getDevice().getSerialNumber(),
                            LogDataType.PNG,
                            screenSource);
                }
            }

            if (mCollectGmsVersion) {
                String gmsVersionCode =
                        DeviceUtils.getPackageVersionCode(mBaseTest.getDevice(), GMS_PACKAGE_NAME);
                String gmsVersionName =
                        DeviceUtils.getPackageVersionName(mBaseTest.getDevice(), GMS_PACKAGE_NAME);
                CLog.i(
                        "GMS core versionCode=%s, versionName=%s",
                        packageName, gmsVersionCode, gmsVersionName);
                mBaseTest.addTestArtifact(
                        String.format("%s_[GMS_versionCode=%s]", packageName, gmsVersionCode),
                        LogDataType.TEXT,
                        gmsVersionCode.getBytes());
                mBaseTest.addTestArtifact(
                        String.format("%s_[GMS_versionName=%s]", packageName, gmsVersionName),
                        LogDataType.TEXT,
                        gmsVersionName.getBytes());
            }
        } finally {
            reportResult(result);
            stopPackage(packageName);
            try {
                postLogcat(result);
            } catch (JSONException e) {
                CLog.w("Posting failed: %s.", e.getMessage());
            }

            CLog.d("Completed testing package: %s.", packageName);
        }
    }

    /**
     * Method which attempts to launch a package.
     *
     * <p>Will set the result status to success if the package could be launched. Otherwise the
     * result status will be set to failure.
     *
     * @param result the {@link CompatibilityTestResult} containing the package info.
     * @throws DeviceNotAvailableException
     */
    private void launchPackage(CompatibilityTestResult result) throws DeviceNotAvailableException {
        CLog.d("Launching package: %s.", result.packageName);

        CommandResult resetResult = resetPackage(result.packageName);
        if (resetResult.getStatus() != CommandStatus.SUCCESS) {
            result.status = CompatibilityTestResult.STATUS_ERROR;
            result.message = resetResult.getStatus() + resetResult.getStderr();
            return;
        }

        InstrumentationTest instrTest = createInstrumentationTest(result.packageName);

        FailureCollectingListener failureListener = createFailureListener();
        instrTest.run(mBaseTest.getTestInfo(), failureListener);
        CLog.d("Stack Trace: %s", failureListener.getStackTrace());

        if (failureListener.getStackTrace() != null) {
            CLog.w("Failed to launch package: %s.", result.packageName);
            result.status = CompatibilityTestResult.STATUS_FAILURE;
            result.message = failureListener.getStackTrace();
        } else {
            result.status = CompatibilityTestResult.STATUS_SUCCESS;
        }

        CLog.d("Completed launching package: %s", result.packageName);
    }

    /** Helper method which reports a test failed if the status is either a failure or an error. */
    private void reportResult(CompatibilityTestResult result) {
        String message = result.message != null ? result.message : "unknown";
        String tag = errorStatusToTag(result.status);
        if (tag != null) {
            mBaseTest.testFailed(result.status + ":" + message);
        }
    }

    private String errorStatusToTag(String status) {
        if (status.equals(CompatibilityTestResult.STATUS_ERROR)) {
            return "ERROR";
        }
        if (status.equals(CompatibilityTestResult.STATUS_FAILURE)) {
            return "FAILURE";
        }
        return null;
    }

    /** Helper method which posts the logcat. */
    private void postLogcat(CompatibilityTestResult result) throws JSONException {
        InputStreamSource stream = null;
        String header =
                String.format(
                        "%s%s%s\n",
                        CompatibilityTestResult.SEPARATOR,
                        result.toJsonString(),
                        CompatibilityTestResult.SEPARATOR);

        try (InputStreamSource logcatData = mLogcat.getLogcatData()) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                baos.write(header.getBytes());
                StreamUtil.copyStreams(logcatData.createInputStream(), baos);
                stream = new ByteArrayInputStreamSource(baos.toByteArray());
            } catch (IOException e) {
                CLog.e("error inserting compatibility test result into logcat");
                CLog.e(e);
                // fallback to logcat data
                stream = logcatData;
            }
            mBaseTest.addTestArtifact("logcat_" + result.packageName, LogDataType.LOGCAT, stream);
        } finally {
            StreamUtil.cancel(stream);
        }
    }

    protected CommandResult resetPackage(String packageName) throws DeviceNotAvailableException {
        return mBaseTest
                .getDevice()
                .executeShellV2Command(String.format("pm clear %s", packageName));
    }

    private void stopPackage(String packageName) throws DeviceNotAvailableException {
        mBaseTest.getDevice().executeShellCommand(String.format("am force-stop %s", packageName));
    }

    /** Get a FailureCollectingListener for failure listening. */
    private FailureCollectingListener createFailureListener() {
        return new FailureCollectingListener();
    }

    /**
     * Get a CompatibilityTestResult for encapsulating compatibility run results for a single app
     * package tested.
     */
    protected CompatibilityTestResult createCompatibilityTestResult() {
        return new CompatibilityTestResult();
    }

    /**
     * Creates and sets up an instrumentation test with information about the test runner as well as
     * the package being tested (provided as a parameter).
     */
    protected InstrumentationTest createInstrumentationTest(String packageBeingTested) {
        InstrumentationTest instrumentationTest = new InstrumentationTest();

        instrumentationTest.setPackageName(LAUNCH_TEST_PACKAGE);
        instrumentationTest.setConfiguration(mBaseTest.getConfiguration());
        instrumentationTest.addInstrumentationArg(PACKAGE_TO_LAUNCH, packageBeingTested);
        instrumentationTest.setRunnerName(LAUNCH_TEST_RUNNER);
        instrumentationTest.setDevice(mBaseTest.getDevice());
        instrumentationTest.addInstrumentationArg(
                APP_LAUNCH_TIMEOUT_LABEL, Integer.toString(mAppLaunchTimeoutMs));
        instrumentationTest.addInstrumentationArg(
                ENABLE_SPLASH_SCREEN, Boolean.toString(mEnableSplashScreen));

        int testTimeoutMs = BASE_INSTRUMENTATION_TEST_TIMEOUT_MS + mAppLaunchTimeoutMs * 2;
        instrumentationTest.setShellTimeout(testTimeoutMs);
        instrumentationTest.setTestTimeout(testTimeoutMs);

        return instrumentationTest;
    }

    private static final class DeviceUtils {
        static final String UNKNOWN = "Unknown";
        private static final String VIDEO_PATH_ON_DEVICE = "/sdcard/screenrecord.mp4";
        private static final int WAIT_FOR_SCREEN_RECORDING_START_MS = 10 * 1000;

        static File runWithScreenRecording(ITestDevice device, RunnerTask action)
                throws DeviceNotAvailableException {
            // Start the recording thread in background
            CompletableFuture<CommandResult> recordingFuture =
                    CompletableFuture.supplyAsync(
                                    () -> {
                                        try {
                                            return device.executeShellV2Command(
                                                    String.format(
                                                            "screenrecord %s",
                                                            VIDEO_PATH_ON_DEVICE));
                                        } catch (DeviceNotAvailableException e) {
                                            throw new RuntimeException(e);
                                        }
                                    })
                            .whenComplete(
                                    (commandResult, exception) -> {
                                        if (exception != null) {
                                            CLog.e(
                                                    "Device was lost during screenrecording: %s",
                                                    exception);
                                        } else {
                                            CLog.d(
                                                    "Screenrecord command completed: %s",
                                                    commandResult);
                                        }
                                    });

            // Make sure the recording has started
            String pid;
            long start = System.currentTimeMillis();
            while (true) {
                if (System.currentTimeMillis() - start > WAIT_FOR_SCREEN_RECORDING_START_MS) {
                    throw new RuntimeException(
                            "Unnable to start screenrecord. Pid is not detected.");
                }

                String[] pids = device.executeShellCommand("pidof screenrecord").trim().split(" ");

                if (pids.length > 0) {
                    pid = pids[0];
                    break;
                }
            }

            File video = null;

            try {
                action.run();
            } finally {
                if (pid != null) {
                    device.executeShellV2Command(String.format("kill -2 %s", pid));
                    try {
                        recordingFuture.get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                    video = device.pullFile(VIDEO_PATH_ON_DEVICE);
                    device.deleteFile(VIDEO_PATH_ON_DEVICE);
                }
            }

            return video;
        }

        interface RunnerTask {
            void run() throws DeviceNotAvailableException;
        }

        /**
         * Gets the version name of a package installed on the device.
         *
         * @param packageName The full package name to query
         * @return The package version name, or 'Unknown' if the package doesn't exist or the adb
         *     command failed.
         * @throws DeviceNotAvailableException
         */
        static String getPackageVersionName(ITestDevice device, String packageName)
                throws DeviceNotAvailableException {
            CommandResult cmdResult =
                    device.executeShellV2Command(
                            String.format("dumpsys package %s | grep versionName", packageName));

            String prefix = "versionName=";

            if (cmdResult.getStatus() != CommandStatus.SUCCESS
                    || !cmdResult.getStdout().contains(prefix)) {
                return UNKNOWN;
            }

            return cmdResult.getStdout().trim().substring(prefix.length());
        }

        /**
         * Gets the version code of a package installed on the device.
         *
         * @param packageName The full package name to query
         * @return The package version code, or 'Unknown' if the package doesn't exist or the adb
         *     command failed.
         * @throws DeviceNotAvailableException
         */
        static String getPackageVersionCode(ITestDevice device, String packageName)
                throws DeviceNotAvailableException {
            CommandResult cmdResult =
                    device.executeShellV2Command(
                            String.format("dumpsys package %s | grep versionCode", packageName));

            String prefix = "versionCode=";

            if (cmdResult.getStatus() != CommandStatus.SUCCESS
                    || !cmdResult.getStdout().contains(prefix)) {
                return UNKNOWN;
            }

            return cmdResult.getStdout().trim().split(" ")[0].substring(prefix.length());
        }
    }

    /** @param mRecordScreen the mRecordScreen to set */
    public void setRecordScreen(boolean mRecordScreen) {
        this.mRecordScreen = mRecordScreen;
    }

    /** @param mScreenshotAfterLaunch the mScreenshotAfterLaunch to set */
    public void setScreenshotAfterLaunch(boolean mScreenshotAfterLaunch) {
        this.mScreenshotAfterLaunch = mScreenshotAfterLaunch;
    }

    /** @param mCollectAppVersion the mCollectAppVersion to set */
    public void setCollectAppVersion(boolean mCollectAppVersion) {
        this.mCollectAppVersion = mCollectAppVersion;
    }

    /** @param mCollectGmsVersion the mCollectGmsVersion to set */
    public void setCollectGmsVersion(boolean mCollectGmsVersion) {
        this.mCollectGmsVersion = mCollectGmsVersion;
    }

    /** @param mEnableSplashScreen the mEnableSplashScreen to set */
    public void setEnableSplashScreen(boolean mEnableSplashScreen) {
        this.mEnableSplashScreen = mEnableSplashScreen;
    }

    /** @param mAppLaunchTimeoutMs the mAppLaunchTimeoutMs to set */
    public void setAppLaunchTimeoutMs(int mAppLaunchTimeoutMs) {
        this.mAppLaunchTimeoutMs = mAppLaunchTimeoutMs;
    }
}
